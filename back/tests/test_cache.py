import asyncio

from app.services import cache


def prueba_con_respaldo_entrega_lo_viejo_y_renueva_por_detras() -> None:
    llamadas = []

    async def fabrica() -> int:
        llamadas.append(1)
        await asyncio.sleep(0.05)
        return len(llamadas)

    async def escenario() -> list:
        cache.vaciar()
        primero = await cache.obtener_con_respaldo("prueba:lista", fabrica, 0)
        segundo = await cache.obtener_con_respaldo("prueba:lista", fabrica, 0)
        await asyncio.sleep(0.1)
        tercero = await cache.obtener_con_respaldo("prueba:lista", fabrica, 0)
        cache.vaciar()
        return [primero, segundo, tercero]

    assert asyncio.run(escenario()) == [1, 1, 2]


def prueba_si_la_renovacion_falla_sigue_el_respaldo() -> None:
    estado = {"fallar": False}

    async def fabrica() -> str:
        if estado["fallar"]:
            raise RuntimeError("proveedor caído")
        return "datos"

    async def escenario() -> list:
        cache.vaciar()
        primero = await cache.obtener_con_respaldo("prueba:caido", fabrica, 0)
        estado["fallar"] = True
        segundo = await cache.obtener_con_respaldo("prueba:caido", fabrica, 0)
        await asyncio.sleep(0.05)
        tercero = await cache.obtener_con_respaldo("prueba:caido", fabrica, 0)
        cache.vaciar()
        return [primero, segundo, tercero]

    assert asyncio.run(escenario()) == ["datos", "datos", "datos"]
