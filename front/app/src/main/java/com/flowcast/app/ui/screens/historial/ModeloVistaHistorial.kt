package com.flowcast.app.ui.screens.historial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.flowcast.app.data.network.RespuestaHistorial
import com.flowcast.app.data.network.RespuestaProgreso
import com.flowcast.app.data.network.mensajeDeError
import com.flowcast.app.data.repository.RepositorioBiblioteca
import com.flowcast.app.ui.screens.reproductor.ObjetivoReproduccion
import com.flowcast.app.ui.screens.reproductor.TipoReproduccion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoHistorial(
    val cargando: Boolean = true,
    val error: String? = null,
    val continuar: List<RespuestaProgreso> = emptyList(),
    val historial: List<RespuestaHistorial> = emptyList(),
    val masRecientePrimero: Boolean = true,
    val editando: Boolean = false,
) {
    val vacio: Boolean get() = continuar.isEmpty() && historial.isEmpty()
    val historialOrdenado: List<RespuestaHistorial> get() = if (masRecientePrimero) historial else historial.asReversed()
}

class ModeloVistaHistorial(private val biblioteca: RepositorioBiblioteca) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoHistorial())
    val estado: StateFlow<EstadoHistorial> = _estado.asStateFlow()

    fun cargar() {
        _estado.update { it.copy(cargando = it.vacio, error = null) }
        viewModelScope.launch {
            val continuar = biblioteca.continuarViendo().getOrElse { fallar(it); return@launch }
            val historial = biblioteca.historial().getOrElse { fallar(it); return@launch }
            _estado.update { it.copy(cargando = false, continuar = continuar, historial = historial) }
        }
    }

    fun alternarOrden() = _estado.update { it.copy(masRecientePrimero = !it.masRecientePrimero) }
    fun alternarEdicion() = _estado.update { it.copy(editando = !it.editando) }

    fun borrarEntrada(entrada: RespuestaHistorial) {
        _estado.update { it.copy(historial = it.historial - entrada) }
        viewModelScope.launch { biblioteca.borrarEntradaHistorial(entrada.id) }
    }

    fun borrarProgreso(progreso: RespuestaProgreso) {
        _estado.update { it.copy(continuar = it.continuar - progreso) }
        viewModelScope.launch { biblioteca.borrarProgreso(progreso.tipoContenido, progreso.streamId) }
    }

    fun vaciar() {
        _estado.update { it.copy(historial = emptyList(), editando = false) }
        viewModelScope.launch { biblioteca.vaciarHistorial() }
    }

    fun objetivoDe(progreso: RespuestaProgreso) = ObjetivoReproduccion(
        tipo = if (progreso.tipoContenido == "episodio") TipoReproduccion.EPISODIO else TipoReproduccion.PELICULA,
        id = progreso.streamId,
        titulo = progreso.titulo,
        serieId = progreso.serieId,
        portada = progreso.urlPortada,
    )

    suspend fun objetivoDe(entrada: RespuestaHistorial): ObjetivoReproduccion? = when (entrada.tipoContenido) {
        "pelicula" -> ObjetivoReproduccion(TipoReproduccion.PELICULA, entrada.streamId, entrada.titulo, portada = entrada.urlPortada)
        "en_vivo" -> ObjetivoReproduccion(TipoReproduccion.EN_VIVO, entrada.streamId, entrada.titulo, portada = entrada.urlPortada)
        else -> biblioteca.obtenerProgreso("episodio", entrada.streamId)?.serieId?.let { serieId ->
            ObjetivoReproduccion(TipoReproduccion.EPISODIO, entrada.streamId, entrada.titulo, serieId, entrada.urlPortada)
        }
    }

    private fun fallar(error: Throwable) = _estado.update { it.copy(cargando = false, error = mensajeDeError(error)) }

    companion object {
        fun fabrica(biblioteca: RepositorioBiblioteca) = viewModelFactory { initializer { ModeloVistaHistorial(biblioteca) } }
    }
}
