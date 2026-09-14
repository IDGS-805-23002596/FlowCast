import uuid
from datetime import UTC, datetime
from typing import Annotated

import httpx2 as httpx
from fastapi import APIRouter, HTTPException, Query, status
from sqlalchemy import select

from app.deps import SesionBD, UsuarioActual
from app.models import EquipoFavorito, Perfil
from app.routers.catalogo import (
    SEGUNDOS_LISTAS,
    ConexionPropia,
    Control,
    _categorias_bloqueadas,
    _consultar,
    _esta_bloqueado,
    _lista_completa,
)
from app.routers.conexiones import ClienteHTTP
from app.routers.perfiles import PerfilPropio
from app.schemas import catalogo as esquemas
from app.schemas.deportes import (
    Deporte,
    EquipoFavoritoRespuesta,
    EquipoLiga,
    EquipoPartido,
    LigaDeporte,
    Partido,
    RespuestaDeportes,
)
from app.services import cache, deportes, xtream

router = APIRouter(tags=["deportes"])

SEGUNDOS_CRUCE = 300


def _catalogo() -> list[Deporte]:
    resultado = []
    for clave, nombre in deportes.DEPORTES.items():
        ligas = [
            LigaDeporte(clave=liga.clave, nombre=liga.nombre)
            for liga in deportes.LIGAS
            if liga.deporte == clave
        ]
        if ligas:
            resultado.append(Deporte(clave=clave, nombre=nombre, ligas=ligas))
    return resultado


def _liga(clave: str) -> deportes.Liga:
    liga = deportes.LIGAS_POR_CLAVE.get(clave)
    if liga is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Liga no encontrada")
    return liga


async def _equipos(liga: deportes.Liga, cliente: httpx.AsyncClient) -> list[deportes.Equipo]:
    try:
        return await deportes.equipos_de_liga(liga, cliente)
    except (httpx.HTTPError, ValueError, KeyError, IndexError) as error:
        raise HTTPException(
            status.HTTP_502_BAD_GATEWAY, detail="No se pudo consultar la lista de equipos"
        ) from error


async def _perfil_del_usuario(
    sesion_bd: SesionBD, usuario: UsuarioActual, perfil_id: uuid.UUID | None
) -> Perfil | None:
    if perfil_id is None:
        return None
    perfil = await sesion_bd.get(Perfil, perfil_id)
    return perfil if perfil is not None and perfil.usuario_id == usuario.id else None


async def _favoritos(sesion_bd: SesionBD, perfil: Perfil | None) -> list[EquipoFavorito]:
    if perfil is None:
        return []
    consulta = (
        select(EquipoFavorito)
        .where(EquipoFavorito.perfil_id == perfil.id)
        .order_by(EquipoFavorito.creado_en)
    )
    return list((await sesion_bd.execute(consulta)).scalars())


def _respuesta_favorito(favorito: EquipoFavorito) -> EquipoFavoritoRespuesta:
    liga = deportes.LIGAS_POR_CLAVE.get(favorito.liga)
    return EquipoFavoritoRespuesta(
        liga=favorito.liga,
        liga_nombre=liga.nombre if liga else favorito.liga,
        deporte=favorito.deporte,
        equipo_id=favorito.equipo_id,
        nombre=favorito.nombre,
        logo=favorito.logo,
    )


@router.get("/deportes", response_model=list[Deporte])
async def listar_deportes(_usuario: UsuarioActual) -> list[Deporte]:
    return _catalogo()


@router.get("/deportes/ligas/{liga}/equipos", response_model=list[EquipoLiga])
async def equipos_de_liga(
    liga: str,
    usuario: UsuarioActual,
    sesion_bd: SesionBD,
    cliente: ClienteHTTP,
    perfil_id: uuid.UUID | None = None,
) -> list[EquipoLiga]:
    datos = _liga(liga)
    equipos = await _equipos(datos, cliente)
    perfil = await _perfil_del_usuario(sesion_bd, usuario, perfil_id)
    marcados = {f.equipo_id for f in await _favoritos(sesion_bd, perfil) if f.liga == liga}
    return [
        EquipoLiga(id=e.id, nombre=e.nombre, logo=e.logo, favorito=e.id in marcados)
        for e in equipos
    ]


@router.get("/perfiles/{perfil_id}/equipos", response_model=list[EquipoFavoritoRespuesta])
async def mis_equipos(perfil: PerfilPropio, sesion_bd: SesionBD) -> list[EquipoFavoritoRespuesta]:
    return [_respuesta_favorito(f) for f in await _favoritos(sesion_bd, perfil)]


@router.put(
    "/perfiles/{perfil_id}/equipos/{liga}/{equipo_id}",
    response_model=EquipoFavoritoRespuesta,
)
async def agregar_equipo(
    liga: str,
    equipo_id: str,
    perfil: PerfilPropio,
    sesion_bd: SesionBD,
    cliente: ClienteHTTP,
) -> EquipoFavoritoRespuesta:
    datos = _liga(liga)
    equipo = next((e for e in await _equipos(datos, cliente) if e.id == equipo_id), None)
    if equipo is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Equipo no encontrado")
    favorito = await sesion_bd.get(EquipoFavorito, (perfil.id, liga, equipo_id))
    if favorito is None:
        favorito = EquipoFavorito(perfil_id=perfil.id, liga=liga, equipo_id=equipo_id)
        sesion_bd.add(favorito)
    favorito.deporte = datos.deporte
    favorito.nombre = equipo.nombre
    favorito.logo = equipo.logo
    await sesion_bd.commit()
    return _respuesta_favorito(favorito)


@router.delete(
    "/perfiles/{perfil_id}/equipos/{liga}/{equipo_id}", status_code=status.HTTP_204_NO_CONTENT
)
async def quitar_equipo(
    liga: str, equipo_id: str, perfil: PerfilPropio, sesion_bd: SesionBD
) -> None:
    favorito = await sesion_bd.get(EquipoFavorito, (perfil.id, liga, equipo_id))
    if favorito is not None:
        await sesion_bd.delete(favorito)
        await sesion_bd.commit()


@router.get("/conexiones/{conexion_id}/deportes", response_model=RespuestaDeportes)
async def partidos_de_hoy(
    contexto: ConexionPropia,
    usuario: UsuarioActual,
    control: Control,
    sesion_bd: SesionBD,
    cliente: ClienteHTTP,
    perfil_id: Annotated[uuid.UUID | None, Query()] = None,
) -> RespuestaDeportes:
    ahora = datetime.now(UTC)

    async def calcular() -> list[deportes.Partido]:
        canales, categorias, eventos = await _datos_de_partidos(contexto, cliente, ahora)
        nombres = {c.id: c.nombre for c in categorias}
        hoy = ahora.astimezone(deportes.ZONA).date()
        armados = deportes.armar_partidos(
            eventos, deportes.canales_con_partido(canales, nombres, hoy)
        )
        deportes.completar_logos(armados, await deportes.indice_de_logos(cliente))
        return armados

    cruzados = await cache.obtener_o_calcular(contexto.clave("deportes"), calcular, SEGUNDOS_CRUCE)
    try:
        partidos = deportes.con_marcadores(cruzados, await deportes.eventos_de_hoy(cliente, ahora))
    except (httpx.HTTPError, ValueError, KeyError, IndexError):
        partidos = cruzados
    bloqueadas = await _categorias_bloqueadas(contexto, cliente, control.bloqueos, "en_vivo")
    perfil = await _perfil_del_usuario(sesion_bd, usuario, perfil_id)
    favoritos = {(f.liga, f.equipo_id) for f in await _favoritos(sesion_bd, perfil)}
    visibles = deportes.para_perfil(
        partidos,
        favoritos,
        lambda canal: control.desbloqueado or not _esta_bloqueado(canal, bloqueadas),
    )
    return RespuestaDeportes(
        actualizado_en=ahora,
        deportes=_catalogo(),
        mis_equipos=len(favoritos),
        partidos=[_partido(p) for p in visibles],
    )


async def _datos_de_partidos(contexto, cliente, ahora):
    canales = await _lista_completa(contexto, cliente, "en_vivo")
    categorias = await _consultar(
        contexto.clave("categorias", "en_vivo"),
        SEGUNDOS_LISTAS,
        lambda: xtream.obtener_categorias(contexto.credenciales, "en_vivo", cliente=cliente),
    )
    eventos = await deportes.eventos_de_hoy(cliente, ahora)
    return canales, categorias, eventos


def _equipo(equipo: deportes.Equipo) -> EquipoPartido:
    return EquipoPartido(nombre=equipo.nombre, logo=equipo.logo, marcador=equipo.marcador)


def _partido(partido: deportes.Partido) -> Partido:
    return Partido(
        id=partido.id,
        deporte=partido.deporte,
        liga=partido.liga,
        liga_clave=partido.liga_clave,
        estado=partido.estado,
        detalle=partido.detalle,
        inicio=partido.inicio,
        local=_equipo(partido.local),
        visitante=_equipo(partido.visitante),
        favorito=partido.favorito,
        canales=[esquemas.Canal.model_validate(c) for c in partido.canales],
    )
