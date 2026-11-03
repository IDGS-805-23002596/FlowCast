package com.flowcast.app.ui.screens.reproductor

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.flowcast.app.BuildConfig
import com.flowcast.app.data.descargas.GestorDescargas
import com.flowcast.app.data.descargas.RepositorioDescargas
import com.flowcast.app.data.dlna.ClienteDlna
import com.flowcast.app.data.dlna.TelevisorDlna
import com.flowcast.app.data.network.Canal
import com.flowcast.app.data.network.mensajeDeError
import com.flowcast.app.data.repository.EstadoPin
import com.flowcast.app.data.repository.RepositorioBiblioteca
import com.flowcast.app.data.repository.RepositorioCatalogo
import com.flowcast.app.data.repository.RepositorioControlParental
import com.flowcast.app.data.repository.bloqueoParentalDe
import com.flowcast.app.data.repository.verificarPin
import com.flowcast.app.data.session.AlmacenPreferencias
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException

enum class TipoReproduccion(val valorApi: String, val guardaProgreso: Boolean) {
    PELICULA("pelicula", guardaProgreso = true),
    EPISODIO("episodio", guardaProgreso = true),
    EN_VIVO("en_vivo", guardaProgreso = false),
}

data class ObjetivoReproduccion(
    val tipo: TipoReproduccion,
    val id: String,
    val titulo: String,
    val serieId: String? = null,
    val portada: String? = null,
)

data class PistaOpcion(val clave: String, val etiqueta: String, val seleccionada: Boolean)

data class EstadoReproductor(
    val cargando: Boolean = true,
    val error: String? = null,
    val titulo: String = "",
    val pin: EstadoPin = EstadoPin(),
    val siguiente: ObjetivoReproduccion? = null,
    val indiceCanal: Int = -1,
    val totalCanales: Int = 0,
    val pistasAudio: List<PistaOpcion> = emptyList(),
    val pistasSubtitulos: List<PistaOpcion> = emptyList(),
    val televisores: List<TelevisorDlna> = emptyList(),
    val buscandoTelevisores: Boolean = false,
    val televisorActivo: TelevisorDlna? = null,
    val enviandoA: String? = null,
    val aviso: String? = null,
)

@UnstableApi
class ModeloVistaReproductor(
    contexto: Context,
    private val tipo: TipoReproduccion,
    idInicial: String,
    private val serieId: String?,
    titulo: String,
    portadaInicial: String?,
    private val catalogo: RepositorioCatalogo,
    private val biblioteca: RepositorioBiblioteca,
    private val descargas: RepositorioDescargas,
    private val gestorDescargas: GestorDescargas,
    private val controlParental: RepositorioControlParental,
    private val preferencias: AlmacenPreferencias,
    private val dlna: ClienteDlna,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoReproductor(titulo = titulo))
    val estado: StateFlow<EstadoReproductor> = _estado.asStateFlow()

    val reproductor: ExoPlayer = ExoPlayer.Builder(contexto.applicationContext).build()
    val esEnVivo: Boolean = tipo == TipoReproduccion.EN_VIVO
    private var id: String = idInicial
    private var portada: String? = portadaInicial
    private var guardadoPeriodico: Job? = null
    private var canalesVecinos: List<Canal> = emptyList()
    private var urlRemota: String? = null
    private var pedidoEn: Long = 0L
    private var inicioReportado = false
    private var fallaReportada = false

    init {
        reproductor.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                _estado.update { it.copy(error = "No se pudo reproducir: ${error.errorCodeName}") }
                reportarFalla(error.errorCodeName, error.message)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    reportarInicio()
                    iniciarGuardadoPeriodico()
                } else {
                    detenerGuardadoPeriodico(guardarAhora = true)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) prepararSiguiente()
            }

            override fun onTracksChanged(tracks: Tracks) {
                _estado.update { it.copy(pistasAudio = opcionesDe(tracks, C.TRACK_TYPE_AUDIO), pistasSubtitulos = opcionesDe(tracks, C.TRACK_TYPE_TEXT)) }
            }
        })
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null, pin = EstadoPin()) }
        pedidoEn = SystemClock.elapsedRealtime()
        inicioReportado = false
        fallaReportada = false
        viewModelScope.launch {
            val descargada = descargas.completada(tipo, id)
            urlRemota = null
            val (url, userAgent) = if (descargada != null) {
                descargada.uri to GestorDescargas.USER_AGENT
            } else {
                val respuesta = when (tipo) {
                    TipoReproduccion.PELICULA -> catalogo.urlPelicula(id)
                    TipoReproduccion.EPISODIO -> catalogo.urlEpisodio(serieId.orEmpty(), id)
                    TipoReproduccion.EN_VIVO -> catalogo.urlEnVivo(id)
                }
                val reproduccion = respuesta.getOrElse { fallo ->
                    val bloqueo = bloqueoParentalDe(fallo)
                    _estado.update { it.copy(cargando = false, error = if (bloqueo == null) mensajeDeError(fallo) else null, pin = EstadoPin(bloqueo)) }
                    if (bloqueo == null) reportarFalla(codigoDe(fallo), mensajeDeError(fallo))
                    return@launch
                }
                urlRemota = reproduccion.url
                reproduccion.url to reproduccion.userAgent
            }

            val fuente = gestorDescargas.fabricaReproduccion(userAgent)
            reproductor.setMediaSource(DefaultMediaSourceFactory(fuente).createMediaSource(MediaItem.fromUri(url)))
            reproductor.prepare()

            if (tipo.guardaProgreso) {
                val progreso = biblioteca.obtenerProgreso(tipo.valorApi, id)
                if (progreso != null && !progreso.completado && progreso.posicionSegundos > 5) {
                    reproductor.seekTo((progreso.posicionSegundos * 1000).toLong())
                }
            }
            reproductor.playWhenReady = true
            _estado.update { it.copy(cargando = false) }
            biblioteca.registrarReproduccion(tipo.valorApi, id, _estado.value.titulo, portada)
            cargarVecinos()
        }
    }

    private fun reportarInicio() {
        if (inicioReportado) return
        inicioReportado = true
        val segundos = ((SystemClock.elapsedRealtime() - pedidoEn) / 100L).coerceAtMost(6000L) / 10.0
        reportar("inicio", segundosInicio = segundos)
    }

    private fun reportarFalla(codigo: String, mensaje: String?) {
        if (fallaReportada) return
        fallaReportada = true
        reportar("falla", codigo, mensaje)
    }

    private fun codigoDe(fallo: Throwable): String =
        (fallo as? HttpException)?.let { "http_${it.code()}" } ?: (fallo::class.simpleName ?: "error")

    private fun reportar(resultado: String, codigo: String? = null, mensaje: String? = null, segundosInicio: Double? = null) {
        val contenidoId = id
        val titulo = _estado.value.titulo
        viewModelScope.launch {
            biblioteca.reportarEventoReproduccion(resultado, tipo.valorApi, contenidoId, serieId, titulo, codigo, mensaje, segundosInicio)
        }
    }

    private suspend fun cargarVecinos() {
        if (!esEnVivo || canalesVecinos.isNotEmpty()) return
        val todos = catalogo.canales(null).getOrNull() ?: return
        val actual = todos.firstOrNull { it.id == id } ?: return
        canalesVecinos = todos.filter { it.categoriaId == actual.categoriaId }.distinctBy { it.id }
        actualizarIndiceCanal()
    }

    private fun actualizarIndiceCanal() {
        val indice = canalesVecinos.indexOfFirst { it.id == id }
        _estado.update { it.copy(indiceCanal = indice, totalCanales = canalesVecinos.size) }
    }

    fun canalSiguiente() = cambiarCanal(1)

    fun canalAnterior() = cambiarCanal(-1)

    private fun cambiarCanal(paso: Int) {
        if (canalesVecinos.size < 2 || _estado.value.cargando) return
        val indice = canalesVecinos.indexOfFirst { it.id == id }
        if (indice < 0) return
        val destino = canalesVecinos[(indice + paso + canalesVecinos.size) % canalesVecinos.size]
        id = destino.id
        portada = destino.icono
        _estado.update { it.copy(titulo = destino.nombre) }
        actualizarIndiceCanal()
        cargar()
    }

    fun desbloquear(pin: String) {
        viewModelScope.launch { controlParental.verificarPin(pin, { nuevo -> _estado.update { it.copy(pin = nuevo) } }, ::cargar) }
    }

    fun cancelarDesbloqueo() = _estado.update { it.copy(pin = EstadoPin(), error = "Contenido bloqueado por el control parental") }

    fun siguienteConsumido() = _estado.update { it.copy(siguiente = null) }

    private fun prepararSiguiente() {
        if (tipo != TipoReproduccion.EPISODIO) return
        viewModelScope.launch {
            if (!preferencias.autoSiguienteEpisodio.first()) return@launch
            val ficha = catalogo.fichaSerie(serieId.orEmpty()).getOrNull() ?: return@launch
            val episodios = ficha.temporadas.sortedBy { it.numero }.flatMap { t -> t.episodios.sortedBy { it.numero } }
            val indice = episodios.indexOfFirst { it.id == id }
            if (indice < 0) return@launch
            val siguiente = episodios.getOrNull(indice + 1) ?: return@launch
            val titulo = "${ficha.serie.nombre} · T${siguiente.temporada} E${siguiente.numero}"
            _estado.update { it.copy(siguiente = ObjetivoReproduccion(TipoReproduccion.EPISODIO, siguiente.id, titulo, ficha.serie.id, ficha.serie.portada)) }
        }
    }

    private fun opcionesDe(tracks: Tracks, tipo: Int): List<PistaOpcion> {
        val opciones = mutableListOf<PistaOpcion>()
        tracks.groups.forEachIndexed { indiceGrupo, grupo ->
            if (grupo.type != tipo) return@forEachIndexed
            for (indice in 0 until grupo.length) {
                if (!grupo.isTrackSupported(indice)) continue
                opciones += PistaOpcion("$indiceGrupo:$indice", etiquetaDe(grupo.getTrackFormat(indice), tipo, opciones.size + 1), grupo.isTrackSelected(indice))
            }
        }
        return opciones
    }

    private fun etiquetaDe(formato: Format, tipo: Int, numero: Int): String {
        val etiqueta = formato.label?.takeIf { it.isNotBlank() }
        val idioma = formato.language?.takeIf { it.isNotBlank() && it != "und" }?.let { codigo ->
            val nombre = Locale.forLanguageTag(codigo).getDisplayLanguage(Locale("es"))
            if (nombre.isBlank() || nombre.equals(codigo, ignoreCase = true)) codigo else nombre.replaceFirstChar { it.uppercase() }
        }
        val preferida = if (tipo == C.TRACK_TYPE_AUDIO) idioma ?: etiqueta else etiqueta ?: idioma
        return preferida ?: "Pista $numero"
    }

    fun elegirPista(tipo: Int, clave: String) {
        val (indiceGrupo, indice) = clave.split(":").map { it.toInt() }
        val grupo = reproductor.currentTracks.groups.getOrNull(indiceGrupo) ?: return
        reproductor.trackSelectionParameters = reproductor.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(tipo, false)
            .setOverrideForType(TrackSelectionOverride(grupo.mediaTrackGroup, listOf(indice)))
            .build()
    }

    fun buscarTelevisores() {
        if (_estado.value.buscandoTelevisores) return
        _estado.update { it.copy(buscandoTelevisores = true) }
        viewModelScope.launch {
            val extra = if (BuildConfig.DEBUG) listOf("10.0.2.2") else emptyList()
            val encontrados = runCatching { dlna.buscar(direccionesExtra = extra) }.getOrDefault(emptyList())
            _estado.update { it.copy(buscandoTelevisores = false, televisores = encontrados) }
        }
    }

    fun enviarATelevisor(televisor: TelevisorDlna) {
        if (_estado.value.enviandoA != null) return
        _estado.update { it.copy(enviandoA = televisor.id) }
        viewModelScope.launch {
            val url = when {
                tipo == TipoReproduccion.EN_VIVO -> catalogo.urlEnVivo(id, formato = "ts").getOrNull()?.url ?: urlRemota
                else -> urlRemota
            }
            if (url == null) {
                _estado.update { it.copy(enviandoA = null, aviso = "Las descargas solo se pueden ver en este teléfono") }
                return@launch
            }
            runCatching { dlna.reproducir(televisor, url, _estado.value.titulo) }
                .onSuccess {
                    reproductor.pause()
                    _estado.update { it.copy(enviandoA = null, televisorActivo = televisor, aviso = "Reproduciendo en ${televisor.nombre}") }
                }
                .onFailure { fallo -> _estado.update { it.copy(enviandoA = null, aviso = "No se pudo enviar a la TV: ${fallo.message ?: "error"}") } }
        }
    }

    fun volverAlTelefono() {
        val televisor = _estado.value.televisorActivo ?: return
        _estado.update { it.copy(televisorActivo = null) }
        reproductor.play()
        viewModelScope.launch { runCatching { dlna.detener(televisor) } }
    }

    fun limpiarAviso() = _estado.update { it.copy(aviso = null) }

    fun desactivarSubtitulos() {
        reproductor.trackSelectionParameters = reproductor.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    private fun iniciarGuardadoPeriodico() {
        if (!tipo.guardaProgreso || guardadoPeriodico?.isActive == true) return
        guardadoPeriodico = viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                guardarProgreso()
            }
        }
    }

    private fun detenerGuardadoPeriodico(guardarAhora: Boolean) {
        guardadoPeriodico?.cancel()
        guardadoPeriodico = null
        if (guardarAhora && tipo.guardaProgreso) viewModelScope.launch { guardarProgreso() }
    }

    private suspend fun guardarProgreso() {
        guardarProgreso(reproductor.currentPosition / 1000.0, reproductor.duration.takeIf { it > 0 }?.div(1000.0))
    }

    private suspend fun guardarProgreso(posicion: Double, duracion: Double?) {
        if (posicion <= 0) return
        biblioteca.guardarProgreso(tipo.valorApi, id, serieId, _estado.value.titulo, portada, posicion, duracion)
    }

    override fun onCleared() {
        detenerGuardadoPeriodico(guardarAhora = false)
        val posicion = reproductor.currentPosition / 1000.0
        val duracion = reproductor.duration.takeIf { it > 0 }?.div(1000.0)
        reproductor.release()
        if (tipo.guardaProgreso) alcanceFinal.launch { guardarProgreso(posicion, duracion) }
    }

    companion object {
        private val alcanceFinal = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun fabrica(
            contexto: Context,
            tipo: TipoReproduccion,
            id: String,
            serieId: String?,
            titulo: String,
            portada: String?,
            catalogo: RepositorioCatalogo,
            biblioteca: RepositorioBiblioteca,
            descargas: RepositorioDescargas,
            gestorDescargas: GestorDescargas,
            controlParental: RepositorioControlParental,
            preferencias: AlmacenPreferencias,
            dlna: ClienteDlna,
        ) = viewModelFactory {
            initializer {
                ModeloVistaReproductor(contexto, tipo, id, serieId, titulo, portada, catalogo, biblioteca, descargas, gestorDescargas, controlParental, preferencias, dlna)
            }
        }
    }
}
