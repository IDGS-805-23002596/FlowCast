import uuid
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field

TipoEvento = Literal["inicio", "falla"]
TipoContenidoEvento = Literal["en_vivo", "pelicula", "episodio"]


class SolicitudEventoReproduccion(BaseModel):
    resultado: TipoEvento
    tipo_contenido: TipoContenidoEvento
    contenido_id: str = Field(min_length=1, max_length=100)
    serie_id: str | None = Field(default=None, max_length=100)
    perfil_id: uuid.UUID | None = None
    titulo: str | None = Field(default=None, max_length=300)
    detalle: str | None = Field(default=None, max_length=300)
    dispositivo: str = Field(min_length=1, max_length=20)
    modelo: str | None = Field(default=None, max_length=120)
    version_app: str | None = Field(default=None, max_length=40)
    codigo_error: str | None = Field(default=None, max_length=60)
    mensaje: str | None = Field(default=None, max_length=500)
    segundos_inicio: float | None = Field(default=None, ge=0, le=600)


class ConteoDispositivo(BaseModel):
    dispositivo: str
    inicios: int
    fallas: int


class ConteoDia(BaseModel):
    fecha: str
    inicios: int
    fallas: int


class ConteoContenido(BaseModel):
    tipo_contenido: str
    titulo: str
    veces: int
    ultimo_error: str | None = None


class ConteoUsuario(BaseModel):
    usuario: str
    inicios: int
    fallas: int
    ultimo_uso_en: datetime | None


class ResumenReproduccion(BaseModel):
    dias: int
    inicios: int
    fallas: int
    segundos_inicio_promedio: float | None
    por_dispositivo: list[ConteoDispositivo]
    por_hora: list[int]
    por_dia: list[ConteoDia]
    mas_vistos: list[ConteoContenido]
    mas_fallas: list[ConteoContenido]
    por_usuario: list[ConteoUsuario]


class EventoRegistrado(BaseModel):
    id: int
    resultado: str
    creado_en: datetime
    usuario: str | None
    perfil: str | None
    dispositivo: str
    modelo: str | None
    version_app: str | None
    tipo_contenido: str
    contenido_id: str
    titulo: str | None
    detalle: str | None
    codigo_error: str | None
    mensaje: str | None
    cuenta: str | None
