from dataclasses import dataclass
from typing import Any

import httpx2 as httpx

URL_API = "https://api.skipdb.tv/api/segments"
AGENTE = "FlowCast v1.0"
TIEMPO_ESPERA_SEGUNDOS = 6.0
CONFIANZA_MINIMA = 0.5
COINCIDENCIAS = {"exact", "shifted"}
INTRO_MINIMA = 5
INTRO_MAXIMA = 300
ATRIBUCION = "Tiempos de intro y créditos: SkipDB y sus colaboradores (ODbL 1.0)"


@dataclass(frozen=True)
class Saltos:
    intro_inicio: float | None = None
    intro_fin: float | None = None
    creditos: float | None = None


def _confiable(segmento: Any) -> dict[str, Any] | None:
    if not isinstance(segmento, dict):
        return None
    if segmento.get("match") not in COINCIDENCIAS:
        return None
    if (segmento.get("confidence") or 0) < CONFIANZA_MINIMA:
        return None
    return segmento


def interpretar(datos: Any, duracion: float) -> Saltos:
    segmentos = (datos or {}).get("segments") or {}
    intro_inicio = intro_fin = creditos = None
    if intro := _confiable(segmentos.get("intro")):
        inicio = (intro.get("start_ms") or 0) / 1000
        fin = (intro.get("end_ms") or 0) / 1000
        if INTRO_MINIMA <= fin - inicio <= INTRO_MAXIMA and fin < duracion:
            intro_inicio, intro_fin = round(inicio, 2), round(fin, 2)
    if outro := _confiable(segmentos.get("outro")):
        inicio = (outro.get("start_ms") or 0) / 1000
        if duracion / 2 < inicio < duracion:
            creditos = round(inicio, 2)
    return Saltos(intro_inicio, intro_fin, creditos)


async def consultar(imdb_id: str, temporada: int, episodio: int, duracion: float) -> Saltos | None:
    parametros = {
        "imdb_id": imdb_id,
        "season": str(temporada),
        "episode": str(episodio),
        "duration": str(round(duracion)),
    }
    try:
        async with httpx.AsyncClient(timeout=TIEMPO_ESPERA_SEGUNDOS) as cliente:
            respuesta = await cliente.get(
                URL_API,
                params=parametros,
                headers={"User-Agent": AGENTE, "Accept": "application/json"},
            )
    except httpx.HTTPError:
        return None
    if respuesta.status_code != 200:
        return None
    try:
        return interpretar(respuesta.json(), duracion)
    except ValueError:
        return None
