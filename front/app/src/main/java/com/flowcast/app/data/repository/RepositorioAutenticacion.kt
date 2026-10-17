package com.flowcast.app.data.repository

import android.os.Build
import com.flowcast.app.data.network.ApiFlowCast
import com.flowcast.app.data.network.RespuestaUsuario
import com.flowcast.app.data.network.SolicitudInicioSesion
import com.flowcast.app.data.network.SolicitudRefresco
import com.flowcast.app.data.network.SolicitudRegistro
import com.flowcast.app.data.session.AlmacenSesion
import kotlinx.coroutines.flow.first

class RepositorioAutenticacion(private val api: ApiFlowCast, private val almacen: AlmacenSesion) {

    private val nombreDispositivo = "${Build.MANUFACTURER} ${Build.MODEL}"

    suspend fun iniciarSesion(correo: String, contrasena: String): Result<RespuestaUsuario> = runCatching {
        val tokens = api.iniciarSesion(SolicitudInicioSesion(correo.trim(), contrasena, nombreDispositivo))
        almacen.guardarSesion(tokens)
        tokens.usuario
    }

    suspend fun registrar(nombre: String, correo: String, contrasena: String): Result<RespuestaUsuario> = runCatching {
        val tokens = api.registrar(SolicitudRegistro(correo.trim(), contrasena, nombre.trim(), nombreDispositivo))
        almacen.guardarSesion(tokens)
        tokens.usuario
    }

    suspend fun usuarioActual(): Result<RespuestaUsuario> = runCatching { api.yo() }

    suspend fun cerrarSesion() {
        val refresco = almacen.tokenRefresco.first()
        if (refresco != null) runCatching { api.cerrarSesion(SolicitudRefresco(refresco)) }
        almacen.limpiar()
    }
}
