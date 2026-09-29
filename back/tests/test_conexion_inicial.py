import asyncio
from collections.abc import Iterator

import httpx2 as httpx
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select

from app import cifrado
from app.config import configuracion
from app.db import FabricaSesiones, motor
from app.main import aplicacion
from app.models import ConexionXtream
from app.routers.conexiones import obtener_cliente_http
from app.services import xtream
from tests.ayudantes import borrar_usuarios, registrar

CORREOS = ["inicial-uno@ejemplo.com", "inicial-dos@ejemplo.com"]


@pytest.fixture(autouse=True)
def usuarios_limpios() -> Iterator[None]:
    borrar_usuarios(CORREOS)
    yield
    borrar_usuarios(CORREOS)


@pytest.fixture
def conexion_inicial_activada(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(configuracion, "conexion_inicial_etiqueta", "FlowCast prueba")
    monkeypatch.setattr(configuracion, "conexion_inicial_url", "falso.local:8080/")
    monkeypatch.setattr(configuracion, "conexion_inicial_usuario", "casa")
    monkeypatch.setattr(configuracion, "conexion_inicial_contrasena", "buena")


@pytest.fixture
def proveedor() -> Iterator[dict]:
    estado = {"modo": "ok"}

    def responder(peticion: httpx.Request) -> httpx.Response:
        if estado["modo"] == "caido":
            raise httpx.ConnectError("rechazada", request=peticion)
        if peticion.url.params.get("password") != "buena":
            return httpx.Response(200, json={"user_info": {"auth": 0}})
        return httpx.Response(
            200,
            json={
                "user_info": {"auth": 1, "status": "Active", "allowed_output_formats": ["ts"]},
                "server_info": {},
            },
        )

    async def cliente_falso():
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(responder), headers=xtream.CABECERAS
        ) as cliente:
            yield cliente

    aplicacion.dependency_overrides[obtener_cliente_http] = cliente_falso
    yield estado
    aplicacion.dependency_overrides.pop(obtener_cliente_http, None)


@pytest.fixture
def cliente() -> Iterator[TestClient]:
    with TestClient(aplicacion) as cliente_http:
        yield cliente_http


def prueba_registrarse_recibe_la_conexion_inicial(
    cliente: TestClient, proveedor: dict, conexion_inicial_activada: None
) -> None:
    cabeceras = registrar(cliente, CORREOS[0])
    conexiones = cliente.get("/conexiones", headers=cabeceras).json()
    assert len(conexiones) == 1
    assert conexiones[0]["etiqueta"] == "FlowCast prueba"
    assert conexiones[0]["url_base"] == "http://falso.local:8080"
    assert conexiones[0]["nombre_usuario"] == "casa"
    assert conexiones[0]["estado_cuenta"] == "activa" and conexiones[0][
        "formatos_salida_permitidos"
    ] == ["ts"]
    assert len(cliente.get("/conexiones", headers=cabeceras).json()) == 1


def prueba_sin_configurar_no_se_crea_nada(cliente: TestClient, proveedor: dict) -> None:
    cabeceras = registrar(cliente, CORREOS[0])
    assert cliente.get("/conexiones", headers=cabeceras).json() == []


def prueba_el_proveedor_caido_no_impide_registrarse(
    cliente: TestClient, proveedor: dict, conexion_inicial_activada: None
) -> None:
    proveedor["modo"] = "caido"
    cabeceras = registrar(cliente, CORREOS[0])
    conexiones = cliente.get("/conexiones", headers=cabeceras).json()
    assert len(conexiones) == 1 and conexiones[0]["estado_cuenta"] == "desconocido"
    proveedor["modo"] = "ok"
    revalidada = cliente.post(f"/conexiones/{conexiones[0]['id']}/revalidar", headers=cabeceras)
    assert revalidada.json()["estado_cuenta"] == "activa"


def prueba_cuenta_antigua_la_recibe_al_listar(
    cliente: TestClient, proveedor: dict, monkeypatch: pytest.MonkeyPatch
) -> None:
    cabeceras = registrar(cliente, CORREOS[0])
    assert cliente.get("/conexiones", headers=cabeceras).json() == []
    monkeypatch.setattr(configuracion, "conexion_inicial_url", "falso.local:8080")
    monkeypatch.setattr(configuracion, "conexion_inicial_usuario", "casa")
    monkeypatch.setattr(configuracion, "conexion_inicial_contrasena", "buena")
    assert [c["nombre_usuario"] for c in cliente.get("/conexiones", headers=cabeceras).json()] == [
        "casa"
    ]


def prueba_url_y_usuario_quedan_cifrados_en_la_base(
    proveedor: dict, conexion_inicial_activada: None
) -> None:
    with TestClient(aplicacion) as cliente:
        registrar(cliente, CORREOS[0])

    async def leer() -> ConexionXtream:
        async with FabricaSesiones() as sesion_bd:
            huella = cifrado.calcular_huella("http://falso.local:8080", "casa")
            fila = (
                await sesion_bd.execute(
                    select(ConexionXtream).where(ConexionXtream.huella == huella)
                )
            ).scalar_one()
        await motor.dispose()
        return fila

    fila = asyncio.run(leer())
    assert (
        b"falso.local" not in fila.url_base_cifrada and b"casa" not in fila.nombre_usuario_cifrado
    )
    assert fila.url_base == "http://falso.local:8080" and fila.nombre_usuario == "casa"
    assert cifrado.descifrar(fila.contrasena_cifrada) == "buena"
