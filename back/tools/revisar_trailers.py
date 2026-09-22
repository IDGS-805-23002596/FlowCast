import asyncio
import csv
import sys
import time
from pathlib import Path

import httpx2 as httpx

from app.services import titulos, tmdb, xtream, youtube
from tools.sincronizar_plataformas import credenciales_del_env

CONSULTAS_TMDB_A_LA_VEZ = 20
PAUSA_PROVEEDOR_SEGUNDOS = 0.3
ARCHIVO = Path.home() / "Desktop" / "FlowCast sin tráiler.csv"


def clave_tmdb(tipo: str, nombre: str, tmdb_id: str | None) -> tuple[str, ...]:
    if tmdb_id:
        return (tipo, "id", tmdb_id)
    titulo = titulos.analizar(nombre)
    return (tipo, "nombre", titulo.titulo, str(titulo.anio or ""))


async def trailer_de_tmdb(clave: tuple[str, ...], cliente: httpx.AsyncClient) -> str | None:
    tipo = clave[0]
    if clave[1] == "id":
        tmdb_id = clave[2]
    else:
        tmdb_id = await tmdb.buscar_id(tipo, clave[2], int(clave[3]) if clave[3] else None, cliente)
    if not tmdb_id:
        return None
    encontrados = await tmdb.trailers(tipo, tmdb_id, cliente)
    return encontrados[0].video_id if encontrados else None


async def buscar_en_tmdb(claves: set[tuple[str, ...]]) -> dict[tuple[str, ...], str | None]:
    resultados: dict[tuple[str, ...], str | None] = {}
    turno = asyncio.Semaphore(CONSULTAS_TMDB_A_LA_VEZ)
    async with httpx.AsyncClient(timeout=tmdb.TIEMPO_ESPERA_SEGUNDOS) as cliente:

        async def una(clave: tuple[str, ...]) -> None:
            async with turno:
                resultados[clave] = await trailer_de_tmdb(clave, cliente)
                if len(resultados) % 1000 == 0:
                    print(f"  TMDB: {len(resultados)} de {len(claves)}", flush=True)

        await asyncio.gather(*(una(clave) for clave in claves))
    return resultados


async def principal() -> int:
    if not tmdb.disponible():
        print("Falta TMDB_CLAVE_API en el .env")
        return 1
    inicio = time.monotonic()
    credenciales = credenciales_del_env()
    async with httpx.AsyncClient(
        timeout=xtream.TIEMPO_ESPERA_SEGUNDOS * 4, headers=xtream.CABECERAS, follow_redirects=True
    ) as proveedor:
        peliculas = await xtream.obtener_peliculas(credenciales, None, cliente=proveedor)
        series = await xtream._llamar_catalogo(credenciales, "get_series", cliente=proveedor)
        elementos = [("pelicula", p.id, p.nombre, p.tmdb_id, None) for p in peliculas]
        for serie in series:
            elegida = xtream._serie_desde(serie, serie.get("series_id"))
            propio = youtube.id_de_video(serie.get("youtube_trailer"))
            elementos.append(("serie", elegida.id, elegida.nombre, elegida.tmdb_id, propio))
        print(f"Catálogo: {len(peliculas)} películas y {len(series)} series", flush=True)

        claves = {clave_tmdb(tipo, nombre, tmdb_id) for tipo, _, nombre, tmdb_id, _ in elementos}
        print(f"Buscando tráileres en TMDB para {len(claves)} títulos distintos", flush=True)
        de_tmdb = await buscar_en_tmdb(claves)

        pendientes = [
            e for e in elementos if e[0] == "pelicula" and not de_tmdb[clave_tmdb(e[0], e[2], e[3])]
        ]
        print(
            f"Revisando en el proveedor {len(pendientes)} películas sin tráiler en TMDB", flush=True
        )
        del_proveedor: dict[str, str | None] = {}
        for numero, (_, pelicula_id, _, _, _) in enumerate(pendientes, 1):
            try:
                ficha = await xtream.obtener_ficha_pelicula(
                    credenciales, pelicula_id, cliente=proveedor
                )
                del_proveedor[pelicula_id] = youtube.id_de_video(ficha.trailer_youtube)
            except (xtream.ErrorXtream, httpx.HTTPError):
                del_proveedor[pelicula_id] = None
            if numero % 500 == 0:
                print(f"  Proveedor: {numero} de {len(pendientes)}", flush=True)
            await asyncio.sleep(PAUSA_PROVEEDOR_SEGUNDOS)

    conteo = {"tmdb": 0, "proveedor": 0, "ninguno": 0}
    sin_trailer = []
    for tipo, elemento_id, nombre, tmdb_id, propio in elementos:
        if tipo == "pelicula":
            propio = del_proveedor.get(elemento_id)
        if de_tmdb[clave_tmdb(tipo, nombre, tmdb_id)]:
            conteo["tmdb"] += 1
        elif propio:
            conteo["proveedor"] += 1
        else:
            conteo["ninguno"] += 1
            sin_trailer.append(("Película" if tipo == "pelicula" else "Serie", nombre, elemento_id))

    sin_trailer.sort()
    with ARCHIVO.open("w", newline="", encoding="utf-8-sig") as archivo:
        escritor = csv.writer(archivo)
        escritor.writerow(["Tipo", "Nombre", "ID en el proveedor"])
        escritor.writerows(sin_trailer)
    total = len(elementos)
    print(f"\nTotal: {total} títulos")
    print(f"  Con tráiler en TMDB: {conteo['tmdb']}")
    print(f"  Solo con el tráiler del proveedor: {conteo['proveedor']}")
    print(f"  Sin tráiler en ninguna fuente: {conteo['ninguno']}")
    print(f"Lista de los que no tienen: {ARCHIVO}")
    print(f"Tardó {int(time.monotonic() - inicio) // 60} min")
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(principal()))
