from collections.abc import Iterator
from typing import Any

import httpx2 as httpx
import pytest
from fastapi.testclient import TestClient

from app.main import aplicacion
from app.routers import inicio
from app.routers.conexiones import obtener_cliente_http
from app.services import cache, tmdb, xtream
from tests.ayudantes import borrar_usuarios, crear_conexion, registrar

CORREOS = ["inicio-prueba@ejemplo.com"]
FUTURO = "4102444800"


def _pelicula(numero: int, categoria: str = "2") -> dict[str, Any]:
    return {
        "stream_id": numero,
        "name": f"Pelicula {numero}",
        "category_id": categoria,
        "tmdb_id": f"9900001{numero:02d}",
        "added": str(1700000000 + numero),
    }


def _serie(numero: int, modificada: str = "1700000000") -> dict[str, Any]:
    return {
        "series_id": numero,
        "name": f"Serie {numero}",
        "category_id": "1",
        "tmdb_id": f"9900001{numero:02d}",
        "last_modified": modificada,
    }


PELICULAS = [_pelicula(n) for n in range(1, 7)] + [_pelicula(7, "9")]
SERIES = [_serie(10, FUTURO)] + [_serie(n) for n in range(11, 15)]
TENDENCIAS = {
    "pelicula": ["990000107", "990000102", "990000103", "990000104", "990000105"],
    "serie": ["990000111", "990000112", "990000113", "990000114"],
}
RECOMENDACIONES = {
    "990000101": ["990000101", "990000102", "990000103", "990000104", "990000106"],
    "990000110": ["990000111", "990000112", "990000113", "990000114"],
}


def _responder(peticion: httpx.Request) -> httpx.Response:
    parametros = peticion.url.params
    if parametros.get("password") != "buena":
        return httpx.Response(200, json={"user_info": {"auth": 0}})
    respuestas = {
        None: {"user_info": {"auth": 1, "status": "Active"}, "server_info": {}},
        "get_vod_streams": PELICULAS,
        "get_series": SERIES,
        "get_vod_categories": [
            {"category_id": "2", "category_name": "Cine"},
            {"category_id": "9", "category_name": "Adultos +18"},
        ],
        "get_series_categories": [{"category_id": "1", "category_name": "Drama"}],
    }
    return httpx.Response(200, json=respuestas.get(parametros.get("action"), []))


@pytest.fixture(autouse=True)
def entorno(monkeypatch: pytest.MonkeyPatch) -> Iterator[None]:
    borrar_usuarios(CORREOS)
    cache.vaciar()

    async def tendencias(tipo: str, paginas: int = 3, cliente: Any = None) -> list[str]:
        return TENDENCIAS[tipo]

    async def recomendaciones(tipo: str, tmdb_id: str, cliente: Any = None) -> list[str]:
        return RECOMENDACIONES.get(tmdb_id, [])

    async def cliente_falso():
        transporte = httpx.MockTransport(_responder)
        async with httpx.AsyncClient(transport=transporte, headers=xtream.CABECERAS) as cliente:
            yield cliente

    async def logo(tipo: str, tmdb_id: str, cliente: Any = None) -> str:
        return f"https://image.tmdb.org/t/p/w500/{tmdb_id}.png"

    async def trailer(sesion_bd: Any, tipo: str, tmdb_id: str | None, nombre: str | None) -> Any:
        return tmdb.Trailer(f"v{tmdb_id[-10:]}", "en") if tmdb_id != "990000103" else None

    monkeypatch.setattr(tmdb, "logo", logo)
    monkeypatch.setattr(inicio.trailers, "obtener", trailer)
    monkeypatch.setattr(tmdb, "tendencias", tendencias)
    monkeypatch.setattr(tmdb, "recomendaciones", recomendaciones)
    aplicacion.dependency_overrides[obtener_cliente_http] = cliente_falso
    yield
    aplicacion.dependency_overrides.pop(obtener_cliente_http, None)
    borrar_usuarios(CORREOS)
    cache.vaciar()


def _progreso(cliente: TestClient, cabeceras: dict, perfil_id: str, datos: dict) -> None:
    respuesta = cliente.put(
        f"/perfiles/{perfil_id}/progreso",
        json={"posicion_segundos": 300, "duracion_segundos": 3000, **datos},
        headers=cabeceras,
    )
    assert respuesta.status_code == 200, respuesta.text


def prueba_inicio_personaliza_y_respeta_el_control_parental() -> None:
    with TestClient(aplicacion) as cliente:
        cabeceras = registrar(cliente, CORREOS[0])
        conexion_id = crear_conexion(cliente, cabeceras)
        perfil_id = cliente.get("/perfiles", headers=cabeceras).json()[0]["id"]
        _progreso(
            cliente,
            cabeceras,
            perfil_id,
            {
                "conexion_id": conexion_id,
                "tipo_contenido": "episodio",
                "stream_id": "501",
                "serie_id": "10",
                "titulo": "Serie 10 T1E1",
            },
        )
        _progreso(
            cliente,
            cabeceras,
            perfil_id,
            {
                "conexion_id": conexion_id,
                "tipo_contenido": "pelicula",
                "stream_id": "1",
                "titulo": "Pelicula 1",
            },
        )
        respuesta = cliente.get(
            f"/conexiones/{conexion_id}/inicio",
            params={"perfil_id": perfil_id},
            headers=cabeceras,
        )
        infantil = cliente.post(
            "/perfiles", json={"nombre": "Peques", "es_infantil": True}, headers=cabeceras
        ).json()
        vacia = cliente.get(
            f"/conexiones/{conexion_id}/inicio",
            params={"perfil_id": infantil["id"]},
            headers=cabeceras,
        )
    assert respuesta.status_code == 200, respuesta.text
    filas = {fila["clave"]: fila for fila in respuesta.json()}
    assert list(filas) == [
        "porque-pelicula-1",
        "nuevos-episodios",
        "tendencias-peliculas",
        "porque-serie-10",
        "tendencias-series",
        "recientes-peliculas",
        "recientes-series",
    ]
    nombres = {
        clave: [e["nombre"] for e in fila["peliculas"] or fila["series"]]
        for clave, fila in filas.items()
    }
    assert filas["porque-pelicula-1"]["titulo"] == "Porque viste Pelicula 1"
    assert nombres["porque-pelicula-1"] == [f"Pelicula {n}" for n in (2, 3, 4, 6)]
    assert nombres["nuevos-episodios"] == ["Serie 10"]
    assert nombres["tendencias-peliculas"] == [f"Pelicula {n}" for n in (2, 3, 4, 5)]
    assert nombres["porque-serie-10"] == [f"Serie {n}" for n in (11, 12, 13, 14)]
    assert nombres["recientes-peliculas"] == [f"Pelicula {n}" for n in (6, 5, 4, 3, 2, 1)]
    assert vacia.json() == []
    portadas = filas["tendencias-peliculas"]["portadas"]
    assert list(portadas) == ["2", "3", "4", "5"]
    assert portadas["2"] == {
        "trailer_youtube": "v990000102",
        "trailer_idioma": "en",
        "logo": "https://image.tmdb.org/t/p/w500/990000102.png",
    }
    assert portadas["3"]["trailer_youtube"] is None
    assert filas["recientes-peliculas"]["portadas"] == {}
