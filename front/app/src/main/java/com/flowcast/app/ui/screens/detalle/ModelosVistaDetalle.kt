package com.flowcast.app.ui.screens.detalle

import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import com.flowcast.app.data.descargas.Descarga
import com.flowcast.app.data.descargas.MetadatosDescarga
import com.flowcast.app.data.descargas.RepositorioDescargas
import com.flowcast.app.data.network.Actor
import com.flowcast.app.data.network.Episodio
import com.flowcast.app.data.network.FichaPelicula
import com.flowcast.app.data.network.FichaSerie
import com.flowcast.app.data.network.mensajeDeError
import com.flowcast.app.data.repository.RepositorioBiblioteca
import com.flowcast.app.data.repository.RepositorioCatalogo
import com.flowcast.app.data.repository.RepositorioControlParental
import com.flowcast.app.data.repository.EstadoPin
import com.flowcast.app.data.repository.bloqueoParentalDe
import com.flowcast.app.data.repository.verificarPin
import com.flowcast.app.ui.components.ElementoCatalogo
import com.flowcast.app.ui.screens.reproductor.TipoReproduccion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoFicha<T>(
    val cargando: Boolean = true,
    val error: String? = null,
    val datos: T? = null,
    val favorito: Boolean = false,
    val aviso: String? = null,
    val elenco: List<Actor> = emptyList(),
    val similares: List<ElementoCatalogo> = emptyList(),
    val cargandoSimilares: Boolean = false,
    val pin: EstadoPin = EstadoPin(),
)

private fun <T> EstadoFicha<T>.conFallo(fallo: Throwable): EstadoFicha<T> {
    val bloqueo = bloqueoParentalDe(fallo)
    return if (bloqueo != null) copy(pin = EstadoPin(bloqueo)) else copy(aviso = mensajeDeError(fallo))
}

private fun <T> fichaFallida(fallo: Throwable): EstadoFicha<T> {
    val bloqueo = bloqueoParentalDe(fallo)
    return EstadoFicha(cargando = false, error = if (bloqueo == null) mensajeDeError(fallo) else null, pin = EstadoPin(bloqueo))
}

@OptIn(UnstableApi::class)
class ModeloVistaPelicula(
    private val id: String,
    private val catalogo: RepositorioCatalogo,
    private val biblioteca: RepositorioBiblioteca,
    private val descargas: RepositorioDescargas,
    private val controlParental: RepositorioControlParental,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoFicha<FichaPelicula>())
    val estado: StateFlow<EstadoFicha<FichaPelicula>> = _estado.asStateFlow()
    private var trasDesbloqueo: () -> Unit = {}

    val descarga: StateFlow<Descarga?> = descargas.descargas
        .map { lista -> lista.firstOrNull { it.id == descargas.idDe(TipoReproduccion.PELICULA, id) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, descargas.buscar(TipoReproduccion.PELICULA, id))

    init {
        cargar()
    }

    fun cargar() {
        _estado.value = EstadoFicha()
        viewModelScope.launch {
            catalogo.fichaPelicula(id)
                .onSuccess { ficha ->
                    _estado.value = EstadoFicha(cargando = false, datos = ficha, favorito = biblioteca.esFavorito("pelicula", id))
                    catalogo.elencoPelicula(id).onSuccess { e -> _estado.update { it.copy(elenco = e.actores) } }
                }
                .onFailure { fallo ->
                    trasDesbloqueo = ::cargar
                    _estado.value = fichaFallida(fallo)
                }
        }
    }

    fun cargarSimilares() {
        val pelicula = _estado.value.datos?.pelicula ?: return
        if (_estado.value.similares.isNotEmpty() || _estado.value.cargandoSimilares) return
        _estado.update { it.copy(cargandoSimilares = true) }
        viewModelScope.launch {
            val lista = catalogo.peliculas(pelicula.categoriaId, 40).getOrDefault(emptyList())
                .filter { it.id != pelicula.id }
                .distinctBy { it.id }
                .take(20)
                .map { p -> ElementoCatalogo(p.id, p.nombre, p.portada, _estado.value.datos?.genero?.substringBefore("/")?.trim() ?: "Película", p.agregadoEn?.take(4), p.calificacion, esSerie = false, bloqueada = p.bloqueada) }
            _estado.update { it.copy(similares = lista, cargandoSimilares = false) }
        }
    }

    fun alternarFavorito() {
        val pelicula = _estado.value.datos?.pelicula ?: return
        val nuevo = !_estado.value.favorito
        _estado.update { it.copy(favorito = nuevo) }
        viewModelScope.launch {
            if (nuevo) biblioteca.marcarFavorito("pelicula", id, pelicula.nombre, pelicula.portada) else biblioteca.quitarFavorito("pelicula", id)
        }
    }

    fun descargar() {
        val pelicula = _estado.value.datos?.pelicula ?: return
        viewModelScope.launch {
            catalogo.urlPelicula(id)
                .onSuccess { descargas.descargar(it.url, MetadatosDescarga("pelicula", id, null, pelicula.nombre, null, pelicula.portada)) }
                .onFailure { fallo ->
                    trasDesbloqueo = ::descargar
                    _estado.update { it.conFallo(fallo) }
                }
        }
    }

    fun desbloquear(pin: String) {
        viewModelScope.launch { controlParental.verificarPin(pin, { nuevo -> _estado.update { it.copy(pin = nuevo) } }) { trasDesbloqueo() } }
    }

    fun cancelarDesbloqueo() = _estado.update { it.copy(pin = EstadoPin()) }

    fun eliminarDescarga() = descargas.eliminar(descargas.idDe(TipoReproduccion.PELICULA, id))

    fun limpiarAviso() = _estado.update { it.copy(aviso = null) }

    companion object {
        fun fabrica(
            id: String,
            catalogo: RepositorioCatalogo,
            biblioteca: RepositorioBiblioteca,
            descargas: RepositorioDescargas,
            controlParental: RepositorioControlParental,
        ) = viewModelFactory { initializer { ModeloVistaPelicula(id, catalogo, biblioteca, descargas, controlParental) } }
    }
}

@OptIn(UnstableApi::class)
class ModeloVistaSerie(
    private val id: String,
    private val catalogo: RepositorioCatalogo,
    private val biblioteca: RepositorioBiblioteca,
    private val descargas: RepositorioDescargas,
    private val controlParental: RepositorioControlParental,
) : ViewModel() {
    private var episodioPendiente: Episodio? = null
    private var trasDesbloqueo: () -> Unit = {}

    private val _estado = MutableStateFlow(EstadoFicha<FichaSerie>())
    val estado: StateFlow<EstadoFicha<FichaSerie>> = _estado.asStateFlow()

    val descargasEpisodios: StateFlow<Map<String, Descarga>> = descargas.descargas
        .map { lista -> lista.filter { it.metadatos.tipo == "episodio" && it.metadatos.serieId == id }.associateBy { it.metadatos.contenidoId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        cargar()
    }

    fun cargar() {
        _estado.value = EstadoFicha()
        viewModelScope.launch {
            catalogo.fichaSerie(id)
                .onSuccess { ficha ->
                    _estado.value = EstadoFicha(cargando = false, datos = ficha, favorito = biblioteca.esFavorito("serie", id))
                    catalogo.elencoSerie(id).onSuccess { e -> _estado.update { it.copy(elenco = e.actores) } }
                }
                .onFailure { fallo ->
                    trasDesbloqueo = ::cargar
                    _estado.value = fichaFallida(fallo)
                }
        }
    }

    fun cargarSimilares() {
        val serie = _estado.value.datos?.serie ?: return
        if (_estado.value.similares.isNotEmpty() || _estado.value.cargandoSimilares) return
        _estado.update { it.copy(cargandoSimilares = true) }
        viewModelScope.launch {
            val lista = catalogo.series(serie.categoriaId, 40).getOrDefault(emptyList())
                .filter { it.id != serie.id }
                .distinctBy { it.id }
                .take(20)
                .map { s -> ElementoCatalogo(s.id, s.nombre, s.portada, s.genero ?: "Serie", s.fechaEstreno?.take(4), s.calificacion, esSerie = true, bloqueada = s.bloqueada) }
            _estado.update { it.copy(similares = lista, cargandoSimilares = false) }
        }
    }

    fun alternarFavorito() {
        val serie = _estado.value.datos?.serie ?: return
        val nuevo = !_estado.value.favorito
        _estado.update { it.copy(favorito = nuevo) }
        viewModelScope.launch {
            if (nuevo) biblioteca.marcarFavorito("serie", id, serie.nombre, serie.portada) else biblioteca.quitarFavorito("serie", id)
        }
    }

    fun descargarEpisodio(episodio: Episodio) {
        val serie = _estado.value.datos?.serie ?: return
        episodioPendiente = episodio
        viewModelScope.launch {
            catalogo.urlEpisodio(id, episodio.id)
                .onSuccess {
                    val subtitulo = "T${episodio.temporada} · E${episodio.numero} · ${episodio.titulo}"
                    descargas.descargar(it.url, MetadatosDescarga("episodio", episodio.id, id, serie.nombre, subtitulo, episodio.imagen ?: serie.portada))
                }
                .onFailure { fallo ->
                    trasDesbloqueo = { episodioPendiente?.let(::descargarEpisodio) }
                    _estado.update { it.conFallo(fallo) }
                }
        }
    }

    fun desbloquear(pin: String) {
        viewModelScope.launch { controlParental.verificarPin(pin, { nuevo -> _estado.update { it.copy(pin = nuevo) } }) { trasDesbloqueo() } }
    }

    fun cancelarDesbloqueo() = _estado.update { it.copy(pin = EstadoPin()) }

    fun eliminarDescarga(episodio: Episodio) = descargas.eliminar(descargas.idDe(TipoReproduccion.EPISODIO, episodio.id))

    fun limpiarAviso() = _estado.update { it.copy(aviso = null) }

    companion object {
        fun fabrica(
            id: String,
            catalogo: RepositorioCatalogo,
            biblioteca: RepositorioBiblioteca,
            descargas: RepositorioDescargas,
            controlParental: RepositorioControlParental,
        ) = viewModelFactory { initializer { ModeloVistaSerie(id, catalogo, biblioteca, descargas, controlParental) } }
    }
}
