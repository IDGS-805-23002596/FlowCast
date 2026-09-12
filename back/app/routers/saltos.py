from typing import Annotated

from fastapi import APIRouter, HTTPException, Query, status
from pydantic import BaseModel

from app.routers.catalogo import SEGUNDOS_FICHAS, ConexionPropia, _consultar, _tmdb_de_serie
from app.routers.conexiones import ClienteHTTP
from app.services import cache, skipdb, titulos, tmdb, xtream

router = APIRouter(prefix="/conexiones/{conexion_id}/saltos", tags=["saltos"])

SEGUNDOS_IMDB = 30 * 24 * 3600
SEGUNDOS_SALTOS = 24 * 3600
SEGUNDOS_SIN_RESPUESTA = 600
Identificador = Annotated[str, Query(min_length=1, max_length=100)]


class RespuestaSaltos(BaseModel):
    intro_inicio: float | None = None
    intro_fin: float | None = None
    creditos: float | None = None
    fuente: str | None = None


async def _imdb(tmdb_id: str) -> str | None:
    clave = f"tmdb:imdb:serie:{tmdb_id}"
    imdb_id = await cache.obtener_o_calcular(
        clave, lambda: tmdb.imdb_de_serie(tmdb_id), SEGUNDOS_IMDB
    )
    if imdb_id is None:
        cache.invalidar(clave)
    return imdb_id or None


async def _saltos(imdb_id: str, temporada: int, numero: int, duracion: float) -> skipdb.Saltos:
    clave = f"skipdb:{imdb_id}:{temporada}:{numero}:{round(duracion / 10)}"
    saltos = await cache.obtener_o_calcular(
        clave, lambda: skipdb.consultar(imdb_id, temporada, numero, duracion), SEGUNDOS_SALTOS
    )
    if saltos is None:
        cache.guardar(clave, skipdb.Saltos(), SEGUNDOS_SIN_RESPUESTA)
        return skipdb.Saltos()
    return saltos


@router.get("", response_model=RespuestaSaltos)
async def saltos_de_episodio(
    contexto: ConexionPropia,
    cliente: ClienteHTTP,
    serie_id: Identificador,
    episodio_id: Identificador,
    duracion: Annotated[float, Query(ge=60, le=6 * 3600)],
) -> RespuestaSaltos:
    ficha = await _consultar(
        contexto.clave("serie", serie_id),
        SEGUNDOS_FICHAS,
        lambda: xtream.obtener_ficha_serie(contexto.credenciales, serie_id, cliente=cliente),
    )
    episodio = next(
        (e for t in ficha.temporadas for e in t.episodios if episodio_id in (e.id, *e.alternos)),
        None,
    )
    if episodio is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Episodio no encontrado")
    tmdb_id = await _tmdb_de_serie(contexto, cliente, ficha.serie)
    if not tmdb_id:
        analisis = titulos.analizar(ficha.serie.nombre)
        tmdb_id = await tmdb.buscar_id("serie", analisis.titulo, analisis.anio)
    imdb_id = await _imdb(tmdb_id) if tmdb_id else None
    if not imdb_id:
        return RespuestaSaltos()
    saltos = await _saltos(imdb_id, episodio.temporada, episodio.numero, duracion)
    if saltos.intro_inicio is None and saltos.creditos is None:
        return RespuestaSaltos()
    return RespuestaSaltos(
        intro_inicio=saltos.intro_inicio,
        intro_fin=saltos.intro_fin,
        creditos=saltos.creditos,
        fuente="SkipDB",
    )
