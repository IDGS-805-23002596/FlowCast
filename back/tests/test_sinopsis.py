import asyncio
from typing import Any

import pytest

from app.routers import catalogo
from app.services import cache, tmdb


def prueba_sinopsis_de_tmdb_solo_si_falta(monkeypatch: pytest.MonkeyPatch) -> None:
    cache.vaciar()
    pedidas: list = []

    async def sinopsis(tipo: str, tmdb_id: str, cliente: Any = None) -> str:
        pedidas.append((tipo, tmdb_id))
        return "" if tmdb_id == "2" else "Desde TMDB"

    monkeypatch.setattr(tmdb, "disponible", lambda: True)
    monkeypatch.setattr(tmdb, "sinopsis", sinopsis)

    async def todo() -> list:
        return [
            await catalogo._sinopsis("serie", "1", "Del proveedor"),
            await catalogo._sinopsis("serie", "1", None),
            await catalogo._sinopsis("serie", "1", ""),
            await catalogo._sinopsis("pelicula", "2", None),
            await catalogo._sinopsis("pelicula", None, None),
        ]

    assert asyncio.run(todo()) == ["Del proveedor", "Desde TMDB", "Desde TMDB", None, None]
    assert pedidas == [("serie", "1"), ("pelicula", "2")]
    cache.vaciar()
