from collections.abc import Iterator
from typing import Any

import httpx2 as httpx
import pytest
from fastapi.testclient import TestClient

from app.main import aplicacion
from app.routers.conexiones import obtener_cliente_http
from app.services import cache, tmdb, xtream
from tests.ayudantes import borrar_usuarios, crear_conexion, registrar
from tests.test_inicio import _responder

CORREOS = ["personas-prueba@ejemplo.com"]
PERSONAS = [
    tmdb.Persona("1", "Ana Actriz", "https://foto/1.jpg", "Acting"),
    tmdb.Persona("2", "Diego Director", None, "Directing"),
    tmdb.Persona("3", "Otra Persona", None, "Acting"),
]
CREDITOS = {
    "1": tmdb.CreditosPersona(
        actuacion={"pelicula": ["990000103", "990000101", "990000107"], "serie": ["990000111"]},
        direccion={"pelicula": [], "serie": []},
    ),
    "2": tmdb.CreditosPersona(
        actuacion={"pelicula": ["990000102"], "serie": []},
        direccion={"pelicula": ["990000104"], "serie": []},
    ),
    "3": tmdb.CreditosPersona(
        actuacion={"pelicula": ["999"], "serie": []}, direccion={"pelicula": [], "serie": []}
    ),
}


@pytest.fixture(autouse=True)
def entorno(monkeypatch: pytest.MonkeyPatch) -> Iterator[list]:
    borrar_usuarios(CORREOS)
    cache.vaciar()
    consultas: list = []

    async def buscar_personas(texto: str, cliente: Any = None) -> list[tmdb.Persona]:
        consultas.append(texto)
        return PERSONAS

    async def creditos_persona(persona_id: str, cliente: Any = None) -> Any:
        return CREDITOS[persona_id]

    async def cliente_falso():
        transporte = httpx.MockTransport(_responder)
        async with httpx.AsyncClient(transport=transporte, headers=xtream.CABECERAS) as cliente:
            yield cliente

    monkeypatch.setattr(tmdb, "disponible", lambda: True)
    monkeypatch.setattr(tmdb, "buscar_personas", buscar_personas)
    monkeypatch.setattr(tmdb, "creditos_persona", creditos_persona)
    aplicacion.dependency_overrides[obtener_cliente_http] = cliente_falso
    yield consultas
    aplicacion.dependency_overrides.pop(obtener_cliente_http, None)
    borrar_usuarios(CORREOS)
    cache.vaciar()


def prueba_buscar_personas(entorno: list) -> None:
    with TestClient(aplicacion) as cliente:
        cabeceras = registrar(cliente, CORREOS[0])
        base = f"/conexiones/{crear_conexion(cliente, cabeceras)}/buscar/personas"
        actriz = cliente.get(base, params={"q": "ana act"}, headers=cabeceras)
        otra_vez = cliente.get(base, params={"q": "Ana Act"}, headers=cabeceras)
        director = cliente.get(base, params={"q": "diego"}, headers=cabeceras)
        nadie = cliente.get(base, params={"q": "zzz"}, headers=cabeceras)
        corta = cliente.get(base, params={"q": "an"}, headers=cabeceras)
        sin_sesion = cliente.get(base, params={"q": "ana"})
    assert actriz.status_code == 200, actriz.text
    [encontrada] = actriz.json()
    assert encontrada["nombre"] == "Ana Actriz"
    assert encontrada["rol"] == "actuacion"
    assert [p["nombre"] for p in encontrada["peliculas"]] == ["Pelicula 3", "Pelicula 1"]
    assert [s["nombre"] for s in encontrada["series"]] == ["Serie 11"]
    assert otra_vez.json() == actriz.json()
    [dirige] = director.json()
    assert dirige["rol"] == "direccion"
    assert [p["nombre"] for p in dirige["peliculas"]] == ["Pelicula 4"]
    assert nadie.json() == []
    assert corta.status_code == 422
    assert sin_sesion.status_code == 401
    assert entorno == ["ana act", "diego", "zzz"]
