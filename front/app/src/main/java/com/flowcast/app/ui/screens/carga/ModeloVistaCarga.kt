package com.flowcast.app.ui.screens.carga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.flowcast.app.data.network.esSesionInvalida
import com.flowcast.app.data.network.mensajeDeError
import com.flowcast.app.data.repository.RepositorioAutenticacion
import com.flowcast.app.data.repository.RepositorioConexiones
import com.flowcast.app.data.repository.RepositorioPerfiles
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DestinoCarga { INICIO_SESION, CONFIRMAR_PERFIL, ELEGIR_PERFIL }

data class EstadoCarga(
    val mensaje: String = "Verificando sesión",
    val progreso: Float = 0f,
    val paso: Int = 0,
    val error: String? = null,
    val destino: DestinoCarga? = null,
)

class ModeloVistaCarga(
    private val autenticacion: RepositorioAutenticacion,
    private val perfiles: RepositorioPerfiles,
    private val conexiones: RepositorioConexiones,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoCarga())
    val estado: StateFlow<EstadoCarga> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            _estado.value = EstadoCarga()

            avanzar("Verificando sesión", 0.15f, 0)
            val usuario = autenticacion.usuarioActual()
            val falloSesion = usuario.exceptionOrNull()
            if (falloSesion != null) {
                if (esSesionInvalida(falloSesion)) {
                    autenticacion.cerrarSesion()
                    _estado.update { it.copy(destino = DestinoCarga.INICIO_SESION) }
                } else {
                    fallar(falloSesion)
                }
                return@launch
            }

            avanzar("Cargando perfiles", 0.4f, 1)
            val listaPerfiles = perfiles.listar().getOrElse { fallar(it); return@launch }

            avanzar("Preparando canales en vivo", 0.7f, 2)
            conexiones.asegurarConexionActiva().getOrElse { fallar(it); return@launch }

            avanzar("Casi listo", 0.9f, 3)
            delay(250)
            avanzar("Listo", 1f, 4)

            val recordado = perfiles.perfilActivo.first()
            val destino = when {
                recordado != null && listaPerfiles.any { it.id == recordado.id } -> DestinoCarga.CONFIRMAR_PERFIL
                listaPerfiles.size == 1 -> { perfiles.seleccionar(listaPerfiles.first()); DestinoCarga.CONFIRMAR_PERFIL }
                else -> DestinoCarga.ELEGIR_PERFIL
            }
            _estado.update { it.copy(destino = destino) }
        }
    }

    fun cerrarSesion() {
        viewModelScope.launch {
            autenticacion.cerrarSesion()
            _estado.update { it.copy(destino = DestinoCarga.INICIO_SESION) }
        }
    }

    private suspend fun avanzar(mensaje: String, progreso: Float, paso: Int) {
        _estado.update { it.copy(mensaje = mensaje, progreso = progreso, paso = paso) }
        delay(150)
    }

    private fun fallar(error: Throwable) {
        _estado.update { it.copy(error = mensajeDeError(error)) }
    }

    companion object {
        fun fabrica(
            autenticacion: RepositorioAutenticacion,
            perfiles: RepositorioPerfiles,
            conexiones: RepositorioConexiones,
        ) = viewModelFactory { initializer { ModeloVistaCarga(autenticacion, perfiles, conexiones) } }
    }
}
