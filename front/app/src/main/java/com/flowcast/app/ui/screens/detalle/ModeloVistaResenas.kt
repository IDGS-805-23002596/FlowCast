package com.flowcast.app.ui.screens.detalle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.flowcast.app.data.network.ResumenResenas
import com.flowcast.app.data.network.mensajeDeError
import com.flowcast.app.data.repository.RepositorioBiblioteca
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoResenas(
    val cargando: Boolean = true,
    val error: String? = null,
    val resumen: ResumenResenas? = null,
    val guardando: Boolean = false,
)

class ModeloVistaResenas(
    private val tipo: String,
    private val id: String,
    private val biblioteca: RepositorioBiblioteca,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoResenas())
    val estado: StateFlow<EstadoResenas> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = it.resumen == null, error = null) }
        viewModelScope.launch {
            biblioteca.resenas(tipo, id)
                .onSuccess { resumen -> _estado.update { it.copy(cargando = false, resumen = resumen) } }
                .onFailure { fallo -> _estado.update { it.copy(cargando = false, error = mensajeDeError(fallo)) } }
        }
    }

    fun guardar(titulo: String, calificacion: Int, texto: String?, alTerminar: (String?) -> Unit = {}) {
        _estado.update { it.copy(guardando = true) }
        viewModelScope.launch {
            val resultado = biblioteca.guardarResena(tipo, id, titulo, calificacion, texto)
            _estado.update { it.copy(guardando = false) }
            resultado.onSuccess { cargar(); alTerminar(null) }.onFailure { alTerminar(mensajeDeError(it)) }
        }
    }

    fun borrar() {
        viewModelScope.launch { biblioteca.borrarResena(tipo, id).onSuccess { cargar() } }
    }

    companion object {
        fun fabrica(tipo: String, id: String, biblioteca: RepositorioBiblioteca) =
            viewModelFactory { initializer { ModeloVistaResenas(tipo, id, biblioteca) } }
    }
}
