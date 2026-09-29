from app.services import xtream


def _episodio(episodio_id: str, numero: int, **extras) -> xtream.Episodio:
    datos = {"extension": "mkv", "imagen": None, "sinopsis": None, "duracion_segundos": None}
    return xtream.Episodio(
        id=episodio_id, temporada=1, numero=numero, titulo=f"E{numero}", **(datos | extras)
    )


def prueba_sin_copias_deja_un_episodio_por_numero() -> None:
    episodios = [
        _episodio("30", 1),
        _episodio("10", 1, extension="mp4", duracion_segundos=1800),
        _episodio("20", 1),
        _episodio("21", 2),
        _episodio("11", 2, extension="mp4"),
        _episodio("40", 0),
        _episodio("41", 0),
        _episodio("12", 3),
    ]
    unicos = xtream._sin_copias(episodios)
    assert [(e.id, e.numero) for e in unicos] == [
        ("10", 1),
        ("21", 2),
        ("12", 3),
        ("40", 0),
        ("41", 0),
    ]
    primero = unicos[0]
    assert primero.alternos == ["30", "20"]
    assert primero.extension_de("10") == "mp4"
    assert primero.extension_de("20") == "mkv"
    assert primero.extension_de("99") is None
    assert unicos[1].alternos == ["11"]
    assert unicos[1].extension_de("11") == "mp4"
    assert unicos[2].alternos == []
