import asyncio
from collections.abc import Iterator

import httpx2 as httpx
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import delete

from app.db import FabricaSesiones, motor
from app.main import aplicacion
from app.models import Usuario
from app.routers.conexiones import obtener_cliente_http
from app.services import cache, xtream

CORREOS = ["repro-yo@ejemplo.com", "repro-otro@ejemplo.com"]
URL_BASE = "http://falso.local:8080"
CONTRASENAS_VALIDAS = {"buena", "p@ss/word?"}

FICHA_PELICULA = {
    "info": {"plot": "Trama."},
    "movie_data": {"stream_id": 501, "name": "Película Uno", "container_extension": "mkv"},
}
FICHA_SERIE = {
    "seasons": [],
    "info": {"name": "Serie Uno"},
    "episodes": {
        "1": [
            {
                "id": "9001",
                "episode_num": 1,
                "title": "Piloto",
                "container_extension": "avi",
                "season": 1,
            }
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
    estado = {"llamadas": 0}

    def responder(peticion: httpx.Request) -> httpx.Response:
        estado["llamadas"] += 1
        parametros = peticion.url.params
        if parametros.get("password") not in CONTRASENAS_VALIDAS:
            return httpx.Response(200, json={"user_info": {"auth": 0}})
        accion = parametros.get("action")
        if accion is None:
            formatos = ["ts"] if parametros.get("username") == "solo_ts" else ["m3u8", "ts"]
            return httpx.Response(
                200,
                json={
                    "user_info": {
                        "auth": 1,
                        "status": "Active",
                        "allowed_output_formats": formatos,
                    },
                    "server_info": {},
                },
            )
        if accion == "get_vod_info":
            return httpx.Response(
                200,
                json=FICHA_PELICULA
                if parametros.get("vod_id") == "501"
                else {"info": [], "movie_data": []},
            )
        if accion == "get_series_info":
            return httpx.Response(200, json=FICHA_SERIE)
        if accion in (
            "get_live_streams",
            "get_live_categories",
            "get_vod_categories",
            "get_series_categories",
        ):
            return httpx.Response(200, json=[])
        return httpx.Response(404)

    async def cliente_falso():
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(responder), headers=xtream.CABECERAS
        ) as cliente:
            yield cliente

    aplicacion.dependency_overrides[obtener_cliente_http] = cliente_falso
    yield estado
    aplicacion.dependency_overrides.pop(obtener_cliente_http, None)


@pytest.fixture
def cliente() -> Iterator[TestClient]:
    with TestClient(aplicacion) as cliente_http:
        yield cliente_http


def _registrar(cliente: TestClient, correo: str) -> dict[str, str]:
    respuesta = cliente.post(
        "/auth/registrar", json={"correo": correo, "contrasena": "secreta123", "nombre": "Prueba"}
    )
    assert respuesta.status_code == 201, respuesta.text
    return {"Authorization": f"Bearer {respuesta.json()['token_acceso']}"}


def _conectar(
    cliente: TestClient, cabeceras: dict, usuario: str = "demo", contrasena: str = "buena"
) -> str:
    respuesta = cliente.post(
        "/conexiones",
        json={"url_base": URL_BASE, "nombre_usuario": usuario, "contrasena": contrasena},
        headers=cabeceras,
    )
    assert respuesta.status_code == 201, respuesta.text
    return f"/conexiones/{respuesta.json()['id']}/reproducir"


@pytest.fixture
def sesion(cliente: TestClient, proveedor: dict) -> dict:
    cabeceras = _registrar(cliente, CORREOS[0])
    return {"cabeceras": cabeceras, "base": _conectar(cliente, cabeceras)}


def prueba_en_vivo_elige_hls_por_defecto(cliente: TestClient, sesion: dict) -> None:
    respuesta = cliente.get(f"{sesion['base']}/en-vivo/101", headers=sesion["cabeceras"])
    assert respuesta.status_code == 200, respuesta.text
    assert respuesta.json() == {
        "url": f"{URL_BASE}/live/demo/buena/101.m3u8",
        "formato": "m3u8",
        "tipo_contenido": "en_vivo",
        "contenido_id": "101",
        "user_agent": "FlowCast/0.1",
    }


def prueba_en_vivo_respeta_el_formato_pedido(cliente: TestClient, sesion: dict) -> None:
    respuesta = cliente.get(f"{sesion['base']}/en-vivo/101?formato=ts", headers=sesion["cabeceras"])
    assert respuesta.json()["url"].endswith("/live/demo/buena/101.ts")
    assert (
        cliente.get(
            f"{sesion['base']}/en-vivo/101?formato=avi", headers=sesion["cabeceras"]
        ).status_code
        == 422
    )


def prueba_en_vivo_usa_ts_si_el_proveedor_no_ofrece_hls(
    cliente: TestClient, proveedor: dict
) -> None:
    cabeceras = _registrar(cliente, CORREOS[0])
    base = _conectar(cliente, cabeceras, usuario="solo_ts")
    sin_preferencia = cliente.get(f"{base}/en-vivo/7", headers=cabeceras).json()
    con_hls_pedido = cliente.get(f"{base}/en-vivo/7?formato=m3u8", headers=cabeceras).json()
    assert sin_preferencia["formato"] == "ts"
    assert con_hls_pedido["formato"] == "ts"


def prueba_pelicula_consulta_la_extension_si_falta(
    cliente: TestClient, sesion: dict, proveedor: dict
) -> None:
    antes = proveedor["llamadas"]
    respuesta = cliente.get(f"{sesion['base']}/peliculas/501", headers=sesion["cabeceras"])
    assert respuesta.status_code == 200, respuesta.text
    assert respuesta.json()["url"] == f"{URL_BASE}/movie/demo/buena/501.mkv"
    assert respuesta.json()["formato"] == "mkv"
    assert proveedor["llamadas"] - antes == 2
    cliente.get(f"{sesion['base']}/peliculas/501", headers=sesion["cabeceras"])
    assert proveedor["llamadas"] - antes == 2


def prueba_pelicula_con_extension_dada_no_consulta(
    cliente: TestClient, sesion: dict, proveedor: dict
) -> None:
    antes = proveedor["llamadas"]
    respuesta = cliente.get(
        f"{sesion['base']}/peliculas/777?extension=mp4", headers=sesion["cabeceras"]
    )
    assert respuesta.json()["url"].endswith("/movie/demo/buena/777.mp4")
    assert proveedor["llamadas"] == antes + 1
    assert (
        cliente.get(f"{sesion['base']}/peliculas/999", headers=sesion["cabeceras"]).status_code
        == 404
    )


def prueba_episodio_busca_la_extension_en_la_serie(cliente: TestClient, sesion: dict) -> None:
    respuesta = cliente.get(
        f"{sesion['base']}/series/900/episodios/9001", headers=sesion["cabeceras"]
    )
    assert respuesta.status_code == 200, respuesta.text
    assert respuesta.json()["url"] == f"{URL_BASE}/series/demo/buena/9001.avi"
    assert respuesta.json()["tipo_contenido"] == "episodio"
    ajeno = cliente.get(f"{sesion['base']}/series/900/episodios/4444", headers=sesion["cabeceras"])
    assert ajeno.status_code == 404


def prueba_ids_raros_no_alteran_la_ruta(cliente: TestClient, sesion: dict) -> None:
    assert cliente.get(
        f"{sesion['base']}/peliculas/..%2Fadmin?extension=mp4", headers=sesion["cabeceras"]
    ).status_code in (404, 422)
    assert (
        cliente.get(
            f"{sesion['base']}/peliculas/501?extension=mp4%2F..", headers=sesion["cabeceras"]
        ).status_code
        == 422
    )


def prueba_credenciales_con_simbolos_van_escapadas(cliente: TestClient, proveedor: dict) -> None:
    cabeceras = _registrar(cliente, CORREOS[0])
    base = _conectar(cliente, cabeceras, usuario="demo", contrasena="p@ss/word?")
    url = cliente.get(f"{base}/en-vivo/1", headers=cabeceras).json()["url"]
    assert url == f"{URL_BASE}/live/demo/p%40ss%2Fword%3F/1.m3u8"


def prueba_conexion_ajena_y_sin_token(cliente: TestClient, sesion: dict) -> None:
    otro = _registrar(cliente, CORREOS[1])
    assert cliente.get(f"{sesion['base']}/en-vivo/1", headers=otro).status_code == 404
    assert cliente.get(f"{sesion['base']}/en-vivo/1").status_code == 401
