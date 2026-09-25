from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient

from app.main import aplicacion
from tests.ayudantes import borrar_usuarios, crear_conexion, instalar_proveedor_minimo, registrar

CORREOS = ["biblio-yo@ejemplo.com", "biblio-otro@ejemplo.com"]


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


@pytest.fixture
def sesion(cliente: TestClient) -> dict:
    cabeceras = registrar(cliente, CORREOS[0])
    perfil_id = cliente.get("/perfiles", headers=cabeceras).json()[0]["id"]
    conexion_id = crear_conexion(cliente, cabeceras)
    return {"cabeceras": cabeceras, "base": f"/perfiles/{perfil_id}", "conexion_id": conexion_id}


def _pelicula(sesion: dict, **cambios) -> dict:
    return {
        "conexion_id": sesion["conexion_id"],
        "tipo_contenido": "pelicula",
        "stream_id": "501",
        "titulo": "Película Uno",
        "url_portada": "http://img/p1.jpg",
        **cambios,
    }


def prueba_favoritos_marcar_listar_y_quitar(cliente: TestClient, sesion: dict) -> None:
    ruta = f"{sesion['base']}/favoritos"
    primero = cliente.put(ruta, json=_pelicula(sesion), headers=sesion["cabeceras"])
    assert primero.status_code == 200, primero.text
    repetido = cliente.put(
        ruta, json=_pelicula(sesion, titulo="Título corregido"), headers=sesion["cabeceras"]
    )
    assert repetido.json()["titulo"] == "Título corregido"
    cliente.put(
        ruta,
        json=_pelicula(sesion, tipo_contenido="en_vivo", stream_id="101", titulo="Canal Uno"),
        headers=sesion["cabeceras"],
    )

    todos = cliente.get(ruta, headers=sesion["cabeceras"]).json()
    assert [f["titulo"] for f in todos] == ["Canal Uno", "Título corregido"]
    solo_tv = cliente.get(f"{ruta}?tipo_contenido=en_vivo", headers=sesion["cabeceras"]).json()
    assert [f["stream_id"] for f in solo_tv] == ["101"]

    quitar = f"{ruta}/{sesion['conexion_id']}/pelicula/501"
    assert cliente.delete(quitar, headers=sesion["cabeceras"]).status_code == 204
    assert cliente.delete(quitar, headers=sesion["cabeceras"]).status_code == 204
    assert len(cliente.get(ruta, headers=sesion["cabeceras"]).json()) == 1


def prueba_favorito_con_conexion_ajena_o_inexistente(cliente: TestClient, sesion: dict) -> None:
    otro = registrar(cliente, CORREOS[1])
    conexion_ajena = crear_conexion(cliente, otro, usuario="otro")
    ruta = f"{sesion['base']}/favoritos"
    ajena = cliente.put(
        ruta, json=_pelicula(sesion, conexion_id=conexion_ajena), headers=sesion["cabeceras"]
    )
    assert ajena.status_code == 404
    inexistente = cliente.put(
        ruta,
        json=_pelicula(sesion, conexion_id="00000000-0000-0000-0000-000000000000"),
        headers=sesion["cabeceras"],
    )
    assert inexistente.status_code == 404
    assert cliente.get(ruta, headers=otro).status_code == 404


def prueba_progreso_guardar_actualizar_y_completar(cliente: TestClient, sesion: dict) -> None:
    ruta = f"{sesion['base']}/progreso"
    inicio = cliente.put(
        ruta,
        json=_pelicula(sesion, posicion_segundos=600, duracion_segundos=5400),
        headers=sesion["cabeceras"],
    )
    assert inicio.status_code == 200, inicio.text
    assert inicio.json()["posicion_segundos"] == 600 and inicio.json()["completado"] is False

    mas_tarde = cliente.put(
        ruta,
        json=_pelicula(sesion, posicion_segundos=3000, duracion_segundos=5400),
        headers=sesion["cabeceras"],
    )
    assert mas_tarde.json()["posicion_segundos"] == 3000
    assert len(cliente.get(ruta, headers=sesion["cabeceras"]).json()) == 1

    uno = cliente.get(f"{ruta}/{sesion['conexion_id']}/pelicula/501", headers=sesion["cabeceras"])
    assert uno.status_code == 200 and uno.json()["posicion_segundos"] == 3000

    casi_final = cliente.put(
        ruta,
        json=_pelicula(sesion, posicion_segundos=5200, duracion_segundos=5400),
        headers=sesion["cabeceras"],
    )
    assert casi_final.json()["completado"] is True
    assert cliente.get(ruta, headers=sesion["cabeceras"]).json() == []

    reiniciar = cliente.put(
        ruta,
        json=_pelicula(sesion, posicion_segundos=0, duracion_segundos=5400, completado=False),
        headers=sesion["cabeceras"],
    )
    assert reiniciar.json()["completado"] is False

    assert (
        cliente.delete(
            f"{ruta}/{sesion['conexion_id']}/pelicula/501", headers=sesion["cabeceras"]
        ).status_code
        == 204
    )
    assert (
        cliente.get(
            f"{ruta}/{sesion['conexion_id']}/pelicula/501", headers=sesion["cabeceras"]
        ).status_code
        == 404
    )


def prueba_progreso_de_episodio_requiere_serie(cliente: TestClient, sesion: dict) -> None:
    ruta = f"{sesion['base']}/progreso"
    sin_serie = cliente.put(
        ruta,
        json=_pelicula(sesion, tipo_contenido="episodio", stream_id="9001", posicion_segundos=10),
        headers=sesion["cabeceras"],
    )
    assert sin_serie.status_code == 422
    con_serie = cliente.put(
        ruta,
        json=_pelicula(
            sesion,
            tipo_contenido="episodio",
            stream_id="9001",
            serie_id="900",
            posicion_segundos=10,
        ),
        headers=sesion["cabeceras"],
    )
    assert con_serie.status_code == 200 and con_serie.json()["serie_id"] == "900"
    en_vivo = cliente.put(
        ruta,
        json=_pelicula(sesion, tipo_contenido="en_vivo", stream_id="101", posicion_segundos=10),
        headers=sesion["cabeceras"],
    )
    assert en_vivo.status_code == 422
    mas_que_duracion = cliente.put(
        ruta,
        json=_pelicula(sesion, posicion_segundos=99, duracion_segundos=50),
        headers=sesion["cabeceras"],
    )
    assert mas_que_duracion.status_code == 422


def prueba_historial_registrar_sin_duplicar_y_borrar(cliente: TestClient, sesion: dict) -> None:
    ruta = f"{sesion['base']}/historial"
    primera = cliente.post(ruta, json=_pelicula(sesion), headers=sesion["cabeceras"])
    assert primera.status_code == 201, primera.text
    repetida = cliente.post(ruta, json=_pelicula(sesion), headers=sesion["cabeceras"])
    assert repetida.status_code == 201 and repetida.json()["id"] == primera.json()["id"]
    cliente.post(
        ruta,
        json=_pelicula(sesion, tipo_contenido="en_vivo", stream_id="101", titulo="Canal Uno"),
        headers=sesion["cabeceras"],
    )

    todo = cliente.get(ruta, headers=sesion["cabeceras"]).json()
    assert [h["titulo"] for h in todo] == ["Canal Uno", "Película Uno"]
    assert len(cliente.get(f"{ruta}?limite=1", headers=sesion["cabeceras"]).json()) == 1
    assert [
        h["tipo_contenido"]
        for h in cliente.get(f"{ruta}?tipo_contenido=pelicula", headers=sesion["cabeceras"]).json()
    ] == ["pelicula"]

    assert (
        cliente.delete(f"{ruta}/{primera.json()['id']}", headers=sesion["cabeceras"]).status_code
        == 204
    )
    assert len(cliente.get(ruta, headers=sesion["cabeceras"]).json()) == 1
    assert cliente.delete(ruta, headers=sesion["cabeceras"]).status_code == 204
    assert cliente.get(ruta, headers=sesion["cabeceras"]).json() == []


def prueba_borrar_el_perfil_arrastra_su_biblioteca(cliente: TestClient, sesion: dict) -> None:
    cliente.put(f"{sesion['base']}/favoritos", json=_pelicula(sesion), headers=sesion["cabeceras"])
    cliente.post(f"{sesion['base']}/historial", json=_pelicula(sesion), headers=sesion["cabeceras"])
    segundo = cliente.post(
        "/perfiles", json={"nombre": "Segundo"}, headers=sesion["cabeceras"]
    ).json()["id"]
    assert cliente.delete(sesion["base"], headers=sesion["cabeceras"]).status_code == 204
    assert (
        cliente.get(f"{sesion['base']}/favoritos", headers=sesion["cabeceras"]).status_code == 404
    )
    assert cliente.get(f"/perfiles/{segundo}/favoritos", headers=sesion["cabeceras"]).json() == []


def prueba_pistas_de_una_serie(cliente: TestClient, sesion: dict) -> None:
    ruta = f"{sesion['base']}/pistas/{sesion['conexion_id']}/900"
    cabeceras = sesion["cabeceras"]
    assert cliente.get(ruta, headers=cabeceras).json() == {"audio": None, "subtitulos": None}
    elegidas = {"audio": "Español latino", "subtitulos": "Español"}
    assert cliente.put(ruta, json=elegidas, headers=cabeceras).json() == elegidas
    cliente.put(ruta, json={"audio": "Inglés", "subtitulos": None}, headers=cabeceras)
    assert cliente.get(ruta, headers=cabeceras).json() == {"audio": "Inglés", "subtitulos": None}
    ajena = f"{sesion['base']}/pistas/00000000-0000-0000-0000-000000000000/900"
    assert cliente.put(ajena, json=elegidas, headers=cabeceras).status_code == 404
