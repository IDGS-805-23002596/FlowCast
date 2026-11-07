package com.flowcast.app.data.musica

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.flowcast.app.data.network.Cancion
import com.flowcast.app.data.repository.RepositorioMusica
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class EstadoMusica(
    val cancionId: String? = null,
    val titulo: String = "",
    val artista: String = "",
    val album: String = "",
    val portada: Uri? = null,
    val reproduciendo: Boolean = false,
    val cargando: Boolean = false,
    val posicionMs: Long = 0,
    val duracionMs: Long = 0,
    val indice: Int = 0,
    val total: Int = 0,
) {
    val haySonando: Boolean get() = cancionId != null
}

class ControladorMusica(private val contexto: Context) {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var futuro: ListenableFuture<MediaController>? = null
    private var controlador: MediaController? = null
    private var ticker: Job? = null
    private val _estado = MutableStateFlow(EstadoMusica())
    val estado: StateFlow<EstadoMusica> = _estado.asStateFlow()

    private val oyente = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = actualizar()
    }

    fun conectar() {
        if (futuro != null) return
        val token = SessionToken(contexto, ComponentName(contexto, ServicioMusica::class.java))
        futuro = MediaController.Builder(contexto, token).buildAsync().also { f ->
            f.addListener({
                runCatching { f.get() }.onSuccess { c ->
                    controlador = c
                    c.addListener(oyente)
                    actualizar()
                }.onFailure { futuro = null }
            }, MoreExecutors.directExecutor())
        }
    }

    fun reproducir(canciones: List<Cancion>, indice: Int) {
        val c = controlador ?: return
        c.setMediaItems(canciones.map(::aMediaItem), indice, 0)
        c.prepare()
        c.play()
    }

    fun alternar() {
        val c = controlador ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun siguiente() = controlador?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem()
    fun anterior() = controlador?.seekToPrevious()
    fun saltarA(ms: Long) = controlador?.seekTo(ms)

    fun detener() {
        controlador?.run {
            stop()
            clearMediaItems()
        }
        actualizar()
    }

    private fun actualizar() {
        val c = controlador ?: return
        val datos = c.mediaMetadata
        _estado.value = EstadoMusica(
            cancionId = c.currentMediaItem?.mediaId,
            titulo = datos.title?.toString().orEmpty(),
            artista = datos.artist?.toString().orEmpty(),
            album = datos.albumTitle?.toString().orEmpty(),
            portada = datos.artworkUri,
            reproduciendo = c.isPlaying,
            cargando = c.playbackState == Player.STATE_BUFFERING,
            posicionMs = c.currentPosition.coerceAtLeast(0),
            duracionMs = c.duration.coerceAtLeast(0),
            indice = c.currentMediaItemIndex,
            total = c.mediaItemCount,
        )
        if (c.isPlaying && ticker?.isActive != true) {
            ticker = alcance.launch {
                while (isActive && controlador?.isPlaying == true) {
                    delay(500)
                    actualizar()
                }
            }
        }
    }

    private fun aMediaItem(cancion: Cancion): MediaItem {
        val audio = Uri.parse(RepositorioMusica.urlAbsoluta(cancion.urlAudio))
        val portada = cancion.urlPortada?.let { Uri.parse(RepositorioMusica.urlAbsoluta(it)) }
        return MediaItem.Builder()
            .setMediaId(cancion.id)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(audio).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(cancion.titulo)
                    .setArtist(cancion.artista)
                    .setAlbumTitle(cancion.album)
                    .setArtworkUri(portada)
                    .build(),
            )
            .build()
    }
}
