import asyncio
from collections.abc import Iterator

import httpx2 as httpx
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import delete

from app.db import FabricaSesiones, motor
from app.main import aplicacion
from app.models import DisponibilidadTmdb, Plataforma
from app.routers.conexiones import obtener_cliente_http
from app.services import cache, plataformas, xtream
from tests.ayudantes import borrar_usuarios, crear_conexion, registrar

CORREOS = ["plataformas-prueba@ejemplo.com"]
CLAVE = "plataforma-prueba"
PELICULAS = [
    {
        "stream_id": 1,
        "name": "Uno",
        "category_id": "2",
        "tmdb_id": "990000001",
        "added": "1700000000",
    },
    {
        "stream_id": 2,
        "name": "Dos",
        "category_id": "2",
        "tmdb_id": "990000002",
        "added": "1800000000",
    },
    {"stream_id": 3, "name": "Tres", "category_id": "2", "tmdb_id": ""},
]
SERIES = [{"series_id": 10, "name": "Serie", "category_id": "1", "tmdb_id": 990000003}]


def _responder(peticion: httpx.Request) -> httpx.Response:
    parametros = peticion.url.params
    if parametros.get("password") != "buena":
        return httpx.Response(200, json={"user_info": {"auth": 0}})
    respuestas = {
        None: {"user_info": {"auth": 1, "status": "Active"}, "server_info": {}},
        "get_vod_streams": PELICULAS,
        "get_series": SERIES,
        "get_vod_categories": [{"category_id": "2", "category_name": "Cine"}],
        "get_series_categories": [{"category_id": "1", "category_name": "Drama"}],
    }
    return httpx.Response(200, json=respuestas.get(parametros.get("action"), []))


async def _preparar_base(insertar: bool) -> None:
    async with FabricaSesiones() as sesion_bd:
        await sesion_bd.execute(delete(Plataforma).where(Plataforma.clave == CLAVE))
        await sesion_bd.execute(
            delete(DisponibilidadTmdb).where(DisponibilidadTmdb.tmdb_id.like("99000000%"))
        )
        if insertar:
            sesion_bd.add(Plataforma(clave=CLAVE, nombre="Prueba+", logo=None, orden=-1))
            sesion_bd.add_all(
                [
                    DisponibilidadTmdb(
                        tipo_contenido="pelicula", tmdb_id="990000001", plataformas=[CLAVE]
                    ),
                    DisponibilidadTmdb(
                        tipo_contenido="pelicula", tmdb_id="990000002", plataformas=[CLAVE]
                    ),
                    DisponibilidadTmdb(
                        tipo_contenido="serie", tmdb_id="990000003", plataformas=[CLAVE]
                    ),
                ]
            )
        await sesion_bd.commit()
    await motor.dispose()


@pytest.fixture(autouse=True)
def entorno() -> Iterator[None]:
    borrar_usuarios(CORREOS)
    asyncio.run(_preparar_base(True))
    cache.vaciar()

    async def cliente_falso():
        transporte = httpx.MockTransport(_responder)
        async with httpx.AsyncClient(transport=transporte, headers=xtream.CABECERAS) as cliente:
            yield cliente

    aplicacion.dependency_overrides[obtener_cliente_http] = cliente_falso
    yield
    aplicacion.dependency_overrides.pop(obtener_cliente_http, None)
    asyncio.run(_preparar_base(False))
    borrar_usuarios(CORREOS)
    cache.vaciar()


@pytest.fixture
def sesion() -> Iterator[dict]:
    with TestClient(aplicacion) as cliente:
        cabeceras = registrar(cliente, CORREOS[0])
        conexion_id = crear_conexion(cliente, cabeceras)
        yield {"cliente": cliente, "cabeceras": cabeceras, "base": f"/conexiones/{conexion_id}"}


@pytest.mark.parametrize(
    ("nombre", "esperada"),
    [
        ("Netflix", "netflix"),
        ("Netflix Standard with Ads", "netflix"),
        ("Amazon Prime Video", "prime"),
        ("Amazon Prime Video with Ads", "prime"),
        ("Paramount+ Amazon Channel", "paramount"),
        ("HBO Max", "max"),
        ("Max", "max"),
        ("Disney Plus", "disney"),
        ("Apple TV Plus", "apple"),
        ("ViX ", "vix"),
        ("Maxdome", None),
        ("Amazon Video", None),
        ("Apple TV", "apple"),
        ("Apple TV Amazon Channel", "apple"),
        ("Adrenalina Pura Apple TV channel", None),
        ("MGM+ Apple TV Channel", "mgm"),
        ("MGM Plus Amazon Channel", "mgm"),
        ("Paramount Plus Apple TV channel", "paramount"),
        ("Tubi TV", "tubi"),
        ("Universal+ Amazon Channel", "universal"),
        ("Plex", "plex"),
        ("Lionsgate+ Amazon Channel", "lionsgate"),
        ("Sony One Amazon Channel", "sonyone"),
        ("Looke Amazon Channel", None),
    ],
)
def prueba_clasificar_proveedores(nombre: str, esperada: str | None) -> None:
    assert plataformas.clasificar(nombre) == esperada


def prueba_claves_de_respuesta_solo_mexico_y_suscripcion() -> None:
    datos = {
        "results": {
            "MX": {
                "flatrate": [{"provider_name": "Netflix"}],
                "rent": [{"provider_name": "Apple TV Plus"}],
                "ads": [{"provider_name": "Amazon Prime Video with Ads"}],
            },
            "US": {"flatrate": [{"provider_name": "Disney Plus"}]},
        }
    }
    assert plataformas.claves_de_respuesta(datos) == {"netflix", "prime"}


def prueba_lista_y_contenido_de_una_plataforma(sesion: dict) -> None:
    lista = sesion["cliente"].get(f"{sesion['base']}/plataformas", headers=sesion["cabeceras"])
    assert lista.status_code == 200, lista.text
    prueba = next(p for p in lista.json() if p["clave"] == CLAVE)
    assert prueba == {
        "clave": CLAVE,
        "nombre": "Prueba+",
        "logo": None,
        "total_peliculas": 2,
        "total_series": 1,
    }
    contenido = (
        sesion["cliente"]
        .get(f"{sesion['base']}/plataformas/{CLAVE}", headers=sesion["cabeceras"])
        .json()
    )
    assert [p["nombre"] for p in contenido["peliculas"]] == ["Dos", "Uno"]
    assert [s["nombre"] for s in contenido["series"]] == ["Serie"]


def prueba_plataforma_desconocida_da_404(sesion: dict) -> None:
    respuesta = sesion["cliente"].get(
        f"{sesion['base']}/plataformas/no-existe", headers=sesion["cabeceras"]
    )
    assert respuesta.status_code == 404
