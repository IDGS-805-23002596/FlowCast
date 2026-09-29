from collections.abc import Iterator

import httpx2 as httpx
import pytest
from fastapi.testclient import TestClient

from app.main import aplicacion
from app.routers.conexiones import obtener_cliente_http
from app.services import cache, xtream
from tests.ayudantes import borrar_usuarios, crear_conexion, registrar

CORREOS = ["tele-fase3@ejemplo.com"]
INICIO_MKV = b"\x1a\x45\xdf\xa3" + b"\x00" * 12
INICIO_MP4 = b"\x00\x00\x00\x18ftypmp42" + b"\x00" * 4
SERIES = [
    {
        "series_id": 10,
        "name": "Mariposa azul",
        "cover": "http://img/portada.jpg",
        "backdrop_path": ["http://img/fondo.jpg"],
        "category_id": "1",
    },
    {"series_id": 11, "name": "Otra cosa", "cover": None, "category_id": "1"},
]
PELICULAS = [
    {"stream_id": i, "name": f"Mariposa {i}", "category_id": "2", "container_extension": "mp4"}
    for i in range(1, 31)
]
FICHA_SERIE = {
    "info": {"name": "Mariposa azul", "backdrop_path": ["http://img/fondo.jpg"]},
    "episodes": {
        "1": [{"id": "900", "episode_num": 1, "title": "Uno", "container_extension": "mp4"}]
    },
}


def _responder(peticion: httpx.Request) -> httpx.Response:
    ruta = peticion.url.path
    if ruta.startswith("/movie/"):
        return httpx.Response(206, content=INICIO_MKV)
    if ruta.startswith("/series/"):
        return httpx.Response(206, content=INICIO_MP4)
    parametros = peticion.url.params
    if parametros.get("password") != "buena":
        return httpx.Response(200, json={"user_info": {"auth": 0}})
    accion = parametros.get("action")
    respuestas = {
        None: {"user_info": {"auth": 1, "status": "Active"}, "server_info": {}},
        "get_series": SERIES,
        "get_vod_streams": PELICULAS,
        "get_live_streams": [],
        "get_series_categories": [{"category_id": "1", "category_name": "Drama"}],
        "get_vod_categories": [{"category_id": "2", "category_name": "Cine"}],
        "get_live_categories": [],
        "get_series_info": FICHA_SERIE,
        "get_vod_info": {
            "info": {"name": "Mariposa 1"},
            "movie_data": {"stream_id": 1, "name": "Mariposa 1", "container_extension": "mp4"},
        },
    }
    return httpx.Response(200, json=respuestas.get(accion, []))


@pytest.fixture(autouse=True)
def entorno() -> Iterator[None]:
    borrar_usuarios(CORREOS)
    cache.vaciar()

    async def cliente_falso():
        transporte = httpx.MockTransport(_responder)
        async with httpx.AsyncClient(transport=transporte, headers=xtream.CABECERAS) as cliente:
            yield cliente

    aplicacion.dependency_overrides[obtener_cliente_http] = cliente_falso
    yield
    aplicacion.dependency_overrides.pop(obtener_cliente_http, None)
    borrar_usuarios(CORREOS)
    cache.vaciar()


@pytest.fixture
def sesion() -> Iterator[dict]:
    with TestClient(aplicacion) as cliente:
        cabeceras = registrar(cliente, CORREOS[0])
        conexion_id = crear_conexion(cliente, cabeceras)
        yield {"cliente": cliente, "cabeceras": cabeceras, "base": f"/conexiones/{conexion_id}"}


@pytest.mark.parametrize(
    ("inicio", "esperada"),
    [
        (INICIO_MKV, "mkv"),
        (INICIO_MP4, "mp4"),
        (b"RIFF\x00\x00\x00\x00AVI ", "avi"),
        (b"\x47\x40\x00\x10", "ts"),
        (b'{"user_info"', None),
    ],
)
def prueba_extension_por_firma(inicio: bytes, esperada: str | None) -> None:
    assert xtream.extension_por_firma(inicio) == esperada


def prueba_las_series_traen_su_fondo_en_la_lista(sesion: dict) -> None:
    respuesta = sesion["cliente"].get(
        f"{sesion['base']}/series", params={"categoria_id": "1"}, headers=sesion["cabeceras"]
    )
    assert respuesta.status_code == 200, respuesta.text
    fondos = {serie["nombre"]: serie["fondo"] for serie in respuesta.json()}
    assert fondos == {"Mariposa azul": "http://img/fondo.jpg", "Otra cosa": None}


def prueba_buscar_devuelve_totales_y_acepta_limites_grandes(sesion: dict) -> None:
    respuesta = sesion["cliente"].get(
        f"{sesion['base']}/buscar",
        params={"q": "mariposa", "limite": 10},
        headers=sesion["cabeceras"],
    )
    assert respuesta.status_code == 200, respuesta.text
    datos = respuesta.json()
    assert len(datos["peliculas"]) == 10 and datos["total_peliculas"] == 30
    assert datos["total_series"] == 1 and datos["total_canales"] == 0
    grande = sesion["cliente"].get(
        f"{sesion['base']}/buscar",
        params={"q": "mariposa", "limite": 700},
        headers=sesion["cabeceras"],
    )
    assert len(grande.json()["peliculas"]) == 30


def prueba_detectar_corrige_la_extension_de_la_pelicula(sesion: dict) -> None:
    sin_detectar = sesion["cliente"].get(
        f"{sesion['base']}/reproducir/peliculas/1", headers=sesion["cabeceras"]
    )
    detectada = sesion["cliente"].get(
        f"{sesion['base']}/reproducir/peliculas/1",
        params={"detectar": "true"},
        headers=sesion["cabeceras"],
    )
    assert sin_detectar.json()["formato"] == "mp4"
    assert detectada.json()["formato"] == "mkv"
    assert detectada.json()["url"].endswith("/1.mkv")


def prueba_detectar_respeta_un_mp4_real_en_episodios(sesion: dict) -> None:
    respuesta = sesion["cliente"].get(
        f"{sesion['base']}/reproducir/series/10/episodios/900",
        params={"detectar": "true"},
        headers=sesion["cabeceras"],
    )
    assert respuesta.status_code == 200, respuesta.text
    assert respuesta.json()["formato"] == "mp4"


def prueba_listas_por_paginas(sesion: dict) -> None:
    def pedir(**parametros) -> list[str]:
        respuesta = sesion["cliente"].get(
            f"{sesion['base']}/peliculas", params=parametros, headers=sesion["cabeceras"]
        )
        assert respuesta.status_code == 200, respuesta.text
        return [pelicula["id"] for pelicula in respuesta.json()]

    todas = pedir()
    assert pedir(desde=0, limite=10) == todas[:10]
    assert pedir(desde=10, limite=10) == todas[10:20]
    assert pedir(desde=25, limite=10) == todas[25:]
    assert pedir(desde=100, limite=10) == []
