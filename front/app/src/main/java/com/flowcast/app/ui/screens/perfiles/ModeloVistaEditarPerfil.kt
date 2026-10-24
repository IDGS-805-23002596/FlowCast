package com.flowcast.app.ui.screens.perfiles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.flowcast.app.data.network.RespuestaPerfil
import com.flowcast.app.data.network.mensajeDeError
import com.flowcast.app.data.repository.RepositorioPerfiles
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EstadoEditarPerfil(
    val cargando: Boolean = true,
    val error: String? = null,
    val perfil: RespuestaPerfil? = null,
    val nombre: String = "",
    val avatar: String = "01",
    val guardando: Boolean = false,
    val subiendoImagen: Boolean = false,
    val confirmarBorrado: Boolean = false,
    val terminado: Boolean = false,
    val esUnico: Boolean = false,
) {
    val hayCambios: Boolean get() = perfil != null && (nombre.trim() != perfil.nombre || avatar != perfil.avatar)
}

class ModeloVistaEditarPerfil(private val perfilId: String, private val repositorio: RepositorioPerfiles) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoEditarPerfil())
    val estado: StateFlow<EstadoEditarPerfil> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            repositorio.listar()
                .onSuccess { lista ->
                    val perfil = lista.firstOrNull { it.id == perfilId }
                    if (perfil == null) {
                        _estado.update { it.copy(cargando = false, error = "El perfil ya no existe") }
                    } else {
                        _estado.update { it.copy(cargando = false, perfil = perfil, nombre = perfil.nombre, avatar = perfil.avatar, esUnico = lista.size <= 1) }
                    }
                }
                .onFailure { fallo -> _estado.update { it.copy(cargando = false, error = mensajeDeError(fallo)) } }
        }
    }

    fun cambiarNombre(valor: String) = _estado.update { it.copy(nombre = valor, error = null) }
    fun elegirAvatar(codigo: String) = _estado.update { it.copy(avatar = codigo) }
    fun pedirConfirmacionBorrado(valor: Boolean) = _estado.update { it.copy(confirmarBorrado = valor) }

    fun guardar() {
        val actual = _estado.value
        val nombre = actual.nombre.trim()
        if (nombre.length < 2) {
            _estado.update { it.copy(error = "El nombre necesita al menos 2 letras") }
            return
        }
        _estado.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            repositorio.actualizar(perfilId, nombre, actual.avatar)
                .onSuccess { nuevo -> _estado.update { it.copy(guardando = false, perfil = nuevo, nombre = nuevo.nombre, avatar = nuevo.avatar, terminado = true) } }
                .onFailure { fallo -> _estado.update { it.copy(guardando = false, error = mensajeDeError(fallo)) } }
        }
    }

    fun subirImagen(contexto: Context, uri: Uri) {
        _estado.update { it.copy(subiendoImagen = true, error = null) }
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { prepararImagen(contexto, uri) }.getOrNull() }
            if (bytes == null) {
                _estado.update { it.copy(subiendoImagen = false, error = "No se pudo leer la imagen") }
                return@launch
            }
            repositorio.subirImagen(perfilId, bytes)
                .onSuccess { nuevo -> _estado.update { it.copy(subiendoImagen = false, perfil = nuevo) } }
                .onFailure { fallo -> _estado.update { it.copy(subiendoImagen = false, error = mensajeDeError(fallo)) } }
        }
    }

    fun quitarImagen() {
        _estado.update { it.copy(subiendoImagen = true, error = null) }
        viewModelScope.launch {
            repositorio.quitarImagen(perfilId)
                .onSuccess { nuevo -> _estado.update { it.copy(subiendoImagen = false, perfil = nuevo) } }
                .onFailure { fallo -> _estado.update { it.copy(subiendoImagen = false, error = mensajeDeError(fallo)) } }
        }
    }

    fun borrar() {
        _estado.update { it.copy(confirmarBorrado = false, guardando = true, error = null) }
        viewModelScope.launch {
            repositorio.borrar(perfilId)
                .onSuccess { _estado.update { it.copy(guardando = false, terminado = true) } }
                .onFailure { fallo -> _estado.update { it.copy(guardando = false, error = mensajeDeError(fallo)) } }
        }
    }

    private fun prepararImagen(contexto: Context, uri: Uri): ByteArray {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contexto.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, limites) }
        var muestra = 1
        while (limites.outWidth / muestra > LADO * 2 && limites.outHeight / muestra > LADO * 2) muestra *= 2
        val opciones = BitmapFactory.Options().apply { inSampleSize = muestra }
        val original = contexto.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opciones) }!!
        val lado = minOf(original.width, original.height)
        val cuadrado = Bitmap.createBitmap(original, (original.width - lado) / 2, (original.height - lado) / 2, lado, lado)
        val escalado = Bitmap.createScaledBitmap(cuadrado, LADO, LADO, true)
        return ByteArrayOutputStream().use { salida ->
            escalado.compress(Bitmap.CompressFormat.JPEG, 88, salida)
            salida.toByteArray()
        }
    }

    companion object {
        private const val LADO = 512

        fun fabrica(perfilId: String, repositorio: RepositorioPerfiles) =
            viewModelFactory { initializer { ModeloVistaEditarPerfil(perfilId, repositorio) } }
    }
}
