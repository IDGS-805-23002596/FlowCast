import asyncio
from datetime import UTC, datetime, timedelta
from typing import Any

import httpx2 as httpx
import pytest
from sqlalchemy import delete, select

from app.config import configuracion
from app.db import FabricaSesiones, motor
from app.models import TrailerTmdb, VideoNoIncrustable
from app.routers import catalogo
from app.services import cache, tmdb, trailers, youtube


def prueba_reconoce_el_id_de_youtube_en_cualquier_formato() -> None:
    assert youtube.id_de_video("iVLrJTugzco") == "iVLrJTugzco"
    assert youtube.id_de_video("https://www.youtube.com/watch?v=iVLrJTugzco") == "iVLrJTugzco"
    assert youtube.id_de_video("https://youtu.be/iVLrJTugzco") == "iVLrJTugzco"
    assert youtube.id_de_video("") is None
    assert youtube.id_de_video(None) is None


def prueba_tmdb_prefiere_trailer_en_espanol_de_mexico(monkeypatch: pytest.MonkeyPatch) -> None:
    resultados = [
        {
            "site": "YouTube",
            "type": "Teaser",
            "key": "teaserEsMX1",
            "iso_639_1": "es",
            "iso_3166_1": "MX",
        },
        {
            "site": "YouTube",
            "type": "Trailer",
            "key": "trailerEnUS",
            "iso_639_1": "en",
            "iso_3166_1": "US",
        },
        {
            "site": "Vimeo",
            "type": "Trailer",
            "key": "vimeoEsMX01",
            "iso_639_1": "es",
            "iso_3166_1": "MX",
        },
        {
            "site": "YouTube",
            "type": "Featurette",
            "key": "detrasEsMX1",
            "iso_639_1": "es",
            "iso_3166_1": "MX",
        },
        {
            "site": "YouTube",
            "type": "Trailer",
            "key": "trailerEsES",
            "iso_639_1": "es",
            "iso_3166_1": "ES",
        },
        {
            "site": "YouTube",
            "type": "Trailer",
            "key": "trailerEsMX",
            "iso_639_1": "es",
            "iso_3166_1": "MX",
        },
    ]
    videos = {"original_language": "fr", "videos": {"results": resultados}}

    async def obtener(ruta: str, cliente: Any, idioma: str | None = None, **extras: str) -> Any:
        assert ruta == "/movie/603"
        assert extras["append_to_response"] == "videos"
        return videos

    monkeypatch.setattr(configuracion, "tmdb_clave_api", "clave")
    monkeypatch.setattr(tmdb, "_obtener", obtener)
    assert asyncio.run(tmdb.trailers("pelicula", "603")) == [
        tmdb.Trailer("trailerEsMX", "es"),
        tmdb.Trailer("teaserEsMX1", "es"),
        tmdb.Trailer("trailerEsES", "es"),
        tmdb.Trailer("trailerEnUS", "fr"),
    ]


async def _con_sesion(funcion: Any) -> Any:
    async with FabricaSesiones() as sesion_bd:
        await sesion_bd.execute(delete(TrailerTmdb).where(TrailerTmdb.referencia.like("99000000%")))
        await sesion_bd.commit()
        try:
            return await funcion(sesion_bd)
        finally:
            await sesion_bd.execute(
                delete(TrailerTmdb).where(TrailerTmdb.referencia.like("99000000%"))
            )
            await sesion_bd.commit()
            await motor.dispose()


def prueba_usa_tmdb_cuando_el_del_proveedor_falta_o_ya_no_existe(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    consultas = []

    async def trailers_tmdb(tipo: str, tmdb_id: str, cliente: Any = None) -> list[tmdb.Trailer]:
        consultas.append(tmdb_id)
        return [tmdb.Trailer("borradoTmdb", "en"), tmdb.Trailer("trailerTmdb", "en")]

    async def se_puede_incrustar(video_id: str, cliente: Any = None) -> bool:
        return video_id not in ("borradoProv", "borradoTmdb")

    monkeypatch.setattr(configuracion, "tmdb_clave_api", "clave")
    monkeypatch.setattr(tmdb, "trailers", trailers_tmdb)
    monkeypatch.setattr(youtube, "se_puede_incrustar", se_puede_incrustar)
    cache.vaciar()

    async def revisar(sesion_bd: Any) -> list[Any]:
        return [
            await catalogo._trailer(sesion_bd, "pelicula", "vivoProveed", "990000001"),
            await catalogo._trailer(sesion_bd, "pelicula", "borradoProv", "990000001"),
            await catalogo._trailer(sesion_bd, "serie", None, "990000002"),
            await catalogo._trailer(sesion_bd, "serie", None, "990000002"),
            await catalogo._trailer(sesion_bd, "serie", None, None),
            await sesion_bd.get(TrailerTmdb, ("serie", "990000002")),
        ]

    resultado = asyncio.run(_con_sesion(revisar))
    cache.vaciar()
    assert resultado[0] == tmdb.Trailer("vivoProveed", None)
    assert resultado[1] == tmdb.Trailer("trailerTmdb", "en")
    assert resultado[2] == resultado[3] == tmdb.Trailer("trailerTmdb", "en")
    assert resultado[4] is None
    assert resultado[5].video_id == "trailerTmdb"
    assert consultas == ["990000001", "990000002"]


def prueba_la_rutina_busca_nuevos_y_renueva_los_vencidos(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def trailers_tmdb(tipo: str, tmdb_id: str, cliente: Any = None) -> Any:
        return {"990000001": [tmdb.Trailer("nuevoTrail1", "es")], "990000003": None}.get(
            tmdb_id, []
        )

    async def se_puede_incrustar(video_id: str, cliente: Any = None) -> bool:
        return True

    monkeypatch.setattr(configuracion, "tmdb_clave_api", "clave")
    monkeypatch.setattr(tmdb, "trailers", trailers_tmdb)
    monkeypatch.setattr(youtube, "se_puede_incrustar", se_puede_incrustar)

    async def correr(sesion_bd: Any) -> Any:
        hace_mucho = datetime.now(UTC) - timedelta(days=40)
        sesion_bd.add_all(
            [
                TrailerTmdb(
                    tipo_contenido="pelicula",
                    referencia="990000004",
                    video_id="viejoTrail1",
                    idioma="en",
                    consultado_en=hace_mucho,
                ),
                TrailerTmdb(
                    tipo_contenido="pelicula",
                    referencia="990000005",
                    video_id="recienTrail",
                    idioma="en",
                ),
                TrailerTmdb(
                    tipo_contenido="pelicula",
                    referencia="990000006",
                    video_id=None,
                    idioma=None,
                    consultado_en=datetime.now(UTC) - timedelta(days=8),
                ),
            ]
        )
        await sesion_bd.commit()
        catalogo_actual = [("pelicula", f"99000000{n}") for n in (1, 3, 4, 5, 6)]
        faltan = await trailers.pendientes(sesion_bd, catalogo_actual)
        async with httpx.AsyncClient() as cliente:
            conteo = await trailers.sincronizar(sesion_bd, faltan, cliente)
        guardados = {
            fila.referencia: fila.video_id
            for fila in (
                await sesion_bd.execute(
                    select(TrailerTmdb).where(TrailerTmdb.referencia.like("99000000%"))
                )
            ).scalars()
        }
        return faltan, conteo, guardados

    faltan, conteo, guardados = asyncio.run(_con_sesion(correr))
    assert faltan == [
        ("pelicula", "990000001"),
        ("pelicula", "990000003"),
        ("pelicula", "990000004"),
        ("pelicula", "990000006"),
    ]
    assert conteo == (1, 2)
    assert guardados == {
        "990000001": "nuevoTrail1",
        "990000004": None,
        "990000005": "recienTrail",
        "990000006": None,
    }


def prueba_busca_en_tmdb_por_nombre_y_anio_sin_confundir_peliculas(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    resultados = {
        "results": [
            {"id": 11, "title": "María", "original_title": "Maria", "release_date": "1990-01-01"},
            {
                "id": 22,
                "title": "María Callas",
                "original_title": "Maria",
                "release_date": "2024-11-27",
            },
        ]
    }
    pedidos = []

    async def obtener(ruta: str, cliente: Any, idioma: str | None = None, **extras: str) -> Any:
        pedidos.append((ruta, extras))
        return resultados

    monkeypatch.setattr(configuracion, "tmdb_clave_api", "clave")
    monkeypatch.setattr(tmdb, "_obtener", obtener)
    assert asyncio.run(tmdb.buscar_id("pelicula", "Maria", 2024)) == "22"
    assert pedidos[0] == ("/search/movie", {"query": "Maria", "year": "2024"})
    assert asyncio.run(tmdb.buscar_id("pelicula", "Maria", 2010)) is None
    assert asyncio.run(tmdb.buscar_id("pelicula", "Otra cosa", None)) is None


def prueba_un_video_rechazado_se_cambia_por_otro_y_no_se_vuelve_a_usar(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def trailers_tmdb(tipo: str, tmdb_id: str, cliente: Any = None) -> list[tmdb.Trailer]:
        return [tmdb.Trailer("rechazado01", "en"), tmdb.Trailer("reemplazo01", "es")]

    async def se_puede_incrustar(video_id: str, cliente: Any = None) -> bool:
        return True

    monkeypatch.setattr(configuracion, "tmdb_clave_api", "clave")
    monkeypatch.setattr(tmdb, "trailers", trailers_tmdb)
    monkeypatch.setattr(youtube, "se_puede_incrustar", se_puede_incrustar)
    cache.vaciar()

    async def correr(sesion_bd: Any) -> list[Any]:
        await sesion_bd.execute(
            delete(VideoNoIncrustable).where(VideoNoIncrustable.video_id == "rechazado01")
        )
        await sesion_bd.commit()
        try:
            antes = await catalogo._trailer(sesion_bd, "pelicula", None, "990000007")
            reemplazo = await trailers.reportar_fallo(sesion_bd, "rechazado01", "150")
            despues = await catalogo._trailer(sesion_bd, "pelicula", None, "990000007")
            del_proveedor = await catalogo._trailer(
                sesion_bd, "pelicula", "rechazado01", "990000007"
            )
            return [antes, reemplazo, despues, del_proveedor]
        finally:
            await sesion_bd.execute(
                delete(VideoNoIncrustable).where(VideoNoIncrustable.video_id == "rechazado01")
            )
            await sesion_bd.commit()

    antes, reemplazo, despues, del_proveedor = asyncio.run(_con_sesion(correr))
    cache.vaciar()
    assert antes == tmdb.Trailer("rechazado01", "en")
    assert reemplazo == despues == del_proveedor == tmdb.Trailer("reemplazo01", "es")
