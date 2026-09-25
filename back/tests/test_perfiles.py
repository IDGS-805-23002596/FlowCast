from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient

from app.main import aplicacion
from tests.ayudantes import borrar_usuarios, registrar

CORREOS = ["perfil-yo@ejemplo.com", "perfil-otro@ejemplo.com"]


@pytest.fixture(autouse=True)
def usuarios_limpios() -> Iterator[None]:
    borrar_usuarios(CORREOS)
    yield
    borrar_usuarios(CORREOS)


@pytest.fixture
def cliente() -> Iterator[TestClient]:
    with TestClient(aplicacion) as cliente_http:
        yield cliente_http


def prueba_registrarse_crea_un_perfil_inicial(cliente: TestClient) -> None:
    cabeceras = registrar(cliente, CORREOS[0], nombre="Álvaro")
    perfiles = cliente.get("/perfiles", headers=cabeceras).json()
    assert [p["nombre"] for p in perfiles] == ["Álvaro"]
    assert perfiles[0]["avatar"] == "01" and perfiles[0]["tiene_pin"] is False


def prueba_crear_ver_y_duplicado(cliente: TestClient) -> None:
    cabeceras = registrar(cliente, CORREOS[0])
    creado = cliente.post(
        "/perfiles",
        json={"nombre": "Niños", "avatar": "07", "es_infantil": True, "pin": "1234"},
        headers=cabeceras,
    )
    assert creado.status_code == 201, creado.text
    assert creado.json()["es_infantil"] is True and creado.json()["tiene_pin"] is True
    assert "hash" not in creado.text
    visto = cliente.get(f"/perfiles/{creado.json()['id']}", headers=cabeceras)
    assert visto.status_code == 200 and visto.json()["nombre"] == "Niños"
    repetido = cliente.post("/perfiles", json={"nombre": "Niños"}, headers=cabeceras)
    assert repetido.status_code == 409
    assert (
        cliente.post("/perfiles", json={"nombre": "X", "pin": "12"}, headers=cabeceras).status_code
        == 422
    )


def prueba_actualizar_y_pin(cliente: TestClient) -> None:
    cabeceras = registrar(cliente, CORREOS[0])
    perfil_id = cliente.get("/perfiles", headers=cabeceras).json()[0]["id"]
    ruta = f"/perfiles/{perfil_id}"
    assert cliente.post(
        f"{ruta}/verificar-pin", json={"pin": "0000"}, headers=cabeceras
    ).json() == {"valido": True}

    cambiado = cliente.patch(
        ruta, json={"nombre": "Casa", "avatar": "12", "pin": "4321"}, headers=cabeceras
    )
    assert cambiado.status_code == 200, cambiado.text
    assert (
        cambiado.json()["nombre"] == "Casa"
        and cambiado.json()["avatar"] == "12"
        and cambiado.json()["tiene_pin"]
    )
    assert cliente.post(
        f"{ruta}/verificar-pin", json={"pin": "4321"}, headers=cabeceras
    ).json() == {"valido": True}
    assert cliente.post(
        f"{ruta}/verificar-pin", json={"pin": "9999"}, headers=cabeceras
    ).json() == {"valido": False}

    sin_pin = cliente.patch(ruta, json={"quitar_pin": True}, headers=cabeceras)
    assert sin_pin.json()["tiene_pin"] is False


def prueba_no_se_puede_borrar_el_unico_perfil(cliente: TestClient) -> None:
    cabeceras = registrar(cliente, CORREOS[0])
    unico = cliente.get("/perfiles", headers=cabeceras).json()[0]["id"]
    assert cliente.delete(f"/perfiles/{unico}", headers=cabeceras).status_code == 409
    segundo = cliente.post("/perfiles", json={"nombre": "Segundo"}, headers=cabeceras).json()["id"]
    assert cliente.delete(f"/perfiles/{segundo}", headers=cabeceras).status_code == 204
    assert len(cliente.get("/perfiles", headers=cabeceras).json()) == 1


def prueba_perfil_ajeno_y_sin_token(cliente: TestClient) -> None:
    yo, otro = registrar(cliente, CORREOS[0]), registrar(cliente, CORREOS[1])
    mio = cliente.get("/perfiles", headers=yo).json()[0]["id"]
    assert cliente.get(f"/perfiles/{mio}", headers=otro).status_code == 404
    assert (
        cliente.patch(f"/perfiles/{mio}", json={"nombre": "Hackeado"}, headers=otro).status_code
        == 404
    )
    assert cliente.get("/perfiles").status_code == 401


PNG_MINIMO = (
    b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89"
    b"\x00\x00\x00\rIDATx\x9cc\xf8\x0f\x00\x01\x01\x01\x00\x18\xdd\x8d\xb0\x00\x00\x00\x00IEND\xaeB`\x82"
)


def prueba_imagen_de_perfil(cliente: TestClient) -> None:
    cabeceras = registrar(cliente, CORREOS[0])
    perfil = cliente.get("/perfiles", headers=cabeceras).json()[0]
    assert perfil["url_imagen"] is None
    ruta = f"/perfiles/{perfil['id']}/imagen"
    assert cliente.get(ruta, headers=cabeceras).status_code == 404

    subida = cliente.put(
        ruta, files={"archivo": ("foto.png", PNG_MINIMO, "image/png")}, headers=cabeceras
    )
    assert subida.status_code == 200, subida.text
    assert subida.json()["url_imagen"].startswith(f"{ruta}?v=")
    descarga = cliente.get(ruta, headers=cabeceras)
    assert descarga.status_code == 200
    assert descarga.headers["content-type"] == "image/png"
    assert descarga.content == PNG_MINIMO
    assert cliente.get("/perfiles", headers=cabeceras).json()[0]["url_imagen"] is not None

    no_imagen = cliente.put(
        ruta, files={"archivo": ("x.txt", b"hola", "text/plain")}, headers=cabeceras
    )
    assert no_imagen.status_code == 415
    assert cliente.get(ruta).status_code == 401

    quitada = cliente.delete(ruta, headers=cabeceras)
    assert quitada.status_code == 200 and quitada.json()["url_imagen"] is None
    assert cliente.get(ruta, headers=cabeceras).status_code == 404


def prueba_preferencias_del_perfil(cliente: TestClient) -> None:
    cabeceras = registrar(cliente, CORREOS[0])
    perfil = cliente.get("/perfiles", headers=cabeceras).json()[0]
    assert perfil["preferencias"] == {"letra_grande": False, "pantalla_inicial": "inicio"}
    cambio = {"preferencias": {"letra_grande": True, "pantalla_inicial": "en-vivo"}}
    actualizado = cliente.patch(f"/perfiles/{perfil['id']}", json=cambio, headers=cabeceras)
    assert actualizado.json()["preferencias"] == cambio["preferencias"]
    malo = {"preferencias": {"pantalla_inicial": "cocina"}}
    assert (
        cliente.patch(f"/perfiles/{perfil['id']}", json=malo, headers=cabeceras).status_code == 422
    )
