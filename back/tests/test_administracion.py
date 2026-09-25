import asyncio
from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient

from app import security
from app.config import configuracion
from app.db import FabricaSesiones, motor
from app.main import aplicacion
from app.models import Usuario
from app.schemas.administracion import correo_interno
from tests.ayudantes import borrar_usuarios, instalar_proveedor_minimo, registrar

CONTRASENA = "secreta123"
ADMIN = "admin-prueba"
CORREO_ADMIN = "admin-prueba@ejemplo.com"
CORREO_NORMAL = "normal-admin@ejemplo.com"
NUEVO = "tele-prueba"
CORREOS = [CORREO_ADMIN, CORREO_NORMAL, correo_interno(NUEVO), correo_interno("otra-tele")]
PROVEEDOR = {
    "predeterminado": False,
    "url_base": "falso.local:8080",
    "nombre_usuario": "demo",
    "contrasena": "buena",
}


def _crear_administrador() -> None:
    async def _crear() -> None:
        async with FabricaSesiones() as sesion_bd:
            sesion_bd.add(
                Usuario(
                    correo=CORREO_ADMIN,
                    hash_contrasena=security.generar_hash_contrasena(CONTRASENA),
                    nombre="Administrador",
                    nombre_usuario=ADMIN,
                    es_administrador=True,
                )
            )
            await sesion_bd.commit()
        await motor.dispose()

    asyncio.run(_crear())


@pytest.fixture(autouse=True)
def usuarios_limpios() -> Iterator[None]:
    borrar_usuarios(CORREOS)
    _crear_administrador()
    yield
    borrar_usuarios(CORREOS)


@pytest.fixture(autouse=True)
def proveedor() -> Iterator[None]:
    yield from instalar_proveedor_minimo()


@pytest.fixture
def cliente() -> Iterator[TestClient]:
    with TestClient(aplicacion) as cliente_http:
        yield cliente_http


def _entrar(cliente: TestClient, usuario: str, contrasena: str = CONTRASENA) -> dict:
    respuesta = cliente.post(
        "/auth/iniciar-sesion", json={"usuario": usuario, "contrasena": contrasena}
    )
    assert respuesta.status_code == 200, respuesta.text
    return respuesta.json()


def _cabeceras(tokens: dict) -> dict[str, str]:
    return {"Authorization": f"Bearer {tokens['token_acceso']}"}


@pytest.fixture
def admin(cliente: TestClient) -> dict[str, str]:
    return _cabeceras(_entrar(cliente, ADMIN))


def _crear(cliente: TestClient, admin: dict[str, str], **extra) -> dict:
    datos = {
        "nombre": "Tele de la sala",
        "nombre_usuario": NUEVO,
        "contrasena": CONTRASENA,
        "proveedor": PROVEEDOR,
        **extra,
    }
    return cliente.post("/admin/api/usuarios", json=datos, headers=admin)


def prueba_la_pagina_de_administracion_se_sirve(cliente: TestClient) -> None:
    respuesta = cliente.get("/admin")
    assert respuesta.status_code == 200
    assert "Administración" in respuesta.text


def prueba_sin_token_da_401_y_usuario_normal_da_403(cliente: TestClient) -> None:
    normal = registrar(cliente, CORREO_NORMAL)
    assert cliente.get("/admin/api/usuarios").status_code == 401
    assert cliente.get("/admin/api/usuarios", headers=normal).status_code == 403


def prueba_crear_usuario_con_proveedor_y_entrar_con_el(
    cliente: TestClient, admin: dict[str, str]
) -> None:
    respuesta = _crear(cliente, admin, nombre_usuario="Tele-Prueba")
    assert respuesta.status_code == 201, respuesta.text
    creado = respuesta.json()
    assert creado["nombre_usuario"] == NUEVO
    assert creado["correo"] == correo_interno(NUEVO)
    assert creado["proveedores"][0]["servidor"] == "falso.local"
    assert creado["proveedores"][0]["estado_cuenta"] == "activa"

    tokens = _entrar(cliente, NUEVO)
    conexiones = cliente.get("/conexiones", headers=_cabeceras(tokens)).json()
    assert [conexion["nombre_usuario"] for conexion in conexiones] == ["demo"]

    lista = cliente.get("/admin/api/usuarios", headers=admin).json()
    assert {usuario["nombre_usuario"] for usuario in lista} >= {ADMIN, NUEVO}


def prueba_usuario_repetido_da_409(cliente: TestClient, admin: dict[str, str]) -> None:
    assert _crear(cliente, admin).status_code == 201
    assert _crear(cliente, admin, nombre_usuario=NUEVO.upper()).status_code == 409


def prueba_proveedor_que_rechaza_da_400_y_no_crea_nada(
    cliente: TestClient, admin: dict[str, str]
) -> None:
    respuesta = _crear(cliente, admin, proveedor={**PROVEEDOR, "contrasena": "mala"})
    assert respuesta.status_code == 400
    lista = cliente.get("/admin/api/usuarios", headers=admin).json()
    assert NUEVO not in {usuario["nombre_usuario"] for usuario in lista}


def prueba_proveedor_predeterminado_sin_configurar_da_400(
    cliente: TestClient, admin: dict[str, str]
) -> None:
    respuesta = _crear(cliente, admin, proveedor={"predeterminado": True})
    assert respuesta.status_code == 400


def prueba_proveedor_predeterminado_configurado(
    cliente: TestClient, admin: dict[str, str], monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr(configuracion, "conexion_inicial_url", "falso.local:8080")
    monkeypatch.setattr(configuracion, "conexion_inicial_usuario", "casa")
    monkeypatch.setattr(configuracion, "conexion_inicial_contrasena", "buena")
    monkeypatch.setattr(configuracion, "conexion_inicial_etiqueta", "FlowCast")
    respuesta = _crear(cliente, admin, proveedor={"predeterminado": True})
    assert respuesta.status_code == 201, respuesta.text
    proveedor = respuesta.json()["proveedores"][0]
    assert proveedor["etiqueta"] == "FlowCast" and proveedor["nombre_usuario"] == "casa"


def prueba_desactivar_cierra_sesiones_e_impide_entrar(
    cliente: TestClient, admin: dict[str, str]
) -> None:
    usuario_id = _crear(cliente, admin).json()["id"]
    tokens = _entrar(cliente, NUEVO)
    respuesta = cliente.patch(
        f"/admin/api/usuarios/{usuario_id}", json={"activo": False}, headers=admin
    )
    assert respuesta.status_code == 200 and respuesta.json()["activo"] is False
    refresco = cliente.post("/auth/refrescar", json={"token_refresco": tokens["token_refresco"]})
    login = cliente.post("/auth/iniciar-sesion", json={"usuario": NUEVO, "contrasena": CONTRASENA})
    assert refresco.status_code == 401
    assert login.status_code == 403


def prueba_cambiar_contrasena(cliente: TestClient, admin: dict[str, str]) -> None:
    usuario_id = _crear(cliente, admin).json()["id"]
    cliente.patch(
        f"/admin/api/usuarios/{usuario_id}", json={"contrasena": "nueva-clave-1"}, headers=admin
    )
    vieja = cliente.post("/auth/iniciar-sesion", json={"usuario": NUEVO, "contrasena": CONTRASENA})
    nueva = cliente.post(
        "/auth/iniciar-sesion", json={"usuario": NUEVO, "contrasena": "nueva-clave-1"}
    )
    assert vieja.status_code == 401
    assert nueva.status_code == 200


def prueba_no_puedes_desactivarte_ni_quitarte_admin(
    cliente: TestClient, admin: dict[str, str]
) -> None:
    yo = _entrar(cliente, ADMIN)["usuario"]["id"]
    for cambio in ({"activo": False}, {"es_administrador": False}):
        respuesta = cliente.patch(f"/admin/api/usuarios/{yo}", json=cambio, headers=admin)
        assert respuesta.status_code == 400


def prueba_cambiar_proveedor_reemplaza_el_anterior(
    cliente: TestClient, admin: dict[str, str]
) -> None:
    usuario_id = _crear(cliente, admin).json()["id"]
    respuesta = cliente.put(
        f"/admin/api/usuarios/{usuario_id}/proveedor",
        json={**PROVEEDOR, "nombre_usuario": "otro-demo"},
        headers=admin,
    )
    assert respuesta.status_code == 200, respuesta.text
    assert [p["nombre_usuario"] for p in respuesta.json()["proveedores"]] == ["otro-demo"]


def prueba_cerrar_sesiones(cliente: TestClient, admin: dict[str, str]) -> None:
    usuario_id = _crear(cliente, admin).json()["id"]
    primera = _entrar(cliente, NUEVO)
    _entrar(cliente, NUEVO)
    respuesta = cliente.post(f"/admin/api/usuarios/{usuario_id}/cerrar-sesiones", headers=admin)
    assert respuesta.json() == {"cerradas": 2}
    refresco = cliente.post("/auth/refrescar", json={"token_refresco": primera["token_refresco"]})
    assert refresco.status_code == 401


def prueba_sesiones_con_datos_del_dispositivo(cliente: TestClient, admin: dict[str, str]) -> None:
    usuario_id = _crear(cliente, admin).json()["id"]
    datos = {
        "usuario": NUEVO,
        "contrasena": CONTRASENA,
        "nombre_dispositivo": "Tele Samsung",
        "dispositivo": {"modelo": "QN55LS01TAFXZX", "sistema": "Tizen", "version_sistema": "5.5"},
    }
    tokens = cliente.post(
        "/auth/iniciar-sesion", json=datos, headers={"User-Agent": "Tizen 5.5 FlowCast"}
    ).json()
    cliente.post(
        "/auth/refrescar",
        json={"token_refresco": tokens["token_refresco"], "dispositivo": {"version_app": "1.0.0"}},
        headers={"User-Agent": "Tizen 5.5 FlowCast"},
    )
    sesiones = cliente.get(f"/admin/api/usuarios/{usuario_id}/sesiones", headers=admin).json()
    assert len(sesiones) == 1
    sesion = sesiones[0]
    assert sesion["nombre_dispositivo"] == "Tele Samsung"
    assert sesion["dispositivo"] == {"version_app": "1.0.0"}
    assert sesion["agente_usuario"] == "Tizen 5.5 FlowCast"
    assert sesion["ip_inicio"] == sesion["ip_ultima"] == "testclient"
    cerrada = cliente.post(f"/admin/api/sesiones/{sesion['id']}/cerrar", headers=admin)
    assert cerrada.json() == {"cerradas": 1}
    assert cliente.get(f"/admin/api/usuarios/{usuario_id}/sesiones", headers=admin).json() == []
    otra_vez = cliente.post(f"/admin/api/sesiones/{sesion['id']}/cerrar", headers=admin)
    assert otra_vez.status_code == 404


def prueba_datos_del_dispositivo_se_recortan(cliente: TestClient, admin: dict[str, str]) -> None:
    usuario_id = _crear(cliente, admin).json()["id"]
    muchos = {f"dato{i}": "x" * 500 for i in range(40)}
    cliente.post(
        "/auth/iniciar-sesion",
        json={"usuario": NUEVO, "contrasena": CONTRASENA, "dispositivo": muchos},
    )
    sesion = cliente.get(f"/admin/api/usuarios/{usuario_id}/sesiones", headers=admin).json()[0]
    assert len(sesion["dispositivo"]) == 25
    assert all(len(valor) == 200 for valor in sesion["dispositivo"].values())


def _cuenta(**extra) -> dict:
    return {
        "etiqueta": "Proveedor de prueba",
        "url_base": "falso.local:8080",
        "nombre_usuario": "demo",
        "contrasena": "buena",
        **extra,
    }


def prueba_cuentas_de_proveedor_crear_editar_y_borrar(
    cliente: TestClient, admin: dict[str, str]
) -> None:
    creada = cliente.post("/admin/api/proveedores", json=_cuenta(notas="Plan anual"), headers=admin)
    assert creada.status_code == 201, creada.text
    cuenta = creada.json()
    assert cuenta["servidor"] == "falso.local" and cuenta["estado_cuenta"] == "activa"
    assert cuenta["usuarios"] == [] and "contrasena" not in cuenta
    repetida = cliente.post("/admin/api/proveedores", json=_cuenta(), headers=admin)
    assert repetida.status_code == 409
    mala = cliente.post(
        "/admin/api/proveedores",
        json=_cuenta(nombre_usuario="otro", contrasena="mala"),
        headers=admin,
    )
    assert mala.status_code == 400

    usuario = _crear(cliente, admin, proveedor={"cuenta_id": cuenta["id"]}).json()
    assert usuario["proveedores"][0]["cuenta_id"] == cuenta["id"]
    lista = cliente.get("/admin/api/proveedores", headers=admin).json()
    assert next(c for c in lista if c["id"] == cuenta["id"])["usuarios"] == [NUEVO]

    cambio = cliente.patch(
        f"/admin/api/proveedores/{cuenta['id']}",
        json={"etiqueta": "Nuevo nombre", "url_base": "http://falso.local:9090"},
        headers=admin,
    )
    assert cambio.status_code == 200, cambio.text
    assert cambio.json()["url_base"] == "http://falso.local:9090"
    tokens = _entrar(cliente, NUEVO)
    conexion = cliente.get("/conexiones", headers=_cabeceras(tokens)).json()[0]
    assert conexion["etiqueta"] == "Nuevo nombre"

    ocupada = cliente.delete(f"/admin/api/proveedores/{cuenta['id']}", headers=admin)
    assert ocupada.status_code == 409 and NUEVO in ocupada.json()["detail"]
    revisada = cliente.post(f"/admin/api/proveedores/{cuenta['id']}/revisar", headers=admin)
    assert revisada.status_code == 200

    otra = cliente.post(
        "/admin/api/proveedores", json=_cuenta(nombre_usuario="libre"), headers=admin
    ).json()
    assert cliente.delete(f"/admin/api/proveedores/{otra['id']}", headers=admin).status_code == 204


def prueba_solo_una_cuenta_predeterminada(cliente: TestClient, admin: dict[str, str]) -> None:
    primera = cliente.post(
        "/admin/api/proveedores", json=_cuenta(predeterminada=True), headers=admin
    ).json()
    segunda = cliente.post(
        "/admin/api/proveedores",
        json=_cuenta(nombre_usuario="segunda", predeterminada=True),
        headers=admin,
    ).json()
    lista = {c["id"]: c for c in cliente.get("/admin/api/proveedores", headers=admin).json()}
    assert lista[segunda["id"]]["predeterminada"] and not lista[primera["id"]]["predeterminada"]
    usuario = _crear(cliente, admin, proveedor={"predeterminado": True}).json()
    assert usuario["proveedores"][0]["cuenta_id"] == segunda["id"]


def _ligar_otra_conexion(usuario_id: str, cuenta_id: str) -> None:
    async def _ligar() -> None:
        import uuid

        from app.models import ConexionXtream, CuentaProveedor

        async with FabricaSesiones() as sesion_bd:
            cuenta = await sesion_bd.get(CuentaProveedor, uuid.UUID(cuenta_id))
            conexion = ConexionXtream.nueva(
                usuario_id=uuid.UUID(usuario_id),
                etiqueta=cuenta.etiqueta,
                url_base=cuenta.url_base,
                nombre_usuario=cuenta.nombre_usuario,
                contrasena=cuenta.contrasena,
            )
            conexion.cuenta_proveedor_id = cuenta.id
            sesion_bd.add(conexion)
            await sesion_bd.commit()
        await motor.dispose()

    asyncio.run(_ligar())


def prueba_eliminar_cuenta_ocupada_pasa_usuarios_a_la_predeterminada(
    cliente: TestClient, admin: dict[str, str]
) -> None:
    principal = cliente.post(
        "/admin/api/proveedores", json=_cuenta(predeterminada=True), headers=admin
    ).json()
    vieja = cliente.post(
        "/admin/api/proveedores", json=_cuenta(nombre_usuario="vieja"), headers=admin
    ).json()
    sobrante = cliente.post(
        "/admin/api/proveedores", json=_cuenta(nombre_usuario="sobrante"), headers=admin
    ).json()
    usuario = _crear(cliente, admin, proveedor={"cuenta_id": vieja["id"]}).json()
    conexion_vieja = usuario["proveedores"][0]["id"]
    _ligar_otra_conexion(usuario["id"], sobrante["id"])

    respuesta = cliente.delete(
        f"/admin/api/proveedores/{vieja['id']}?mover_usuarios=true", headers=admin
    )
    assert respuesta.status_code == 204, respuesta.text
    proveedores = next(
        u
        for u in cliente.get("/admin/api/usuarios", headers=admin).json()
        if u["id"] == usuario["id"]
    )["proveedores"]
    assert (conexion_vieja, principal["id"]) in [(p["id"], p["cuenta_id"]) for p in proveedores]
    assert sorted(p["cuenta_id"] for p in proveedores) == sorted([principal["id"], sobrante["id"]])

    assert (
        cliente.delete(
            f"/admin/api/proveedores/{sobrante['id']}?mover_usuarios=true", headers=admin
        ).status_code
        == 204
    )
    proveedores = next(
        u
        for u in cliente.get("/admin/api/usuarios", headers=admin).json()
        if u["id"] == usuario["id"]
    )["proveedores"]
    assert [p["cuenta_id"] for p in proveedores] == [principal["id"]]
    ids = {c["id"] for c in cliente.get("/admin/api/proveedores", headers=admin).json()}
    assert vieja["id"] not in ids and sobrante["id"] not in ids


def prueba_la_predeterminada_ocupada_no_se_mueve(
    cliente: TestClient, admin: dict[str, str]
) -> None:
    principal = cliente.post(
        "/admin/api/proveedores", json=_cuenta(predeterminada=True), headers=admin
    ).json()
    _crear(cliente, admin, proveedor={"cuenta_id": principal["id"]})
    respuesta = cliente.delete(
        f"/admin/api/proveedores/{principal['id']}?mover_usuarios=true", headers=admin
    )
    assert respuesta.status_code == 409
    assert "predeterminada" in respuesta.json()["detail"]


def prueba_eliminar_usuario(cliente: TestClient, admin: dict[str, str]) -> None:
    usuario = _crear(cliente, admin).json()
    tokens = _entrar(cliente, NUEVO)
    assert cliente.delete(f"/admin/api/usuarios/{usuario['id']}", headers=admin).status_code == 204
    ids = {u["id"] for u in cliente.get("/admin/api/usuarios", headers=admin).json()}
    assert usuario["id"] not in ids
    assert cliente.get("/perfiles", headers=_cabeceras(tokens)).status_code == 401
    assert cliente.delete(f"/admin/api/usuarios/{usuario['id']}", headers=admin).status_code == 404


def prueba_no_puedes_eliminarte_a_ti_mismo(cliente: TestClient, admin: dict[str, str]) -> None:
    yo = cliente.get("/auth/yo", headers=admin).json()
    respuesta = cliente.delete(f"/admin/api/usuarios/{yo['id']}", headers=admin)
    assert respuesta.status_code == 400
