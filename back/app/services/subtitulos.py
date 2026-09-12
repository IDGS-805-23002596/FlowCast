import re
from datetime import UTC, datetime, timedelta
from typing import Any

import httpx2 as httpx
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import configuracion
from app.models import SubtituloExterno
from app.services import cache

URL_API = "https://api.opensubtitles.com/api/v1"
AGENTE = "FlowCast v1.0"
IDIOMA = "es"
VARIANTES = {"ea": 2, "es": 1, "sp": 0}
TIEMPO_ESPERA_SEGUNDOS = 15
SEGUNDOS_TOKEN = 20 * 3600
DIAS_REINTENTO = 7
MAXIMO_LINEAS = 5000

_SIN_RESPUESTA = object()
_TIEMPO = re.compile(
    r"(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})"
)
_ETIQUETAS = re.compile(r"<[^>]+>|\{\\[^}]*\}")


def disponible() -> bool:
    return bool(configuracion.opensubtitles_clave_api)


def referencia(tmdb_id: str, temporada: int | None = None, episodio: int | None = None) -> str:
    return f"{tmdb_id}:{temporada}:{episodio}" if temporada is not None else tmdb_id


def _cabeceras(token: str | None = None) -> dict[str, str]:
    cabeceras = {
        "Api-Key": configuracion.opensubtitles_clave_api or "",
        "User-Agent": AGENTE,
        "Accept": "application/json",
    }
    if token:
        cabeceras["Authorization"] = f"Bearer {token}"
    return cabeceras


async def _iniciar_sesion(cliente: httpx.AsyncClient) -> str | None:
    if not configuracion.opensubtitles_usuario or not configuracion.opensubtitles_contrasena:
        return None
    try:
        respuesta = await cliente.post(
            f"{URL_API}/login",
            headers=_cabeceras(),
            json={
                "username": configuracion.opensubtitles_usuario,
                "password": configuracion.opensubtitles_contrasena,
            },
        )
    except httpx.HTTPError:
        return None
    if respuesta.status_code != 200:
        return None
    return (respuesta.json() or {}).get("token")


async def _token(cliente: httpx.AsyncClient) -> str | None:
    token = await cache.obtener_o_calcular(
        "opensubtitles:token", lambda: _iniciar_sesion(cliente), SEGUNDOS_TOKEN
    )
    if token is None:
        cache.invalidar("opensubtitles:token")
    return token


def _puntaje(resultado: dict[str, Any]) -> tuple:
    atributos = resultado.get("attributes") or {}
    return (
        not atributos.get("machine_translated") and not atributos.get("ai_translated"),
        VARIANTES.get(str(atributos.get("language") or "").lower(), -1),
        bool(atributos.get("from_trusted")),
        not atributos.get("hearing_impaired"),
        atributos.get("download_count") or 0,
    )


def elegir(resultados: list[Any]) -> str | None:
    candidatos = []
    for resultado in resultados:
        atributos = (resultado or {}).get("attributes") or {}
        archivos = atributos.get("files") or []
        idioma = str(atributos.get("language") or "").lower()
        if archivos and archivos[0].get("file_id") and idioma in VARIANTES:
            candidatos.append(resultado)
    if not candidatos:
        return None
    mejor = max(candidatos, key=_puntaje)
    return str(mejor["attributes"]["files"][0]["file_id"])


async def buscar(
    tmdb_id: str, temporada: int | None, episodio: int | None, cliente: httpx.AsyncClient
) -> Any:
    parametros = {"languages": ",".join(sorted(VARIANTES))}
    if temporada is None:
        parametros |= {"tmdb_id": tmdb_id, "type": "movie"}
    else:
        parametros |= {
            "parent_tmdb_id": tmdb_id,
            "season_number": str(temporada),
            "episode_number": str(episodio),
            "type": "episode",
        }
    try:
        respuesta = await cliente.get(
            f"{URL_API}/subtitles",
            headers=_cabeceras(),
            params=sorted(parametros.items()),
            follow_redirects=True,
        )
    except httpx.HTTPError:
        return _SIN_RESPUESTA
    if respuesta.status_code != 200:
        return _SIN_RESPUESTA
    return elegir((respuesta.json() or {}).get("data") or [])


def _decodificar(contenido: bytes) -> str:
    for codificacion in ("utf-8-sig", "cp1252"):
        try:
            return contenido.decode(codificacion)
        except UnicodeDecodeError:
            continue
    return contenido.decode("latin-1")


async def _pedir_enlace(
    archivo_id: str, cliente: httpx.AsyncClient, token: str | None
) -> httpx.Response:
    return await cliente.post(
        f"{URL_API}/download",
        headers=_cabeceras(token),
        json={"file_id": int(archivo_id), "sub_format": "srt"},
    )


async def descargar(archivo_id: str, cliente: httpx.AsyncClient) -> Any:
    try:
        respuesta = await _pedir_enlace(archivo_id, cliente, None)
        if respuesta.status_code != 200 and (token := await _token(cliente)):
            respuesta = await _pedir_enlace(archivo_id, cliente, token)
        if respuesta.status_code != 200:
            return _SIN_RESPUESTA
        enlace = (respuesta.json() or {}).get("link")
        if not enlace:
            return _SIN_RESPUESTA
        archivo = await cliente.get(enlace, headers={"User-Agent": AGENTE}, follow_redirects=True)
    except httpx.HTTPError:
        return _SIN_RESPUESTA
    if archivo.status_code != 200:
        return _SIN_RESPUESTA
    return _decodificar(archivo.content)


def _segundos(horas: str, minutos: str, segundos: str, milesimas: str) -> float:
    return (
        int(horas) * 3600 + int(minutos) * 60 + int(segundos) + int(milesimas.ljust(3, "0")) / 1000
    )


def leer_srt(texto: str) -> list[dict[str, Any]]:
    lineas = []
    for bloque in re.split(r"\r?\n\s*\r?\n", texto.replace("\r\n", "\n")):
        filas = [fila.strip() for fila in bloque.strip().split("\n")]
        tiempo = next((i for i, fila in enumerate(filas) if _TIEMPO.search(fila)), None)
        if tiempo is None:
            continue
        partes = _TIEMPO.search(filas[tiempo]).groups()
        contenido = "\n".join(
            limpio for fila in filas[tiempo + 1 :] if (limpio := _ETIQUETAS.sub("", fila).strip())
        )
        inicio, fin = _segundos(*partes[:4]), _segundos(*partes[4:])
        if contenido and fin > inicio:
            lineas.append({"inicio": round(inicio, 3), "fin": round(fin, 3), "texto": contenido})
    lineas.sort(key=lambda linea: linea["inicio"])
    return lineas[:MAXIMO_LINEAS]


async def obtener(
    sesion_bd: AsyncSession,
    tipo: str,
    tmdb_id: str,
    temporada: int | None = None,
    episodio: int | None = None,
    cliente: httpx.AsyncClient | None = None,
) -> list[dict[str, Any]] | None:
    ref = referencia(tmdb_id, temporada, episodio)
    guardado = await sesion_bd.get(SubtituloExterno, (tipo, ref, IDIOMA))
    reciente = datetime.now(UTC) - timedelta(days=DIAS_REINTENTO)
    if guardado is not None and (guardado.lineas or guardado.consultado_en > reciente):
        return guardado.lineas or None
    if not disponible():
        return None
    cliente_propio = cliente is None
    if cliente is None:
        cliente = httpx.AsyncClient(timeout=TIEMPO_ESPERA_SEGUNDOS)
    try:
        archivo_id = await buscar(tmdb_id, temporada, episodio, cliente)
        if archivo_id is _SIN_RESPUESTA:
            return None
        lineas = None
        if archivo_id:
            texto = await descargar(archivo_id, cliente)
            if texto is _SIN_RESPUESTA:
                return None
            lineas = leer_srt(texto) or None
    finally:
        if cliente_propio:
            await cliente.aclose()
    valores = {
        "tipo_contenido": tipo,
        "referencia": ref,
        "idioma": IDIOMA,
        "archivo_id": archivo_id,
        "lineas": lineas,
        "consultado_en": datetime.now(UTC),
    }
    await sesion_bd.execute(
        insert(SubtituloExterno)
        .values(**valores)
        .on_conflict_do_update(
            index_elements=["tipo_contenido", "referencia", "idioma"],
            set_={
                k: v
                for k, v in valores.items()
                if k not in ("tipo_contenido", "referencia", "idioma")
            },
        )
    )
    await sesion_bd.commit()
    return lineas
