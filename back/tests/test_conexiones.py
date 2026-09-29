import asyncio
from collections.abc import Iterator

import httpx2 as httpx
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import delete, select

from app import cifrado
from app.db import FabricaSesiones, motor
from app.main import aplicacion
from app.models import ConexionXtream, Usuario
from app.routers.conexiones import obtener_cliente_http
from app.services import xtream

CORREOS = ["yo@ejemplo.com", "otro@ejemplo.com"]
DATOS_CONEXION = {"url_base": "falso.local:8080/", "nombre_usuario": "demo", "contrasena": "buena"}
RESPUESTA_PROVEEDOR_OK = {
    "user_info": {
        "auth": 1,
        "status": "Active",
        "exp_date": "1893456000",
        "max_connections": "2",
        "active_cons": "0",
        "is_trial": "0",
        "allowed_output_formats": ["m3u8", "ts"],
    },
    "server_info": {"timezone": "America/Mexico_City"},
}


async def _borrar_usuarios_prueba() -> None:
    async with FabricaSesiones() as sesion_bd:
        await sesion_bd.execute(delete(Usuario).where(Usuario.correo.in_(CORREOS)))
        await sesion_bd.commit()
    await motor.dispose()


@pytest.fixture(autouse=True)
def usuarios_limpios() -> Iterator[None]:
    asyncio.run(_borrar_usuarios_prueba())
    yield
    asyncio.run(_borrar_usuarios_prueba())


@pytest.fixture
def proveedor(cliente_http_falso: dict) -> dict:
    return cliente_http_falso


@pytest.fixture
def cliente_http_falso() -> Iterator[dict]:
    estado = {"modo": "ok"}

    def responder(peticion: httpx.Request) -> httpx.Response:
        if estado["modo"] == "caido":
            raise httpx.ConnectError("conexión rechazada", request=peticion)
        if estado["modo"] == "rechaza" or peticion.url.params.get("password") != "buena":
            return httpx.Response(200, json={"user_info": {"auth": 0}})
        if estado["modo"] == "expirada":
            info = {**RESPUESTA_PROVEEDOR_OK["user_info"], "status": "Expired"}
            return httpx.Response(200, json={"user_info": info, "server_info": {}})
        return httpx.Response(200, json=RESPUESTA_PROVEEDOR_OK)

    async def cliente_falso():
        transporte = httpx.MockTransport(responder)
        async with httpx.AsyncClient(transport=transporte, headers=xtream.CABECERAS) as cliente:
            yield cliente

    aplicacion.dependency_overrides[obtener_cliente_http] = cliente_falso
    yield estado
    aplicacion.dependency_overrides.pop(obtener_cliente_http, None)


@pytest.fixture
def cliente() -> Iterator[TestClient]:
    with TestClient(aplicacion) as cliente_http:
        yield cliente_http


def _cabeceras(cliente: TestClient, correo: str) -> dict[str, str]:
    respuesta = cliente.post(
        "/auth/registrar", json={"correo": correo, "contrasena": "secreta123", "nombre": "Prueba"}
    )
    assert respuesta.status_code == 201, respuesta.text
    return {"Authorization": f"Bearer {respuesta.json()['token_acceso']}"}


def _crear(cliente: TestClient, cabeceras: dict[str, str], **cambios) -> httpx.Response:
    return cliente.post("/conexiones", json={**DATOS_CONEXION, **cambios}, headers=cabeceras)


def prueba_crear_valida_con_el_proveedor_y_guarda(cliente: TestClient, proveedor: dict) -> None:
    cabeceras = _cabeceras(cliente, CORREOS[0])
    respuesta = _crear(cliente, cabeceras)
    assert respuesta.status_code == 201, respuesta.text
    cuerpo = respuesta.json()
    assert cuerpo["url_base"] == "http://falso.local:8080"
    assert cuerpo["etiqueta"] == "falso.local"
    assert cuerpo["estado_cuenta"] == "activa"
    assert cuerpo["formatos_salida_permitidos"] == ["m3u8", "ts"]
    assert not any("contrasena" in campo for campo in cuerpo)


def prueba_crear_requiere_token(cliente: TestClient, proveedor: dict) -> None:
    respuesta = cliente.post("/conexiones", json=DATOS_CONEXION)
    assert respuesta.status_code == 401


def prueba_crear_repetida_da_409(cliente: TestClient, proveedor: dict) -> None:
    cabeceras = _cabeceras(cliente, CORREOS[0])
    assert _crear(cliente, cabeceras).status_code == 201
    assert _crear(cliente, cabeceras).status_code == 409


def prueba_credenciales_rechazadas_dan_401_y_no_se_guarda(
    cliente: TestClient, proveedor: dict
) -> None:
    cabeceras = _cabeceras(cliente, CORREOS[0])
    assert _crear(cliente, cabeceras, contrasena="mala").status_code == 401
    assert cliente.get("/conexiones", headers=cabeceras).json() == []


def prueba_proveedor_caido_da_502(cliente: TestClient, proveedor: dict) -> None:
    cabeceras = _cabeceras(cliente, CORREOS[0])
    proveedor["modo"] = "caido"
    assert _crear(cliente, cabeceras).status_code == 502


def prueba_la_contrasena_del_proveedor_se_guarda_cifrada(proveedor: dict) -> None:
    with TestClient(aplicacion) as cliente:
        cabeceras = _cabeceras(cliente, CORREOS[0])
        assert _crear(cliente, cabeceras).status_code == 201

    async def leer_de_la_base() -> bytes:
        async with FabricaSesiones() as sesion_bd:
            huella = cifrado.calcular_huella("http://falso.local:8080", "demo")
            consulta = select(ConexionXtream).where(ConexionXtream.huella == huella)
            conexion = (await sesion_bd.execute(consulta)).scalar_one()
            datos = conexion.contrasena_cifrada
        await motor.dispose()
        return datos

    guardado = asyncio.run(leer_de_la_base())
    assert b"buena" not in guardado
    assert cifrado.descifrar(guardado) == "buena"


def prueba_cada_usuario_ve_solo_sus_conexiones(cliente: TestClient, proveedor: dict) -> None:
    yo, otro = _cabeceras(cliente, CORREOS[0]), _cabeceras(cliente, CORREOS[1])
    conexion_id = _crear(cliente, yo).json()["id"]

    assert len(cliente.get("/conexiones", headers=yo).json()) == 1
    assert cliente.get("/conexiones", headers=otro).json() == []
    assert cliente.get(f"/conexiones/{conexion_id}", headers=yo).status_code == 200
    assert cliente.get(f"/conexiones/{conexion_id}", headers=otro).status_code == 404
    assert cliente.delete(f"/conexiones/{conexion_id}", headers=otro).status_code == 404


def prueba_revalidar_actualiza_el_estado(cliente: TestClient, proveedor: dict) -> None:
    cabeceras = _cabeceras(cliente, CORREOS[0])
    conexion_id = _crear(cliente, cabeceras).json()["id"]

    proveedor["modo"] = "expirada"
    respuesta = cliente.post(f"/conexiones/{conexion_id}/revalidar", headers=cabeceras)
    assert respuesta.status_code == 200
    assert respuesta.json()["estado_cuenta"] == "expirada"

    proveedor["modo"] = "rechaza"
    respuesta = cliente.post(f"/conexiones/{conexion_id}/revalidar", headers=cabeceras)
    assert respuesta.status_code == 200
    assert respuesta.json()["estado_cuenta"] == "deshabilitada"

    proveedor["modo"] = "caido"
    assert (
        cliente.post(f"/conexiones/{conexion_id}/revalidar", headers=cabeceras).status_code == 502
    )


def prueba_borrar_conexion(cliente: TestClient, proveedor: dict) -> None:
    cabeceras = _cabeceras(cliente, CORREOS[0])
    conexion_id = _crear(cliente, cabeceras).json()["id"]
    assert cliente.delete(f"/conexiones/{conexion_id}", headers=cabeceras).status_code == 204
    assert cliente.get(f"/conexiones/{conexion_id}", headers=cabeceras).status_code == 404
    assert cliente.get("/conexiones", headers=cabeceras).json() == []
