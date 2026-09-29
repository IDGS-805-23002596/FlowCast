import wave
from collections.abc import Iterator
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.config import configuracion
from app.main import aplicacion
from app.services import musica
from tests.ayudantes import borrar_usuarios, registrar

CORREOS = ["musica@ejemplo.com"]


def _wav(ruta: Path, segundos: float = 1.0) -> None:
    ruta.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(ruta), "wb") as archivo:
        archivo.setnchannels(1)
        archivo.setsampwidth(2)
        archivo.setframerate(8000)
        archivo.writeframes(b"\x00\x00" * int(8000 * segundos))


@pytest.fixture(autouse=True)
def usuarios_limpios() -> Iterator[None]:
    borrar_usuarios(CORREOS)
    yield
    borrar_usuarios(CORREOS)


@pytest.fixture
def carpeta(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> Path:
    _wav(tmp_path / "Los Faros - Marea alta" / "01 - Puerto escondido.wav", 2.0)
    _wav(tmp_path / "Los Faros - Marea alta" / "02 - Luz de tormenta.wav", 1.0)
    (tmp_path / "Los Faros - Marea alta" / "cover.jpg").write_bytes(
        b"\xff\xd8\xff\xe0portada-falsa"
    )
    _wav(tmp_path / "Eco Binario - Pulso" / "01 - Latido.wav", 1.5)
    (tmp_path / "notas.txt").write_text("no soy audio")
    monkeypatch.setattr(configuracion, "musica_carpeta", str(tmp_path))
    musica.reescanear()
    yield tmp_path
    monkeypatch.setattr(configuracion, "musica_carpeta", None)
    musica.reescanear()


@pytest.fixture
def cliente() -> Iterator[TestClient]:
    with TestClient(aplicacion) as cliente_http:
        yield cliente_http


@pytest.fixture
def cabeceras(cliente: TestClient) -> dict[str, str]:
    return registrar(cliente, CORREOS[0])


def prueba_resumen_y_albumes(cliente: TestClient, cabeceras: dict, carpeta: Path) -> None:
    assert cliente.get("/musica").status_code == 401
    resumen = cliente.get("/musica", headers=cabeceras).json()
    assert resumen["carpeta_configurada"] is True
    assert (resumen["total_canciones"], resumen["total_albumes"]) == (3, 2)

    albumes = cliente.get("/musica/albumes", headers=cabeceras).json()
    assert [(a["artista"], a["titulo"], a["total_canciones"]) for a in albumes] == [
        ("Eco Binario", "Pulso", 1),
        ("Los Faros", "Marea alta", 2),
    ]
    marea = albumes[1]
    assert marea["duracion_segundos"] == 3.0
    assert marea["url_portada"] is not None
    assert albumes[0]["url_portada"] is None

    detalle = cliente.get(f"/musica/albumes/{marea['id']}", headers=cabeceras).json()
    assert [c["titulo"] for c in detalle["canciones"]] == ["Puerto escondido", "Luz de tormenta"]
    assert [c["numero_pista"] for c in detalle["canciones"]] == [1, 2]
    assert cliente.get("/musica/albumes/inexistente", headers=cabeceras).status_code == 404


def prueba_buscar_canciones(cliente: TestClient, cabeceras: dict, carpeta: Path) -> None:
    todas = cliente.get("/musica/canciones", headers=cabeceras).json()
    assert [c["titulo"] for c in todas] == ["Latido", "Puerto escondido", "Luz de tormenta"]
    por_texto = cliente.get("/musica/canciones?buscar=faros", headers=cabeceras).json()
    assert {c["artista"] for c in por_texto} == {"Los Faros"}
    por_album = cliente.get(
        f"/musica/canciones?album_id={todas[0]['album_id']}", headers=cabeceras
    ).json()
    assert [c["titulo"] for c in por_album] == ["Latido"]


def prueba_audio_con_url_firmada(cliente: TestClient, cabeceras: dict, carpeta: Path) -> None:
    cancion = cliente.get("/musica/canciones?buscar=latido", headers=cabeceras).json()[0]
    assert cancion["url_audio"].startswith(f"/musica/canciones/{cancion['id']}/audio?caduca=")

    completo = cliente.get(cancion["url_audio"])
    assert completo.status_code == 200
    assert completo.headers["content-type"].startswith("audio/")
    parcial = cliente.get(cancion["url_audio"], headers={"Range": "bytes=0-99"})
    assert parcial.status_code == 206 and len(parcial.content) == 100

    sin_firma = cliente.get(f"/musica/canciones/{cancion['id']}/audio?caduca=9999999999&firma=mala")
    assert sin_firma.status_code == 403
    caducada = cliente.get(cancion["url_audio"].replace("caduca=", "caduca=1"))
    assert caducada.status_code == 403


def prueba_portada(cliente: TestClient, cabeceras: dict, carpeta: Path) -> None:
    con = cliente.get("/musica/canciones?buscar=puerto", headers=cabeceras).json()[0]
    respuesta = cliente.get(con["url_portada"])
    assert respuesta.status_code == 200
    assert respuesta.headers["content-type"] == "image/jpeg"
    assert respuesta.content.startswith(b"\xff\xd8")
    sin = cliente.get("/musica/canciones?buscar=latido", headers=cabeceras).json()[0]
    assert sin["url_portada"] is None
    assert cliente.get(f"/musica/canciones/{sin['id']}/portada").status_code == 404


def prueba_reescanear_detecta_archivos_nuevos(
    cliente: TestClient, cabeceras: dict, carpeta: Path
) -> None:
    _wav(carpeta / "Eco Binario - Pulso" / "02 - Señal.wav")
    assert cliente.get("/musica", headers=cabeceras).json()["total_canciones"] == 3
    nuevo = cliente.post("/musica/reescanear", headers=cabeceras).json()
    assert nuevo["total_canciones"] == 4


def prueba_sin_carpeta_configurada(
    cliente: TestClient, cabeceras: dict, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr(configuracion, "musica_carpeta", "/carpeta/que/no/existe")
    musica.reescanear()
    resumen = cliente.get("/musica", headers=cabeceras).json()
    assert resumen == {
        "carpeta_configurada": False,
        "total_canciones": 0,
        "total_albumes": 0,
        "escaneado_en": resumen["escaneado_en"],
    }
    assert cliente.get("/musica/albumes", headers=cabeceras).json() == []
    monkeypatch.setattr(configuracion, "musica_carpeta", None)
    musica.reescanear()
