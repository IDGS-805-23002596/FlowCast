package com.flowcast.app.data.musica

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.flowcast.app.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
class ServicioMusica : MediaSessionService() {
    private var sesion: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val reproductor = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        val abrirApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        sesion = MediaSession.Builder(this, reproductor)
            .setSessionActivity(abrirApp)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> {
                    val completos = mediaItems.map { it.buildUpon().setUri(it.requestMetadata.mediaUri).build() }
                    return Futures.immediateFuture(completos.toMutableList())
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = sesion

    override fun onTaskRemoved(rootIntent: Intent?) {
        val reproductor = sesion?.player
        if (reproductor == null || !reproductor.playWhenReady || reproductor.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        sesion?.run {
            player.release()
            release()
            sesion = null
        }
        super.onDestroy()
    }
}
