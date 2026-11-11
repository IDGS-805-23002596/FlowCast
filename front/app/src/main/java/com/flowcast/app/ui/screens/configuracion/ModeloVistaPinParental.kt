package com.flowcast.app.ui.screens.configuracion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.flowcast.app.data.network.mensajeDeError
import com.flowcast.app.data.repository.RepositorioControlParental
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoPinParental(
    val cargando: Boolean = true,
    val tienePin: Boolean = false,
    val pinActual: String = "",
    val pinNuevo: String = "",
    val pinConfirmacion: String = "",
    val contrasena: String = "",
    val usarContrasena: Boolean = false,
    val guardando: Boolean = false,
    val error: String? = null,
    val confirmarQuitar: Boolean = false,
    val mensajeFinal: String? = null,
)

class ModeloVistaPinParental(private val controlParental: RepositorioControlParental) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoPinParental())
    val estado: StateFlow<EstadoPinParental> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            controlParental.estado()
                .onSuccess { e -> _estado.update { it.copy(cargando = false, tienePin = e.tienePin) } }
                .onFailure { fallo -> _estado.update { it.copy(cargando = false, error = mensajeDeError(fallo)) } }
        }
    }

    fun cambiarPinActual(valor: String) = _estado.update { it.copy(pinActual = valor, error = null) }
    fun cambiarPinNuevo(valor: String) = _estado.update { it.copy(pinNuevo = valor, error = null) }
    fun cambiarConfirmacion(valor: String) = _estado.update { it.copy(pinConfirmacion = valor, error = null) }
    fun cambiarContrasena(valor: String) = _estado.update { it.copy(contrasena = valor, error = null) }
    fun alternarUsarContrasena() = _estado.update { it.copy(usarContrasena = !it.usarContrasena, error = null) }
    fun pedirConfirmacionQuitar(valor: Boolean) = _estado.update { it.copy(confirmarQuitar = valor) }

    private fun credencialActual(): Pair<String?, String?>? {
        val actual = _estado.value
        if (!actual.tienePin) return null to null
        if (actual.usarContrasena) {
            if (actual.contrasena.isBlank()) {
                _estado.update { it.copy(error = "Escribe la contraseña de tu cuenta") }
                return null
            }
            return null to actual.contrasena
        }
        if (actual.pinActual.length != 4) {
            _estado.update { it.copy(error = "Escribe tu PIN actual de 4 dígitos") }
            return null
        }
        return actual.pinActual to null
    }

    fun guardar() {
        val actual = _estado.value
        if (actual.pinNuevo.length != 4) {
            _estado.update { it.copy(error = "El PIN debe tener 4 dígitos") }
            return
        }
        if (actual.pinNuevo != actual.pinConfirmacion) {
            _estado.update { it.copy(error = "Los PIN no coinciden") }
            return
        }
        val (pinActual, contrasena) = credencialActual() ?: return
        _estado.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            controlParental.guardarPin(actual.pinNuevo, pinActual, contrasena)
                .onSuccess { _estado.update { it.copy(guardando = false, tienePin = true, mensajeFinal = "PIN guardado") } }
                .onFailure { fallo -> _estado.update { it.copy(guardando = false, error = mensajeDeError(fallo)) } }
        }
    }

    fun pedirQuitar() {
        if (credencialActual() != null) pedirConfirmacionQuitar(true)
    }

    fun quitar() {
        val (pinActual, contrasena) = credencialActual() ?: return
        _estado.update { it.copy(confirmarQuitar = false, guardando = true, error = null) }
        viewModelScope.launch {
            controlParental.quitarPin(pinActual, contrasena)
                .onSuccess { _estado.update { it.copy(guardando = false, tienePin = false, mensajeFinal = "PIN eliminado") } }
                .onFailure { fallo -> _estado.update { it.copy(guardando = false, error = mensajeDeError(fallo)) } }
        }
    }

    companion object {
        fun fabrica(controlParental: RepositorioControlParental) = viewModelFactory { initializer { ModeloVistaPinParental(controlParental) } }
    }
}
