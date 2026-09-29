import asyncio
from collections.abc import Iterator
from typing import Any

import httpx2 as httpx
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import delete

from app.config import configuracion
from app.db import FabricaSesiones, motor
from app.main import aplicacion
from app.models import SubtituloExterno
from app.routers.conexiones import obtener_cliente_http
from app.services import cache, subtitulos, tmdb, xtream
from tests.ayudantes import borrar_usuarios, crear_conexion, registrar
from tests.test_catalogo import LOGIN_OK, RESPUESTAS

CORREOS = ["subtitulos-prueba@ejemplo.com"]
REFERENCIAS = ["123", "77:1:1", "77:2:1"]

SRT = (
    "﻿1\r\n00:00:01,500 --> 00:00:03,000\r\n<i>Hola</i>\r\n{\\an8}mundo\r\n\r\n"
    "2\n00:01:02.25 --> 00:01:04,000\nAdiós\n\n"
    "3\n00:00:05,000 --> 00:00:04,000\nAl revés\n\n"
    "basura sin tiempo\n"
)


async def _borrar_guardados() -> None:
    async with FabricaSesiones() as sesion_bd:
        await sesion_bd.execute(
            delete(SubtituloExterno).where(SubtituloExterno.referencia.in_(REFERENCIAS))
        )
        await sesion_bd.commit()
    await motor.dispose()


@pytest.fixture(autouse=True)
def entorno(monkeypatch: pytest.MonkeyPatch) -> Iterator[list]:
    borrar_usuarios(CORREOS)
    asyncio.run(_borrar_guardados())
    cache.vaciar()
    llamadas: list = []

    def responder(peticion: httpx.Request) -> httpx.Response:
        if peticion.url.params.get("password") != "buena":
            return httpx.Response(200, json={"user_info": {"auth": 0}})
        accion = peticion.url.params.get("action")
        if accion is None:
            return httpx.Response(200, json=LOGIN_OK)
        return httpx.Response(200, json=RESPUESTAS.get(accion, []))

    async def cliente_falso():
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(responder), headers=xtream.CABECERAS
        ) as c:
            yield c

    async def buscar(tmdb_id: str, temporada: Any, episodio: Any, cliente: Any) -> Any:
        llamadas.append(("buscar", tmdb_id, temporada, episodio))
        return None if (temporada, episodio) == (2, 1) else f"f{tmdb_id}"

    async def descargar(archivo_id: str, cliente: Any) -> str:
        llamadas.append(("descargar", archivo_id))
        return SRT

    async def buscar_id(tipo: str, titulo: str, anio: Any, cliente: Any = None) -> str:
        return "77"

    monkeypatch.setattr(configuracion, "opensubtitles_clave_api", "clave-de-prueba")
    monkeypatch.setattr(subtitulos, "buscar", buscar)
    monkeypatch.setattr(subtitulos, "descargar", descargar)
    monkeypatch.setattr(tmdb, "buscar_id", buscar_id)
    aplicacion.dependency_overrides[obtener_cliente_http] = cliente_falso
    yield llamadas
    aplicacion.dependency_overrides.pop(obtener_cliente_http, None)
    borrar_usuarios(CORREOS)
    asyncio.run(_borrar_guardados())
    cache.vaciar()


def prueba_leer_srt_limpia_etiquetas_y_ordena() -> None:
    assert subtitulos.leer_srt(SRT) == [
        {"inicio": 1.5, "fin": 3.0, "texto": "Hola\nmundo"},
        {"inicio": 62.25, "fin": 64.0, "texto": "Adiós"},
    ]


def prueba_elegir_prefiere_humanos_y_confiables() -> None:
    def resultado(archivo: int, idioma: str = "es", **extras: Any) -> dict:
        return {"attributes": {"language": idioma, "files": [{"file_id": archivo}], **extras}}

    resultados = [
        resultado(1, download_count=9000, machine_translated=True),
        resultado(2, "en", download_count=99999),
        resultado(3, download_count=10),
        resultado(4, download_count=500, from_trusted=True),
        {"attributes": {"language": "es", "files": []}},
    ]
    assert subtitulos.elegir(resultados) == "4"
    assert subtitulos.elegir(resultados[:3]) == "3"
    assert subtitulos.elegir([resultados[1]]) is None
    variantes = [resultado(5, "sp", download_count=900), resultado(6, "ea", download_count=50)]
    assert subtitulos.elegir(variantes) == "6"


def prueba_subtitulos_de_pelicula_y_episodio(entorno: list) -> None:
    with TestClient(aplicacion) as cliente:
        cabeceras = registrar(cliente, CORREOS[0])
        base = f"/conexiones/{crear_conexion(cliente, cabeceras)}/subtitulos"
        pelicula = cliente.get(base, params={"tipo": "pelicula", "id": "501"}, headers=cabeceras)
        otra_vez = cliente.get(base, params={"tipo": "pelicula", "id": "501"}, headers=cabeceras)
        episodio = cliente.get(
            base, params={"tipo": "episodio", "id": "9001", "serie_id": "900"}, headers=cabeceras
        )
        sin_subtitulos = cliente.get(
            base, params={"tipo": "episodio", "id": "9002", "serie_id": "900"}, headers=cabeceras
        )
        sin_serie = cliente.get(base, params={"tipo": "episodio", "id": "9001"}, headers=cabeceras)
        ajeno = cliente.get(
            base, params={"tipo": "episodio", "id": "1", "serie_id": "900"}, headers=cabeceras
        )
        sin_sesion = cliente.get(base, params={"tipo": "pelicula", "id": "501"})
    assert pelicula.status_code == 200, pelicula.text
    assert pelicula.json()["idioma"] == "es"
    assert pelicula.json()["recomendado"] is False
    assert pelicula.json()["subtitulado"] is False
    assert pelicula.json()["lineas"][0] == {"inicio": 1.5, "fin": 3.0, "texto": "Hola\nmundo"}
    assert otra_vez.json() == pelicula.json()
    assert episodio.status_code == 200, episodio.text
    assert sin_subtitulos.status_code == 404
    assert sin_serie.status_code == 422
    assert ajeno.status_code == 404
    assert sin_sesion.status_code == 401
    assert entorno == [
        ("buscar", "123", None, None),
        ("descargar", "f123"),
        ("buscar", "77", 1, 1),
        ("descargar", "f77"),
        ("buscar", "77", 2, 1),
    ]
