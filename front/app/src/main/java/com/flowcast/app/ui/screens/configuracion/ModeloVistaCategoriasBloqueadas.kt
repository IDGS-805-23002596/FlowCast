package com.flowcast.app.ui.screens.configuracion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.flowcast.app.data.network.CategoriaControl
import com.flowcast.app.data.network.mensajeDeError
import com.flowcast.app.data.repository.RepositorioControlParental
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FiltroTipo(val valorApi: String, val etiqueta: String) {
    EN_VIVO("en_vivo", "TV en vivo"),
    PELICULAS("pelicula", "Películas"),
    SERIES("serie", "Series"),
}

data class EstadoCategoriasBloqueadas(
    val tienePin: Boolean? = null,
    val desbloqueado: Boolean = false,
    val verificando: Boolean = false,
    val errorPin: String? = null,
    val cargando: Boolean = false,
    val error: String? = null,
    val categorias: List<CategoriaControl> = emptyList(),
    val filtro: FiltroTipo = FiltroTipo.EN_VIVO,
    val busqueda: String = "",
    val aviso: String? = null,
    val huboCambios: Boolean = false,
) {
    val visibles: List<CategoriaControl>
        get() = categorias.filter { it.tipoContenido == filtro.valorApi && (busqueda.isBlank() || it.nombre.contains(busqueda, ignoreCase = true)) }
    val totalBloqueadas: Int get() = categorias.count { it.bloqueada }
}

class ModeloVistaCategoriasBloqueadas(private val controlParental: RepositorioControlParental) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoCategoriasBloqueadas())
    val estado: StateFlow<EstadoCategoriasBloqueadas> = _estado.asStateFlow()

    init {
        iniciar()
    }

    fun iniciar() {
        _estado.update { it.copy(error = null) }
        viewModelScope.launch {
            controlParental.estado()
                .onSuccess { e ->
                    val libre = e.tienePin && controlParental.tokenVigente() != null
                    _estado.update { it.copy(tienePin = e.tienePin, desbloqueado = libre) }
                    if (libre) cargar()
                }
                .onFailure { fallo -> _estado.update { it.copy(error = mensajeDeError(fallo)) } }
        }
    }

    fun verificar(pin: String) {
        _estado.update { it.copy(verificando = true, errorPin = null) }
        viewModelScope.launch {
            controlParental.verificar(pin)
                .onSuccess { valido ->
                    if (valido) {
                        _estado.update { it.copy(verificando = false, desbloqueado = true) }
                        cargar()
                    } else {
                        _estado.update { it.copy(verificando = false, errorPin = "PIN incorrecto") }
                    }
                }
                .onFailure { fallo -> _estado.update { it.copy(verificando = false, errorPin = mensajeDeError(fallo)) } }
        }
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            controlParental.categorias()
                .onSuccess { lista -> _estado.update { it.copy(cargando = false, categorias = lista.sortedBy { c -> c.nombre.lowercase() }) } }
                .onFailure { fallo -> _estado.update { it.copy(cargando = false, error = mensajeDeError(fallo)) } }
        }
    }

    fun filtrar(filtro: FiltroTipo) = _estado.update { it.copy(filtro = filtro) }
    fun buscar(texto: String) = _estado.update { it.copy(busqueda = texto) }
    fun limpiarAviso() = _estado.update { it.copy(aviso = null) }

    fun alternar(categoria: CategoriaControl) {
        val nuevo = !categoria.bloqueada
        cambiarLocal(categoria, nuevo)
        viewModelScope.launch {
            controlParental.bloquear(categoria.tipoContenido, categoria.id, nuevo).onFailure { fallo ->
                cambiarLocal(categoria, !nuevo)
                _estado.update { it.copy(aviso = mensajeDeError(fallo)) }
            }
        }
    }

    private fun cambiarLocal(categoria: CategoriaControl, bloqueada: Boolean) {
        _estado.update { actual ->
            actual.copy(
                huboCambios = true,
                categorias = actual.categorias.map { c ->
                    if (c.tipoContenido == categoria.tipoContenido && c.id == categoria.id) c.copy(bloqueada = bloqueada) else c
                },
            )
        }
    }

    companion object {
        fun fabrica(controlParental: RepositorioControlParental) = viewModelFactory { initializer { ModeloVistaCategoriasBloqueadas(controlParental) } }
    }
}
