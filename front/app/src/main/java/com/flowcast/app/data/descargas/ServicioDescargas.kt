package com.flowcast.app.data.descargas

import android.app.Notification
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.flowcast.app.AplicacionFlowCast
import com.flowcast.app.R

@UnstableApi
class ServicioDescargas : DownloadService(
    ID_NOTIFICACION,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CANAL_NOTIFICACIONES,
    R.string.canal_descargas,
    R.string.canal_descargas_descripcion,
) {
    private val ayudante by lazy { DownloadNotificationHelper(this, CANAL_NOTIFICACIONES) }

    override fun getDownloadManager(): DownloadManager = (application as AplicacionFlowCast).descargas.gestor

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification =
        ayudante.buildProgressNotification(this, R.drawable.ic_descarga, null, "Descargando para ver sin conexión", downloads, notMetRequirements)

    companion object {
        const val CANAL_NOTIFICACIONES = "descargas"
        const val ID_NOTIFICACION = 1
    }
}
