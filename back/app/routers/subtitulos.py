from typing import Annotated, Literal

from fastapi import APIRouter, HTTPException, Query, status
from pydantic import BaseModel

from app.deps import SesionBD
from app.routers.catalogo import SEGUNDOS_FICHAS, ConexionPropia, _consultar, _tmdb_de_serie
from app.routers.conexiones import ClienteHTTP
from app.services import subtitulos, titulos, tmdb, xtream

router = APIRouter(prefix="/conexiones/{conexion_id}/subtitulos", tags=["subtitulos"])

IDIOMAS_SIN_DOBLAJE = {"Inglés"}
SUBTITULADO = "Subtitulado"
Identificador = Annotated[str, Query(min_length=1, max_length=100)]


class LineaSubtitulo(BaseModel):
    inicio: float
    fin: float
    texto: str


class RespuestaSubtitulos(BaseModel):
    idioma: str
    recomendado: bool
    subtitulado: bool = False
    lineas: list[LineaSubtitulo]


@router.get("", response_model=RespuestaSubtitulos)
async def subtitulos_en_espanol(
    contexto: ConexionPropia,
    cliente: ClienteHTTP,
    sesion_bd: SesionBD,
    tipo: Literal["pelicula", "episodio"],
    id: Identificador,
    serie_id: Annotated[str | None, Query(max_length=100)] = None,
) -> RespuestaSubtitulos:
    temporada = numero = None
    if tipo == "pelicula":
        ficha = await _consultar(
            contexto.clave("pelicula", id),
            SEGUNDOS_FICHAS,
            lambda: xtream.obtener_ficha_pelicula(contexto.credenciales, id, cliente=cliente),
        )
        nombre = ficha.pelicula.nombre
        tmdb_id = ficha.tmdb_id or ficha.pelicula.tmdb_id
    else:
        if not serie_id:
            raise HTTPException(status.HTTP_422_UNPROCESSABLE_CONTENT, detail="Falta serie_id")
        ficha = await _consultar(
            contexto.clave("serie", serie_id),
            SEGUNDOS_FICHAS,
            lambda: xtream.obtener_ficha_serie(contexto.credenciales, serie_id, cliente=cliente),
        )
        episodio = next(
            (e for t in ficha.temporadas for e in t.episodios if id in (e.id, *e.alternos)), None
        )
        if episodio is None:
            raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Episodio no encontrado")
        nombre = ficha.serie.nombre
        temporada, numero = episodio.temporada, episodio.numero
        tmdb_id = await _tmdb_de_serie(contexto, cliente, ficha.serie)
    analisis = titulos.analizar(nombre)
    if not tmdb_id:
        tmdb_id = await tmdb.buscar_id(
            "pelicula" if tipo == "pelicula" else "serie", analisis.titulo, analisis.anio
        )
    lineas = (
        await subtitulos.obtener(sesion_bd, tipo, tmdb_id, temporada, numero) if tmdb_id else None
    )
    if not lineas:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="No hay subtítulos en español")
    return RespuestaSubtitulos(
        idioma=subtitulos.IDIOMA,
        recomendado=analisis.idioma in IDIOMAS_SIN_DOBLAJE,
        subtitulado=analisis.idioma == SUBTITULADO,
        lineas=lineas,
    )
