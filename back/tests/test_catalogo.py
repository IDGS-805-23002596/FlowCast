import asyncio
import base64
from collections.abc import Iterator
from datetime import UTC, datetime

import httpx2 as httpx
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import delete

from app.db import FabricaSesiones, motor
from app.main import aplicacion
from app.models import Usuario
from app.routers import catalogo
from app.routers.conexiones import obtener_cliente_http
from app.services import cache, xtream

CORREOS = ["catalogo-yo@ejemplo.com", "catalogo-otro@ejemplo.com"]


def _b64(texto: str) -> str:
    return base64.b64encode(texto.encode()).decode()


LOGIN_OK = {
    "user_info": {"auth": 1, "status": "Active", "allowed_output_formats": ["m3u8", "ts"]},
    "server_info": {"timezone": "UTC"},
}
RESPUESTAS = {
    "get_live_categories": [
        {"category_id": "1", "category_name": "Noticias", "parent_id": 0},
        {"category_id": "2", "category_name": "Deportes", "parent_id": "1"},
    ],
    "get_live_streams": [
        {
            "num": 1,
            "name": "Canal Uno",
            "stream_id": 101,
            "stream_icon": "http://img/1.png",
            "epg_channel_id": "uno.mx",
            "category_id": "1",
            "tv_archive": 1,
            "tv_archive_duration": 3,
        },
        {
            "num": "2",
            "name": "Canal Dos",
            "stream_id": "102",
            "stream_icon": "",
            "epg_channel_id": None,
            "category_id": "2",
            "tv_archive": 0,
            "tv_archive_duration": 0,
        },
    ],
    "get_vod_categories": [{"category_id": "10", "category_name": "Acción", "parent_id": 0}],
    "get_vod_streams": [
        {
            "name": "Película Uno",
            "stream_id": 501,
            "stream_icon": "http://img/p1.jpg",
            "rating": "7.5",
            "added": "1700000000",
            "category_id": "10",
            "container_extension": "mkv",
        },
    ],
    "get_vod_info": {
        "info": {
            "plot": "Trama.",
            "genre": "Acción",
            "duration_secs": "5400",
            "backdrop_path": ["http://img/fondo.jpg"],
            "youtube_trailer": "abc",
            "tmdb_id": 123,
        },
        "movie_data": {
            "stream_id": 501,
            "name": "Película Uno",
            "category_id": "10",
            "container_extension": "mkv",
        },
    },
    "get_series_categories": [{"category_id": "20", "category_name": "Drama", "parent_id": 0}],
    "get_series": [
        {
            "name": "Serie Uno",
            "series_id": 900,
            "cover": "http://img/s1.jpg",
            "plot": "Drama",
            "category_id": "20",
            "rating": "8",
        },
    ],
    "get_series_info": {
        "seasons": [
            {"season_number": 2, "name": "Temporada 2"},
            {"season_number": 1, "name": "Temporada 1"},
        ],
        "info": {"name": "Serie Uno", "plot": "Drama", "cast": "C, D"},
        "episodes": {
            "2": [
                {
                    "id": "9002",
                    "episode_num": 1,
                    "title": "Regreso",
                    "container_extension": "mp4",
                    "season": 2,
                }
            ],
            "1": [
                {
                    "id": "9001",
                    "episode_num": "1",
                    "title": "Piloto",
                    "container_extension": "mkv",
                    "season": 1,
                    "info": {"duration_secs": "2700"},
                }
            ],
        },
    },
    "get_short_epg": {
        "epg_listings": [
            {
                "title": _b64("Noticiero"),
                "description": _b64("Resumen"),
                "start_timestamp": "1700000000",
                "stop_timestamp": "1700003600",
                "now_playing": 1,
            },
            {
                "title": _b64("Después"),
                "description": _b64("No Description Info"),
                "start_timestamp": "1700003600",
                "stop_timestamp": "1700007200",
                "now_playing": 0,
            },
        ]
    },
}


async def _borrar_usuarios_prueba() -> None:
    async with FabricaSesiones() as sesion_bd:
        await sesion_bd.execute(delete(Usuario).where(Usuario.correo.in_(CORREOS)))
        await sesion_bd.commit()
    await motor.dispose()


@pytest.fixture(autouse=True)
def entorno_limpio() -> Iterator[None]:
    asyncio.run(_borrar_usuarios_prueba())
    cache.vaciar()
    yield
    asyncio.run(_borrar_usuarios_prueba())
    cache.vaciar()


@pytest.fixture
def proveedor() -> Iterator[dict]:
    estado = {"modo": "ok", "llamadas": 0}

    def responder(peticion: httpx.Request) -> httpx.Response:
        estado["llamadas"] += 1
        parametros = peticion.url.params
        if estado["modo"] == "caido":
            raise httpx.ConnectError("conexión rechazada", request=peticion)
        if estado["modo"] == "rechaza" or parametros.get("password") != "buena":
            return httpx.Response(200, json={"user_info": {"auth": 0}})
        accion = parametros.get("action")
        if accion is None:
            return httpx.Response(200, json=LOGIN_OK)
        if accion == "get_vod_info" and parametros.get("vod_id") == "999":
            return httpx.Response(200, json={"info": [], "movie_data": []})
        if accion == "get_live_streams" and parametros.get("category_id"):
            filtrados = [
                c for c in RESPUESTAS[accion] if c["category_id"] == parametros["category_id"]
            ]
            return httpx.Response(200, json=filtrados)
        return httpx.Response(200, json=RESPUESTAS[accion])

    async def cliente_falso():
        transporte = httpx.MockTransport(responder)
        async with httpx.AsyncClient(transport=transporte, headers=xtream.CABECERAS) as cliente:
            yield cliente

    aplicacion.dependency_overrides[obtener_cliente_http] = cliente_falso
    yield estado
    aplicacion.dependency_overrides.pop(obtener_cliente_http, None)


@pytest.fixture
def cliente() -> Iterator[TestClient]:
    with TestClient(aplicacion) as cliente_http:
        yield cliente_http


@pytest.fixture
def sesion(cliente: TestClient, proveedor: dict) -> dict:
    cabeceras = _registrar(cliente, CORREOS[0])
    respuesta = cliente.post(
        "/conexiones",
        json={"url_base": "falso.local:8080", "nombre_usuario": "demo", "contrasena": "buena"},
        headers=cabeceras,
    )
    assert respuesta.status_code == 201, respuesta.text
    conexion_id = respuesta.json()["id"]
    return {
        "cabeceras": cabeceras,
        "base": f"/conexiones/{conexion_id}",
        "conexion_id": conexion_id,
    }


def _registrar(cliente: TestClient, correo: str) -> dict[str, str]:
    respuesta = cliente.post(
        "/auth/registrar", json={"correo": correo, "contrasena": "secreta123", "nombre": "Prueba"}
    )
    assert respuesta.status_code == 201, respuesta.text
    return {"Authorization": f"Bearer {respuesta.json()['token_acceso']}"}


def prueba_categorias_por_tipo(cliente: TestClient, sesion: dict) -> None:
    for tipo, esperado in [
        ("en_vivo", ["Noticias", "Deportes"]),
        ("pelicula", ["Acción"]),
        ("serie", ["Drama"]),
    ]:
        respuesta = cliente.get(
            f"{sesion['base']}/categorias", params={"tipo": tipo}, headers=sesion["cabeceras"]
        )
        assert respuesta.status_code == 200, respuesta.text
        assert [c["nombre"] for c in respuesta.json()] == esperado
    deportes = cliente.get(
        f"{sesion['base']}/categorias?tipo=en_vivo", headers=sesion["cabeceras"]
    ).json()[1]
    assert deportes["padre_id"] == "1"
    assert (
        cliente.get(
            f"{sesion['base']}/categorias?tipo=musica", headers=sesion["cabeceras"]
        ).status_code
        == 422
    )


def prueba_canales_y_filtro_por_categoria(cliente: TestClient, sesion: dict) -> None:
    todos = cliente.get(f"{sesion['base']}/canales", headers=sesion["cabeceras"]).json()
    assert [c["nombre"] for c in todos] == ["Canal Uno", "Canal Dos"]
    assert {k: v for k, v in todos[0].items() if k != "bloqueada"} == {
        "id": "101",
        "nombre": "Canal Uno",
        "icono": "http://img/1.png",
        "categoria_id": "1",
        "numero": 1,
        "epg_id": "uno.mx",
        "dias_catchup": 3,
    }
    assert (
        todos[1]["icono"] is None and todos[1]["epg_id"] is None and todos[1]["dias_catchup"] == 0
    )
    filtrados = cliente.get(
        f"{sesion['base']}/canales?categoria_id=2", headers=sesion["cabeceras"]
    ).json()
    assert [c["nombre"] for c in filtrados] == ["Canal Dos"]


def prueba_epg_de_un_canal(
    cliente: TestClient, sesion: dict, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr(catalogo, "_ahora", lambda: datetime(2023, 11, 14, 22, 30, tzinfo=UTC))
    programas = cliente.get(
        f"{sesion['base']}/canales/101/epg?limite=5", headers=sesion["cabeceras"]
    ).json()
    assert [p["titulo"] for p in programas] == ["Noticiero", "Después"]
    assert programas[0]["descripcion"] == "Resumen" and programas[1]["descripcion"] is None
    assert programas[0]["en_emision"] is True and programas[1]["en_emision"] is False
    assert programas[0]["inicio"] == "2023-11-14T22:13:20Z"
    assert (
        cliente.get(
            f"{sesion['base']}/canales/101/epg?limite=0", headers=sesion["cabeceras"]
        ).status_code
        == 422
    )


def prueba_epg_omite_lo_que_ya_termino_y_respeta_el_limite(
    cliente: TestClient, sesion: dict, monkeypatch: pytest.MonkeyPatch
) -> None:
    ruta = f"{sesion['base']}/canales/101/epg"
    monkeypatch.setattr(catalogo, "_ahora", lambda: datetime(2023, 11, 14, 22, 30, tzinfo=UTC))
    uno = cliente.get(f"{ruta}?limite=1", headers=sesion["cabeceras"]).json()
    assert [p["titulo"] for p in uno] == ["Noticiero"]
    monkeypatch.setattr(catalogo, "_ahora", lambda: datetime(2023, 11, 14, 23, 30, tzinfo=UTC))
    despues = cliente.get(f"{ruta}?limite=5", headers=sesion["cabeceras"]).json()
    assert [p["titulo"] for p in despues] == ["Después"]


def prueba_peliculas_y_ficha(cliente: TestClient, sesion: dict) -> None:
    peliculas = cliente.get(f"{sesion['base']}/peliculas", headers=sesion["cabeceras"]).json()
    assert peliculas[0]["nombre"] == "Película Uno" and peliculas[0]["calificacion"] == 7.5
    assert peliculas[0]["extension"] == "mkv" and peliculas[0]["agregado_en"].startswith(
        "2023-11-14"
    )
    ficha = cliente.get(f"{sesion['base']}/peliculas/501", headers=sesion["cabeceras"]).json()
    assert ficha["pelicula"]["id"] == "501" and ficha["sinopsis"] == "Trama."
    assert ficha["duracion_segundos"] == 5400 and ficha["fondo"] == "http://img/fondo.jpg"
    assert ficha["tmdb_id"] == "123"
    inexistente = cliente.get(f"{sesion['base']}/peliculas/999", headers=sesion["cabeceras"])
    assert inexistente.status_code == 404


def prueba_series_y_ficha_con_temporadas_ordenadas(cliente: TestClient, sesion: dict) -> None:
    series = cliente.get(f"{sesion['base']}/series", headers=sesion["cabeceras"]).json()
    nuevas = {"bloqueada", "titulo", "anio", "idioma", "versiones"}
    assert [{k: v for k, v in s.items() if k not in nuevas} for s in series] == [
        {
            "id": "900",
            "nombre": "Serie Uno",
            "portada": "http://img/s1.jpg",
            "categoria_id": "20",
            "calificacion": 8.0,
            "sinopsis": "Drama",
            "genero": None,
            "fecha_estreno": None,
            "fondo": None,
        }
    ]
    ficha = cliente.get(f"{sesion['base']}/series/900", headers=sesion["cabeceras"]).json()
    assert ficha["serie"]["id"] == "900" and ficha["reparto"] == "C, D"
    assert [t["numero"] for t in ficha["temporadas"]] == [1, 2]
    piloto = ficha["temporadas"][0]["episodios"][0]
    assert piloto == {
        "id": "9001",
        "temporada": 1,
        "numero": 1,
        "titulo": "Piloto",
        "extension": "mkv",
        "imagen": None,
        "sinopsis": None,
        "duracion_segundos": 2700,
        "alternos": [],
    }


def prueba_conexion_ajena_y_sin_token(cliente: TestClient, sesion: dict) -> None:
    otro = _registrar(cliente, CORREOS[1])
    assert cliente.get(f"{sesion['base']}/canales", headers=otro).status_code == 404
    assert cliente.get(f"{sesion['base']}/canales").status_code == 401


def prueba_la_cache_evita_llamadas_repetidas(
    cliente: TestClient, sesion: dict, proveedor: dict
) -> None:
    rutas = [
        "/categorias?tipo=en_vivo",
        "/canales",
        "/peliculas/501",
        "/series/900",
        "/canales/101/epg",
    ]
    antes = proveedor["llamadas"]
    for ruta in rutas:
        assert cliente.get(sesion["base"] + ruta, headers=sesion["cabeceras"]).status_code == 200
    assert proveedor["llamadas"] - antes == len(rutas) + 2
    for ruta in rutas:
        assert cliente.get(sesion["base"] + ruta, headers=sesion["cabeceras"]).status_code == 200
    assert proveedor["llamadas"] - antes == len(rutas) + 2


def prueba_errores_del_proveedor(cliente: TestClient, sesion: dict, proveedor: dict) -> None:
    assert cliente.get(f"{sesion['base']}/canales", headers=sesion["cabeceras"]).status_code == 200
    proveedor["modo"] = "caido"
    en_cache = cliente.get(f"{sesion['base']}/canales", headers=sesion["cabeceras"])
    assert en_cache.status_code == 200
    assert (
        cliente.get(f"{sesion['base']}/peliculas", headers=sesion["cabeceras"]).status_code == 502
    )
    proveedor["modo"] = "rechaza"
    rechazo = cliente.get(f"{sesion['base']}/series", headers=sesion["cabeceras"])
    assert rechazo.status_code == 403
    assert "revalídala" in rechazo.json()["detail"]


def prueba_borrar_la_conexion_vacia_su_cache(cliente: TestClient, sesion: dict) -> None:
    cliente.get(f"{sesion['base']}/canales", headers=sesion["cabeceras"])
    prefijo = f"conexion:{sesion['conexion_id']}:"
    assert any(clave.startswith(prefijo) for clave in cache._entradas)
    assert cliente.delete(sesion["base"], headers=sesion["cabeceras"]).status_code == 204
    assert not any(clave.startswith(prefijo) for clave in cache._entradas)
