package com.flowcast.app.data.repository

import com.flowcast.app.data.network.ApiFlowCast
import com.flowcast.app.data.network.RespuestaConexion
import com.flowcast.app.data.session.AlmacenSesion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class RepositorioConexiones(private val api: ApiFlowCast, private val almacen: AlmacenSesion) {

    val conexionActivaId: Flow<String?> = almacen.conexionActivaId

    suspend fun listar(): Result<List<RespuestaConexion>> = runCatching { api.listarConexiones() }

    suspend fun seleccionar(conexionId: String) = almacen.guardarConexionActiva(conexionId)

    suspend fun asegurarConexionActiva(): Result<RespuestaConexion?> = runCatching {
        val conexiones = api.listarConexiones()
        val guardadaId = almacen.conexionActivaId.first()
        val elegida = conexiones.firstOrNull { it.id == guardadaId } ?: conexiones.firstOrNull()
        if (elegida != null) almacen.guardarConexionActiva(elegida.id)
        elegida
    }
}
