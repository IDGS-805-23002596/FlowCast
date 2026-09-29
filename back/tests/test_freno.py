import asyncio
import time
from itertools import pairwise

from app.services import freno


async def _simular(
    url: str, tiempos: list[tuple[float, float]], ocupadas: list[int], duracion: float
) -> None:
    async with freno.turno(url):
        inicio = time.monotonic()
        ocupadas.append(ocupadas[-1] + 1 if ocupadas else 1)
        await asyncio.sleep(duracion)
        ocupadas.append(ocupadas[-1] - 1)
        tiempos.append((inicio, time.monotonic()))


async def prueba_maximo_dos_a_la_vez_y_pausa_entre_arranques() -> None:
    tiempos: list[tuple[float, float]] = []
    ocupadas: list[int] = []
    await asyncio.gather(
        *(_simular("http://proveedor.test:8080", tiempos, ocupadas, 0.05) for _ in range(6))
    )
    assert max(ocupadas) <= freno.MAXIMO_SIMULTANEAS
    inicios = sorted(i for i, _ in tiempos)
    separaciones = [b - a for a, b in pairwise(inicios)]
    assert all(s >= freno.PAUSA_SEGUNDOS * 0.9 for s in separaciones), separaciones


async def prueba_servidores_distintos_no_se_frenan_entre_si() -> None:
    tiempos_a: list[tuple[float, float]] = []
    tiempos_b: list[tuple[float, float]] = []
    inicio = time.monotonic()
    await asyncio.gather(
        _simular("http://uno.test", tiempos_a, [], 0.02),
        _simular("http://dos.test", tiempos_b, [], 0.02),
    )
    assert tiempos_a[0][0] - inicio < freno.PAUSA_SEGUNDOS / 2
    assert tiempos_b[0][0] - inicio < freno.PAUSA_SEGUNDOS / 2


def prueba_el_servidor_se_identifica_por_host_y_puerto() -> None:
    assert freno._servidor("http://Proveedor.com:8880/ruta") == "proveedor.com:8880"
    assert freno._servidor("http://proveedor.com:8880") == freno._servidor(
        "HTTP://PROVEEDOR.COM:8880/"
    )
