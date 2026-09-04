import asyncio
import unicodedata
from dataclasses import dataclass
from typing import Any

import httpx2 as httpx

from app.config import configuracion
from app.services import titulos

URL_API = "https://api.themoviedb.org/3"
URL_FOTOS = "https://image.tmdb.org/t/p/w185"
URL_ESCENAS = "https://image.tmdb.org/t/p/w300"
URL_LOGOS = "https://image.tmdb.org/t/p/w500"
IDIOMAS_EPISODIOS = ("es-MX", "es-ES", "en-US")
TIEMPO_ESPERA_SEGUNDOS = 10.0
MAXIMO_ACTORES = 20
TIPOS_TRAILER = ("Trailer", "Teaser")
MAXIMO_PERSONAS = 3
POPULARIDAD_MINIMA = 1.0
TIPOS_TMDB = {"movie": "pelicula", "tv": "serie"}
TRABAJOS_DIRECCION = {"Director", "Creator"}
GENEROS_SIN_PAPEL = {10763, 10767}
PERSONAJES_PROPIOS = ("self", "himself", "herself", "sí mismo", "sí misma")


@dataclass(frozen=True)
class Actor:
    nombre: str
    personaje: str | None
    foto: str | None


@dataclass(frozen=True)
class EpisodioTmdb:
    nombre: str | None
    idioma_nombre: str | None
    sinopsis: str | None
    imagen: str | None


@dataclass(frozen=True)
class Persona:
    id: str
    nombre: str
    foto: str | None
    departamento: str | None


@dataclass(frozen=True)
class CreditosPersona:
    actuacion: dict[str, list[str]]
    direccion: dict[str, list[str]]


@dataclass(frozen=True)
class Trailer:
    video_id: str
    idioma: str | None


def disponible() -> bool:
    return bool(configuracion.tmdb_clave_api)


async def reparto_pelicula(tmdb_id: str, cliente: httpx.AsyncClient | None = None) -> list[Actor]:
    if not disponible():
        return []
    parametros = {"api_key": configuracion.tmdb_clave_api, "language": "es-MX"}
    cliente_propio = cliente is None
    if cliente is None:
        cliente = httpx.AsyncClient(timeout=TIEMPO_ESPERA_SEGUNDOS)
    try:
        respuesta = await cliente.get(f"{URL_API}/movie/{tmdb_id}/credits", params=parametros)
    except httpx.HTTPError:
        return []
    finally:
        if cliente_propio:
            await cliente.aclose()
    if respuesta.status_code != 200:
        return []
    try:
        datos: Any = respuesta.json()
    except ValueError:
        return []
    actores = []
    for persona in (datos.get("cast") or [])[:MAXIMO_ACTORES]:
        nombre = str(persona.get("name") or "").strip()
        if not nombre:
            continue
        ruta_foto = persona.get("profile_path")
        actores.append(
            Actor(
                nombre=nombre,
                personaje=(str(persona.get("character")).strip() or None)
                if persona.get("character")
                else None,
                foto=f"{URL_FOTOS}{ruta_foto}" if ruta_foto else None,
            )
        )
    return actores


def reparto_desde_texto(texto: str | None) -> list[Actor]:
    if not texto:
        return []
    nombres = [n.strip() for n in texto.replace(";", ",").split(",")]
    return [Actor(nombre=n, personaje=None, foto=None) for n in nombres if n][:MAXIMO_ACTORES]


async def episodios_temporada(
    tmdb_id: str, temporada: int, cliente: httpx.AsyncClient | None = None
) -> dict[int, EpisodioTmdb]:
    if not disponible():
        return {}
    nombres: dict[int, str] = {}
    idiomas: dict[int, str] = {}
    sinopsis: dict[int, str] = {}
    imagenes: dict[int, str] = {}
    for idioma in IDIOMAS_EPISODIOS:
        datos = await _obtener(f"/tv/{tmdb_id}/season/{temporada}", cliente, idioma)
        if not isinstance(datos, dict):
            break
        episodios = [e for e in datos.get("episodes") or [] if isinstance(e, dict)]
        for episodio in episodios:
            numero = episodio.get("episode_number")
            if not isinstance(numero, int):
                continue
            nombre = str(episodio.get("name") or "").strip()
            if nombre and not titulos.es_nombre_generico(nombre):
                nombres.setdefault(numero, nombre)
                idiomas.setdefault(numero, idioma)
            resumen = str(episodio.get("overview") or "").strip()
            if resumen:
                sinopsis.setdefault(numero, resumen)
            if episodio.get("still_path"):
                imagenes.setdefault(numero, f"{URL_ESCENAS}{episodio['still_path']}")
        numeros = {e.get("episode_number") for e in episodios}
        if numeros and all(n in nombres and n in sinopsis for n in numeros):
            break
    return {
        numero: EpisodioTmdb(
            nombre=nombres.get(numero),
            idioma_nombre=idiomas.get(numero),
            sinopsis=sinopsis.get(numero),
            imagen=imagenes.get(numero),
        )
        for numero in set(nombres) | set(sinopsis) | set(imagenes)
    }


async def trailers(
    tipo: str, tmdb_id: str, cliente: httpx.AsyncClient | None = None
) -> list[Trailer] | None:
    if not disponible():
        return []
    ruta = f"/movie/{tmdb_id}" if tipo == "pelicula" else f"/tv/{tmdb_id}"
    datos = await _obtener(
        ruta, cliente, "es-MX", append_to_response="videos", include_video_language="es,en"
    )
    if not isinstance(datos, dict):
        return None
    original = datos.get("original_language") or None
    videos = [
        v
        for v in (datos.get("videos") or {}).get("results") or []
        if isinstance(v, dict)
        and v.get("site") == "YouTube"
        and v.get("type") in TIPOS_TRAILER
        and v.get("key")
    ]
    videos.sort(key=_prioridad_trailer)
    idiomas = {
        str(v["key"]): "es" if v.get("iso_639_1") == "es" else original or v.get("iso_639_1")
        for v in reversed(videos)
    }
    claves = dict.fromkeys(str(v["key"]) for v in videos)
    return [Trailer(video_id=clave, idioma=idiomas[clave]) for clave in claves]


async def tendencias(
    tipo: str, paginas: int = 3, cliente: httpx.AsyncClient | None = None
) -> list[str] | None:
    if not disponible():
        return []
    ruta = "/trending/movie/week" if tipo == "pelicula" else "/trending/tv/week"
    respuestas = await asyncio.gather(
        *(_obtener(ruta, cliente, "es-MX", page=str(pagina)) for pagina in range(1, paginas + 1))
    )
    if all(respuesta is None for respuesta in respuestas):
        return None
    return _ids_de(respuestas)


async def recomendaciones(
    tipo: str, tmdb_id: str, cliente: httpx.AsyncClient | None = None
) -> list[str] | None:
    if not disponible():
        return []
    base = f"/movie/{tmdb_id}" if tipo == "pelicula" else f"/tv/{tmdb_id}"
    respuestas = await asyncio.gather(
        _obtener(f"{base}/recommendations", cliente, "es-MX"),
        _obtener(f"{base}/similar", cliente, "es-MX"),
    )
    if all(respuesta is None for respuesta in respuestas):
        return None
    return _ids_de(respuestas)


async def buscar_personas(
    texto: str, cliente: httpx.AsyncClient | None = None
) -> list[Persona] | None:
    if not disponible():
        return None
    datos = await _obtener("/search/person", cliente, "es-MX", query=texto, include_adult="false")
    if datos is None:
        return None
    personas = []
    for resultado in datos.get("results") or []:
        if not isinstance(resultado, dict) or not resultado.get("id") or not resultado.get("name"):
            continue
        if (resultado.get("popularity") or 0) < POPULARIDAD_MINIMA:
            continue
        foto = resultado.get("profile_path")
        personas.append(
            Persona(
                id=str(resultado["id"]),
                nombre=resultado["name"],
                foto=f"{URL_FOTOS}{foto}" if foto else None,
                departamento=resultado.get("known_for_department"),
            )
        )
        if len(personas) == MAXIMO_PERSONAS:
            break
    return personas


def _es_papel(credito: dict[str, Any]) -> bool:
    if GENEROS_SIN_PAPEL & set(credito.get("genre_ids") or []):
        return False
    personaje = str(credito.get("character") or "").lower()
    return not any(propio in personaje for propio in PERSONAJES_PROPIOS)


def _ordenar(creditos: list[dict[str, Any]]) -> dict[str, list[str]]:
    ordenados: dict[str, list[str]] = {"pelicula": [], "serie": []}
    creditos.sort(key=lambda c: -(c.get("popularity") or 0))
    for credito in creditos:
        tipo = TIPOS_TMDB.get(credito.get("media_type"))
        if tipo and credito.get("id"):
            ordenados[tipo].append(str(credito["id"]))
    return {tipo: list(dict.fromkeys(ids)) for tipo, ids in ordenados.items()}


async def creditos_persona(
    persona_id: str, cliente: httpx.AsyncClient | None = None
) -> CreditosPersona | None:
    if not disponible():
        return None
    datos = await _obtener(f"/person/{persona_id}/combined_credits", cliente, "es-MX")
    if datos is None:
        return None
    reparto = [c for c in datos.get("cast") or [] if isinstance(c, dict) and _es_papel(c)]
    equipo = [
        c
        for c in datos.get("crew") or []
        if isinstance(c, dict) and c.get("job") in TRABAJOS_DIRECCION
    ]
    return CreditosPersona(actuacion=_ordenar(reparto), direccion=_ordenar(equipo))


async def imdb_de_serie(tmdb_id: str, cliente: httpx.AsyncClient | None = None) -> str | None:
    if not disponible():
        return None
    datos = await _obtener(f"/tv/{tmdb_id}/external_ids", cliente)
    if datos is None:
        return None
    return datos.get("imdb_id") or ""


async def logo(tipo: str, tmdb_id: str, cliente: httpx.AsyncClient | None = None) -> str | None:
    if not disponible():
        return None
    ruta = f"/movie/{tmdb_id}/images" if tipo == "pelicula" else f"/tv/{tmdb_id}/images"
    datos = await _obtener(ruta, cliente, include_image_language="es,en,null")
    if datos is None:
        return None
    logos = [l for l in datos.get("logos") or [] if isinstance(l, dict) and l.get("file_path")]
    if not logos:
        return ""
    preferencia = {"es": 0, "en": 1, None: 2}
    logos.sort(
        key=lambda l: (preferencia.get(l.get("iso_639_1"), 3), -(l.get("vote_average") or 0))
    )
    return f"{URL_LOGOS}{logos[0]['file_path']}"


def _ids_de(respuestas: list[Any]) -> list[str]:
    ids = []
    for respuesta in respuestas:
        for resultado in (respuesta or {}).get("results") or []:
            if isinstance(resultado, dict) and resultado.get("id"):
                ids.append(str(resultado["id"]))
    return list(dict.fromkeys(ids))


async def buscar_id(
    tipo: str, titulo: str, anio: int | None, cliente: httpx.AsyncClient | None = None
) -> str | None:
    if not disponible() or not titulo.strip():
        return None
    pelicula = tipo == "pelicula"
    extras = {"query": titulo}
    if anio:
        extras["year" if pelicula else "first_air_date_year"] = str(anio)
    datos = await _obtener(
        "/search/movie" if pelicula else "/search/tv", cliente, "es-MX", **extras
    )
    buscado = _comparable(titulo)
    for resultado in ((datos or {}).get("results") or [])[:5]:
        if not isinstance(resultado, dict) or not resultado.get("id"):
            continue
        nombres = (
            (resultado.get("title"), resultado.get("original_title"))
            if pelicula
            else (resultado.get("name"), resultado.get("original_name"))
        )
        fecha = str(resultado.get("release_date" if pelicula else "first_air_date") or "")
        anio_resultado = int(fecha[:4]) if fecha[:4].isdigit() else None
        mismo_nombre = buscado in {_comparable(n) for n in nombres if n}
        mismo_anio = bool(anio and anio_resultado and abs(anio - anio_resultado) <= 1)
        if mismo_nombre and (mismo_anio or not anio):
            return str(resultado["id"])
    return None


def _comparable(texto: str) -> str:
    sin_acentos = unicodedata.normalize("NFKD", texto.casefold())
    return "".join(c for c in sin_acentos if c.isalnum())


def _prioridad_trailer(video: dict[str, Any]) -> tuple[int, int, int, int]:
    idioma = video.get("iso_639_1")
    if idioma == "es" and video.get("iso_3166_1") == "MX":
        orden_idioma = 0
    elif idioma == "es":
        orden_idioma = 1
    elif idioma == "en":
        orden_idioma = 2
    else:
        orden_idioma = 3
    return (
        orden_idioma,
        TIPOS_TRAILER.index(video.get("type")),
        0 if video.get("official") else 1,
        -int(video.get("size") or 0),
    )


async def _obtener(
    ruta: str, cliente: httpx.AsyncClient | None, idioma: str | None = None, **extras: str
) -> Any:
    parametros = {"api_key": configuracion.tmdb_clave_api, **extras}
    if idioma:
        parametros["language"] = idioma
    cliente_propio = cliente is None
    if cliente is None:
        cliente = httpx.AsyncClient(timeout=TIEMPO_ESPERA_SEGUNDOS)
    try:
        respuesta = await cliente.get(f"{URL_API}{ruta}", params=parametros)
    except httpx.HTTPError:
        return None
    finally:
        if cliente_propio:
            await cliente.aclose()
    if respuesta.status_code != 200:
        return None
    try:
        return respuesta.json()
    except ValueError:
        return None


async def sinopsis(tipo: str, tmdb_id: str, cliente: httpx.AsyncClient | None = None) -> str:
    if not disponible():
        return ""
    ruta = f"/{'movie' if tipo == 'pelicula' else 'tv'}/{tmdb_id}"
    for idioma in ("es-MX", "es-ES"):
        datos = await _obtener(ruta, cliente, idioma)
        texto = str((datos or {}).get("overview") or "").strip()
        if texto:
            return texto
    return ""


def _por_pais(clasificaciones: dict[str, str]) -> str | None:
    if clasificaciones.get("MX"):
        return clasificaciones["MX"]
    if clasificaciones.get("US"):
        return f"{clasificaciones['US']} (EE. UU.)"
    return None


async def clasificacion(
    tipo: str, tmdb_id: str, cliente: httpx.AsyncClient | None = None
) -> str | None:
    if not disponible():
        return None
    encontradas: dict[str, str] = {}
    if tipo == "pelicula":
        datos = await _obtener(f"/movie/{tmdb_id}/release_dates", cliente)
        for pais in (datos or {}).get("results") or []:
            for estreno in pais.get("release_dates") or []:
                valor = str(estreno.get("certification") or "").strip()
                if valor and pais.get("iso_3166_1") not in encontradas:
                    encontradas[pais["iso_3166_1"]] = valor
    else:
        datos = await _obtener(f"/tv/{tmdb_id}/content_ratings", cliente)
        for pais in (datos or {}).get("results") or []:
            valor = str(pais.get("rating") or "").strip()
            if valor:
                encontradas[pais.get("iso_3166_1")] = valor
    return _por_pais(encontradas)
