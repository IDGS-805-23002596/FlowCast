import asyncio

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import delete

from app.db import FabricaSesiones, motor
from app.main import aplicacion
from app.models import Usuario
from app.services import limite_acceso

CORREO = "prueba-seguridad@ejemplo.com"
CONTRASENA = "secreta123"


async def _borrar() -> None:
    async with FabricaSesiones() as sesion_bd:
        await sesion_bd.execute(delete(Usuario).where(Usuario.correo == CORREO))
        await sesion_bd.commit()
    await motor.dispose()


@pytest.fixture
def cliente() -> TestClient:
    asyncio.run(_borrar())
    with TestClient(aplicacion) as cliente_http:
        respuesta = cliente_http.post(
            "/auth/registrar",
            json={"correo": CORREO, "contrasena": CONTRASENA, "nombre": "Seguridad"},
        )
        assert respuesta.status_code == 201, respuesta.text
        yield cliente_http
    asyncio.run(_borrar())


def _entrar(cliente: TestClient, contrasena: str) -> int:
    return cliente.post(
        "/auth/iniciar-sesion", json={"correo": CORREO, "contrasena": contrasena}
    ).status_code


def prueba_cinco_fallos_bloquean_la_cuenta(cliente: TestClient) -> None:
    for _ in range(limite_acceso.MAXIMO_POR_CUENTA):
        assert _entrar(cliente, "equivocada") == 401
    respuesta = cliente.post(
        "/auth/iniciar-sesion", json={"correo": CORREO, "contrasena": CONTRASENA}
    )
    assert respuesta.status_code == 429
    assert int(respuesta.headers["retry-after"]) > 0


def prueba_entrar_bien_reinicia_el_contador(cliente: TestClient) -> None:
    for _ in range(limite_acceso.MAXIMO_POR_CUENTA - 1):
        assert _entrar(cliente, "equivocada") == 401
    assert _entrar(cliente, CONTRASENA) == 200
    for _ in range(limite_acceso.MAXIMO_POR_CUENTA - 1):
        assert _entrar(cliente, "equivocada") == 401
    assert _entrar(cliente, CONTRASENA) == 200


def prueba_muchas_cuentas_desde_una_ip_se_bloquean(cliente: TestClient) -> None:
    for numero in range(limite_acceso.MAXIMO_POR_IP):
        cliente.post(
            "/auth/iniciar-sesion",
            json={"correo": f"nadie{numero}@ejemplo.com", "contrasena": "x"},
        )
    assert _entrar(cliente, CONTRASENA) == 429


def prueba_cabeceras_de_seguridad() -> None:
    with TestClient(aplicacion) as cliente_http:
        respuesta = cliente_http.get("/salud")
    assert respuesta.headers["x-content-type-options"] == "nosniff"
    assert respuesta.headers["x-frame-options"] == "DENY"


def prueba_documentacion_oculta() -> None:
    with TestClient(aplicacion) as cliente_http:
        assert cliente_http.get("/docs").status_code == 404
        assert cliente_http.get("/openapi.json").status_code == 404


@pytest.mark.parametrize("origen", ["null", "file://", "app://flowcast"])
def prueba_cors_permite_apps_instaladas(origen: str) -> None:
    with TestClient(aplicacion) as cliente_http:
        respuesta = cliente_http.options(
            "/auth/iniciar-sesion",
            headers={"Origin": origen, "Access-Control-Request-Method": "POST"},
        )
    assert respuesta.status_code == 200
    assert respuesta.headers["access-control-allow-origin"] == origen


def prueba_cors_rechaza_sitios_ajenos() -> None:
    with TestClient(aplicacion) as cliente_http:
        respuesta = cliente_http.options(
            "/auth/iniciar-sesion",
            headers={"Origin": "https://sitio-malo.com", "Access-Control-Request-Method": "POST"},
        )
    assert "access-control-allow-origin" not in respuesta.headers


def prueba_ip_detras_del_proxy_no_se_puede_falsificar(monkeypatch: pytest.MonkeyPatch) -> None:
    from starlette.requests import Request

    from app.config import configuracion
    from app.routers.autenticacion import ip_de

    monkeypatch.setattr(configuracion, "confiar_proxy", True)
    peticion = Request(
        {
            "type": "http",
            "headers": [(b"x-forwarded-for", b"1.2.3.4, 201.141.10.20")],
            "client": ("127.0.0.1", 5000),
        }
    )
    assert ip_de(peticion) == "201.141.10.20"
