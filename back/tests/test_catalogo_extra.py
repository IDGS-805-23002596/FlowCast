from collections.abc import Iterator

import httpx2 as httpx
import pytest
from fastapi.testclient import TestClient

from app.main import aplicacion
from app.routers.conexiones import obtener_cliente_http
from app.services import cache, xtream
from tests.ayudantes import borrar_usuarios, crear_conexion, registrar
from tests.test_catalogo import LOGIN_OK, RESPUESTAS

CORREOS = ["catalogo-extra@ejemplo.com"]


@pytest.fixture(autouse=True)
def limpieza() -> Iterator[None]:
    borrar_usuarios(CORREOS)
    cache.vaciar()
    yield
    borrar_usuarios(CORREOS)
    cache.vaciar()


@pytest.fixture(autouse=True)
def proveedor() -> Iterator[None]:
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

    aplicacion.dependency_overrides[obtener_cliente_http] = cliente_falso
    yield
    aplicacion.dependency_overrides.pop(obtener_cliente_http, None)


@pytest.fixture
def cliente() -> Iterator[TestClient]:
    with TestClient(aplicacion) as cliente_http:
        yield cliente_http


@pytest.fixture
def sesion(cliente: TestClient) -> dict:
    cabeceras = registrar(cliente, CORREOS[0])
    return {"cabeceras": cabeceras, "base": f"/conexiones/{crear_conexion(cliente, cabeceras)}"}


def prueba_categorias_con_totales(cliente: TestClient, sesion: dict) -> None:
    sin = cliente.get(
        f"{sesion['base']}/categorias?tipo=en_vivo", headers=sesion["cabeceras"]
    ).json()
    assert [c["total"] for c in sin] == [None, None]
    con = cliente.get(
        f"{sesion['base']}/categorias?tipo=en_vivo&con_totales=true", headers=sesion["cabeceras"]
    ).json()
    assert [(c["nombre"], c["total"]) for c in con] == [("Noticias", 1), ("Deportes", 1)]
    peliculas = cliente.get(
        f"{sesion['base']}/categorias?tipo=pelicula&con_totales=true", headers=sesion["cabeceras"]
    ).json()
    assert [(c["nombre"], c["total"]) for c in peliculas] == [("Acción", 1)]


def prueba_destacados(cliente: TestClient, sesion: dict) -> None:
    datos = cliente.get(f"{sesion['base']}/destacados?limite=5", headers=sesion["cabeceras"]).json()
    assert [p["nombre"] for p in datos["peliculas"]] == ["Película Uno"]
    assert isinstance(datos["series"], list)


def prueba_elenco_sin_tmdb_usa_los_nombres_del_proveedor(cliente: TestClient, sesion: dict) -> None:
    pelicula_id = RESPUESTAS["get_vod_streams"][0]["stream_id"]
    datos = cliente.get(
        f"{sesion['base']}/peliculas/{pelicula_id}/elenco", headers=sesion["cabeceras"]
    ).json()
    assert datos["fuente"] == "proveedor"
    assert all(a["foto"] is None for a in datos["actores"])
    reparto = (
        RESPUESTAS["get_vod_info"]["info"].get("cast")
        or RESPUESTAS["get_vod_info"]["info"].get("actors")
        or ""
    )
    esperados = [n.strip() for n in reparto.split(",") if n.strip()]
    assert [a["nombre"] for a in datos["actores"]] == esperados
