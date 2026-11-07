package com.flowcast.app.ui.screens.musica

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.flowcast.app.data.network.Album
import com.flowcast.app.data.network.AlbumConCanciones
import com.flowcast.app.data.network.Cancion
import com.flowcast.app.data.network.mensajeDeError
import com.flowcast.app.data.repository.RepositorioMusica
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoMusicaBiblioteca(
    val cargando: Boolean = true,
    val error: String? = null,
    val carpetaConfigurada: Boolean = true,
    val albumes: List<Album> = emptyList(),
    val canciones: List<Cancion> = emptyList(),
    val busqueda: String = "",
) {
    val cancionesVisibles: List<Cancion>
        get() = if (busqueda.isBlank()) canciones else canciones.filter {
            it.titulo.contains(busqueda, true) || it.artista.contains(busqueda, true) || it.album.contains(busqueda, true)
        }
}

class ModeloVistaMusica(private val musica: RepositorioMusica) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoMusicaBiblioteca())
    val estado: StateFlow<EstadoMusicaBiblioteca> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val resumen = musica.resumen().getOrElse { fallar(it); return@launch }
            if (!resumen.carpetaConfigurada) {
                _estado.update { it.copy(cargando = false, carpetaConfigurada = false) }
                return@launch
            }
            val albumes = musica.albumes().getOrElse { fallar(it); return@launch }
            val canciones = musica.canciones().getOrElse { fallar(it); return@launch }
            _estado.update { it.copy(cargando = false, carpetaConfigurada = true, albumes = albumes, canciones = canciones) }
        }
    }

    fun buscar(texto: String) = _estado.update { it.copy(busqueda = texto) }

    private fun fallar(error: Throwable) = _estado.update { it.copy(cargando = false, error = mensajeDeError(error)) }

    companion object {
        fun fabrica(musica: RepositorioMusica) = viewModelFactory { initializer { ModeloVistaMusica(musica) } }
    }
}

data class EstadoAlbum(val cargando: Boolean = true, val error: String? = null, val datos: AlbumConCanciones? = null)

class ModeloVistaAlbum(private val id: String, private val musica: RepositorioMusica) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoAlbum())
    val estado: StateFlow<EstadoAlbum> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _estado.value = EstadoAlbum()
        viewModelScope.launch {
            musica.album(id)
                .onSuccess { _estado.value = EstadoAlbum(cargando = false, datos = it) }
                .onFailure { _estado.value = EstadoAlbum(cargando = false, error = mensajeDeError(it)) }
        }
    }

    companion object {
        fun fabrica(id: String, musica: RepositorioMusica) = viewModelFactory { initializer { ModeloVistaAlbum(id, musica) } }
    }
}
