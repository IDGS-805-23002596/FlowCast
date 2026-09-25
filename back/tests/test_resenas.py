from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient

from app.main import aplicacion
from tests.ayudantes import borrar_usuarios, crear_conexion, instalar_proveedor_minimo, registrar

CORREOS = ["resena-yo@ejemplo.com", "resena-otro@ejemplo.com"]


@pytest.fixture(autouse=True)
def usuarios_limpios() -> Iterator[None]:
    borrar_usuarios(CORREOS)
    yield
    borrar_usuarios(CORREOS)


@pytest.fixture(autouse=True)
def proveedor() -> Iterator[None]:
    yield from instalar_proveedor_minimo()


@pytest.fixture
def cliente() -> Iterator[TestClient]:
    with TestClient(aplicacion) as cliente_http:
        yield cliente_http


def _sesion(cliente: TestClient, correo: str, usuario_proveedor: str) -> dict:
    cabeceras = registrar(cliente, correo)
    perfil_id = cliente.get("/perfiles", headers=cabeceras).json()[0]["id"]
    conexion_id = crear_conexion(cliente, cabeceras, usuario=usuario_proveedor)
    return {
        "cabeceras": cabeceras,
        "base": f"/perfiles/{perfil_id}/resenas",
        "conexion_id": conexion_id,
    }


@pytest.fixture
def sesion(cliente: TestClient) -> dict:
    return _sesion(cliente, CORREOS[0], "demo")


def _resena(sesion: dict, **cambios) -> dict:
    return {
        "conexion_id": sesion["conexion_id"],
        "tipo_contenido": "pelicula",
        "stream_id": "501",
        "titulo": "Película Uno",
        "calificacion": 4,
        "texto": "Buen ritmo, final predecible.",
        **cambios,
    }


def _resumen(
    cliente: TestClient, sesion: dict, tipo: str = "pelicula", stream_id: str = "501"
) -> dict:
    respuesta = cliente.get(
        f"{sesion['base']}/{sesion['conexion_id']}/{tipo}/{stream_id}", headers=sesion["cabeceras"]
    )
    assert respuesta.status_code == 200, respuesta.text
    return respuesta.json()


def prueba_guardar_resena_crea_y_despues_actualiza(cliente: TestClient, sesion: dict) -> None:
    primera = cliente.put(sesion["base"], json=_resena(sesion), headers=sesion["cabeceras"])
    assert primera.status_code == 200, primera.text
    assert primera.json()["calificacion"] == 4
    assert primera.json()["es_mia"] is True
    assert primera.json()["autor"] == "Prueba"

    segunda = cliente.put(
        sesion["base"],
        json=_resena(sesion, calificacion=5, texto="   "),
        headers=sesion["cabeceras"],
    )
    assert segunda.status_code == 200
    assert segunda.json()["id"] == primera.json()["id"]
    assert segunda.json()["texto"] is None

    resumen = _resumen(cliente, sesion)
    assert resumen["total"] == 1
    assert resumen["promedio"] == 5.0
    assert resumen["distribucion"] == {"1": 0, "2": 0, "3": 0, "4": 0, "5": 1}
    assert resumen["mia"]["id"] == primera.json()["id"]
    assert [r["es_mia"] for r in resumen["resenas"]] == [True]


def prueba_resumen_junta_las_resenas_de_todos_los_usuarios_del_mismo_servidor(
    cliente: TestClient, sesion: dict
) -> None:
    otro = _sesion(cliente, CORREOS[1], "otro")
    cliente.put(sesion["base"], json=_resena(sesion, calificacion=5), headers=sesion["cabeceras"])
    cliente.put(
        otro["base"],
        json=_resena(otro, calificacion=3, texto="Regular."),
        headers=otro["cabeceras"],
    )

    resumen = _resumen(cliente, sesion)
    assert resumen["total"] == 2
    assert resumen["promedio"] == 4.0
    assert resumen["distribucion"]["3"] == 1 and resumen["distribucion"]["5"] == 1
    assert resumen["mia"]["calificacion"] == 5
    ajena = next(r for r in resumen["resenas"] if not r["es_mia"])
    assert ajena["calificacion"] == 3 and ajena["texto"] == "Regular."

    resumen_otro = _resumen(cliente, otro)
    assert resumen_otro["mia"]["calificacion"] == 3
    assert [r["calificacion"] for r in resumen_otro["resenas"] if not r["es_mia"]] == [5]


def prueba_contenido_sin_resenas(cliente: TestClient, sesion: dict) -> None:
    resumen = _resumen(cliente, sesion, stream_id="999")
    assert resumen == {
        "promedio": None,
        "total": 0,
        "distribucion": {"1": 0, "2": 0, "3": 0, "4": 0, "5": 0},
        "mia": None,
        "resenas": [],
    }


def prueba_borrar_resena(cliente: TestClient, sesion: dict) -> None:
    cliente.put(sesion["base"], json=_resena(sesion), headers=sesion["cabeceras"])
    ruta = f"{sesion['base']}/{sesion['conexion_id']}/pelicula/501"
    assert cliente.delete(ruta, headers=sesion["cabeceras"]).status_code == 204
    assert cliente.delete(ruta, headers=sesion["cabeceras"]).status_code == 204
    assert _resumen(cliente, sesion)["total"] == 0


def prueba_mis_resenas_mas_reciente_primero(cliente: TestClient, sesion: dict) -> None:
    cliente.put(sesion["base"], json=_resena(sesion), headers=sesion["cabeceras"])
    cliente.put(
        sesion["base"],
        json=_resena(
            sesion, tipo_contenido="serie", stream_id="900", titulo="Serie Uno", calificacion=2
        ),
        headers=sesion["cabeceras"],
    )
    cliente.put(
        sesion["base"],
        json=_resena(
            sesion, tipo_contenido="en_vivo", stream_id="101", titulo="Canal Uno", calificacion=3
        ),
        headers=sesion["cabeceras"],
    )

    mias = cliente.get(sesion["base"], headers=sesion["cabeceras"]).json()
    assert [r["titulo"] for r in mias] == ["Canal Uno", "Serie Uno", "Película Uno"]
    assert all(r["es_mia"] for r in mias)


def prueba_validaciones_de_la_resena(cliente: TestClient, sesion: dict) -> None:
    for calificacion in (0, 6):
        respuesta = cliente.put(
            sesion["base"],
            json=_resena(sesion, calificacion=calificacion),
            headers=sesion["cabeceras"],
        )
        assert respuesta.status_code == 422, calificacion
    demasiado_largo = cliente.put(
        sesion["base"], json=_resena(sesion, texto="x" * 2001), headers=sesion["cabeceras"]
    )
    assert demasiado_largo.status_code == 422
    tipo_invalido = cliente.put(
        sesion["base"], json=_resena(sesion, tipo_contenido="episodio"), headers=sesion["cabeceras"]
    )
    assert tipo_invalido.status_code == 422


def prueba_conexion_o_perfil_ajenos(cliente: TestClient, sesion: dict) -> None:
    otro = _sesion(cliente, CORREOS[1], "otro")
    ajena = cliente.put(
        sesion["base"],
        json=_resena(sesion, conexion_id=otro["conexion_id"]),
        headers=sesion["cabeceras"],
    )
    assert ajena.status_code == 404
    inexistente = cliente.put(
        sesion["base"],
        json=_resena(sesion, conexion_id="00000000-0000-0000-0000-000000000000"),
        headers=sesion["cabeceras"],
    )
    assert inexistente.status_code == 404
    perfil_ajeno = cliente.put(otro["base"], json=_resena(sesion), headers=sesion["cabeceras"])
    assert perfil_ajeno.status_code == 404
    assert cliente.get(f"{sesion['base']}/{sesion['conexion_id']}/pelicula/501").status_code == 401
