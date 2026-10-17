package com.flowcast.app.data.repository

import com.flowcast.app.data.network.ApiFlowCast
import com.flowcast.app.data.network.CategoriaControl
import com.flowcast.app.data.network.EstadoControlParental
import com.flowcast.app.data.network.RespuestaBloqueoCategoria
import com.flowcast.app.data.network.SolicitudBloqueoCategoria
import com.flowcast.app.data.network.SolicitudPin
import com.flowcast.app.data.network.SolicitudQuitarPin
import com.flowcast.app.data.network.SolicitudVerificarPin
import com.flowcast.app.data.session.AlmacenSesion
import kotlinx.coroutines.flow.first

class RepositorioControlParental(private val api: ApiFlowCast, private val almacen: AlmacenSesion) {
    @Volatile private var token: String? = null
    @Volatile private var expiraEn: Long = 0L

    fun tokenVigente(): String? = token?.takeIf { System.currentTimeMillis() < expiraEn }

    fun olvidarDesbloqueo() {
        token = null
        expiraEn = 0L
    }

    suspend fun estado(): Result<EstadoControlParental> = runCatching { api.estadoControlParental() }

    suspend fun guardarPin(pinNuevo: String, pinActual: String?, contrasena: String?): Result<EstadoControlParental> =
        runCatching { api.guardarPinParental(SolicitudPin(pinNuevo, pinActual?.ifBlank { null }, contrasena?.ifBlank { null })) }

    suspend fun quitarPin(pinActual: String?, contrasena: String?): Result<EstadoControlParental> = runCatching {
        api.quitarPinParental(SolicitudQuitarPin(pinActual?.ifBlank { null }, contrasena?.ifBlank { null })).also { olvidarDesbloqueo() }
    }

    suspend fun verificar(pin: String): Result<Boolean> = runCatching {
        val respuesta = api.verificarPinParental(SolicitudVerificarPin(pin))
        if (respuesta.valido && respuesta.tokenDesbloqueo != null) {
            token = respuesta.tokenDesbloqueo
            expiraEn = System.currentTimeMillis() + (respuesta.segundosValidez - 30).coerceAtLeast(60) * 1000L
        }
        respuesta.valido
    }

    private suspend fun conexion(): String =
        almacen.conexionActivaId.first() ?: throw IllegalStateException("No hay ningún proveedor configurado")

    suspend fun categorias(): Result<List<CategoriaControl>> = runCatching { api.categoriasControlParental(conexion()) }

    suspend fun bloquear(tipo: String, categoriaId: String, bloqueada: Boolean): Result<RespuestaBloqueoCategoria> =
        runCatching { api.bloquearCategoria(SolicitudBloqueoCategoria(conexion(), tipo, categoriaId, bloqueada)) }
}
