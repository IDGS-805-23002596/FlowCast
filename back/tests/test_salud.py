import asyncio
import shutil

from fastapi.testclient import TestClient
from sqlalchemy import delete

from app.db import FabricaSesiones, motor
from app.main import ARCHIVOS_DE_TELE, aplicacion
from app.models import VideoNoIncrustable


def prueba_salud_responde_ok() -> None:
    with TestClient(aplicacion) as cliente:
        respuesta = cliente.get("/salud")
    assert respuesta.status_code == 200
    assert respuesta.json() == {"estado": "ok"}


def prueba_salud_bd_consulta_postgres() -> None:
    with TestClient(aplicacion) as cliente:
        respuesta = cliente.get("/salud/bd")
    assert respuesta.status_code == 200
    assert respuesta.json() == {"estado": "ok", "base_datos": "ok"}


def prueba_pagina_del_trailer_se_puede_incrustar_y_manda_el_origen() -> None:
    with TestClient(aplicacion) as cliente:
        trailer = cliente.get("/trailer", params={"v": "aqz-KE-bpKQ"})
        administracion = cliente.get("/admin")
    assert trailer.status_code == 200
    assert "youtube.com/iframe_api" in trailer.text
    assert "X-Frame-Options" not in trailer.headers
    assert trailer.headers["Referrer-Policy"] == "strict-origin-when-cross-origin"
    assert administracion.headers["X-Frame-Options"] == "DENY"
    assert administracion.headers["Referrer-Policy"] == "no-referrer"


def prueba_reportar_un_trailer_valida_el_id() -> None:
    with TestClient(aplicacion) as cliente:
        malo = cliente.post("/trailer/fallo", params={"v": "no-es-un-id-valido"})
        bueno = cliente.post("/trailer/fallo", params={"v": "noExiste123", "codigo": "150"})
    assert malo.status_code == 422
    assert bueno.json() == {"video_id": None, "idioma": None}
    asyncio.run(_olvidar_video("noExiste123"))


async def _olvidar_video(video_id: str) -> None:
    async with FabricaSesiones() as sesion_bd:
        await sesion_bd.execute(
            delete(VideoNoIncrustable).where(VideoNoIncrustable.video_id == video_id)
        )
        await sesion_bd.commit()
    await motor.dispose()


def prueba_la_carpeta_de_las_teles_cachea_versiones_y_no_el_manifiesto() -> None:
    carpeta = ARCHIVOS_DE_TELE / "prueba-pytest"
    (carpeta / "abc123").mkdir(parents=True, exist_ok=True)
    (carpeta / "manifest.json").write_text('{"version": "abc123"}')
    (carpeta / "abc123" / "app.js").write_text("var x = 1;")
    try:
        with TestClient(aplicacion) as cliente:
            manifiesto = cliente.get("/tv/prueba-pytest/manifest.json", headers={"Origin": "null"})
            codigo = cliente.get("/tv/prueba-pytest/abc123/app.js")
            falta = cliente.get("/tv/prueba-pytest/abc123/no-existe.js")
    finally:
        shutil.rmtree(carpeta)
    assert manifiesto.json() == {"version": "abc123"}
    assert manifiesto.headers["Cache-Control"] == "no-cache"
    assert manifiesto.headers["Access-Control-Allow-Origin"] == "null"
    assert codigo.text == "var x = 1;"
    assert codigo.headers["Cache-Control"] == "public, max-age=31536000, immutable"
    assert falta.status_code == 404
