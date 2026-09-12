import random
from typing import Annotated

from fastapi import APIRouter, Query, status
from sqlalchemy import delete, text

from app.deps import AdministradorActual, SesionBD, UsuarioActual
from app.models import EventoReproduccion, Perfil
from app.routers.catalogo import ConexionPropia
from app.schemas.eventos import (
    ConteoContenido,
    ConteoDia,
    ConteoDispositivo,
    ConteoUsuario,
    EventoRegistrado,
    ResumenReproduccion,
    SolicitudEventoReproduccion,
)

router = APIRouter(tags=["eventos"])

DIAS_GUARDADOS = 90
ZONA_HORARIA = "America/Mexico_City"


async def _perfil_propio(sesion_bd, usuario, perfil_id):
    if perfil_id is None:
        return None
    perfil = await sesion_bd.get(Perfil, perfil_id)
    return perfil.id if perfil is not None and perfil.usuario_id == usuario.id else None


@router.post(
    "/conexiones/{conexion_id}/eventos-reproduccion",
    status_code=status.HTTP_204_NO_CONTENT,
)
async def registrar_evento(
    datos: SolicitudEventoReproduccion,
    contexto: ConexionPropia,
    usuario: UsuarioActual,
    sesion_bd: SesionBD,
) -> None:
    conexion = contexto.conexion
    sesion_bd.add(
        EventoReproduccion(
            usuario_id=usuario.id,
            perfil_id=await _perfil_propio(sesion_bd, usuario, datos.perfil_id),
            conexion_id=conexion.id,
            cuenta_proveedor_id=conexion.cuenta_proveedor_id,
            resultado=datos.resultado,
            tipo_contenido=datos.tipo_contenido,
            contenido_id=datos.contenido_id,
            serie_id=datos.serie_id,
            titulo=datos.titulo,
            detalle=datos.detalle,
            dispositivo=datos.dispositivo,
            modelo=datos.modelo,
            version_app=datos.version_app,
            codigo_error=datos.codigo_error,
            mensaje=datos.mensaje,
            segundos_inicio=datos.segundos_inicio,
        )
    )
    if random.random() < 0.01:
        await sesion_bd.execute(
            delete(EventoReproduccion).where(
                EventoReproduccion.creado_en < text(f"now() - interval '{DIAS_GUARDADOS} days'")
            )
        )
    await sesion_bd.commit()


Dias = Annotated[int, Query(ge=1, le=DIAS_GUARDADOS)]


async def _filas(sesion_bd, sql: str, dias: int):
    return (await sesion_bd.execute(text(sql), {"dias": dias, "zona": ZONA_HORARIA})).all()


DESDE = "creado_en >= now() - make_interval(days => :dias)"


@router.get("/admin/api/reproduccion/resumen", response_model=ResumenReproduccion)
async def resumen_reproduccion(
    _administrador: AdministradorActual, sesion_bd: SesionBD, dias: Dias = 7
) -> ResumenReproduccion:
    totales = (
        await _filas(
            sesion_bd,
            f"""SELECT count(*) FILTER (WHERE resultado = 'inicio'),
                       count(*) FILTER (WHERE resultado = 'falla'),
                       avg(segundos_inicio) FILTER (WHERE resultado = 'inicio')
                FROM evento_reproduccion WHERE {DESDE}""",
            dias,
        )
    )[0]
    por_dispositivo = await _filas(
        sesion_bd,
        f"""SELECT dispositivo,
                   count(*) FILTER (WHERE resultado = 'inicio'),
                   count(*) FILTER (WHERE resultado = 'falla')
            FROM evento_reproduccion WHERE {DESDE}
            GROUP BY dispositivo ORDER BY 2 DESC""",
        dias,
    )
    horas = [0] * 24
    for hora, veces in await _filas(
        sesion_bd,
        f"""SELECT extract(hour FROM creado_en AT TIME ZONE :zona)::int, count(*)
            FROM evento_reproduccion WHERE {DESDE} AND resultado = 'inicio'
            GROUP BY 1""",
        dias,
    ):
        horas[hora] = veces
    por_dia = await _filas(
        sesion_bd,
        f"""SELECT to_char((creado_en AT TIME ZONE :zona)::date, 'YYYY-MM-DD'),
                   count(*) FILTER (WHERE resultado = 'inicio'),
                   count(*) FILTER (WHERE resultado = 'falla')
            FROM evento_reproduccion WHERE {DESDE}
            GROUP BY 1 ORDER BY 1""",
        dias,
    )
    mas_vistos = await _filas(
        sesion_bd,
        f"""SELECT tipo_contenido, coalesce(titulo, contenido_id), count(*)
            FROM evento_reproduccion WHERE {DESDE} AND resultado = 'inicio'
            GROUP BY 1, 2 ORDER BY 3 DESC LIMIT 15""",
        dias,
    )
    mas_fallas = await _filas(
        sesion_bd,
        f"""SELECT tipo_contenido, coalesce(titulo, contenido_id), count(*),
                   (array_agg(coalesce(mensaje, codigo_error) ORDER BY creado_en DESC))[1]
            FROM evento_reproduccion WHERE {DESDE} AND resultado = 'falla'
            GROUP BY 1, 2 ORDER BY 3 DESC LIMIT 15""",
        dias,
    )
    por_usuario = await _filas(
        sesion_bd,
        f"""SELECT coalesce(u.nombre_usuario, u.nombre),
                   count(*) FILTER (WHERE e.resultado = 'inicio'),
                   count(*) FILTER (WHERE e.resultado = 'falla'),
                   max(e.creado_en)
            FROM evento_reproduccion e JOIN usuario u ON u.id = e.usuario_id
            WHERE e.{DESDE}
            GROUP BY 1 ORDER BY 2 DESC""",
        dias,
    )
    return ResumenReproduccion(
        dias=dias,
        inicios=totales[0],
        fallas=totales[1],
        segundos_inicio_promedio=round(totales[2], 1) if totales[2] is not None else None,
        por_dispositivo=[
            ConteoDispositivo(dispositivo=d, inicios=i, fallas=f) for d, i, f in por_dispositivo
        ],
        por_hora=horas,
        por_dia=[ConteoDia(fecha=d, inicios=i, fallas=f) for d, i, f in por_dia],
        mas_vistos=[ConteoContenido(tipo_contenido=t, titulo=n, veces=v) for t, n, v in mas_vistos],
        mas_fallas=[
            ConteoContenido(tipo_contenido=t, titulo=n, veces=v, ultimo_error=e)
            for t, n, v, e in mas_fallas
        ],
        por_usuario=[
            ConteoUsuario(usuario=u, inicios=i, fallas=f, ultimo_uso_en=ultimo)
            for u, i, f, ultimo in por_usuario
        ],
    )


@router.get("/admin/api/reproduccion/fallas", response_model=list[EventoRegistrado])
async def fallas_recientes(
    _administrador: AdministradorActual,
    sesion_bd: SesionBD,
    dias: Dias = 7,
    limite: Annotated[int, Query(ge=1, le=500)] = 200,
) -> list[EventoRegistrado]:
    consulta = text(
        f"""SELECT e.id, e.resultado, e.creado_en, coalesce(u.nombre_usuario, u.nombre),
                   p.nombre, e.dispositivo, e.modelo, e.version_app, e.tipo_contenido,
                   e.contenido_id, e.titulo, e.detalle, e.codigo_error, e.mensaje, c.etiqueta
            FROM evento_reproduccion e
            JOIN usuario u ON u.id = e.usuario_id
            LEFT JOIN perfil p ON p.id = e.perfil_id
            LEFT JOIN cuenta_proveedor c ON c.id = e.cuenta_proveedor_id
            WHERE e.{DESDE} AND e.resultado = 'falla'
            ORDER BY e.creado_en DESC LIMIT :limite"""
    )
    filas = (await sesion_bd.execute(consulta, {"dias": dias, "limite": limite})).all()
    campos = list(EventoRegistrado.model_fields)
    return [EventoRegistrado(**dict(zip(campos, fila, strict=True))) for fila in filas]
