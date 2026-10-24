package com.flowcast.app.ui.screens.registro

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

data class EstadoRegistro(
    val nombre: String = "",
    val correo: String = "",
    val contrasena: String = "",
    val cargando: Boolean = false,
    val error: String? = null,
    val exito: Boolean = false,
) {
    val puedeEnviar: Boolean
        get() = nombre.trim().length >= 2 && correo.isNotBlank() && contrasena.length >= 8 && !cargando
}

class ModeloVistaRegistro(private val repositorio: RepositorioAutenticacion) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoRegistro())
    val estado: StateFlow<EstadoRegistro> = _estado.asStateFlow()

    fun cambiarNombre(valor: String) = _estado.update { it.copy(nombre = valor, error = null) }
    fun cambiarCorreo(valor: String) = _estado.update { it.copy(correo = valor, error = null) }
    fun cambiarContrasena(valor: String) = _estado.update { it.copy(contrasena = valor, error = null) }

    fun registrar() {
        val actual = _estado.value
        if (!actual.puedeEnviar) return
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            repositorio.registrar(actual.nombre, actual.correo, actual.contrasena)
                .onSuccess { _estado.update { it.copy(cargando = false, exito = true) } }
                .onFailure { fallo -> _estado.update { it.copy(cargando = false, error = mensajeDeError(fallo)) } }
        }
    }

    companion object {
        fun fabrica(repositorio: RepositorioAutenticacion) = viewModelFactory {
            initializer { ModeloVistaRegistro(repositorio) }
        }
    }
}
