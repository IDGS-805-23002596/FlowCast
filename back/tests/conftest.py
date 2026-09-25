import asyncio
from collections.abc import Iterator

import pytest
from sqlalchemy import delete, select, update

from app.config import configuracion
from app.db import FabricaSesiones, motor
from app.models import CuentaProveedor
from app.services import conexion_inicial, limite_acceso


@pytest.fixture(autouse=True)
def sin_tmdb(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(configuracion, "tmdb_clave_api", None)


@pytest.fixture(autouse=True)
def sin_limite_de_acceso() -> Iterator[None]:
    limite_acceso.olvidar()
    yield
    limite_acceso.olvidar()


@pytest.fixture(autouse=True)
def sin_precalentar(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(configuracion, "precalentar_catalogo", False)


@pytest.fixture(autouse=True)
def registro_abierto(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(configuracion, "registro_abierto", True)


@pytest.fixture(autouse=True)
def sin_conexion_inicial(monkeypatch: pytest.MonkeyPatch) -> None:
    for campo in ("etiqueta", "url", "usuario", "contrasena"):
        monkeypatch.setattr(configuracion, f"conexion_inicial_{campo}", None)


def _ids_de_cuentas(solo_predeterminadas: bool = False) -> set:
    async def _leer() -> set:
        consulta = select(CuentaProveedor.id)
        if solo_predeterminadas:
            consulta = consulta.where(CuentaProveedor.predeterminada.is_(True))
        async with FabricaSesiones() as sesion_bd:
            ids = set((await sesion_bd.execute(consulta)).scalars())
        await motor.dispose()
        return ids

    return asyncio.run(_leer())


def _restaurar_predeterminadas(ids: set) -> None:
    async def _restaurar() -> None:
        async with FabricaSesiones() as sesion_bd:
            await sesion_bd.execute(
                update(CuentaProveedor)
                .where(CuentaProveedor.id.in_(ids))
                .values(predeterminada=True)
            )
            await sesion_bd.commit()
        await motor.dispose()

    asyncio.run(_restaurar())


def _borrar_cuentas(ids: set) -> None:
    async def _borrar() -> None:
        async with FabricaSesiones() as sesion_bd:
            await sesion_bd.execute(delete(CuentaProveedor).where(CuentaProveedor.id.in_(ids)))
            await sesion_bd.commit()
        await motor.dispose()

    asyncio.run(_borrar())


@pytest.fixture(autouse=True)
def cuentas_de_proveedor_aisladas(monkeypatch: pytest.MonkeyPatch) -> Iterator[None]:
    existentes = _ids_de_cuentas()
    predeterminadas = _ids_de_cuentas(solo_predeterminadas=True)

    async def solo_de_la_prueba(sesion_bd):
        consulta = select(CuentaProveedor).where(
            CuentaProveedor.predeterminada.is_(True), CuentaProveedor.id.not_in(existentes)
        )
        return (await sesion_bd.execute(consulta.limit(1))).scalar_one_or_none()

    monkeypatch.setattr(conexion_inicial, "cuenta_predeterminada", solo_de_la_prueba)
    yield
    nuevas = _ids_de_cuentas() - existentes
    if nuevas:
        _borrar_cuentas(nuevas)
    if predeterminadas - _ids_de_cuentas(solo_predeterminadas=True):
        _restaurar_predeterminadas(predeterminadas)
