import asyncio
from typing import Annotated, Literal

from fastapi import APIRouter, Query
from pydantic import BaseModel

from app.deps import SesionBD
from app.routers.catalogo import ConexionPropia, Control, _modelos, _normalizar
from app.routers.conexiones import ClienteHTTP
from app.routers.inicio import _Indice
from app.routers.plataformas import _catalogo
from app.schemas import catalogo as esquemas
from app.services import cache, tmdb

router = APIRouter(prefix="/conexiones/{conexion_id}/buscar/personas", tags=["busqueda"])

SEGUNDOS_PERSONAS = 7 * 24 * 3600
SEGUNDOS_SIN_RESPUESTA = 600
DEPARTAMENTOS_DIRECCION = {"Directing", "Writing", "Creator"}


class PersonaEncontrada(BaseModel):
    id: str
    nombre: str
    foto: str | None = None
    rol: Literal["actuacion", "direccion"]
    peliculas: list[esquemas.Pelicula] = []
    series: list[esquemas.Serie] = []


async def _guardado(clave: str, fabrica) -> object:
    valor = await cache.obtener_o_calcular(clave, fabrica, SEGUNDOS_PERSONAS)
    if valor is None:
        cache.guardar(clave, None, SEGUNDOS_SIN_RESPUESTA)
    return valor


def _rol(persona: tmdb.Persona, creditos: tmdb.CreditosPersona) -> str:
    dirige = any(creditos.direccion.values())
    if persona.departamento in DEPARTAMENTOS_DIRECCION and dirige:
        return "direccion"
    return "actuacion"


@router.get("", response_model=list[PersonaEncontrada])
async def buscar_personas(
    contexto: ConexionPropia,
    cliente: ClienteHTTP,
    sesion_bd: SesionBD,
    control: Control,
    q: Annotated[str, Query(min_length=3, max_length=80, description="Nombre a buscar")],
) -> list[PersonaEncontrada]:
    necesidad = _normalizar(q.strip())
    if len(necesidad) < 3 or not tmdb.disponible():
        return []
    personas = await _guardado(
        f"tmdb:personas:{necesidad}", lambda: tmdb.buscar_personas(q.strip())
    )
    personas = [p for p in personas or [] if necesidad in _normalizar(p.nombre)]
    if not personas:
        return []
    datos, creditos = await asyncio.gather(
        _catalogo(contexto, cliente, sesion_bd, control),
        asyncio.gather(
            *(
                _guardado(f"tmdb:creditos:{p.id}", lambda p=p: tmdb.creditos_persona(p.id))
                for p in personas
            )
        ),
    )
    indices = {"pelicula": _Indice(datos["peliculas"]), "serie": _Indice(datos["series"])}
    encontradas = []
    for persona, credito in zip(personas, creditos, strict=True):
        if credito is None:
            continue
        rol = _rol(persona, credito)
        fuente = credito.direccion if rol == "direccion" else credito.actuacion
        peliculas = indices["pelicula"].de_tmdb(fuente["pelicula"], set())
        series = indices["serie"].de_tmdb(fuente["serie"], set())
        if not peliculas and not series:
            continue
        encontradas.append(
            PersonaEncontrada(
                id=persona.id,
                nombre=persona.nombre,
                foto=persona.foto,
                rol=rol,
                peliculas=_modelos(
                    esquemas.Pelicula,
                    peliculas,
                    datos["bloqueadas"]["pelicula"],
                    indices["pelicula"].grupos,
                ),
                series=_modelos(
                    esquemas.Serie, series, datos["bloqueadas"]["serie"], indices["serie"].grupos
                ),
            )
        )
    return encontradas
