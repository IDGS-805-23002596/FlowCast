import asyncio
from collections.abc import Callable, Iterable
from datetime import UTC, datetime, timedelta

import httpx2 as httpx
from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import TrailerTmdb, VideoNoIncrustable
from app.services import titulos, tmdb, youtube

MAXIMO_REVISADOS = 5
CONSULTAS_SIMULTANEAS = 8
TAMANO_LOTE = 200
DIAS_SIN_TRAILER = 7
DIAS_CON_TRAILER = 30
MAXIMO_RENOVADOS_POR_NOCHE = 5000
_SIN_RESPUESTA = object()


def referencia(tmdb_id: str | None, nombre: str | None) -> str | None:
    if tmdb_id:
        return tmdb_id
    if not nombre:
        return None
    titulo = titulos.analizar(nombre)
    return f"nombre:{titulo.titulo.casefold()}|{titulo.anio or ''}"


async def no_incrustables(sesion_bd: AsyncSession) -> set[str]:
    filas = await sesion_bd.execute(select(VideoNoIncrustable.video_id))
    return {video_id for (video_id,) in filas}


async def buscar(
    tipo: str,
    ref: str,
    cliente: httpx.AsyncClient | None = None,
    excluir: set[str] | frozenset[str] = frozenset(),
) -> tmdb.Trailer | None | object:
    tmdb_id: str | None = ref
    if ref.startswith("nombre:"):
        titulo, _, anio = ref.removeprefix("nombre:").rpartition("|")
        tmdb_id = await tmdb.buscar_id(tipo, titulo, int(anio) if anio else None, cliente)
        if not tmdb_id:
            return None
    candidatos = await tmdb.trailers(tipo, tmdb_id, cliente)
    if candidatos is None:
        return _SIN_RESPUESTA
    utiles = [c for c in candidatos if c.video_id not in excluir]
    for candidato in utiles[:MAXIMO_REVISADOS]:
        if await youtube.se_puede_incrustar(candidato.video_id, cliente) is not False:
            return candidato
    return None


async def _guardar(
    sesion_bd: AsyncSession, filas: list[tuple[str, str, tmdb.Trailer | None]]
) -> None:
    if not filas:
        return
    consulta = insert(TrailerTmdb).values(
        [
            {
                "tipo_contenido": tipo,
                "referencia": ref,
                "video_id": trailer.video_id if trailer else None,
                "idioma": trailer.idioma if trailer else None,
                "consultado_en": datetime.now(UTC),
            }
            for tipo, ref, trailer in filas
        ]
    )
    await sesion_bd.execute(
        consulta.on_conflict_do_update(
            index_elements=[TrailerTmdb.tipo_contenido, TrailerTmdb.referencia],
            set_={
                "video_id": consulta.excluded.video_id,
                "idioma": consulta.excluded.idioma,
                "consultado_en": consulta.excluded.consultado_en,
            },
        )
    )
    await sesion_bd.commit()


async def obtener(
    sesion_bd: AsyncSession, tipo: str, tmdb_id: str | None, nombre: str | None
) -> tmdb.Trailer | None:
    ref = referencia(tmdb_id, nombre)
    if not ref or not tmdb.disponible():
        return None
    guardado = await sesion_bd.get(TrailerTmdb, (tipo, ref))
    if guardado is not None:
        return tmdb.Trailer(guardado.video_id, guardado.idioma) if guardado.video_id else None
    encontrado = await buscar(tipo, ref, excluir=await no_incrustables(sesion_bd))
    if encontrado is _SIN_RESPUESTA:
        return None
    await _guardar(sesion_bd, [(tipo, ref, encontrado)])
    return encontrado


async def reportar_fallo(
    sesion_bd: AsyncSession, video_id: str, codigo: str | None
) -> tmdb.Trailer | None:
    await sesion_bd.execute(
        insert(VideoNoIncrustable)
        .values(video_id=video_id, codigo=codigo)
        .on_conflict_do_nothing(index_elements=[VideoNoIncrustable.video_id])
    )
    await sesion_bd.commit()
    afectados = (
        await sesion_bd.execute(
            select(TrailerTmdb.tipo_contenido, TrailerTmdb.referencia).where(
                TrailerTmdb.video_id == video_id
            )
        )
    ).all()
    if not afectados:
        return None
    excluir = await no_incrustables(sesion_bd)
    reemplazo: tmdb.Trailer | None = None
    for tipo, ref in afectados:
        encontrado = await buscar(tipo, ref, excluir=excluir)
        if encontrado is _SIN_RESPUESTA:
            encontrado = None
        await _guardar(sesion_bd, [(tipo, ref, encontrado)])
        reemplazo = reemplazo or encontrado
    return reemplazo


async def pendientes(
    sesion_bd: AsyncSession, titulos_catalogo: Iterable[tuple[str, str]]
) -> list[tuple[str, str]]:
    ahora = datetime.now(UTC)
    filas = await sesion_bd.execute(
        select(
            TrailerTmdb.tipo_contenido,
            TrailerTmdb.referencia,
            TrailerTmdb.video_id,
            TrailerTmdb.consultado_en,
        )
    )
    guardados = {(tipo, ref): (video_id, consultado) for tipo, ref, video_id, consultado in filas}
    nuevos = []
    vencidos = []
    for titulo in sorted(set(titulos_catalogo)):
        guardado = guardados.get(titulo)
        if guardado is None:
            nuevos.append(titulo)
            continue
        video_id, consultado = guardado
        dias = DIAS_CON_TRAILER if video_id else DIAS_SIN_TRAILER
        if consultado < ahora - timedelta(days=dias):
            vencidos.append((consultado, titulo))
    vencidos.sort()
    return nuevos + [titulo for _, titulo in vencidos[:MAXIMO_RENOVADOS_POR_NOCHE]]


async def sincronizar(
    sesion_bd: AsyncSession,
    titulos_pendientes: list[tuple[str, str]],
    cliente: httpx.AsyncClient,
    avisar: Callable[[int, int], None] | None = None,
) -> tuple[int, int]:
    semaforo = asyncio.Semaphore(CONSULTAS_SIMULTANEAS)
    con_trailer = sin_trailer = 0
    excluir = await no_incrustables(sesion_bd)

    async def consultar(tipo: str, ref: str) -> tuple[str, str, tmdb.Trailer | None] | None:
        async with semaforo:
            encontrado = await buscar(tipo, ref, cliente, excluir)
        if encontrado is _SIN_RESPUESTA:
            return None
        return tipo, ref, encontrado

    for inicio in range(0, len(titulos_pendientes), TAMANO_LOTE):
        tramo = titulos_pendientes[inicio : inicio + TAMANO_LOTE]
        resultados = await asyncio.gather(*(consultar(tipo, ref) for tipo, ref in tramo))
        filas = [resultado for resultado in resultados if resultado is not None]
        await _guardar(sesion_bd, filas)
        con_trailer += sum(1 for _, _, trailer in filas if trailer)
        sin_trailer += sum(1 for _, _, trailer in filas if not trailer)
        if avisar:
            avisar(inicio + len(tramo), len(titulos_pendientes))
    return con_trailer, sin_trailer


async def resumen(sesion_bd: AsyncSession) -> tuple[int, int]:
    filas = await sesion_bd.execute(select(TrailerTmdb.video_id))
    valores = [video_id for (video_id,) in filas.all()]
    return sum(1 for v in valores if v), sum(1 for v in valores if not v)
