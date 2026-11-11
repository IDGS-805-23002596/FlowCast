package com.flowcast.app.data.descargas

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.flowcast.app.ui.screens.reproductor.ObjetivoReproduccion
import com.flowcast.app.ui.screens.reproductor.TipoReproduccion
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MetadatosDescarga(
    val tipo: String,
    val contenidoId: String,
    val serieId: String? = null,
    val titulo: String,
    val subtitulo: String? = null,
    val portada: String? = null,
)

enum class EstadoDescarga { EN_COLA, DESCARGANDO, COMPLETADA, FALLIDA, DETENIDA }

data class Descarga(
    val id: String,
    val metadatos: MetadatosDescarga,
    val estado: EstadoDescarga,
    val porcentaje: Float,
    val bytes: Long,
    val uri: String,
) {
    val tipo: TipoReproduccion get() = TipoReproduccion.entries.first { it.valorApi == metadatos.tipo }
    val objetivo: ObjetivoReproduccion
        get() = ObjetivoReproduccion(tipo, metadatos.contenidoId, listOfNotNull(metadatos.titulo, metadatos.subtitulo).joinToString(" · "), metadatos.serieId, metadatos.portada)
}

@UnstableApi
class RepositorioDescargas(private val contexto: Context, private val gestor: GestorDescargas) {
    private val json = Json { ignoreUnknownKeys = true }
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _descargas = MutableStateFlow<List<Descarga>>(emptyList())
    val descargas: StateFlow<List<Descarga>> = _descargas.asStateFlow()
    private var refresco: Job? = null

    init {
        gestor.gestor.addListener(object : DownloadManager.Listener {
            override fun onInitialized(downloadManager: DownloadManager) = refrescar()
            override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) = refrescar()
            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) = refrescar()
        })
        refrescar()
    }

    fun idDe(tipo: TipoReproduccion, contenidoId: String) = "${tipo.valorApi}-$contenidoId"

    fun buscar(tipo: TipoReproduccion, contenidoId: String): Descarga? = _descargas.value.firstOrNull { it.id == idDe(tipo, contenidoId) }

    fun completada(tipo: TipoReproduccion, contenidoId: String): Descarga? =
        buscar(tipo, contenidoId)?.takeIf { it.estado == EstadoDescarga.COMPLETADA }

    fun descargar(url: String, metadatos: MetadatosDescarga) {
        val tipo = TipoReproduccion.entries.first { it.valorApi == metadatos.tipo }
        val peticion = DownloadRequest.Builder(idDe(tipo, metadatos.contenidoId), android.net.Uri.parse(url))
            .setData(json.encodeToString(MetadatosDescarga.serializer(), metadatos).toByteArray())
            .build()
        DownloadService.sendAddDownload(contexto, ServicioDescargas::class.java, peticion, true)
    }

    fun eliminar(id: String) = DownloadService.sendRemoveDownload(contexto, ServicioDescargas::class.java, id, false)

    fun espacioUsado(): Long = gestor.cache.cacheSpace

    private fun refrescar() {
        alcance.launch {
            _descargas.value = leerTodas()
            val hayActivas = _descargas.value.any { it.estado == EstadoDescarga.DESCARGANDO || it.estado == EstadoDescarga.EN_COLA }
            if (hayActivas && refresco?.isActive != true) {
                refresco = alcance.launch {
                    while (isActive) {
                        delay(1000)
                        _descargas.value = leerTodas()
                        if (_descargas.value.none { it.estado == EstadoDescarga.DESCARGANDO || it.estado == EstadoDescarga.EN_COLA }) break
                    }
                }
            }
        }
    }

    private suspend fun leerTodas(): List<Descarga> {
        val enCurso = gestor.gestor.currentDownloads.associateBy { it.request.id }
        val guardadas = withContext(Dispatchers.IO) {
            gestor.gestor.downloadIndex.getDownloads().use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.download) }
            }
        }
        return (guardadas.filter { it.request.id !in enCurso } + enCurso.values)
            .sortedByDescending { it.startTimeMs }
            .mapNotNull(::traducir)
    }

    private fun traducir(descarga: Download): Descarga? {
        val metadatos = runCatching { json.decodeFromString(MetadatosDescarga.serializer(), String(descarga.request.data)) }.getOrNull() ?: return null
        val estado = when (descarga.state) {
            Download.STATE_COMPLETED -> EstadoDescarga.COMPLETADA
            Download.STATE_DOWNLOADING -> EstadoDescarga.DESCARGANDO
            Download.STATE_FAILED -> EstadoDescarga.FALLIDA
            Download.STATE_STOPPED -> EstadoDescarga.DETENIDA
            else -> EstadoDescarga.EN_COLA
        }
        return Descarga(descarga.request.id, metadatos, estado, descarga.percentDownloaded, descarga.bytesDownloaded, descarga.request.uri.toString())
    }
}
