package com.flowcast.app.ui.screens.favoritos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.flowcast.app.data.network.RespuestaFavorito
import com.flowcast.app.data.network.mensajeDeError
import com.flowcast.app.data.repository.RepositorioBiblioteca
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoFavoritos(val cargando: Boolean = true, val error: String? = null, val favoritos: List<RespuestaFavorito> = emptyList()) {
    fun deTipo(tipo: String) = favoritos.filter { it.tipoContenido == tipo }
}

class ModeloVistaFavoritos(private val biblioteca: RepositorioBiblioteca) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoFavoritos())
    val estado: StateFlow<EstadoFavoritos> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            biblioteca.favoritos()
                .onSuccess { lista -> _estado.update { it.copy(cargando = false, favoritos = lista) } }
                .onFailure { fallo -> _estado.update { it.copy(cargando = false, error = mensajeDeError(fallo)) } }
        }
    }

    fun quitar(favorito: RespuestaFavorito) {
        _estado.update { it.copy(favoritos = it.favoritos - favorito) }
        viewModelScope.launch { biblioteca.quitarFavorito(favorito.tipoContenido, favorito.streamId) }
    }

    companion object {
        fun fabrica(biblioteca: RepositorioBiblioteca) = viewModelFactory { initializer { ModeloVistaFavoritos(biblioteca) } }
    }
}
