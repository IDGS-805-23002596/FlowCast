import asyncio
from collections.abc import Iterator

import httpx2 as httpx
from fastapi.testclient import TestClient
from sqlalchemy import delete

from app.db import FabricaSesiones, motor
from app.main import aplicacion
from app.models import Usuario
from app.routers.conexiones import obtener_cliente_http
from app.services import xtream


def borrar_usuarios(correos: list[str]) -> None:

    async def _borrar() -> None:
        async with FabricaSesiones() as sesion_bd:
            await sesion_bd.execute(delete(Usuario).where(Usuario.correo.in_(correos)))
            await sesion_bd.commit()
        await motor.dispose()

    asyncio.run(_borrar())


def registrar(cliente: TestClient, correo: str, nombre: str = "Prueba") -> dict[str, str]:
    respuesta = cliente.post(
        "/auth/registrar", json={"correo": correo, "contrasena": "secreta123", "nombre": nombre}
    )
    assert respuesta.status_code == 201, respuesta.text
    return {"Authorization": f"Bearer {respuesta.json()['token_acceso']}"}


def crear_conexion(cliente: TestClient, cabeceras: dict[str, str], usuario: str = "demo") -> str:
    respuesta = cliente.post(
        "/conexiones",
        json={"url_base": "falso.local:8080", "nombre_usuario": usuario, "contrasena": "buena"},
        headers=cabeceras,
    )
    assert respuesta.status_code == 201, respuesta.text
    return respuesta.json()["id"]


def instalar_proveedor_minimo() -> Iterator[None]:

    def responder(peticion: httpx.Request) -> httpx.Response:
        if peticion.url.params.get("password") != "buena":
            return httpx.Response(200, json={"user_info": {"auth": 0}})
        return httpx.Response(
            200,
            json={"user_info": {"auth": 1, "status": "Active"}, "server_info": {}},
        )

    async def cliente_falso():
        transporte = httpx.MockTransport(responder)
        async with httpx.AsyncClient(transport=transporte, headers=xtream.CABECERAS) as cliente:
            yield cliente

    aplicacion.dependency_overrides[obtener_cliente_http] = cliente_falso
    yield
    aplicacion.dependency_overrides.pop(obtener_cliente_http, None)
