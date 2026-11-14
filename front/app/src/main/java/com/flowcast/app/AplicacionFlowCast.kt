package com.flowcast.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import androidx.media3.common.util.UnstableApi
import com.flowcast.app.data.descargas.GestorDescargas
import com.flowcast.app.data.descargas.RepositorioDescargas
import com.flowcast.app.data.dlna.ClienteDlna
import com.flowcast.app.data.musica.ControladorMusica
import com.flowcast.app.data.network.ClienteApi
import com.flowcast.app.data.repository.RepositorioAutenticacion
import com.flowcast.app.data.repository.RepositorioBiblioteca
import com.flowcast.app.data.repository.RepositorioCatalogo
import com.flowcast.app.data.repository.RepositorioConexiones
import com.flowcast.app.data.repository.RepositorioControlParental
import com.flowcast.app.data.repository.RepositorioMusica
import com.flowcast.app.data.repository.RepositorioPerfiles
import com.flowcast.app.data.session.AlmacenPreferencias
import com.flowcast.app.data.session.AlmacenSesion

@UnstableApi
class AplicacionFlowCast : Application(), ImageLoaderFactory {
    val almacenSesion: AlmacenSesion by lazy { AlmacenSesion(this) }
    val almacenPreferencias: AlmacenPreferencias by lazy { AlmacenPreferencias(this) }
    private val cliente by lazy { ClienteApi.crear(almacenSesion) }
    val api by lazy { cliente.api }
    val repositorioAutenticacion by lazy { RepositorioAutenticacion(api, almacenSesion) }
    val repositorioPerfiles by lazy { RepositorioPerfiles(api, almacenSesion) }
    val repositorioConexiones by lazy { RepositorioConexiones(api, almacenSesion) }
    val repositorioControlParental by lazy { RepositorioControlParental(api, almacenSesion) }
    val repositorioCatalogo by lazy { RepositorioCatalogo(api, almacenSesion, repositorioControlParental, almacenPreferencias) }
    val repositorioBiblioteca by lazy { RepositorioBiblioteca(api, almacenSesion) }
    val descargas by lazy { GestorDescargas(this) }
    val repositorioDescargas by lazy { RepositorioDescargas(this, descargas) }
    val repositorioMusica by lazy { RepositorioMusica(api) }
    val controladorMusica by lazy { ControladorMusica(this) }
    val dlna by lazy { ClienteDlna(this) }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this).okHttpClient { cliente.clienteHttp }.build()
}
