from collections.abc import Iterator

import httpx2 as httpx
import pytest
from fastapi.testclient import TestClient

from app.main import aplicacion
from app.routers.conexiones import obtener_cliente_http
from app.services import cache, control_parental, xtream
from tests.ayudantes import borrar_usuarios, registrar

CORREOS = ["parental@ejemplo.com"]
URL_BASE = "http://falso.local:8080"

CATALOGO = {
    "get_live_categories": [
        {"category_id": "1", "category_name": "Noticias", "parent_id": 0},
        {"category_id": "2", "category_name": "XXX Adultos", "parent_id": 0},
    ],
    "get_live_streams": [
        {"num": 1, "name": "Canal Uno", "stream_id": 101, "category_id": "1"},
        {"num": 2, "name": "Canal Rojo", "stream_id": 102, "category_id": "2"},
        {"num": 3, "name": "Cine +18", "stream_id": 103, "category_id": "1"},
    ],
    "get_vod_categories": [
        {"category_id": "10", "category_name": "Acción", "parent_id": 0},
        {"category_id": "11", "category_name": "Solo adultos", "parent_id": 0},
    ],
    "get_vod_streams": [
        {
            "name": "Película Uno",
            "stream_id": 501,
            "category_id": "10",
            "container_extension": "mp4",
        },
        {
            "name": "Película Roja",
            "stream_id": 502,
            "category_id": "11",
            "container_extension": "mp4",
        },
    ],
    "get_series_categories": [
        {"category_id": "20", "category_name": "Drama", "parent_id": 0},
        {"category_id": "21", "category_name": "Porno", "parent_id": 0},
    ],
    "get_series": [
        {"name": "Serie Uno", "series_id": 900, "category_id": "20"},
        {"name": "Serie Roja", "series_id": 901, "category_id": "21"},
    ],
}
EPISODIOS = {
    "1": [
        {
            "id": "9001",
            "episode_num": 1,
            "title": "Piloto",
            "container_extension": "mp4",
            "season": 1,
        }
    ]
}


@pytest.fixture(autouse=True)
def limpieza() -> Iterator[None]:
    borrar_usuarios(CORREOS)
    cache.vaciar()
    control_parental.olvidar_intentos()
    yield
    borrar_usuarios(CORREOS)
    cache.vaciar()
    control_parental.olvidar_intentos()


@pytest.fixture(autouse=True)
def proveedor() -> Iterator[None]:
    def responder(peticion: httpx.Request) -> httpx.Response:
        parametros = peticion.url.params
        if parametros.get("password") != "buena":
            return httpx.Response(200, json={"user_info": {"auth": 0}})
        accion = parametros.get("action")
        if accion is None:
            return httpx.Response(
                200,
                json={
                    "user_info": {
                        "auth": 1,
                        "status": "Active",
                        "allowed_output_formats": ["m3u8"],
                    },
                    "server_info": {},
                },
            )
        if accion == "get_vod_info":
            pelicula = next(
                p
                for p in CATALOGO["get_vod_streams"]
                if str(p["stream_id"]) == parametros.get("vod_id")
            )
            return httpx.Response(200, json={"info": {"plot": "Trama"}, "movie_data": pelicula})
        if accion == "get_series_info":
            serie = next(
                s
                for s in CATALOGO["get_series"]
                if str(s["series_id"]) == parametros.get("series_id")
            )
            return httpx.Response(200, json={"seasons": [], "info": serie, "episodes": EPISODIOS})
        datos = CATALOGO.get(accion, [])
        categoria = parametros.get("category_id")
        if categoria:
            datos = [d for d in datos if str(d.get("category_id")) == categoria]
        return httpx.Response(200, json=datos)

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
    respuesta = cliente.post(
        "/conexiones",
        json={"url_base": URL_BASE, "nombre_usuario": "demo", "contrasena": "buena"},
        headers=cabeceras,
    )
    assert respuesta.status_code == 201, respuesta.text
    conexion_id = respuesta.json()["id"]
    return {
        "cabeceras": cabeceras,
        "conexion_id": conexion_id,
        "base": f"/conexiones/{conexion_id}",
    }


def _poner_pin(cliente: TestClient, sesion: dict, pin: str = "1234") -> None:
    respuesta = cliente.put(
        "/control-parental/pin", json={"pin_nuevo": pin}, headers=sesion["cabeceras"]
    )
    assert respuesta.status_code == 200, respuesta.text
    assert respuesta.json() == {"tiene_pin": True}


def _token(cliente: TestClient, sesion: dict, pin: str = "1234") -> str:
    respuesta = cliente.post(
        "/control-parental/pin/verificar", json={"pin": pin}, headers=sesion["cabeceras"]
    )
    assert respuesta.status_code == 200, respuesta.text
    assert respuesta.json()["valido"] is True
    return respuesta.json()["token_desbloqueo"]


def prueba_deteccion_de_nombres_adultos() -> None:
    assert control_parental.es_nombre_adulto("XXX Adultos")
    assert control_parental.es_nombre_adulto("Cine +18")
    assert control_parental.es_nombre_adulto("Canales 18+")
    assert control_parental.es_nombre_adulto("Películas eróticas")
    assert control_parental.es_nombre_adulto("Solo adultos")
    assert not control_parental.es_nombre_adulto("Noticias")
    assert not control_parental.es_nombre_adulto("Hot Wheels")
    assert not control_parental.es_nombre_adulto("Adulterio")
    assert not control_parental.es_nombre_adulto("Programa 2018")


def _nombres(cliente: TestClient, sesion: dict, ruta: str, cabeceras: dict | None = None) -> list:
    respuesta = cliente.get(f"{sesion['base']}/{ruta}", headers=cabeceras or sesion["cabeceras"])
    assert respuesta.status_code == 200, respuesta.text
    return [(e["nombre"], e["bloqueada"]) for e in respuesta.json()]


def prueba_sin_desbloqueo_el_catalogo_oculta_lo_bloqueado(
    cliente: TestClient, sesion: dict
) -> None:
    categorias = cliente.get(
        f"{sesion['base']}/categorias?tipo=en_vivo", headers=sesion["cabeceras"]
    ).json()
    assert [(c["nombre"], c["es_adulta"], c["bloqueada"]) for c in categorias] == [
        ("Noticias", False, False),
        ("XXX Adultos", True, True),
    ]
    assert _nombres(cliente, sesion, "canales") == [("Canal Uno", False)]
    assert _nombres(cliente, sesion, "peliculas") == [("Película Uno", False)]
    assert _nombres(cliente, sesion, "series") == [("Serie Uno", False)]
    por_categoria = cliente.get(
        f"{sesion['base']}/canales?categoria_id=2", headers=sesion["cabeceras"]
    )
    assert por_categoria.status_code == 423
    assert por_categoria.json()["detail"]["codigo"] == "pin_no_configurado"
    assert (
        cliente.get(
            f"{sesion['base']}/peliculas?categoria_id=11", headers=sesion["cabeceras"]
        ).status_code
        == 423
    )
    assert (
        cliente.get(f"{sesion['base']}/peliculas/502", headers=sesion["cabeceras"]).status_code
        == 423
    )
    assert (
        cliente.get(f"{sesion['base']}/series/901", headers=sesion["cabeceras"]).status_code == 423
    )
    assert (
        cliente.get(f"{sesion['base']}/peliculas/501", headers=sesion["cabeceras"]).status_code
        == 200
    )
    busqueda = cliente.get(f"{sesion['base']}/buscar?q=roj", headers=sesion["cabeceras"]).json()
    assert busqueda == {
        "canales": [],
        "peliculas": [],
        "series": [],
        "total_canales": 0,
        "total_peliculas": 0,
        "total_series": 0,
    }
    destacados = cliente.get(f"{sesion['base']}/destacados", headers=sesion["cabeceras"]).json()
    assert [p["nombre"] for p in destacados["peliculas"]] == ["Película Uno"]
    assert [s["nombre"] for s in destacados["series"]] == ["Serie Uno"]


def prueba_con_desbloqueo_se_ve_todo_marcado(cliente: TestClient, sesion: dict) -> None:
    _poner_pin(cliente, sesion)
    assert _nombres(cliente, sesion, "canales") == [("Canal Uno", False)]
    por_categoria = cliente.get(
        f"{sesion['base']}/canales?categoria_id=2", headers=sesion["cabeceras"]
    )
    assert por_categoria.status_code == 423
    assert por_categoria.json()["detail"]["codigo"] == "pin_requerido"
    cabeceras = {**sesion["cabeceras"], "X-Desbloqueo-Parental": _token(cliente, sesion)}
    assert _nombres(cliente, sesion, "canales", cabeceras) == [
        ("Canal Uno", False),
        ("Canal Rojo", True),
        ("Cine +18", True),
    ]
    assert _nombres(cliente, sesion, "canales?categoria_id=2", cabeceras) == [("Canal Rojo", True)]
    assert _nombres(cliente, sesion, "peliculas", cabeceras) == [
        ("Película Uno", False),
        ("Película Roja", True),
    ]
    ficha = cliente.get(f"{sesion['base']}/peliculas/502", headers=cabeceras)
    assert ficha.status_code == 200 and ficha.json()["pelicula"]["bloqueada"] is True
    serie = cliente.get(f"{sesion['base']}/series/901", headers=cabeceras)
    assert serie.status_code == 200 and serie.json()["serie"]["bloqueada"] is True
    busqueda = cliente.get(f"{sesion['base']}/buscar?q=roj", headers=cabeceras).json()
    assert (
        busqueda["canales"][0]["bloqueada"]
        and busqueda["peliculas"][0]["bloqueada"]
        and busqueda["series"][0]["bloqueada"]
    )
    destacados = cliente.get(f"{sesion['base']}/destacados", headers=cabeceras).json()
    assert {p["nombre"] for p in destacados["peliculas"]} == {"Película Uno", "Película Roja"}


def prueba_reproducir_bloqueado_sin_pin_configurado(cliente: TestClient, sesion: dict) -> None:
    assert cliente.get("/control-parental", headers=sesion["cabeceras"]).json() == {
        "tiene_pin": False
    }
    libre = cliente.get(f"{sesion['base']}/reproducir/en-vivo/101", headers=sesion["cabeceras"])
    assert libre.status_code == 200
    bloqueado = cliente.get(f"{sesion['base']}/reproducir/en-vivo/102", headers=sesion["cabeceras"])
    assert bloqueado.status_code == 423
    assert bloqueado.json()["detail"]["codigo"] == "pin_no_configurado"
    por_nombre = cliente.get(
        f"{sesion['base']}/reproducir/en-vivo/103", headers=sesion["cabeceras"]
    )
    assert por_nombre.status_code == 423


def prueba_pin_desbloquea_con_token(cliente: TestClient, sesion: dict) -> None:
    _poner_pin(cliente, sesion)
    for ruta in (
        "reproducir/en-vivo/102",
        "reproducir/peliculas/502",
        "reproducir/series/901/episodios/9001",
    ):
        sin = cliente.get(f"{sesion['base']}/{ruta}", headers=sesion["cabeceras"])
        assert sin.status_code == 423 and sin.json()["detail"]["codigo"] == "pin_requerido", ruta
    token = _token(cliente, sesion)
    cabeceras = {**sesion["cabeceras"], "X-Desbloqueo-Parental": token}
    for ruta in (
        "reproducir/en-vivo/102",
        "reproducir/peliculas/502",
        "reproducir/series/901/episodios/9001",
    ):
        assert cliente.get(f"{sesion['base']}/{ruta}", headers=cabeceras).status_code == 200, ruta
    falso = {**sesion["cabeceras"], "X-Desbloqueo-Parental": "no-es-un-token"}
    assert cliente.get(f"{sesion['base']}/reproducir/en-vivo/102", headers=falso).status_code == 423
    assert (
        cliente.get(
            f"{sesion['base']}/reproducir/peliculas/501", headers=sesion["cabeceras"]
        ).status_code
        == 200
    )


def prueba_pin_incorrecto_y_castigo(cliente: TestClient, sesion: dict) -> None:
    _poner_pin(cliente, sesion)
    for _ in range(5):
        malo = cliente.post(
            "/control-parental/pin/verificar", json={"pin": "0000"}, headers=sesion["cabeceras"]
        )
        assert malo.status_code == 200 and malo.json() == {
            "valido": False,
            "token_desbloqueo": None,
            "segundos_validez": 0,
        }
    castigado = cliente.post(
        "/control-parental/pin/verificar", json={"pin": "1234"}, headers=sesion["cabeceras"]
    )
    assert castigado.status_code == 429
    assert (
        cliente.post(
            "/control-parental/pin/verificar", json={"pin": "12"}, headers=sesion["cabeceras"]
        ).status_code
        == 422
    )


def prueba_cambiar_y_quitar_pin(cliente: TestClient, sesion: dict) -> None:
    sin_pin = cliente.post(
        "/control-parental/pin/verificar", json={"pin": "1234"}, headers=sesion["cabeceras"]
    )
    assert sin_pin.status_code == 409
    _poner_pin(cliente, sesion, "1234")
    sin_autorizar = cliente.put(
        "/control-parental/pin", json={"pin_nuevo": "9999"}, headers=sesion["cabeceras"]
    )
    assert sin_autorizar.status_code == 403
    mal_pin = cliente.put(
        "/control-parental/pin",
        json={"pin_nuevo": "9999", "pin_actual": "0000"},
        headers=sesion["cabeceras"],
    )
    assert mal_pin.status_code == 403
    con_pin = cliente.put(
        "/control-parental/pin",
        json={"pin_nuevo": "9999", "pin_actual": "1234"},
        headers=sesion["cabeceras"],
    )
    assert con_pin.status_code == 200
    _token(cliente, sesion, "9999")
    con_contrasena = cliente.put(
        "/control-parental/pin",
        json={"pin_nuevo": "4321", "contrasena": "secreta123"},
        headers=sesion["cabeceras"],
    )
    assert con_contrasena.status_code == 200
    quitar_mal = cliente.post(
        "/control-parental/pin/quitar", json={"pin_actual": "0000"}, headers=sesion["cabeceras"]
    )
    assert quitar_mal.status_code == 403
    quitado = cliente.post(
        "/control-parental/pin/quitar",
        json={"contrasena": "secreta123"},
        headers=sesion["cabeceras"],
    )
    assert quitado.status_code == 200 and quitado.json() == {"tiene_pin": False}
    assert cliente.get("/control-parental").status_code == 401


def prueba_bloquear_y_desbloquear_categorias_a_mano(cliente: TestClient, sesion: dict) -> None:
    _poner_pin(cliente, sesion)
    lista = cliente.get(
        f"/control-parental/categorias?conexion_id={sesion['conexion_id']}",
        headers=sesion["cabeceras"],
    ).json()
    assert {(c["tipo_contenido"], c["id"], c["bloqueada"]) for c in lista} == {
        ("en_vivo", "1", False),
        ("en_vivo", "2", True),
        ("pelicula", "10", False),
        ("pelicula", "11", True),
        ("serie", "20", False),
        ("serie", "21", True),
    }
    desbloquear = cliente.put(
        "/control-parental/categorias",
        json={
            "conexion_id": sesion["conexion_id"],
            "tipo_contenido": "en_vivo",
            "categoria_id": "2",
            "bloqueada": False,
        },
        headers=sesion["cabeceras"],
    )
    assert desbloquear.status_code == 200, desbloquear.text
    assert (
        cliente.get(
            f"{sesion['base']}/reproducir/en-vivo/102", headers=sesion["cabeceras"]
        ).status_code
        == 200
    )
    assert _nombres(cliente, sesion, "canales") == [("Canal Uno", False), ("Canal Rojo", False)]
    bloquear = cliente.put(
        "/control-parental/categorias",
        json={
            "conexion_id": sesion["conexion_id"],
            "tipo_contenido": "pelicula",
            "categoria_id": "10",
            "bloqueada": True,
        },
        headers=sesion["cabeceras"],
    )
    assert bloquear.status_code == 200
    assert (
        cliente.get(
            f"{sesion['base']}/reproducir/peliculas/501", headers=sesion["cabeceras"]
        ).status_code
        == 423
    )
    assert _nombres(cliente, sesion, "peliculas") == []
    categorias = cliente.get(
        f"{sesion['base']}/categorias?tipo=pelicula", headers=sesion["cabeceras"]
    ).json()
    assert [(c["nombre"], c["bloqueada"]) for c in categorias] == [
        ("Acción", True),
        ("Solo adultos", True),
    ]
    manuales = cliente.get(
        f"/control-parental/categorias/bloqueadas?conexion_id={sesion['conexion_id']}",
        headers=sesion["cabeceras"],
    ).json()
    assert len(manuales) == 2
    assert (
        cliente.post(f"{sesion['base']}/actualizar", headers=sesion["cabeceras"]).status_code == 204
    )


def prueba_la_biblioteca_oculta_lo_bloqueado(cliente: TestClient, sesion: dict) -> None:
    perfil = f"/perfiles/{cliente.get('/perfiles', headers=sesion['cabeceras']).json()[0]['id']}"
    cabeceras = sesion["cabeceras"]

    def entrada(tipo: str, stream_id: str, titulo: str, **extra) -> dict:
        return {
            "conexion_id": sesion["conexion_id"],
            "tipo_contenido": tipo,
            "stream_id": stream_id,
            "titulo": titulo,
            **extra,
        }

    for canal in (("101", "Canal Uno"), ("102", "Canal Rojo")):
        assert (
            cliente.put(
                f"{perfil}/favoritos", json=entrada("en_vivo", *canal), headers=cabeceras
            ).status_code
            == 200
        )
    favoritos = cliente.get(f"{perfil}/favoritos", headers=cabeceras).json()
    assert [f["stream_id"] for f in favoritos] == ["101"]

    for pelicula in (("501", "Película Uno"), ("502", "Película Roja")):
        assert (
            cliente.post(
                f"{perfil}/historial", json=entrada("pelicula", *pelicula), headers=cabeceras
            ).status_code
            == 201
        )
    assert (
        cliente.post(
            f"{perfil}/historial",
            json=entrada("en_vivo", "101", "Especial XXX en Canal Uno"),
            headers=cabeceras,
        ).status_code
        == 201
    )
    historial = cliente.get(f"{perfil}/historial", headers=cabeceras).json()
    assert [(h["stream_id"], h["titulo"]) for h in historial] == [("501", "Película Uno")]

    progreso = {"posicion_segundos": 10, "duracion_segundos": 100}
    assert (
        cliente.put(
            f"{perfil}/progreso",
            json=entrada("pelicula", "501", "Película Uno", **progreso),
            headers=cabeceras,
        ).status_code
        == 200
    )
    assert (
        cliente.put(
            f"{perfil}/progreso",
            json=entrada("pelicula", "502", "Película Roja", **progreso),
            headers=cabeceras,
        ).status_code
        == 200
    )
    assert (
        cliente.put(
            f"{perfil}/progreso",
            json=entrada("episodio", "9001", "Serie Roja · T1 E1", serie_id="901", **progreso),
            headers=cabeceras,
        ).status_code
        == 200
    )
    assert (
        cliente.put(
            f"{perfil}/progreso",
            json=entrada("episodio", "9000", "Serie Uno · T1 E1", serie_id="900", **progreso),
            headers=cabeceras,
        ).status_code
        == 200
    )
    continuar = cliente.get(f"{perfil}/progreso", headers=cabeceras).json()
    assert sorted(p["stream_id"] for p in continuar) == ["501", "9000"]
