package com.flowcast.app.ui.screens.configuracion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.flowcast.app.data.network.mensajeDeError
import com.flowcast.app.data.repository.RepositorioCatalogo
import com.flowcast.app.data.repository.RepositorioControlParental
import com.flowcast.app.data.session.AlmacenPreferencias
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoConfiguracion(
    val tienePin: Boolean? = null,
    val formatoEnVivo: String = AlmacenPreferencias.FORMATO_AUTO,
    val orientacion: String = AlmacenPreferencias.ORIENTACION_AUTO,
    val pestanaInicial: String = "PELICULAS",
    val autoSiguiente: Boolean = true,
    val actualizando: Boolean = false,
    val aviso: String? = null,
)

class ModeloVistaConfiguracion(
    private val preferencias: AlmacenPreferencias,
    private val controlParental: RepositorioControlParental,
    private val catalogo: RepositorioCatalogo,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoConfiguracion())
    val estado: StateFlow<EstadoConfiguracion> = _estado.asStateFlow()

    init {
        viewModelScope.launch { preferencias.formatoEnVivo.collect { v -> _estado.update { it.copy(formatoEnVivo = v) } } }
        viewModelScope.launch { preferencias.orientacionReproductor.collect { v -> _estado.update { it.copy(orientacion = v) } } }
        viewModelScope.launch { preferencias.pestanaInicial.collect { v -> _estado.update { it.copy(pestanaInicial = v) } } }
        viewModelScope.launch { preferencias.autoSiguienteEpisodio.collect { v -> _estado.update { it.copy(autoSiguiente = v) } } }
        cargarPin()
    }

    fun cargarPin() {
        viewModelScope.launch {
            controlParental.estado()
                .onSuccess { e -> _estado.update { it.copy(tienePin = e.tienePin) } }
                .onFailure { fallo -> _estado.update { it.copy(aviso = mensajeDeError(fallo)) } }
        }
    }

    fun cambiarFormatoEnVivo(valor: String) {
        viewModelScope.launch { preferencias.guardarFormatoEnVivo(valor) }
    }

    fun cambiarOrientacion(valor: String) {
        viewModelScope.launch { preferencias.guardarOrientacion(valor) }
    }

    fun cambiarPestanaInicial(valor: String) {
        viewModelScope.launch { preferencias.guardarPestanaInicial(valor) }
    }

    fun cambiarAutoSiguiente(valor: Boolean) {
        viewModelScope.launch { preferencias.guardarAutoSiguiente(valor) }
    }

    fun actualizarContenido(alTerminar: () -> Unit) {
        if (_estado.value.actualizando) return
        _estado.update { it.copy(actualizando = true) }
        viewModelScope.launch {
            catalogo.actualizarCatalogo()
                .onSuccess {
                    _estado.update { it.copy(actualizando = false, aviso = "Contenido actualizado") }
                    alTerminar()
                }
                .onFailure { fallo -> _estado.update { it.copy(actualizando = false, aviso = mensajeDeError(fallo)) } }
        }
    }

    fun limpiarAviso() = _estado.update { it.copy(aviso = null) }

    companion object {
        fun fabrica(preferencias: AlmacenPreferencias, controlParental: RepositorioControlParental, catalogo: RepositorioCatalogo) =
            viewModelFactory { initializer { ModeloVistaConfiguracion(preferencias, controlParental, catalogo) } }
    }
}
