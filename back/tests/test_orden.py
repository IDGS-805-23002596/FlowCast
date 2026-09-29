import asyncio
from collections.abc import Iterator

import httpx2 as httpx
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import delete

from app.db import FabricaSesiones, motor
from app.main import aplicacion
from app.models import IntroSerie
from app.routers.conexiones import obtener_cliente_http
from app.services import cache, xtream
from tests.ayudantes import borrar_usuarios, crear_conexion, registrar

CORREOS = ["orden-prueba@ejemplo.com"]
PELICULAS = [
    {
        "stream_id": 1,
        "name": "ES- Zorro (2010)",
        "category_id": "1",
        "rating": "6",
        "added": "1600000000",
    },
    {
        "stream_id": 2,
        "name": "Sub Árbol rojo (2024)",
        "category_id": "1",
        "rating": "9",
        "added": "1500000000",
    },
    {"stream_id": 3, "name": "Barco", "category_id": "1", "rating": "", "added": "1700000000"},
    {
        "stream_id": 4,
        "name": "CA- Casa (1999)",
        "category_id": "1",
        "rating": "7.5",
        "added": "1400000000",
    },
]
SERIES = [
    {
        "series_id": 10,
        "name": "Mundo",
        "category_id": "2",
        "releaseDate": "2015-01-01",
        "rating": "8",
    },
    {
        "series_id": 11,
        "name": "Antes",
        "category_id": "2",
        "releaseDate": "2022-05-01",
        "rating": "5",
    },
]


def _responder(peticion: httpx.Request) -> httpx.Response:
    parametros = peticion.url.params
    if parametros.get("password") != "buena":
        return httpx.Response(200, json={"user_info": {"auth": 0}})
    respuestas = {
        None: {"user_info": {"auth": 1, "status": "Active"}, "server_info": {}},
        "get_vod_streams": PELICULAS,
        "get_series": SERIES,
        "get_vod_categories": [{"category_id": "1", "category_name": "Cine"}],
        "get_series_categories": [{"category_id": "2", "category_name": "Drama"}],
    }
    return httpx.Response(200, json=respuestas.get(parametros.get("action"), []))


async def _borrar_intros() -> None:
    async with FabricaSesiones() as sesion_bd:
        await sesion_bd.execute(delete(IntroSerie).where(IntroSerie.serie_id == "10"))
        await sesion_bd.commit()
    await motor.dispose()


@pytest.fixture(autouse=True)
def entorno() -> Iterator[None]:
    borrar_usuarios(CORREOS)
    asyncio.run(_borrar_intros())
    cache.vaciar()

    async def cliente_falso():
        transporte = httpx.MockTransport(_responder)
        async with httpx.AsyncClient(transport=transporte, headers=xtream.CABECERAS) as cliente:
            yield cliente

    aplicacion.dependency_overrides[obtener_cliente_http] = cliente_falso
    yield
    aplicacion.dependency_overrides.pop(obtener_cliente_http, None)
    borrar_usuarios(CORREOS)
    asyncio.run(_borrar_intros())
    cache.vaciar()


@pytest.fixture
def pedir() -> Iterator:
    with TestClient(aplicacion) as cliente:
        cabeceras = registrar(cliente, CORREOS[0])
        base = f"/conexiones/{crear_conexion(cliente, cabeceras)}"

        def _pedir(ruta: str, **parametros) -> list[str]:
            respuesta = cliente.get(f"{base}/{ruta}", params=parametros, headers=cabeceras)
            assert respuesta.status_code == 200, respuesta.text
            return [elemento["id"] for elemento in respuesta.json()]

        yield _pedir


@pytest.mark.parametrize(
    ("orden", "esperado"),
    [
        ("proveedor", ["1", "2", "3", "4"]),
        ("nombre", ["2", "3", "4", "1"]),
        ("nombre_desc", ["1", "4", "3", "2"]),
        ("calificacion", ["2", "4", "1", "3"]),
        ("agregadas", ["3", "1", "2", "4"]),
        ("nuevas", ["2", "1", "4", "3"]),
        ("antiguas", ["4", "1", "2", "3"]),
    ],
)
def prueba_ordenar_peliculas(pedir, orden: str, esperado: list[str]) -> None:
    assert pedir("peliculas", orden=orden) == esperado


def prueba_ordenar_y_paginar_usa_todo_el_catalogo(pedir) -> None:
    assert pedir("peliculas", orden="nombre", desde=2, limite=2) == ["4", "1"]


def prueba_ordenar_series_por_anio(pedir) -> None:
    assert pedir("series", orden="nuevas") == ["11", "10"]
    assert pedir("series", orden="nombre") == ["11", "10"]


def prueba_orden_invalido_da_422(pedir) -> None:
    with pytest.raises(AssertionError):
        pedir("peliculas", orden="al-reves")


def prueba_a_z_ignora_signos_al_inicio() -> None:
    from types import SimpleNamespace

    from app.routers.catalogo import _ordenar

    nombres = ['"Zeta" el regreso', "#Alfa", "亲密敌人", "Beta", "ES- (Gamma)"]
    elementos = [SimpleNamespace(nombre=nombre) for nombre in nombres]
    esperado = ["#Alfa", "Beta", "ES- (Gamma)", '"Zeta" el regreso', "亲密敌人"]
    assert [elemento.nombre for elemento in _ordenar(elementos, "nombre")] == esperado


def prueba_series_recien_actualizadas_y_canales_recien_agregados() -> None:
    from types import SimpleNamespace

    from app.routers.catalogo import _ordenar
    from app.services.xtream import _fecha_desde_unix, _serie_desde

    viejas = _serie_desde({"name": "Vieja", "last_modified": "1600000000"}, 1)
    nuevas = _serie_desde({"name": "Nueva", "last_modified": "1700000000"}, 2)
    assert [s.nombre for s in _ordenar([viejas, nuevas], "agregadas")] == ["Nueva", "Vieja"]
    canales = [
        SimpleNamespace(nombre="A", agregado_en=_fecha_desde_unix("1000")),
        SimpleNamespace(nombre="B", agregado_en=_fecha_desde_unix("2000")),
    ]
    assert [c.nombre for c in _ordenar(canales, "agregadas")] == ["B", "A"]


def prueba_analizar_titulos_del_proveedor() -> None:
    from app.services.titulos import analizar

    assert analizar("SU- La senal del apocalipsis").titulo == "La senal del apocalipsis"
    assert analizar("SU- La senal del apocalipsis").idioma == "Subtitulado"
    xenobot = analizar("Eng Xenobot (2025)")
    assert (xenobot.titulo, xenobot.anio, xenobot.idioma) == ("Xenobot", 2025, "Inglés")
    milicia = analizar("Sub The Militia (2025) 1080p")
    assert (milicia.titulo, milicia.anio, milicia.idioma) == ("The Militia", 2025, "Subtitulado")
    assert analizar("Toy Story (1995) (Lat)").idioma == "Español latino"
    assert analizar("ES- The Isolate Thief (2026) CAM").calidad == "CAM"
    felicidad = analizar("En busca de la felicidad (2006)")
    assert (felicidad.titulo, felicidad.idioma) == ("En busca de la felicidad", "")
    assert analizar("Phony (Farsante)").titulo == "Phony (Farsante)"


def prueba_agrupar_versiones_de_idioma() -> None:
    from types import SimpleNamespace

    from app.services.titulos import agrupar

    elementos = [
        SimpleNamespace(id="1", nombre="Sub Xenobot (2025)"),
        SimpleNamespace(id="2", nombre="Esp Xenobot (2025)"),
        SimpleNamespace(id="3", nombre="Eng Xenobot (2025)"),
        SimpleNamespace(id="4", nombre="Xenobot (1999)"),
        SimpleNamespace(id="5", nombre="Casa"),
        SimpleNamespace(id="6", nombre="Casa"),
    ]
    grupos = agrupar(elementos)
    assert [[e.id for e, _ in g.versiones] for g in grupos] == [
        ["2", "1", "3"],
        ["4"],
        ["5"],
        ["6"],
    ]
    assert grupos[0].titulo.titulo == "Xenobot"


def prueba_listado_agrupa_versiones(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setitem(
        globals(),
        "PELICULAS",
        [
            {"stream_id": 7, "name": "Sub Xenobot (2025)", "category_id": "1", "rating": "6"},
            {"stream_id": 8, "name": "Esp Xenobot (2025)", "category_id": "1", "rating": "8"},
            {"stream_id": 9, "name": "Barco", "category_id": "1", "added": "1700000000"},
        ],
    )
    with TestClient(aplicacion) as cliente:
        cabeceras = registrar(cliente, CORREOS[0])
        base = f"/conexiones/{crear_conexion(cliente, cabeceras)}"
        sueltas = cliente.get(f"{base}/peliculas", headers=cabeceras).json()
        juntas = cliente.get(f"{base}/peliculas?agrupar=true", headers=cabeceras).json()
        buscadas = cliente.get(f"{base}/buscar?q=xeno&agrupar=true", headers=cabeceras).json()
    assert [p["id"] for p in sueltas] == ["7", "8", "9"]
    assert sueltas[0]["titulo"] == "Xenobot" and sueltas[0]["idioma"] == "Subtitulado"
    assert [p["id"] for p in juntas] == ["8", "9"]
    xenobot = juntas[0]
    assert (xenobot["titulo"], xenobot["anio"], xenobot["calificacion"]) == ("Xenobot", 2025, 8.0)
    assert [(v["id"], v["idioma"]) for v in xenobot["versiones"]] == [
        ("8", "Español"),
        ("7", "Subtitulado"),
    ]
    assert juntas[1]["versiones"] == [] and juntas[1]["idioma"] is None
    assert buscadas["total_peliculas"] == 1 and len(buscadas["peliculas"][0]["versiones"]) == 2


def prueba_categorias_traducidas_y_unidas() -> None:
    from app.schemas.catalogo import Categoria
    from app.services.categorias import unir

    lista = [
        Categoria(id="1", nombre="Horror", padre_id=None),
        Categoria(id="2", nombre="Action", padre_id=None),
        Categoria(id="3", nombre="Terror", padre_id=None, bloqueada=True),
        Categoria(id="4", nombre="Mexicanas", padre_id=None),
        Categoria(id="5", nombre="Otra cosa", padre_id=None),
    ]
    assert [(c.id, c.nombre, c.bloqueada) for c in unir(lista)] == [
        ("1,3", "Terror", True),
        ("2", "Acción", False),
        ("4", "Mexicanas", False),
        ("5", "Otra cosa", False),
    ]


def prueba_listado_de_varias_categorias(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setitem(
        globals(),
        "PELICULAS",
        [
            {"stream_id": 1, "name": "Uno", "category_id": "1"},
            {"stream_id": 2, "name": "Dos", "category_id": "2"},
            {"stream_id": 3, "name": "Tres", "category_id": "3"},
        ],
    )
    with TestClient(aplicacion) as cliente:
        cabeceras = registrar(cliente, CORREOS[0])
        base = f"/conexiones/{crear_conexion(cliente, cabeceras)}"
        respuesta = cliente.get(f"{base}/peliculas?categoria_id=1,3", headers=cabeceras).json()
    assert [p["id"] for p in respuesta] == ["1", "3"]


def prueba_clasificacion_prefiere_mexico() -> None:
    from app.services.tmdb import _por_pais

    assert _por_pais({"US": "PG-13", "MX": "B"}) == "B"
    assert _por_pais({"US": "R"}) == "R (EE. UU.)"
    assert _por_pais({}) is None


def prueba_creditos_por_serie() -> None:
    with TestClient(aplicacion) as cliente:
        cabeceras = registrar(cliente, CORREOS[0])
        base = f"/conexiones/{crear_conexion(cliente, cabeceras)}/series/10/creditos"
        assert cliente.get(base, headers=cabeceras).json() == {"segundos": None}
        assert cliente.put(base, json={"segundos": 197}, headers=cabeceras).json() == {
            "segundos": 197.0
        }
        cliente.put(base, json={"segundos": 120.5}, headers=cabeceras)
        assert cliente.get(base, headers=cabeceras).json() == {"segundos": 120.5}
        assert cliente.put(base, json={"segundos": 5}, headers=cabeceras).status_code == 422
        assert cliente.put(base, json={}, headers=cabeceras).status_code == 422


def prueba_intro_por_serie() -> None:
    with TestClient(aplicacion) as cliente:
        cabeceras = registrar(cliente, CORREOS[0])
        base = f"/conexiones/{crear_conexion(cliente, cabeceras)}/series/10/intro"
        assert cliente.get(base, headers=cabeceras).json() == {"inicio": None, "fin": None}
        guardada = cliente.put(base, json={"inicio": 62, "fin": 118.5}, headers=cabeceras)
        assert guardada.json() == {"inicio": 62.0, "fin": 118.5}
        cliente.put(base, json={"inicio": 0, "fin": 45}, headers=cabeceras)
        assert cliente.get(base, headers=cabeceras).json() == {"inicio": 0.0, "fin": 45.0}
        assert (
            cliente.put(base, json={"inicio": 50, "fin": 55}, headers=cabeceras).status_code == 422
        )
        assert (
            cliente.put(base, json={"inicio": 0, "fin": 400}, headers=cabeceras).status_code == 422
        )
        assert cliente.put(base, json={"inicio": 3}, headers=cabeceras).status_code == 422
