package com.flowcast.app.ui.screens.inicio_sesion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.flowcast.app.data.network.mensajeDeError
import com.flowcast.app.data.repository.RepositorioAutenticacion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoInicioSesion(
    val correo: String = "",
    val contrasena: String = "",
    val cargando: Boolean = false,
    val error: String? = null,
    val exito: Boolean = false,
) {
    val puedeEnviar: Boolean get() = correo.isNotBlank() && contrasena.isNotBlank() && !cargando
}

class ModeloVistaInicioSesion(private val repositorio: RepositorioAutenticacion) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoInicioSesion())
    val estado: StateFlow<EstadoInicioSesion> = _estado.asStateFlow()

    fun cambiarCorreo(valor: String) = _estado.update { it.copy(correo = valor, error = null) }
    fun cambiarContrasena(valor: String) = _estado.update { it.copy(contrasena = valor, error = null) }

    fun iniciarSesion() {
        val actual = _estado.value
        if (!actual.puedeEnviar) return
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            repositorio.iniciarSesion(actual.correo, actual.contrasena)
                .onSuccess { _estado.update { it.copy(cargando = false, exito = true) } }
                .onFailure { fallo -> _estado.update { it.copy(cargando = false, error = mensajeDeError(fallo)) } }
        }
    }

    companion object {
        fun fabrica(repositorio: RepositorioAutenticacion) = viewModelFactory {
            initializer { ModeloVistaInicioSesion(repositorio) }
        }
    }
}
