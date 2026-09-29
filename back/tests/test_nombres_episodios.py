import asyncio
from typing import Any

import pytest

from app.config import configuracion
from app.routers import catalogo
from app.schemas import catalogo as esquemas
from app.services import cache, titulos, tmdb


def prueba_limpia_el_titulo_que_manda_el_proveedor() -> None:
    assert titulos.nombre_de_episodio("iCarly (2021) - S01E01 - Piloto") == "Piloto"
    assert titulos.nombre_de_episodio("Battlestar Galactica - S01E01 - 33 minutos") == "33 minutos"
    assert titulos.nombre_de_episodio("Regreso") == "Regreso"
    assert (
        titulos.nombre_de_episodio(
            "Hijos de la Anarquia (Sons of Anarchy) - S01E03 - Hijos de la Anarquia (Sons of Anarchy) S01E03"
        )
        is None
    )
    assert titulos.nombre_de_episodio("La Rabina (Sub) S01E01") is None
    assert titulos.nombre_de_episodio("Fairy Tail  - S01E01 - Episodio 1") is None
    assert titulos.nombre_de_episodio(None) is None


def _temporada_tmdb(nombres: list[str], resumenes: list[str]) -> dict[str, Any]:
    return {
        "episodes": [
            {"episode_number": i + 1, "name": nombre, "overview": resumen, "still_path": None}
            for i, (nombre, resumen) in enumerate(zip(nombres, resumenes, strict=True))
        ]
    }


def prueba_tmdb_usa_espanol_de_mexico_luego_espana_y_al_final_ingles(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    respuestas = {
        "es-MX": _temporada_tmdb(["Episodio 1", "Semillas", "Episodio 3"], ["", "", ""]),
        "es-ES": _temporada_tmdb(["Piloto", "Semillas ES", "Episodio 3"], ["Inicio", "", ""]),
        "en-US": _temporada_tmdb(["Pilot", "Seeds", "Fun Town"], ["Start", "Seeds plot", "Fair"]),
    }
    pedidos = []

    async def obtener(ruta: str, cliente: Any, idioma: str | None = None) -> Any:
        pedidos.append(idioma)
        return respuestas[idioma]

    monkeypatch.setattr(configuracion, "tmdb_clave_api", "clave")
    monkeypatch.setattr(tmdb, "_obtener", obtener)
    episodios = asyncio.run(tmdb.episodios_temporada("1409", 1))
    assert pedidos == ["es-MX", "es-ES", "en-US"]
    assert [(episodios[n].nombre, episodios[n].idioma_nombre) for n in (1, 2, 3)] == [
        ("Piloto", "es-ES"),
        ("Semillas", "es-MX"),
        ("Fun Town", "en-US"),
    ]
    assert [episodios[n].sinopsis for n in (1, 2, 3)] == ["Inicio", "Seeds plot", "Fair"]


def prueba_la_ficha_prefiere_tmdb_mexico_luego_el_proveedor(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def episodios_temporada(tmdb_id: str, temporada: int, cliente: Any = None) -> dict:
        return {
            1: tmdb.EpisodioTmdb("Piloto MX", "es-MX", "Sinopsis", None),
            2: tmdb.EpisodioTmdb("Seeds", "en-US", None, "http://img/2.jpg"),
            3: tmdb.EpisodioTmdb("Fun Town", "en-US", None, None),
        }

    def episodio(numero: int, titulo: str) -> esquemas.Episodio:
        return esquemas.Episodio(
            id=str(numero),
            temporada=1,
            numero=numero,
            titulo=titulo,
            extension="mkv",
            imagen=None,
            sinopsis=None,
            duracion_segundos=None,
        )

    temporadas = [
        esquemas.Temporada(
            numero=1,
            nombre=None,
            portada=None,
            episodios=[
                episodio(1, "Serie - S01E01 - Serie S01E01"),
                episodio(2, "Serie - S01E02 - Semillas"),
                episodio(3, "Serie S01E03"),
                episodio(4, "Serie S01E04"),
            ],
        )
    ]
    monkeypatch.setattr(configuracion, "tmdb_clave_api", "clave")
    monkeypatch.setattr(tmdb, "episodios_temporada", episodios_temporada)
    cache.vaciar()
    asyncio.run(catalogo._nombrar_episodios(temporadas, "1409"))
    cache.vaciar()
    resultado = temporadas[0].episodios
    assert [e.titulo for e in resultado] == ["Piloto MX", "Semillas", "Fun Town", "Episodio 4"]
    assert resultado[0].sinopsis == "Sinopsis"
    assert resultado[1].imagen == "http://img/2.jpg"
