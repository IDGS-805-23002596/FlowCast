from datetime import datetime

from pydantic import BaseModel

from app.schemas.catalogo import Canal


class LigaDeporte(BaseModel):
    clave: str
    nombre: str


class Deporte(BaseModel):
    clave: str
    nombre: str
    ligas: list[LigaDeporte]


class EquipoLiga(BaseModel):
    id: str
    nombre: str
    logo: str | None
    favorito: bool = False


class EquipoFavoritoRespuesta(BaseModel):
    liga: str
    liga_nombre: str
    deporte: str
    equipo_id: str
    nombre: str
    logo: str | None


class EquipoPartido(BaseModel):
    nombre: str
    logo: str | None
    marcador: str | None


class Partido(BaseModel):
    id: str
    deporte: str
    liga: str
    liga_clave: str | None = None
    estado: str
    detalle: str
    inicio: datetime | None
    local: EquipoPartido
    visitante: EquipoPartido
    favorito: bool
    canales: list[Canal]


class RespuestaDeportes(BaseModel):
    actualizado_en: datetime
    deportes: list[Deporte]
    mis_equipos: int = 0
    partidos: list[Partido]
