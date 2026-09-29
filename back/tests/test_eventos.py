import asyncio
import uuid
from collections.abc import Iterator

import httpx2 as httpx
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select

from app import security
from app.db import FabricaSesiones, motor
from app.main import aplicacion
from app.models import ConexionXtream, CuentaProveedor, EventoReproduccion, Usuario
from app.routers.conexiones import obtener_cliente_http
from app.services import xtream
from tests.ayudantes import borrar_usuarios, crear_conexion, registrar

CORREO = "eventos@ejemplo.com"
CORREO_ADMIN = "admin-eventos@ejemplo.com"
ADMIN = "admin-eventos"
CONTRASENA = "secreta123"
consultas: list[str] = []


def _responder(peticion: httpx.Request) -> httpx.Response:
    usuario = peticion.url.params.get("username")
    consultas.append(f"{peticion.url.host}:{usuario}")
    if peticion.url.params.get("password") != "buena":
        return httpx.Response(200, json={"user_info": {"auth": 0}})
    info = {"auth": 1, "status": "Active"}
    return httpx.Response(200, json={"user_info": info, "server_info": {}})


@pytest.fixture(autouse=True)
def proveedor() -> Iterator[None]:
    async def cliente_falso():
        transporte = httpx.MockTransport(_responder)
        async with httpx.AsyncClient(transport=transporte, headers=xtream.CABECERAS) as cliente:
            yield cliente

    consultas.clear()
    aplicacion.dependency_overrides[obtener_cliente_http] = cliente_falso
    yield
    aplicacion.dependency_overrides.pop(obtener_cliente_http, None)


@pytest.fixture(autouse=True)
def usuarios() -> Iterator[None]:
    borrar_usuarios([CORREO, CORREO_ADMIN])

    async def _crear() -> None:
        async with FabricaSesiones() as sesion_bd:
            sesion_bd.add(
                Usuario(
                    correo=CORREO_ADMIN,
                    hash_contrasena=security.generar_hash_contrasena(CONTRASENA),
                    nombre="Admin",
                    nombre_usuario=ADMIN,
                    es_administrador=True,
                )
            )
            await sesion_bd.commit()
        await motor.dispose()

    asyncio.run(_crear())
    yield
    borrar_usuarios([CORREO, CORREO_ADMIN])


@pytest.fixture
def cliente() -> Iterator[TestClient]:
    with TestClient(aplicacion) as cliente_http:
        yield cliente_http


def _ejecutar(funcion):
    async def _correr():
        async with FabricaSesiones() as sesion_bd:
            resultado = await funcion(sesion_bd)
            await sesion_bd.commit()
        await motor.dispose()
        return resultado

    return asyncio.run(_correr())


def _cuenta(etiqueta: str, usuario: str, url: str = "falso.local:8080") -> uuid.UUID:
    async def _crear(sesion_bd):
        cuenta = CuentaProveedor(etiqueta=etiqueta)
        cuenta.poner_credenciales(xtream.normalizar_url_base(url), usuario, "buena")
        sesion_bd.add(cuenta)
        await sesion_bd.flush()
        return cuenta.id

    return _ejecutar(_crear)


def _ligar(conexion_id: str, cuenta_id: uuid.UUID) -> None:
    async def _ligar_cuenta(sesion_bd):
        conexion = await sesion_bd.get(ConexionXtream, uuid.UUID(conexion_id))
        cuenta = await sesion_bd.get(CuentaProveedor, cuenta_id)
        conexion.cuenta_proveedor_id = cuenta.id

    _ejecutar(_ligar_cuenta)


def _cuenta_de(conexion_id: str) -> uuid.UUID | None:
    async def _leer(sesion_bd):
        return (await sesion_bd.get(ConexionXtream, uuid.UUID(conexion_id))).cuenta_proveedor_id

    return _ejecutar(_leer)


def _falla(**extra) -> dict:
    return {
        "resultado": "falla",
        "tipo_contenido": "pelicula",
        "contenido_id": "77",
        "titulo": "Primetime",
        "dispositivo": "tizen",
        "modelo": "UN55LS03",
        "version_app": "1.8.2",
        "codigo_error": "red",
        "mensaje": "La señal se detuvo",
        **extra,
    }


def _preparar(cliente: TestClient) -> tuple[dict, str]:
    cabeceras = registrar(cliente, CORREO)
    conexion_id = crear_conexion(cliente, cabeceras)
    _ligar(conexion_id, _cuenta("Principal", "sana"))
    consultas.clear()
    return cabeceras, conexion_id


def _admin(cliente: TestClient) -> dict:
    tokens = cliente.post(
        "/auth/iniciar-sesion", json={"usuario": ADMIN, "contrasena": CONTRASENA}
    ).json()
    return {"Authorization": f"Bearer {tokens['token_acceso']}"}


def prueba_se_registra_una_falla(cliente: TestClient) -> None:
    cabeceras, conexion_id = _preparar(cliente)
    antes = _cuenta_de(conexion_id)

    respuesta = cliente.post(
        f"/conexiones/{conexion_id}/eventos-reproduccion", json=_falla(), headers=cabeceras
    )

    assert respuesta.status_code == 204, respuesta.text
    assert _cuenta_de(conexion_id) == antes
    assert consultas == []


def prueba_rechaza_resultados_desconocidos(cliente: TestClient) -> None:
    cabeceras, conexion_id = _preparar(cliente)
    respuesta = cliente.post(
        f"/conexiones/{conexion_id}/eventos-reproduccion",
        json=_falla(resultado="respaldo"),
        headers=cabeceras,
    )
    assert respuesta.status_code == 422


def prueba_resumen_y_fallas_en_el_panel(cliente: TestClient) -> None:
    cabeceras, conexion_id = _preparar(cliente)
    ruta = f"/conexiones/{conexion_id}/eventos-reproduccion"
    for dispositivo in ("tizen", "webos", "webos"):
        inicio = _falla(
            resultado="inicio",
            dispositivo=dispositivo,
            segundos_inicio=2.0,
            codigo_error=None,
            mensaje=None,
        )
        assert cliente.post(ruta, json=inicio, headers=cabeceras).status_code == 204
    cliente.post(ruta, json=_falla(), headers=cabeceras)

    resumen = cliente.get("/admin/api/reproduccion/resumen?dias=1", headers=_admin(cliente))
    assert resumen.status_code == 200, resumen.text
    datos = resumen.json()
    assert (datos["inicios"], datos["fallas"]) == (3, 1)
    assert datos["segundos_inicio_promedio"] == 2.0
    assert sum(datos["por_hora"]) == 3
    assert {d["dispositivo"]: d["inicios"] for d in datos["por_dispositivo"]} == {
        "webos": 2,
        "tizen": 1,
    }
    assert datos["mas_vistos"][0] == {
        "tipo_contenido": "pelicula",
        "titulo": "Primetime",
        "veces": 3,
        "ultimo_error": None,
    }
    assert datos["mas_fallas"][0]["ultimo_error"] == "La señal se detuvo"

    fallas = cliente.get("/admin/api/reproduccion/fallas?dias=1", headers=_admin(cliente)).json()
    assert [f["resultado"] for f in fallas] == ["falla"]
    assert fallas[0]["mensaje"] == "La señal se detuvo"
    assert fallas[0]["cuenta"] == "Principal"
    assert fallas[0]["modelo"] == "UN55LS03"


def prueba_el_panel_es_solo_para_administradores(cliente: TestClient) -> None:
    cabeceras = registrar(cliente, CORREO)
    assert cliente.get("/admin/api/reproduccion/resumen", headers=cabeceras).status_code == 403
    assert cliente.get("/admin/api/reproduccion/fallas", headers=cabeceras).status_code == 403


def prueba_no_se_reporta_en_conexion_ajena(cliente: TestClient) -> None:
    cabeceras = registrar(cliente, CORREO)
    respuesta = cliente.post(
        f"/conexiones/{uuid.uuid4()}/eventos-reproduccion", json=_falla(), headers=cabeceras
    )
    assert respuesta.status_code == 404


def prueba_los_eventos_se_borran_con_el_usuario(cliente: TestClient) -> None:
    cabeceras, conexion_id = _preparar(cliente)
    cliente.post(
        f"/conexiones/{conexion_id}/eventos-reproduccion",
        json=_falla(),
        headers=cabeceras,
    )
    borrar_usuarios([CORREO])

    async def _contar(sesion_bd):
        consulta = select(EventoReproduccion).where(EventoReproduccion.titulo == "Primetime")
        return len((await sesion_bd.execute(consulta)).scalars().all())

    assert _ejecutar(_contar) == 0
