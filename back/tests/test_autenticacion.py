import asyncio

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import delete

from app.config import configuracion
from app.db import FabricaSesiones, motor
from app.main import aplicacion
from app.models import Usuario

CORREO = "prueba-auth@ejemplo.com"
CORREO_2 = "prueba-auth-2@ejemplo.com"
CONTRASENA = "secreta123"
DATOS_REGISTRO = {
    "correo": CORREO,
    "contrasena": CONTRASENA,
    "nombre": "Usuario de prueba",
    "nombre_dispositivo": "pytest",
}


async def _borrar_usuario_prueba() -> None:
    async with FabricaSesiones() as sesion_bd:
        await sesion_bd.execute(delete(Usuario).where(Usuario.correo.in_([CORREO, CORREO_2])))
        await sesion_bd.commit()
    await motor.dispose()


@pytest.fixture(autouse=True)
def usuario_prueba_limpio() -> None:
    asyncio.run(_borrar_usuario_prueba())
    yield
    asyncio.run(_borrar_usuario_prueba())


@pytest.fixture
def cliente() -> TestClient:
    with TestClient(aplicacion) as cliente_http:
        yield cliente_http


def _registrar(cliente: TestClient) -> dict:
    respuesta = cliente.post("/auth/registrar", json=DATOS_REGISTRO)
    assert respuesta.status_code == 201, respuesta.text
    return respuesta.json()


def prueba_registro_devuelve_tokens_y_usuario(cliente: TestClient) -> None:
    cuerpo = _registrar(cliente)
    assert cuerpo["tipo_token"] == "bearer"
    assert cuerpo["expira_en_segundos"] == configuracion.minutos_token_acceso * 60
    assert len(cuerpo["token_refresco"]) == 64
    assert cuerpo["usuario"]["correo"] == CORREO
    assert "hash_contrasena" not in cuerpo["usuario"]


def prueba_registro_correo_repetido_da_409(cliente: TestClient) -> None:
    _registrar(cliente)
    respuesta = cliente.post("/auth/registrar", json=DATOS_REGISTRO)
    assert respuesta.status_code == 409


def prueba_registro_datos_invalidos_da_422(cliente: TestClient) -> None:
    respuesta = cliente.post(
        "/auth/registrar", json={"correo": "no-es-correo", "contrasena": "corta", "nombre": "A"}
    )
    assert respuesta.status_code == 422
    campos_con_error = {error["loc"][-1] for error in respuesta.json()["detail"]}
    assert campos_con_error == {"correo", "contrasena", "nombre"}


def prueba_login_correcto_ignora_mayusculas_del_correo(cliente: TestClient) -> None:
    _registrar(cliente)
    respuesta = cliente.post(
        "/auth/iniciar-sesion", json={"correo": CORREO.upper(), "contrasena": CONTRASENA}
    )
    assert respuesta.status_code == 200
    assert respuesta.json()["usuario"]["correo"] == CORREO


def prueba_login_incorrecto_da_401_con_el_mismo_mensaje(cliente: TestClient) -> None:
    _registrar(cliente)
    contrasena_mala = cliente.post(
        "/auth/iniciar-sesion", json={"correo": CORREO, "contrasena": "incorrecta"}
    )
    correo_inexistente = cliente.post(
        "/auth/iniciar-sesion", json={"correo": "nadie@ejemplo.com", "contrasena": CONTRASENA}
    )
    assert contrasena_mala.status_code == 401
    assert correo_inexistente.status_code == 401
    assert contrasena_mala.json()["detail"] == correo_inexistente.json()["detail"]


def prueba_yo_requiere_token_de_acceso(cliente: TestClient) -> None:
    tokens = _registrar(cliente)
    con_token = cliente.get(
        "/auth/yo", headers={"Authorization": f"Bearer {tokens['token_acceso']}"}
    )
    sin_token = cliente.get("/auth/yo")
    assert con_token.status_code == 200
    assert con_token.json()["correo"] == CORREO
    assert sin_token.status_code == 401
    assert sin_token.headers["www-authenticate"] == "Bearer"


def prueba_refrescar_rota_el_token_y_anula_el_anterior(cliente: TestClient) -> None:
    tokens = _registrar(cliente)
    viejo = tokens["token_refresco"]

    primero = cliente.post("/auth/refrescar", json={"token_refresco": viejo})
    assert primero.status_code == 200
    assert primero.json()["token_refresco"] != viejo

    reutilizado = cliente.post("/auth/refrescar", json={"token_refresco": viejo})
    assert reutilizado.status_code == 401


def prueba_refrescar_con_token_inventado_da_401(cliente: TestClient) -> None:
    respuesta = cliente.post("/auth/refrescar", json={"token_refresco": "f" * 64})
    assert respuesta.status_code == 401


def prueba_cerrar_sesion_invalida_el_refresco(cliente: TestClient) -> None:
    tokens = _registrar(cliente)
    token_refresco = tokens["token_refresco"]

    cerrar = cliente.post("/auth/cerrar-sesion", json={"token_refresco": token_refresco})
    assert cerrar.status_code == 204

    refrescar = cliente.post("/auth/refrescar", json={"token_refresco": token_refresco})
    assert refrescar.status_code == 401

    cerrar_otra_vez = cliente.post("/auth/cerrar-sesion", json={"token_refresco": token_refresco})
    assert cerrar_otra_vez.status_code == 204


def prueba_registro_cerrado_da_403(cliente: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(configuracion, "registro_abierto", False)
    respuesta = cliente.post("/auth/registrar", json=DATOS_REGISTRO)
    assert respuesta.status_code == 403
    assert "cerrado" in respuesta.json()["detail"]


def prueba_login_con_nombre_de_usuario(cliente: TestClient) -> None:
    respuesta = cliente.post(
        "/auth/registrar", json={**DATOS_REGISTRO, "nombre_usuario": "Prueba.Auth"}
    )
    assert respuesta.status_code == 201, respuesta.text
    assert respuesta.json()["usuario"]["nombre_usuario"] == "prueba.auth"

    por_usuario = cliente.post(
        "/auth/iniciar-sesion", json={"usuario": "PRUEBA.AUTH", "contrasena": CONTRASENA}
    )
    por_correo_en_usuario = cliente.post(
        "/auth/iniciar-sesion", json={"usuario": CORREO, "contrasena": CONTRASENA}
    )
    assert por_usuario.status_code == 200
    assert por_correo_en_usuario.status_code == 200
    assert por_usuario.json()["usuario"]["es_administrador"] is False


def prueba_nombre_de_usuario_repetido_da_409(cliente: TestClient) -> None:
    primero = cliente.post("/auth/registrar", json={**DATOS_REGISTRO, "nombre_usuario": "repetido"})
    segundo = cliente.post(
        "/auth/registrar",
        json={**DATOS_REGISTRO, "correo": CORREO_2, "nombre_usuario": "REPETIDO"},
    )
    assert primero.status_code == 201
    assert segundo.status_code == 409


def prueba_nombre_de_usuario_con_formato_invalido_da_422(cliente: TestClient) -> None:
    respuesta = cliente.post(
        "/auth/registrar", json={**DATOS_REGISTRO, "nombre_usuario": "con espacio"}
    )
    assert respuesta.status_code == 422


def prueba_login_sin_usuario_ni_correo_da_422(cliente: TestClient) -> None:
    respuesta = cliente.post("/auth/iniciar-sesion", json={"contrasena": CONTRASENA})
    assert respuesta.status_code == 422
