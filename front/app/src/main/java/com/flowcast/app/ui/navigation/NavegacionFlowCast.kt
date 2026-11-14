package com.flowcast.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.flowcast.app.AplicacionFlowCast
import com.flowcast.app.ui.screens.bienvenida.PantallaBienvenida
import com.flowcast.app.ui.screens.busqueda.ModeloVistaBusqueda
import com.flowcast.app.ui.screens.busqueda.PantallaBusqueda
import com.flowcast.app.ui.screens.carga.DestinoCarga
import com.flowcast.app.ui.screens.categoria.ModeloVistaCategoria
import com.flowcast.app.ui.screens.categoria.PantallaCategoria
import com.flowcast.app.ui.screens.detalle.PantallaTrailer
import com.flowcast.app.ui.screens.inicio.ModeloVistaCatalogo
import com.flowcast.app.ui.screens.inicio.TipoCatalogo
import com.flowcast.app.ui.screens.configuracion.ModeloVistaCategoriasBloqueadas
import com.flowcast.app.ui.screens.configuracion.ModeloVistaConfiguracion
import com.flowcast.app.ui.screens.configuracion.ModeloVistaPinParental
import com.flowcast.app.ui.screens.configuracion.PantallaCategoriasBloqueadas
import com.flowcast.app.ui.screens.configuracion.PantallaConfiguracion
import com.flowcast.app.ui.screens.configuracion.PantallaPinParental
import com.flowcast.app.ui.screens.carga.ModeloVistaCarga
import com.flowcast.app.ui.screens.carga.PantallaCarga
import com.flowcast.app.ui.screens.detalle.ModeloVistaPelicula
import com.flowcast.app.ui.screens.detalle.ModeloVistaResenas
import com.flowcast.app.ui.screens.detalle.ModeloVistaSerie
import com.flowcast.app.ui.screens.detalle.PantallaPelicula
import com.flowcast.app.ui.screens.detalle.PantallaSerie
import com.flowcast.app.ui.screens.inicio.PantallaInicio
import com.flowcast.app.ui.screens.musica.ModeloVistaAlbum
import com.flowcast.app.ui.screens.musica.ModeloVistaMusica
import com.flowcast.app.ui.screens.musica.PantallaAlbum
import com.flowcast.app.ui.screens.musica.PantallaMusica
import com.flowcast.app.ui.screens.musica.PantallaReproductorMusica
import com.flowcast.app.ui.screens.inicio_sesion.ModeloVistaInicioSesion
import com.flowcast.app.ui.screens.inicio_sesion.PantallaInicioSesion
import com.flowcast.app.ui.screens.perfiles.ModeloVistaEditarPerfil
import com.flowcast.app.ui.screens.perfiles.ModeloVistaPerfiles
import com.flowcast.app.ui.screens.perfiles.PantallaEditarPerfil
import com.flowcast.app.ui.screens.perfiles.PantallaListaPerfiles
import com.flowcast.app.ui.screens.perfiles.PantallaSeleccionPerfil
import com.flowcast.app.ui.screens.registro.ModeloVistaRegistro
import com.flowcast.app.ui.screens.registro.PantallaRegistro
import com.flowcast.app.ui.screens.favoritos.ModeloVistaFavoritos
import com.flowcast.app.ui.screens.favoritos.PantallaFavoritos
import com.flowcast.app.ui.screens.reproductor.ModeloVistaReproductor
import com.flowcast.app.ui.screens.reproductor.ObjetivoReproduccion
import com.flowcast.app.ui.screens.reproductor.PantallaReproductor
import com.flowcast.app.ui.screens.reproductor.TipoReproduccion
import com.flowcast.app.ui.screens.tv.ModeloVistaTv
import com.flowcast.app.ui.screens.tv.PantallaCanales
import com.flowcast.app.ui.theme.Colores
import kotlinx.coroutines.flow.first

object Rutas {
    const val BIENVENIDA = "bienvenida"
    const val INICIO_SESION = "inicio_sesion"
    const val REGISTRO = "registro"
    const val CARGA = "carga"
    const val SELECCION_PERFIL = "seleccion_perfil"
    const val LISTA_PERFILES = "lista_perfiles"
    const val EDITAR_PERFIL = "perfil/{id}"
    const val INICIO = "inicio"
    const val PELICULA = "pelicula/{id}"
    const val SERIE = "serie/{id}"
    const val CANALES = "canales/{categoriaId}?titulo={titulo}"
    const val FAVORITOS = "favoritos"
    const val MUSICA = "musica"
    const val BUSQUEDA = "busqueda"
    const val CATEGORIA = "categoria/{tipo}/{id}?titulo={titulo}"
    const val TRAILER = "trailer/{id}"
    const val ALBUM = "musica/album/{id}"
    const val REPRODUCTOR_MUSICA = "musica/reproductor"
    const val CANALES_FAVORITOS = "favoritos"
    const val REPRODUCTOR = "reproducir/{tipo}/{id}?serieId={serieId}&titulo={titulo}&portada={portada}"
    const val CONFIGURACION = "configuracion"
    const val PIN_PARENTAL = "configuracion/pin"
    const val CATEGORIAS_BLOQUEADAS = "configuracion/categorias"

    fun pelicula(id: String) = "pelicula/$id"
    fun editarPerfil(id: String) = "perfil/$id"
    fun serie(id: String) = "serie/$id"
    fun album(id: String) = "musica/album/$id"
    fun categoria(tipo: TipoCatalogo, id: String, titulo: String) = "categoria/${tipo.name}/$id?titulo=${Uri.encode(titulo)}"
    fun trailer(id: String) = "trailer/$id"
    fun canales(categoriaId: String, titulo: String) = "canales/$categoriaId?titulo=${Uri.encode(titulo)}"

    fun reproducir(objetivo: ObjetivoReproduccion) = reproducir(objetivo.tipo, objetivo.id, objetivo.titulo, objetivo.serieId, objetivo.portada)

    fun reproducir(tipo: TipoReproduccion, id: String, titulo: String, serieId: String? = null, portada: String? = null): String {
        val codificar = { valor: String -> Uri.encode(valor) }
        return "reproducir/${tipo.valorApi}/$id?serieId=${serieId.orEmpty()}&titulo=${codificar(titulo)}&portada=${codificar(portada.orEmpty())}"
    }
}

private fun NavHostController.irLimpio(ruta: String) = navigate(ruta) { popUpTo(0) { inclusive = true } }

@UnstableApi
@Composable
private fun recargadorInicio(controlador: NavHostController, app: AplicacionFlowCast): () -> Unit {
    val entradaInicio = remember { runCatching { controlador.getBackStackEntry(Rutas.INICIO) }.getOrNull() } ?: return {}
    val vmTv = viewModel<ModeloVistaTv>(
        viewModelStoreOwner = entradaInicio,
        key = "tv",
        factory = ModeloVistaTv.fabrica(app.repositorioCatalogo, app.repositorioConexiones, app.repositorioBiblioteca, app.repositorioControlParental),
    )
    val vmPeliculas = viewModel<ModeloVistaCatalogo>(
        viewModelStoreOwner = entradaInicio,
        key = "catalogo-peliculas",
        factory = ModeloVistaCatalogo.fabrica(TipoCatalogo.PELICULAS, app.repositorioCatalogo, app.repositorioConexiones),
    )
    val vmSeries = viewModel<ModeloVistaCatalogo>(
        viewModelStoreOwner = entradaInicio,
        key = "catalogo-series",
        factory = ModeloVistaCatalogo.fabrica(TipoCatalogo.SERIES, app.repositorioCatalogo, app.repositorioConexiones),
    )
    return {
        vmTv.cargar()
        vmPeliculas.cargar()
        vmSeries.cargar()
    }
}

@UnstableApi
@Composable
fun NavegacionFlowCast(app: AplicacionFlowCast) {
    val contexto = LocalContext.current
    val haySesion by produceState<Boolean?>(initialValue = null) {
        value = !app.almacenSesion.tokenAcceso.first().isNullOrBlank()
    }
    if (haySesion == null) {
        Box(Modifier.fillMaxSize().background(Colores.Fondo))
        return
    }

    val controlador = rememberNavController()
    val destinoTrasBienvenida = if (haySesion == true) Rutas.CARGA else Rutas.INICIO_SESION

    NavHost(navController = controlador, startDestination = Rutas.BIENVENIDA) {
        composable(Rutas.BIENVENIDA) {
            PantallaBienvenida(alTerminar = { controlador.irLimpio(destinoTrasBienvenida) })
        }
        composable(Rutas.INICIO_SESION) {
            PantallaInicioSesion(
                modeloVista = viewModel<ModeloVistaInicioSesion>(factory = ModeloVistaInicioSesion.fabrica(app.repositorioAutenticacion)),
                alEntrar = { controlador.irLimpio(Rutas.CARGA) },
                alIrARegistro = { controlador.navigate(Rutas.REGISTRO) },
            )
        }
        composable(Rutas.REGISTRO) {
            PantallaRegistro(
                modeloVista = viewModel<ModeloVistaRegistro>(factory = ModeloVistaRegistro.fabrica(app.repositorioAutenticacion)),
                alRegistrar = { controlador.irLimpio(Rutas.CARGA) },
                alVolver = { controlador.popBackStack() },
            )
        }
        composable(Rutas.CARGA) {
            PantallaCarga(
                modeloVista = viewModel<ModeloVistaCarga>(
                    factory = ModeloVistaCarga.fabrica(app.repositorioAutenticacion, app.repositorioPerfiles, app.repositorioConexiones),
                ),
                alTerminar = { destino ->
                    when (destino) {
                        DestinoCarga.INICIO_SESION -> controlador.irLimpio(Rutas.INICIO_SESION)
                        DestinoCarga.CONFIRMAR_PERFIL -> controlador.irLimpio(Rutas.SELECCION_PERFIL)
                        DestinoCarga.ELEGIR_PERFIL -> controlador.irLimpio(Rutas.LISTA_PERFILES)
                    }
                },
            )
        }
        composable(Rutas.SELECCION_PERFIL) {
            val perfil by app.almacenSesion.perfilActivo.collectAsState(initial = null)
            val perfilActual = perfil
            if (perfilActual == null) {
                Box(Modifier.fillMaxSize().background(Colores.Fondo))
            } else {
                PantallaSeleccionPerfil(
                    perfil = perfilActual,
                    alContinuar = { controlador.irLimpio(Rutas.INICIO) },
                    alCambiar = { controlador.navigate(Rutas.LISTA_PERFILES) },
                )
            }
        }
        composable(Rutas.LISTA_PERFILES) {
            PantallaListaPerfiles(
                modeloVista = viewModel<ModeloVistaPerfiles>(factory = ModeloVistaPerfiles.fabrica(app.repositorioPerfiles)),
                alSeleccionar = {
                    app.repositorioControlParental.olvidarDesbloqueo()
                    controlador.irLimpio(Rutas.INICIO)
                },
                alEditar = { id -> controlador.navigate(Rutas.editarPerfil(id)) },
            )
        }
        composable(Rutas.EDITAR_PERFIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entrada ->
            val id = entrada.arguments?.getString("id").orEmpty()
            PantallaEditarPerfil(
                modeloVista = viewModel<ModeloVistaEditarPerfil>(key = "editar-perfil-$id", factory = ModeloVistaEditarPerfil.fabrica(id, app.repositorioPerfiles)),
                alVolver = { controlador.popBackStack() },
            )
        }
        composable(Rutas.INICIO) {
            PantallaInicio(
                app = app,
                alAbrirPelicula = { id -> controlador.navigate(Rutas.pelicula(id)) },
                alAbrirSerie = { id -> controlador.navigate(Rutas.serie(id)) },
                alAbrirCategoriaTv = { c -> controlador.navigate(Rutas.canales(c.categoria.id, c.categoria.nombre)) },
                alAbrirCanalesFavoritos = { controlador.navigate(Rutas.canales(Rutas.CANALES_FAVORITOS, "Mis Favoritos")) },
                alAbrirFavoritos = { controlador.navigate(Rutas.FAVORITOS) },
                alReproducir = { objetivo -> controlador.navigate(Rutas.reproducir(objetivo)) },
                alAbrirMusica = { controlador.navigate(Rutas.MUSICA) },
                alAbrirReproductorMusica = { controlador.navigate(Rutas.REPRODUCTOR_MUSICA) },
                alAbrirBusqueda = { controlador.navigate(Rutas.BUSQUEDA) },
                alAbrirCategoria = { tipo, id, titulo -> controlador.navigate(Rutas.categoria(tipo, id, titulo)) },
                alAbrirConfiguracion = { controlador.navigate(Rutas.CONFIGURACION) },
                alReproducirPelicula = { elemento ->
                    controlador.navigate(Rutas.reproducir(TipoReproduccion.PELICULA, elemento.id, elemento.titulo, portada = elemento.portada))
                },
                alCambiarPerfil = { controlador.navigate(Rutas.LISTA_PERFILES) },
                alCerrarSesion = { controlador.irLimpio(Rutas.INICIO_SESION) },
            )
        }
        composable(Rutas.PELICULA, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entrada ->
            val id = entrada.arguments?.getString("id").orEmpty()
            PantallaPelicula(
                modeloVista = viewModel<ModeloVistaPelicula>(
                    key = "pelicula-$id",
                    factory = ModeloVistaPelicula.fabrica(id, app.repositorioCatalogo, app.repositorioBiblioteca, app.repositorioDescargas, app.repositorioControlParental),
                ),
                resenas = viewModel<ModeloVistaResenas>(key = "resenas-pelicula-$id", factory = ModeloVistaResenas.fabrica("pelicula", id, app.repositorioBiblioteca)),
                alVolver = { controlador.popBackStack() },
                alReproducir = { ficha ->
                    controlador.navigate(Rutas.reproducir(TipoReproduccion.PELICULA, ficha.pelicula.id, ficha.pelicula.nombre, portada = ficha.pelicula.portada))
                },
                alAbrirTrailer = { idYoutube -> controlador.navigate(Rutas.trailer(idYoutube)) },
                alAbrirSimilar = { e -> controlador.navigate(Rutas.pelicula(e.id)) },
                alIrConfiguracion = { controlador.navigate(Rutas.CONFIGURACION) },
            )
        }
        composable(Rutas.SERIE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entrada ->
            val id = entrada.arguments?.getString("id").orEmpty()
            PantallaSerie(
                modeloVista = viewModel<ModeloVistaSerie>(
                    key = "serie-$id",
                    factory = ModeloVistaSerie.fabrica(id, app.repositorioCatalogo, app.repositorioBiblioteca, app.repositorioDescargas, app.repositorioControlParental),
                ),
                resenas = viewModel<ModeloVistaResenas>(key = "resenas-serie-$id", factory = ModeloVistaResenas.fabrica("serie", id, app.repositorioBiblioteca)),
                alVolver = { controlador.popBackStack() },
                alReproducir = { ficha, episodio ->
                    val titulo = "${ficha.serie.nombre} · T${episodio.temporada} E${episodio.numero}"
                    controlador.navigate(Rutas.reproducir(TipoReproduccion.EPISODIO, episodio.id, titulo, serieId = ficha.serie.id, portada = ficha.serie.portada))
                },
                alAbrirTrailer = { idYoutube -> controlador.navigate(Rutas.trailer(idYoutube)) },
                alAbrirSimilar = { e -> controlador.navigate(Rutas.serie(e.id)) },
                alIrConfiguracion = { controlador.navigate(Rutas.CONFIGURACION) },
            )
        }
        composable(
            Rutas.CANALES,
            arguments = listOf(navArgument("categoriaId") { type = NavType.StringType }, navArgument("titulo") { type = NavType.StringType; defaultValue = "" }),
        ) { entrada ->
            val categoriaId = entrada.arguments?.getString("categoriaId").orEmpty()
            val entradaInicio = remember(entrada) { controlador.getBackStackEntry(Rutas.INICIO) }
            val vmTv = viewModel<ModeloVistaTv>(
                viewModelStoreOwner = entradaInicio,
                key = "tv",
                factory = ModeloVistaTv.fabrica(app.repositorioCatalogo, app.repositorioConexiones, app.repositorioBiblioteca, app.repositorioControlParental),
            )
            val estadoTv by vmTv.estado.collectAsState()
            val canales = remember(categoriaId, estadoTv) { if (categoriaId == Rutas.CANALES_FAVORITOS) vmTv.canalesFavoritos() else vmTv.canalesDe(categoriaId) }
            PantallaCanales(
                titulo = entrada.arguments?.getString("titulo").orEmpty(),
                canales = canales,
                epgDe = vmTv::epgDe,
                favoritos = estadoTv.favoritos,
                alAlternarFavorito = vmTv::alternarFavorito,
                alVolver = { controlador.popBackStack() },
                alReproducir = { canal ->
                    controlador.navigate(Rutas.reproducir(TipoReproduccion.EN_VIVO, canal.id, canal.nombre, portada = canal.icono))
                },
            )
        }
        composable(
            Rutas.CATEGORIA,
            arguments = listOf(
                navArgument("tipo") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType },
                navArgument("titulo") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entrada ->
            val tipo = TipoCatalogo.valueOf(entrada.arguments?.getString("tipo") ?: TipoCatalogo.PELICULAS.name)
            val id = entrada.arguments?.getString("id").orEmpty()
            val titulo = entrada.arguments?.getString("titulo").orEmpty()
            PantallaCategoria(
                modeloVista = viewModel<ModeloVistaCategoria>(
                    key = "categoria-$tipo-$id",
                    factory = ModeloVistaCategoria.fabrica(tipo, id, titulo, app.repositorioCatalogo, app.repositorioControlParental),
                ),
                titulo = titulo,
                alVolver = { controlador.popBackStack() },
                alAbrir = { e -> controlador.navigate(if (e.esSerie) Rutas.serie(e.id) else Rutas.pelicula(e.id)) },
                alIrConfiguracion = { controlador.navigate(Rutas.CONFIGURACION) },
            )
        }
        composable(Rutas.TRAILER, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entrada ->
            PantallaTrailer(idYoutube = entrada.arguments?.getString("id").orEmpty(), alVolver = { controlador.popBackStack() })
        }
        composable(Rutas.BUSQUEDA) {
            PantallaBusqueda(
                modeloVista = viewModel<ModeloVistaBusqueda>(factory = ModeloVistaBusqueda.fabrica(app.repositorioCatalogo)),
                alVolver = { controlador.popBackStack() },
                alReproducirCanal = { canal -> controlador.navigate(Rutas.reproducir(TipoReproduccion.EN_VIVO, canal.id, canal.nombre, portada = canal.icono)) },
                alAbrirPelicula = { pelicula -> controlador.navigate(Rutas.pelicula(pelicula.id)) },
                alAbrirSerie = { serie -> controlador.navigate(Rutas.serie(serie.id)) },
            )
        }
        composable(Rutas.MUSICA) {
            PantallaMusica(
                modeloVista = viewModel<ModeloVistaMusica>(factory = ModeloVistaMusica.fabrica(app.repositorioMusica)),
                controlador = app.controladorMusica,
                alVolver = { controlador.popBackStack() },
                alAbrirAlbum = { album -> controlador.navigate(Rutas.album(album.id)) },
                alAbrirReproductor = { controlador.navigate(Rutas.REPRODUCTOR_MUSICA) },
            )
        }
        composable(Rutas.ALBUM, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entrada ->
            val id = entrada.arguments?.getString("id").orEmpty()
            PantallaAlbum(
                modeloVista = viewModel<ModeloVistaAlbum>(key = "album-$id", factory = ModeloVistaAlbum.fabrica(id, app.repositorioMusica)),
                controlador = app.controladorMusica,
                alVolver = { controlador.popBackStack() },
                alAbrirReproductor = { controlador.navigate(Rutas.REPRODUCTOR_MUSICA) },
            )
        }
        composable(Rutas.REPRODUCTOR_MUSICA) {
            PantallaReproductorMusica(controlador = app.controladorMusica, alVolver = { controlador.popBackStack() })
        }
        composable(Rutas.FAVORITOS) {
            PantallaFavoritos(
                modeloVista = viewModel<ModeloVistaFavoritos>(factory = ModeloVistaFavoritos.fabrica(app.repositorioBiblioteca)),
                alVolver = { controlador.popBackStack() },
                alAbrir = { favorito ->
                    when (favorito.tipoContenido) {
                        "en_vivo" -> controlador.navigate(Rutas.reproducir(TipoReproduccion.EN_VIVO, favorito.streamId, favorito.titulo, portada = favorito.urlPortada))
                        "pelicula" -> controlador.navigate(Rutas.pelicula(favorito.streamId))
                        else -> controlador.navigate(Rutas.serie(favorito.streamId))
                    }
                },
            )
        }
        composable(
            Rutas.REPRODUCTOR,
            arguments = listOf(
                navArgument("tipo") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType },
                navArgument("serieId") { type = NavType.StringType; defaultValue = "" },
                navArgument("titulo") { type = NavType.StringType; defaultValue = "" },
                navArgument("portada") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entrada ->
            val argumentos = entrada.arguments
            val tipo = TipoReproduccion.entries.first { it.valorApi == argumentos?.getString("tipo") }
            val id = argumentos?.getString("id").orEmpty()
            val orientacionPreferida by produceState<String?>(initialValue = null) { value = app.almacenPreferencias.orientacionReproductor.first() }
            val preferida = orientacionPreferida
            if (preferida == null) {
                Box(Modifier.fillMaxSize().background(Color.Black))
                return@composable
            }
            PantallaReproductor(
                modeloVista = viewModel<ModeloVistaReproductor>(
                    key = "reproductor-${tipo.valorApi}-$id",
                    factory = ModeloVistaReproductor.fabrica(
                        contexto = contexto,
                        tipo = tipo,
                        id = id,
                        serieId = argumentos?.getString("serieId")?.ifBlank { null },
                        titulo = argumentos?.getString("titulo").orEmpty(),
                        portada = argumentos?.getString("portada")?.ifBlank { null },
                        catalogo = app.repositorioCatalogo,
                        biblioteca = app.repositorioBiblioteca,
                        descargas = app.repositorioDescargas,
                        gestorDescargas = app.descargas,
                        controlParental = app.repositorioControlParental,
                        preferencias = app.almacenPreferencias,
                        dlna = app.dlna,
                    ),
                ),
                orientacionPreferida = preferida,
                alVolver = { controlador.popBackStack() },
                alIrConfiguracion = { controlador.navigate(Rutas.CONFIGURACION) },
                alReproducirSiguiente = { objetivo ->
                    controlador.navigate(Rutas.reproducir(objetivo)) { popUpTo(entrada.destination.id) { inclusive = true } }
                },
            )
        }
        composable(Rutas.CONFIGURACION) {
            val recargar = recargadorInicio(controlador, app)
            PantallaConfiguracion(
                modeloVista = viewModel<ModeloVistaConfiguracion>(
                    factory = ModeloVistaConfiguracion.fabrica(app.almacenPreferencias, app.repositorioControlParental, app.repositorioCatalogo),
                ),
                app = app,
                alVolver = { controlador.popBackStack() },
                alActualizarContenido = recargar,
                alConfigurarPin = { controlador.navigate(Rutas.PIN_PARENTAL) },
                alCategoriasBloqueadas = { controlador.navigate(Rutas.CATEGORIAS_BLOQUEADAS) },
                alCambiarPerfil = { controlador.navigate(Rutas.LISTA_PERFILES) },
                alCerrarSesion = { controlador.irLimpio(Rutas.INICIO_SESION) },
            )
        }
        composable(Rutas.PIN_PARENTAL) {
            PantallaPinParental(
                modeloVista = viewModel<ModeloVistaPinParental>(factory = ModeloVistaPinParental.fabrica(app.repositorioControlParental)),
                alVolver = { controlador.popBackStack() },
            )
        }
        composable(Rutas.CATEGORIAS_BLOQUEADAS) {
            val recargar = recargadorInicio(controlador, app)
            val modeloVista = viewModel<ModeloVistaCategoriasBloqueadas>(factory = ModeloVistaCategoriasBloqueadas.fabrica(app.repositorioControlParental))
            DisposableEffect(Unit) { onDispose { if (modeloVista.estado.value.huboCambios) recargar() } }
            PantallaCategoriasBloqueadas(
                modeloVista = modeloVista,
                alVolver = { controlador.popBackStack() },
                alCrearPin = { controlador.navigate(Rutas.PIN_PARENTAL) },
            )
        }
    }
}
