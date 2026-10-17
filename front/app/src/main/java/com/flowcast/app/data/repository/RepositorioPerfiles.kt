package com.flowcast.app.data.repository

import com.flowcast.app.data.network.ApiFlowCast
import com.flowcast.app.data.network.RespuestaPerfil
import com.flowcast.app.data.network.SolicitudActualizarPerfil
import com.flowcast.app.data.network.SolicitudCrearPerfil
import com.flowcast.app.data.session.AlmacenSesion
import com.flowcast.app.data.session.PerfilGuardado
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class RepositorioPerfiles(private val api: ApiFlowCast, private val almacen: AlmacenSesion) {

    val perfilActivo: Flow<PerfilGuardado?> = almacen.perfilActivo

    suspend fun listar(): Result<List<RespuestaPerfil>> = runCatching { api.listarPerfiles() }

    suspend fun crear(nombre: String): Result<RespuestaPerfil> = runCatching {
        api.crearPerfil(SolicitudCrearPerfil(nombre.trim()))
    }

    suspend fun seleccionar(perfil: RespuestaPerfil) = almacen.guardarPerfil(perfil)

    suspend fun actualizar(perfilId: String, nombre: String, avatar: String): Result<RespuestaPerfil> = runCatching {
        api.actualizarPerfil(perfilId, SolicitudActualizarPerfil(nombre = nombre, avatar = avatar)).also { sincronizarActivo(it) }
    }

    suspend fun subirImagen(perfilId: String, jpeg: ByteArray): Result<RespuestaPerfil> = runCatching {
        val parte = MultipartBody.Part.createFormData("archivo", "perfil.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
        api.subirImagenPerfil(perfilId, parte).also { sincronizarActivo(it) }
    }

    suspend fun quitarImagen(perfilId: String): Result<RespuestaPerfil> = runCatching {
        api.quitarImagenPerfil(perfilId).also { sincronizarActivo(it) }
    }

    suspend fun borrar(perfilId: String): Result<Unit> = runCatching {
        val respuesta = api.borrarPerfil(perfilId)
        if (!respuesta.isSuccessful) throw retrofit2.HttpException(respuesta)
        if (almacen.perfilActivo.first()?.id == perfilId) almacen.limpiarPerfil()
    }

    private suspend fun sincronizarActivo(perfil: RespuestaPerfil) {
        if (almacen.perfilActivo.first()?.id == perfil.id) almacen.guardarPerfil(perfil)
    }
}
