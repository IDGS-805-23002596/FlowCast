package com.flowcast.app.ui.screens.busqueda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.flowcast.app.data.network.Destacados
import com.flowcast.app.data.network.RespuestaBusqueda
import com.flowcast.app.data.network.mensajeDeError
import com.flowcast.app.data.repository.RepositorioCatalogo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FiltroBusqueda(val etiqueta: String) { TODO("Todo"), PELICULAS("Películas"), SERIES("Serie"), CANALES("Canales") }

enum class OrdenBusqueda(val etiqueta: String) { RELEVANCIA("Como los manda el proveedor"), NOMBRE("Por nombre"), ANIO("Más recientes primero") }

data class EstadoBusqueda(
    val texto: String = "",
    val cargando: Boolean = false,
    val error: String? = null,
    val resultados: RespuestaBusqueda? = null,
    val filtro: FiltroBusqueda = FiltroBusqueda.TODO,
    val orden: OrdenBusqueda = OrdenBusqueda.RELEVANCIA,
    val destacados: Destacados? = null,
) {
    val enBusqueda: Boolean get() = texto.trim().length >= 2
    val sinResultados: Boolean
        get() = resultados != null && resultados.canales.isEmpty() && resultados.peliculas.isEmpty() && resultados.series.isEmpty()
}

class ModeloVistaBusqueda(private val catalogo: RepositorioCatalogo) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoBusqueda())
    val estado: StateFlow<EstadoBusqueda> = _estado.asStateFlow()
    private var pendiente: Job? = null

    init {
        viewModelScope.launch {
            catalogo.destacados().onSuccess { d ->
                _estado.update { it.copy(destacados = d.copy(peliculas = d.peliculas.distinctBy { p -> p.id }, series = d.series.distinctBy { s -> s.id })) }
            }
        }
    }

    fun escribir(texto: String) {
        _estado.update { it.copy(texto = texto, error = null) }
        pendiente?.cancel()
        val limpio = texto.trim()
        if (limpio.length < 2) {
            _estado.update { it.copy(cargando = false, resultados = null) }
            return
        }
        pendiente = viewModelScope.launch {
            delay(350)
            _estado.update { it.copy(cargando = true) }
            catalogo.buscar(limpio)
                .onSuccess { r ->
                    val limpio = r.copy(canales = r.canales.distinctBy { it.id }, peliculas = r.peliculas.distinctBy { it.id }, series = r.series.distinctBy { it.id })
                    _estado.update { it.copy(cargando = false, resultados = limpio) }
                }
                .onFailure { fallo -> _estado.update { it.copy(cargando = false, error = mensajeDeError(fallo)) } }
        }
    }

    fun filtrar(filtro: FiltroBusqueda) = _estado.update { it.copy(filtro = filtro) }
    fun ordenar(orden: OrdenBusqueda) = _estado.update { it.copy(orden = orden) }
    fun reintentar() = escribir(_estado.value.texto)

    companion object {
        fun fabrica(catalogo: RepositorioCatalogo) = viewModelFactory { initializer { ModeloVistaBusqueda(catalogo) } }
    }
}
