import asyncio
from urllib.parse import urlsplit

from sqlalchemy import select, text

from app import cifrado
from app.config import configuracion
from app.db import FabricaSesiones, motor
from app.models import ConexionXtream, CuentaProveedor
from app.services import xtream

DDL = [
    """CREATE TABLE IF NOT EXISTS cuenta_proveedor (
        id                     uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
        etiqueta               varchar(120) NOT NULL,
        url_base_cifrada       bytea        NOT NULL,
        nombre_usuario_cifrado bytea        NOT NULL,
        contrasena_cifrada     bytea        NOT NULL,
        huella                 text         NOT NULL UNIQUE,
        notas                  text,
        predeterminada         boolean      NOT NULL DEFAULT false,
        estado_cuenta          varchar(20)  NOT NULL DEFAULT 'desconocido',
        expira_en              timestamptz,
        max_conexiones         integer,
        conexiones_activas     integer,
        ultima_revision_en     timestamptz,
        creado_en              timestamptz  NOT NULL DEFAULT now()
    )""",
    """ALTER TABLE conexion_xtream ADD COLUMN IF NOT EXISTS cuenta_proveedor_id uuid
        REFERENCES cuenta_proveedor(id) ON DELETE SET NULL""",
    """CREATE TABLE IF NOT EXISTS trailer_tmdb (
        tipo_contenido varchar(10) NOT NULL CHECK (tipo_contenido IN ('pelicula', 'serie')),
        referencia     text        NOT NULL,
        video_id       text,
        idioma         text,
        consultado_en  timestamptz NOT NULL DEFAULT now(),
        PRIMARY KEY (tipo_contenido, referencia)
    )""",
    """CREATE TABLE IF NOT EXISTS video_no_incrustable (
        video_id      text        PRIMARY KEY,
        codigo        text,
        registrado_en timestamptz NOT NULL DEFAULT now()
    )""",
    """CREATE TABLE IF NOT EXISTS subtitulo_externo (
        tipo_contenido varchar(10) NOT NULL CHECK (tipo_contenido IN ('pelicula', 'episodio')),
        referencia     text        NOT NULL,
        idioma         varchar(10) NOT NULL,
        archivo_id     text,
        lineas         jsonb,
        consultado_en  timestamptz NOT NULL DEFAULT now(),
        PRIMARY KEY (tipo_contenido, referencia, idioma)
    )""",
    """CREATE TABLE IF NOT EXISTS intro_serie (
        huella         text        NOT NULL,
        serie_id       text        NOT NULL,
        inicio         real        NOT NULL,
        fin            real        NOT NULL,
        actualizado_en timestamptz NOT NULL DEFAULT now(),
        PRIMARY KEY (huella, serie_id)
    )""",
    "DELETE FROM trailer_tmdb WHERE consultado_en < '2026-11-03 06:00:00+00'",
    """CREATE TABLE IF NOT EXISTS evento_reproduccion (
        id                  bigserial    PRIMARY KEY,
        usuario_id          uuid         NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
        perfil_id           uuid         REFERENCES perfil(id) ON DELETE SET NULL,
        conexion_id         uuid         REFERENCES conexion_xtream(id) ON DELETE SET NULL,
        cuenta_proveedor_id uuid         REFERENCES cuenta_proveedor(id) ON DELETE SET NULL,
        resultado           varchar(12)  NOT NULL CHECK (resultado IN ('inicio', 'falla')),
        tipo_contenido      varchar(10)  NOT NULL CHECK (tipo_contenido IN ('en_vivo', 'pelicula', 'episodio')),
        contenido_id        text         NOT NULL,
        serie_id            text,
        titulo              text,
        detalle             text,
        dispositivo         varchar(20)  NOT NULL,
        modelo              text,
        version_app         varchar(40),
        codigo_error        varchar(60),
        mensaje             text,
        segundos_inicio     real,
        creado_en           timestamptz  NOT NULL DEFAULT now()
    )""",
    """CREATE INDEX IF NOT EXISTS evento_reproduccion_creado
        ON evento_reproduccion (creado_en)""",
    """CREATE TABLE IF NOT EXISTS equipo_favorito (
        perfil_id  uuid        NOT NULL REFERENCES perfil(id) ON DELETE CASCADE,
        liga       varchar(40) NOT NULL,
        equipo_id  varchar(20) NOT NULL,
        deporte    varchar(20) NOT NULL,
        nombre     text        NOT NULL,
        logo       text,
        creado_en  timestamptz NOT NULL DEFAULT now(),
        PRIMARY KEY (perfil_id, liga, equipo_id)
    )""",
]


async def migrar() -> None:
    async with motor.begin() as conexion_bd:
        for sentencia in DDL:
            await conexion_bd.execute(text(sentencia))
    huella_predeterminada = None
    if configuracion.conexion_inicial_url and configuracion.conexion_inicial_usuario:
        huella_predeterminada = cifrado.calcular_huella(
            xtream.normalizar_url_base(configuracion.conexion_inicial_url),
            configuracion.conexion_inicial_usuario,
        )
    async with FabricaSesiones() as sesion_bd:
        cuentas = {
            c.huella: c for c in (await sesion_bd.execute(select(CuentaProveedor))).scalars()
        }
        conexiones = (await sesion_bd.execute(select(ConexionXtream))).scalars().all()
        creadas = ligadas = 0
        for conexion in conexiones:
            cuenta = cuentas.get(conexion.huella)
            if cuenta is None:
                es_predeterminada = conexion.huella == huella_predeterminada
                cuenta = CuentaProveedor(
                    etiqueta=(
                        configuracion.conexion_inicial_etiqueta if es_predeterminada else None
                    )
                    or conexion.etiqueta
                    or urlsplit(conexion.url_base).hostname
                    or "Proveedor",
                    url_base_cifrada=conexion.url_base_cifrada,
                    nombre_usuario_cifrado=conexion.nombre_usuario_cifrado,
                    contrasena_cifrada=conexion.contrasena_cifrada,
                    huella=conexion.huella,
                    predeterminada=es_predeterminada,
                    estado_cuenta=conexion.estado_cuenta,
                    expira_en=conexion.expira_en,
                    max_conexiones=conexion.max_conexiones,
                )
                sesion_bd.add(cuenta)
                await sesion_bd.flush()
                cuentas[cuenta.huella] = cuenta
                creadas += 1
            if conexion.cuenta_proveedor_id != cuenta.id:
                conexion.cuenta_proveedor_id = cuenta.id
                ligadas += 1
        await sesion_bd.commit()
        print(f"Cuentas de proveedor creadas: {creadas}; conexiones ligadas: {ligadas}")
        for cuenta in cuentas.values():
            print(
                f" - {cuenta.etiqueta} ({urlsplit(cuenta.url_base).hostname})"
                f"{' · predeterminada' if cuenta.predeterminada else ''}"
            )
    await motor.dispose()


if __name__ == "__main__":
    asyncio.run(migrar())
