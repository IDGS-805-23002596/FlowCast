from collections.abc import Iterator

import httpx2 as httpx
import pytest
from fastapi.testclient import TestClient

from app.main import aplicacion
from app.routers.conexiones import obtener_cliente_http
from app.services import cache, xtream
from tests.ayudantes import borrar_usuarios, crear_conexion, registrar
from tests.test_catalogo import LOGIN_OK, RESPUESTAS

CORREOS = ["busqueda@ejemplo.com"]


@pytest.fixture(autouse=True)
def usuarios_limpios() -> Iterator[None]:
    borrar_usuarios(CORREOS)
    cache.vaciar()
    yield
    borrar_usuarios(CORREOS)
    cache.vaciar()


@pytest.fixture(autouse=True)
def proveedor() -> Iterator[list[str]]:
    llamadas: list[str] = []

    def responder(peticion: httpx.Request) -> httpx.Response:
        if peticion.url.params.get("password") != "buena":
            return httpx.Response(200, json={"user_info": {"auth": 0}})
        accion = peticion.url.params.get("action")
        if accion is None:
            return httpx.Response(200, json=LOGIN_OK)
        llamadas.append(accion)
        return httpx.Response(200, json=RESPUESTAS.get(accion, []))

    async def cliente_falso():
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(responder), headers=xtream.CABECERAS
        ) as c:
            yield c

    aplicacion.dependency_overrides[obtener_cliente_http] = cliente_falso
    yield llamadas
    aplicacion.dependency_overrides.pop(obtener_cliente_http, None)


@pytest.fixture
def cliente() -> Iterator[TestClient]:
    with TestClient(aplicacion) as cliente_http:
        yield cliente_http


@pytest.fixture
def sesion(cliente: TestClient) -> dict:
    cabeceras = registrar(cliente, CORREOS[0])
    conexion_id = crear_conexion(cliente, cabeceras)
    return {"cabeceras": cabeceras, "ruta": f"/conexiones/{conexion_id}/buscar"}


def prueba_busca_en_canales_peliculas_y_series(
    cliente: TestClient, sesion: dict, proveedor: list[str]
) -> None:
    respuesta = cliente.get(sesion["ruta"], params={"q": "uno"}, headers=sesion["cabeceras"])
    assert respuesta.status_code == 200, respuesta.text
    datos = respuesta.json()
    assert [c["nombre"] for c in datos["canales"]] == ["Canal Uno"]
    assert [p["nombre"] for p in datos["peliculas"]] == ["Película Uno"]
    assert isinstance(datos["series"], list)
    assert sorted(proveedor) == [
        "get_live_categories",
        "get_live_streams",
        "get_series",
        "get_series_categories",
        "get_vod_categories",
        "get_vod_streams",
    ]

    cliente.get(sesion["ruta"], params={"q": "dos"}, headers=sesion["cabeceras"])
    assert len(proveedor) == 6


def prueba_ignora_mayusculas_y_acentos(cliente: TestClient, sesion: dict) -> None:
    datos = cliente.get(
        sesion["ruta"], params={"q": "PELICULA"}, headers=sesion["cabeceras"]
    ).json()
    assert [p["nombre"] for p in datos["peliculas"]] == ["Película Uno"]
    assert datos["canales"] == []


def prueba_texto_demasiado_corto(cliente: TestClient, sesion: dict) -> None:
    assert (
        cliente.get(sesion["ruta"], params={"q": "u"}, headers=sesion["cabeceras"]).status_code
        == 422
    )
    assert cliente.get(sesion["ruta"], params={"q": "uno"}).status_code == 401
