package com.flowcast.app.ui.screens.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.flowcast.app.data.network.Categoria
import com.flowcast.app.data.network.mensajeDeError
import com.flowcast.app.data.repository.RepositorioCatalogo
import com.flowcast.app.data.repository.RepositorioConexiones
import com.flowcast.app.ui.components.ElementoCatalogo
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TipoCatalogo(val tipoApi: String, val titulo: String) {
    PELICULAS("pelicula", "Películas"),
    SERIES("serie", "Serie"),
}

data class SeccionCatalogo(val categoria: Categoria, val elementos: List<ElementoCatalogo>, val bloqueada: Boolean = false)

data class EstadoCatalogo(
    val cargando: Boolean = true,
    val error: String? = null,
    val destacados: List<ElementoCatalogo> = emptyList(),
    val tendencia: List<ElementoCatalogo> = emptyList(),
    val secciones: List<SeccionCatalogo> = emptyList(),
    val categorias: List<Categoria> = emptyList(),
    val busqueda: String = "",
    val buscando: Boolean = false,
    val resultados: List<ElementoCatalogo>? = null,
) {
    val enBusqueda: Boolean get() = busqueda.trim().length >= 2
}

class ModeloVistaCatalogo(
    val tipo: TipoCatalogo,
    private val catalogo: RepositorioCatalogo,
    conexiones: RepositorioConexiones,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoCatalogo())
    val estado: StateFlow<EstadoCatalogo> = _estado.asStateFlow()
    private var busquedaPendiente: Job? = null

    init {
        viewModelScope.launch { conexiones.conexionActivaId.distinctUntilChanged().collect { cargar() } }
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val categorias = catalogo.categorias(tipo.tipoApi, conTotales = true).getOrElse { fallar(it); return@launch }
            val secciones = coroutineScope {
                categorias.take(MAXIMO_CATEGORIAS).map { categoria ->
                    async {
                        if (categoria.bloqueada) SeccionCatalogo(categoria, emptyList(), bloqueada = true) else SeccionCatalogo(categoria, elementosDe(categoria, categorias))
                    }
                }.awaitAll()
            }.filter { it.elementos.isNotEmpty() || it.bloqueada }
            val todos = secciones.flatMap { it.elementos }.distinctBy { it.id }
            _estado.update {
                it.copy(
                    cargando = false,
                    categorias = categorias,
                    destacados = secciones.firstOrNull { s -> !s.bloqueada }?.elementos?.take(5).orEmpty(),
                    tendencia = todos.sortedByDescending { e -> e.calificacion ?: 0.0 }.take(10),
                    secciones = secciones.map { s -> s.copy(elementos = s.elementos.take(MAXIMO_POR_FILA)) },
                )
            }
        }
    }

    fun buscar(texto: String) {
        _estado.update { it.copy(busqueda = texto) }
        busquedaPendiente?.cancel()
        val limpio = texto.trim()
        if (limpio.length < 2) {
            _estado.update { it.copy(buscando = false, resultados = null) }
            return
        }
        busquedaPendiente = viewModelScope.launch {
            delay(350)
            _estado.update { it.copy(buscando = true) }
            val nombres = _estado.value.categorias.associate { it.id to it.nombre }
            catalogo.buscar(limpio)
                .onSuccess { r ->
                    val elementos = when (tipo) {
                        TipoCatalogo.PELICULAS -> r.peliculas.map { p ->
                            ElementoCatalogo(p.id, p.nombre, p.portada, nombres[p.categoriaId] ?: "Película", p.agregadoEn?.take(4), p.calificacion, esSerie = false, bloqueada = p.bloqueada)
                        }
                        TipoCatalogo.SERIES -> r.series.map { s ->
                            ElementoCatalogo(s.id, s.nombre, s.portada, nombres[s.categoriaId] ?: s.genero ?: "Serie", s.fechaEstreno?.take(4), s.calificacion, esSerie = true, bloqueada = s.bloqueada)
                        }
                    }
                    _estado.update { it.copy(buscando = false, resultados = elementos.distinctBy { e -> e.id }) }
                }
                .onFailure { _estado.update { it.copy(buscando = false, resultados = emptyList()) } }
        }
    }

    fun limpiarBusqueda() = buscar("")

    private suspend fun elementosDe(categoria: Categoria, categorias: List<Categoria>): List<ElementoCatalogo> {
        val nombres = categorias.associate { it.id to it.nombre }
        return when (tipo) {
            TipoCatalogo.PELICULAS -> catalogo.peliculas(categoria.id, MAXIMO_POR_FILA).getOrDefault(emptyList()).map { p ->
                ElementoCatalogo(p.id, p.nombre, p.portada, nombres[p.categoriaId] ?: categoria.nombre, p.agregadoEn?.take(4), p.calificacion, esSerie = false, bloqueada = p.bloqueada)
            }
            TipoCatalogo.SERIES -> catalogo.series(categoria.id, MAXIMO_POR_FILA).getOrDefault(emptyList()).map { s ->
                ElementoCatalogo(s.id, s.nombre, s.portada, nombres[s.categoriaId] ?: categoria.nombre, s.fechaEstreno?.take(4), s.calificacion, esSerie = true, bloqueada = s.bloqueada)
            }
        }
    }

    private fun fallar(error: Throwable) {
        _estado.update { it.copy(cargando = false, error = mensajeDeError(error)) }
    }

    companion object {
        private const val MAXIMO_CATEGORIAS = 8
        private const val MAXIMO_POR_FILA = 20

        fun fabrica(tipo: TipoCatalogo, catalogo: RepositorioCatalogo, conexiones: RepositorioConexiones) =
            viewModelFactory { initializer { ModeloVistaCatalogo(tipo, catalogo, conexiones) } }
    }
}
