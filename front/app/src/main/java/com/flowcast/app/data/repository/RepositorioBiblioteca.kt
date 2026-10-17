package com.flowcast.app.data.repository

import android.os.Build
import com.flowcast.app.BuildConfig
import com.flowcast.app.data.network.ApiFlowCast
import com.flowcast.app.data.network.SolicitudEventoReproduccion
import com.flowcast.app.data.network.RespuestaFavorito
import com.flowcast.app.data.network.RespuestaHistorial
import com.flowcast.app.data.network.SolicitudFavorito
import com.flowcast.app.data.network.RespuestaProgreso
import com.flowcast.app.data.network.RespuestaResena
import com.flowcast.app.data.network.ResumenResenas
import com.flowcast.app.data.network.SolicitudResena
import com.flowcast.app.data.network.SolicitudHistorial
import com.flowcast.app.data.network.SolicitudProgreso
import com.flowcast.app.data.session.AlmacenSesion
import kotlinx.coroutines.flow.first

class RepositorioBiblioteca(private val api: ApiFlowCast, private val almacen: AlmacenSesion) {

    private suspend fun perfilId(): String =
        almacen.perfilActivo.first()?.id ?: throw IllegalStateException("No hay perfil activo")

    private suspend fun conexionId(): String =
        almacen.conexionActivaId.first() ?: throw IllegalStateException("No hay proveedor activo")

    suspend fun reportarEventoReproduccion(
        resultado: String,
        tipo: String,
        contenidoId: String,
        serieId: String?,
        titulo: String,
        codigoError: String? = null,
        mensaje: String? = null,
        segundosInicio: Double? = null,
    ): Result<Unit> = runCatching {
        api.registrarEventoReproduccion(
            conexionId(),
            SolicitudEventoReproduccion(
                resultado = resultado,
                tipoContenido = tipo,
                contenidoId = contenidoId,
                serieId = serieId,
                perfilId = almacen.perfilActivo.first()?.id,
                titulo = titulo.take(300),
                dispositivo = "android",
                modelo = "${Build.MANUFACTURER} ${Build.MODEL}".take(120),
                versionApp = BuildConfig.VERSION_NAME,
                codigoError = codigoError?.take(60),
                mensaje = mensaje?.take(500),
                segundosInicio = segundosInicio,
            ),
        )
        Unit
    }

    suspend fun registrarReproduccion(tipo: String, streamId: String, titulo: String, portada: String?): Result<Unit> = runCatching {
        api.registrarReproduccion(perfilId(), SolicitudHistorial(conexionId(), tipo, streamId, titulo, portada))
        Unit
    }

    suspend fun obtenerProgreso(tipo: String, streamId: String): RespuestaProgreso? =
        runCatching { api.verProgreso(perfilId(), conexionId(), tipo, streamId) }.getOrNull()

    suspend fun guardarProgreso(
        tipo: String,
        streamId: String,
        serieId: String?,
        titulo: String,
        portada: String?,
        posicionSegundos: Double,
        duracionSegundos: Double?,
    ): Result<RespuestaProgreso> = runCatching {
        api.guardarProgreso(
            perfilId(),
            SolicitudProgreso(conexionId(), tipo, streamId, serieId, titulo, portada, posicionSegundos, duracionSegundos),
        )
    }

    suspend fun favoritos(tipo: String? = null): Result<List<RespuestaFavorito>> =
        runCatching { api.listarFavoritos(perfilId(), conexionId(), tipo) }

    suspend fun esFavorito(tipo: String, streamId: String): Boolean =
        favoritos(tipo).getOrDefault(emptyList()).any { it.streamId == streamId }

    suspend fun marcarFavorito(tipo: String, streamId: String, titulo: String, portada: String?): Result<Unit> = runCatching {
        api.marcarFavorito(perfilId(), SolicitudFavorito(conexionId(), tipo, streamId, titulo, portada))
        Unit
    }

    suspend fun quitarFavorito(tipo: String, streamId: String): Result<Unit> = runCatching {
        api.quitarFavorito(perfilId(), conexionId(), tipo, streamId)
        Unit
    }

    suspend fun continuarViendo(): Result<List<RespuestaProgreso>> = runCatching { api.listarContinuarViendo(perfilId(), conexionId()) }

    suspend fun borrarProgreso(tipo: String, streamId: String): Result<Unit> = runCatching {
        api.borrarProgreso(perfilId(), conexionId(), tipo, streamId)
        Unit
    }

    suspend fun historial(): Result<List<RespuestaHistorial>> = runCatching { api.listarHistorial(perfilId(), conexionId()) }

    suspend fun borrarEntradaHistorial(entradaId: Long): Result<Unit> = runCatching {
        api.borrarEntradaHistorial(perfilId(), entradaId)
        Unit
    }

    suspend fun vaciarHistorial(): Result<Unit> = runCatching {
        api.vaciarHistorial(perfilId())
        Unit
    }

    suspend fun resenas(tipo: String, streamId: String): Result<ResumenResenas> =
        runCatching { api.verResenas(perfilId(), conexionId(), tipo, streamId) }

    suspend fun guardarResena(tipo: String, streamId: String, titulo: String, calificacion: Int, texto: String?): Result<RespuestaResena> =
        runCatching { api.guardarResena(perfilId(), SolicitudResena(conexionId(), tipo, streamId, titulo, calificacion, texto?.takeIf { it.isNotBlank() })) }

    suspend fun borrarResena(tipo: String, streamId: String): Result<Unit> = runCatching {
        api.borrarResena(perfilId(), conexionId(), tipo, streamId)
        Unit
    }
}
