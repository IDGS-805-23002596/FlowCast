import asyncio
from datetime import UTC, datetime, timedelta
from typing import Any, Literal

from fastapi import APIRouter
from pydantic import BaseModel
from sqlalchemy import func, select

from app.deps import SesionBD
from app.models import ConexionXtream, Favorito, ProgresoReproduccion
from app.routers.catalogo import ConexionPropia, Control, _agrupar, _modelos
from app.routers.conexiones import ClienteHTTP
from app.routers.perfiles import PerfilPropio
from app.routers.plataformas import _catalogo
from app.schemas import catalogo as esquemas
from app.services import cache, titulos, tmdb, trailers

router = APIRouter(prefix="/conexiones/{conexion_id}/inicio", tags=["inicio"])

SEGUNDOS_TENDENCIAS = 6 * 3600
SEGUNDOS_RECOMENDACIONES = 24 * 3600
MINIMO_POR_FILA = 4
MAXIMO_POR_FILA = 30
FILAS_PORQUE_VISTE = 2
DIAS_MAS_VISTO = 14
TIPOS = ("pelicula", "serie")
PORTADAS_POR_FILA = 4
SEGUNDOS_LOGO = 7 * 24 * 3600


class Portada(BaseModel):
    trailer_youtube: str | None = None
    trailer_idioma: str | None = None
    logo: str | None = None


class FilaInicio(BaseModel):
    clave: str
    titulo: str
    tipo: Literal["pelicula", "serie"]
    peliculas: list[esquemas.Pelicula] = []
    series: list[esquemas.Serie] = []
    portadas: dict[str, Portada] = {}


class _Indice:
    def __init__(self, elementos: list[Any]):
        self.elementos, self.grupos = _agrupar(elementos)
        self.por_id: dict[str, Any] = {}
        self.por_tmdb: dict[str, Any] = {}
        for elemento in self.elementos:
            grupo = self.grupos.get(elemento.id)
            versiones = [v for v, _ in grupo.versiones] if grupo else [elemento]
            for version in versiones:
                self.por_id.setdefault(version.id, elemento)
                if version.tmdb_id:
                    self.por_tmdb.setdefault(version.tmdb_id, elemento)

    def de_tmdb(self, ids: list[str], excluir: set[str]) -> list[Any]:
        encontrados = []
        for tmdb_id in ids:
            elemento = self.por_tmdb.get(tmdb_id)
            if elemento is not None and elemento.id not in excluir and elemento not in encontrados:
                encontrados.append(elemento)
        return encontrados[:MAXIMO_POR_FILA]


async def _vistos(
    sesion_bd: SesionBD, perfil_id: Any, conexion_id: Any
) -> list[tuple[str, str, datetime]]:
    filas = await sesion_bd.execute(
        select(
            ProgresoReproduccion.tipo_contenido,
            ProgresoReproduccion.stream_id,
            ProgresoReproduccion.serie_id,
            ProgresoReproduccion.actualizado_en,
        )
        .where(
            ProgresoReproduccion.perfil_id == perfil_id,
            ProgresoReproduccion.conexion_id == conexion_id,
        )
        .order_by(ProgresoReproduccion.actualizado_en.desc())
        .limit(300)
    )
    vistos: dict[tuple[str, str], datetime] = {}
    for tipo, stream_id, serie_id, fecha in filas:
        clave = ("serie", serie_id) if tipo == "episodio" else ("pelicula", stream_id)
        if clave[1] and clave not in vistos:
            vistos[clave] = fecha
    return [(tipo, elemento_id, fecha) for (tipo, elemento_id), fecha in vistos.items()]


async def _mas_vistos(sesion_bd: SesionBD, huella: str) -> list[tuple[str, str]]:
    desde = datetime.now(UTC) - timedelta(days=DIAS_MAS_VISTO)
    elemento = func.coalesce(ProgresoReproduccion.serie_id, ProgresoReproduccion.stream_id)
    filas = await sesion_bd.execute(
        select(
            ProgresoReproduccion.tipo_contenido,
            elemento,
            func.count(func.distinct(ProgresoReproduccion.perfil_id)),
        )
        .join(ConexionXtream, ConexionXtream.id == ProgresoReproduccion.conexion_id)
        .where(ConexionXtream.huella == huella, ProgresoReproduccion.actualizado_en >= desde)
        .group_by(ProgresoReproduccion.tipo_contenido, elemento)
        .order_by(func.count(func.distinct(ProgresoReproduccion.perfil_id)).desc())
        .limit(200)
    )
    return [
        ("serie" if tipo == "episodio" else "pelicula", elemento_id)
        for tipo, elemento_id, _ in filas
    ]


async def _series_seguidas(
    sesion_bd: SesionBD, perfil_id: Any, conexion_id: Any
) -> list[tuple[str, datetime]]:
    filas = await sesion_bd.execute(
        select(Favorito.stream_id, Favorito.creado_en).where(
            Favorito.perfil_id == perfil_id,
            Favorito.conexion_id == conexion_id,
            Favorito.tipo_contenido == "serie",
        )
    )
    return list(filas)


def _nombre(elemento: Any) -> str:
    return titulos.analizar(elemento.nombre).titulo


async def _tendencias(tipo: str) -> list[str]:
    return (
        await cache.obtener_o_calcular(
            f"tmdb:tendencias:{tipo}",
            lambda: tmdb.tendencias(tipo),
            SEGUNDOS_TENDENCIAS,
        )
        or []
    )


async def _recomendaciones(tipo: str, tmdb_id: str) -> list[str]:
    return (
        await cache.obtener_o_calcular(
            f"tmdb:recomendaciones:{tipo}:{tmdb_id}",
            lambda: tmdb.recomendaciones(tipo, tmdb_id),
            SEGUNDOS_RECOMENDACIONES,
        )
        or []
    )


async def _logo(tipo: str, tmdb_id: str) -> str | None:
    return (
        await cache.obtener_o_calcular(
            f"tmdb:logotipo:{tipo}:{tmdb_id}", lambda: tmdb.logo(tipo, tmdb_id), SEGUNDOS_LOGO
        )
        or None
    )


async def _portadas(sesion_bd: SesionBD, tipo: str, elementos: list[Any]) -> dict[str, Portada]:
    elegidos = elementos[:PORTADAS_POR_FILA]
    logos = await asyncio.gather(
        *(_logo(tipo, e.tmdb_id) if e.tmdb_id else asyncio.sleep(0) for e in elegidos)
    )
    portadas = {}
    for elemento, logo in zip(elegidos, logos, strict=True):
        trailer = await trailers.obtener(sesion_bd, tipo, elemento.tmdb_id, elemento.nombre)
        portadas[elemento.id] = Portada(
            trailer_youtube=trailer.video_id if trailer else None,
            trailer_idioma=trailer.idioma if trailer else None,
            logo=logo,
        )
    return portadas


def _recientes(indice: _Indice) -> list[Any]:
    con_fecha = [e for e in indice.elementos if e.agregado_en]
    con_fecha.sort(key=lambda e: e.agregado_en, reverse=True)
    return con_fecha[:MAXIMO_POR_FILA]


@router.get("", response_model=list[FilaInicio])
async def filas_de_inicio(
    contexto: ConexionPropia,
    cliente: ClienteHTTP,
    sesion_bd: SesionBD,
    control: Control,
    perfil: PerfilPropio,
) -> list[FilaInicio]:
    if perfil.es_infantil:
        return []
    datos = await _catalogo(contexto, cliente, sesion_bd, control)
    indices = {"pelicula": _Indice(datos["peliculas"]), "serie": _Indice(datos["series"])}
    conexion_id = contexto.conexion.id
    vistos = await _vistos(sesion_bd, perfil.id, conexion_id)
    ids_vistos = {
        tipo: {
            indices[tipo].por_id[i].id
            for t, i, _ in vistos
            if t == tipo and i in indices[tipo].por_id
        }
        for tipo in TIPOS
    }

    def fila(
        clave: str, titulo: str, tipo: str, elementos: list[Any], minimo: int = MINIMO_POR_FILA
    ) -> FilaInicio | None:
        if len(elementos) < minimo:
            return None
        indice = indices[tipo]
        modelos = _modelos(
            esquemas.Pelicula if tipo == "pelicula" else esquemas.Serie,
            elementos,
            datos["bloqueadas"][tipo],
            indice.grupos,
        )
        campo = "peliculas" if tipo == "pelicula" else "series"
        return FilaInicio(clave=clave, titulo=titulo, tipo=tipo, **{campo: modelos})

    porque_viste: list[FilaInicio | None] = []
    usados: set[str] = set()
    for tipo, elemento_id, _ in vistos:
        if len(porque_viste) >= FILAS_PORQUE_VISTE:
            break
        visto = indices[tipo].por_id.get(elemento_id)
        if visto is None or not visto.tmdb_id or visto.id in usados:
            continue
        usados.add(visto.id)
        sugeridos = indices[tipo].de_tmdb(
            await _recomendaciones(tipo, visto.tmdb_id), ids_vistos[tipo] | {visto.id}
        )
        nueva = fila(f"porque-{tipo}-{visto.id}", f"Porque viste {_nombre(visto)}", tipo, sugeridos)
        if nueva:
            porque_viste.append(nueva)

    ultima_vez = {i: fecha for t, i, fecha in vistos if t == "serie"}
    for serie_id, desde in await _series_seguidas(sesion_bd, perfil.id, conexion_id):
        ultima_vez[serie_id] = max(ultima_vez.get(serie_id, desde), desde)
    con_novedades = []
    for serie_id, fecha in ultima_vez.items():
        serie = indices["serie"].por_id.get(serie_id)
        if serie and serie.agregado_en and serie.agregado_en > fecha and serie not in con_novedades:
            con_novedades.append(serie)
    con_novedades.sort(key=lambda s: s.agregado_en, reverse=True)

    populares: dict[str, list[Any]] = {"pelicula": [], "serie": []}
    for tipo, elemento_id in await _mas_vistos(sesion_bd, contexto.conexion.huella):
        elemento = indices[tipo].por_id.get(elemento_id)
        if elemento is not None and elemento not in populares[tipo]:
            populares[tipo].append(elemento)

    tendencias_peliculas = indices["pelicula"].de_tmdb(await _tendencias("pelicula"), set())
    tendencias_series = indices["serie"].de_tmdb(await _tendencias("serie"), set())

    filas = [
        porque_viste[0] if porque_viste else None,
        fila("nuevos-episodios", "Nuevos episodios de tus series", "serie", con_novedades, 1),
        fila(
            "tendencias-peliculas",
            "Tendencias de la semana · Películas",
            "pelicula",
            tendencias_peliculas,
        ),
        porque_viste[1] if len(porque_viste) > 1 else None,
        fila("tendencias-series", "Tendencias de la semana · Series", "serie", tendencias_series),
        fila(
            "mas-visto-peliculas",
            "Lo más visto en FlowCast · Películas",
            "pelicula",
            populares["pelicula"][:MAXIMO_POR_FILA],
        ),
        fila(
            "mas-visto-series",
            "Lo más visto en FlowCast · Series",
            "serie",
            populares["serie"][:MAXIMO_POR_FILA],
        ),
        fila(
            "recientes-peliculas",
            "Recién agregadas · Películas",
            "pelicula",
            _recientes(indices["pelicula"]),
        ),
        fila(
            "recientes-series", "Recién agregadas · Series", "serie", _recientes(indices["serie"])
        ),
    ]
    resultado = [f for f in filas if f is not None]
    for actual in resultado:
        if actual.clave == "tendencias-peliculas":
            actual.portadas = await _portadas(sesion_bd, "pelicula", tendencias_peliculas)
        elif actual.clave == "tendencias-series":
            actual.portadas = await _portadas(sesion_bd, "serie", tendencias_series)
    return resultado
