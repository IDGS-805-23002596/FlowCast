from collections.abc import Iterator
from typing import Any

import httpx2 as httpx
import pytest
from fastapi.testclient import TestClient

from app.main import aplicacion
from app.routers.conexiones import obtener_cliente_http
from app.services import cache, skipdb, tmdb, xtream
from tests.ayudantes import borrar_usuarios, crear_conexion, registrar
from tests.test_catalogo import LOGIN_OK, RESPUESTAS

CORREOS = ["saltos-prueba@ejemplo.com"]


@pytest.fixture(autouse=True)
def entorno(monkeypatch: pytest.MonkeyPatch) -> Iterator[list]:
    borrar_usuarios(CORREOS)
    cache.vaciar()
    llamadas: list = []

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

    async def buscar_id(tipo: str, titulo: str, anio: Any, cliente: Any = None) -> str:
        return "77"

    async def imdb_de_serie(tmdb_id: str, cliente: Any = None) -> str:
        return f"tt{tmdb_id}"

    async def consultar(imdb_id: str, temporada: int, episodio: int, duracion: float) -> Any:
        llamadas.append((imdb_id, temporada, episodio, round(duracion)))
        if temporada == 2:
            return skipdb.Saltos()
        return skipdb.Saltos(62.0, 118.5, 2400.0)

    monkeypatch.setattr(tmdb, "buscar_id", buscar_id)
    monkeypatch.setattr(tmdb, "imdb_de_serie", imdb_de_serie)
    monkeypatch.setattr(skipdb, "consultar", consultar)
    aplicacion.dependency_overrides[obtener_cliente_http] = cliente_falso
    yield llamadas
    aplicacion.dependency_overrides.pop(obtener_cliente_http, None)
    borrar_usuarios(CORREOS)
    cache.vaciar()


def prueba_interpretar_solo_acepta_coincidencias_confiables() -> None:
    def segmento(
        inicio: int, fin: int, coincidencia: str = "exact", confianza: float = 0.9
    ) -> dict:
        return {
            "start_ms": inicio,
            "end_ms": fin,
            "match": coincidencia,
            "confidence": confianza,
        }

    datos = {"segments": {"intro": segmento(60000, 118500), "outro": segmento(2400000, 2500000)}}
    assert skipdb.interpretar(datos, 2500) == skipdb.Saltos(60.0, 118.5, 2400.0)
    fuera = {"segments": {"intro": segmento(60000, 118500, "out-of-range")}}
    assert skipdb.interpretar(fuera, 2500) == skipdb.Saltos()
    dudosa = {"segments": {"intro": segmento(60000, 118500, "shifted", 0.2)}}
    assert skipdb.interpretar(dudosa, 2500) == skipdb.Saltos()
    sin_intro = {"segments": {"intro": segmento(0, 0), "outro": segmento(100000, 200000)}}
    assert skipdb.interpretar(sin_intro, 2500) == skipdb.Saltos()
    assert skipdb.interpretar(None, 2500) == skipdb.Saltos()


def prueba_saltos_de_episodio(entorno: list) -> None:
    with TestClient(aplicacion) as cliente:
        cabeceras = registrar(cliente, CORREOS[0])
        base = f"/conexiones/{crear_conexion(cliente, cabeceras)}/saltos"

        def pedir(episodio: str, duracion: float = 2523.4, serie: str = "900") -> Any:
            return cliente.get(
                base,
                params={"serie_id": serie, "episodio_id": episodio, "duracion": duracion},
                headers=cabeceras,
            )

        con_datos = pedir("9001")
        otra_vez = pedir("9001", 2521)
        sin_datos = pedir("9002")
        ajeno = pedir("1")
        corta = pedir("9001", 20)
        sin_sesion = cliente.get(base, params={"serie_id": "900", "episodio_id": "9001"})
    assert con_datos.status_code == 200, con_datos.text
    assert con_datos.json() == {
        "intro_inicio": 62.0,
        "intro_fin": 118.5,
        "creditos": 2400.0,
        "fuente": "SkipDB",
    }
    assert otra_vez.json() == con_datos.json()
    assert sin_datos.json() == {
        "intro_inicio": None,
        "intro_fin": None,
        "creditos": None,
        "fuente": None,
    }
    assert ajeno.status_code == 404
    assert corta.status_code == 422
    assert sin_sesion.status_code == 401
    assert entorno == [("tt77", 1, 1, 2523), ("tt77", 2, 1, 2523)]
