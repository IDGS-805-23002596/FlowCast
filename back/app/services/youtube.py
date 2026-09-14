import re

import httpx2 as httpx

URL_OEMBED = "https://www.youtube.com/oembed"
TIEMPO_ESPERA_SEGUNDOS = 8.0
_ID = re.compile(r"^[\w-]{11}$")
_EN_ENLACE = re.compile(r"(?:v=|youtu\.be/|embed/|shorts/)([\w-]{11})")


def id_de_video(valor: str | None) -> str | None:
    texto = str(valor or "").strip()
    if _ID.match(texto):
        return texto
    coincidencia = _EN_ENLACE.search(texto)
    return coincidencia.group(1) if coincidencia else None


async def se_puede_incrustar(
    video_id: str, cliente: httpx.AsyncClient | None = None
) -> bool | None:
    cliente_propio = cliente is None
    if cliente is None:
        cliente = httpx.AsyncClient(timeout=TIEMPO_ESPERA_SEGUNDOS)
    try:
        respuesta = await cliente.get(
            URL_OEMBED,
            params={"url": f"https://www.youtube.com/watch?v={video_id}", "format": "json"},
        )
    except httpx.HTTPError:
        return None
    finally:
        if cliente_propio:
            await cliente.aclose()
    if respuesta.status_code == 200:
        return True
    if respuesta.status_code in (401, 403, 404):
        return False
    return None
