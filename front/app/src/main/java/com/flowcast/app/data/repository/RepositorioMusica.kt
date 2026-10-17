package com.flowcast.app.data.repository

import com.flowcast.app.BuildConfig
import com.flowcast.app.data.network.Album
import com.flowcast.app.data.network.AlbumConCanciones
import com.flowcast.app.data.network.ApiFlowCast
import com.flowcast.app.data.network.Cancion
import com.flowcast.app.data.network.ResumenMusica

class RepositorioMusica(private val api: ApiFlowCast) {
    suspend fun resumen(): Result<ResumenMusica> = runCatching { api.resumenMusica() }
    suspend fun albumes(): Result<List<Album>> = runCatching { api.listarAlbumes() }
    suspend fun album(id: String): Result<AlbumConCanciones> = runCatching { api.verAlbum(id) }
    suspend fun canciones(buscar: String? = null): Result<List<Cancion>> = runCatching { api.listarCanciones(buscar) }

    companion object {
        fun urlAbsoluta(rutaRelativa: String): String = BuildConfig.URL_BASE_API.trimEnd('/') + rutaRelativa
    }
}
