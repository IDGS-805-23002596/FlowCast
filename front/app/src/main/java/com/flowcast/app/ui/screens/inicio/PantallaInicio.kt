package com.flowcast.app.ui.screens.inicio

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowcast.app.AplicacionFlowCast
import com.flowcast.app.data.network.RespuestaConexion
import com.flowcast.app.data.network.mensajeDeError
import com.flowcast.app.data.network.Categoria
import com.flowcast.app.ui.components.BarraInferior
import com.flowcast.app.ui.components.DialogosParentales
import com.flowcast.app.ui.components.HojaCategorias
import com.flowcast.app.ui.screens.categoria.CATEGORIA_TODAS
import com.flowcast.app.ui.components.BarraSuperior
import com.flowcast.app.ui.components.ElementoCatalogo
import com.flowcast.app.ui.components.Pestana
import com.flowcast.app.ui.screens.almacen.PestanaAlmacen
import com.flowcast.app.ui.screens.historial.ModeloVistaHistorial
import com.flowcast.app.ui.screens.musica.BarraSonando
import com.flowcast.app.ui.screens.historial.PestanaHistorial
import com.flowcast.app.ui.screens.reproductor.ObjetivoReproduccion
import com.flowcast.app.ui.screens.tv.CategoriaTv
import com.flowcast.app.ui.screens.tv.ModeloVistaTv
import com.flowcast.app.ui.screens.tv.PestanaTv
import com.flowcast.app.ui.theme.Colores
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun PantallaInicio(
    app: AplicacionFlowCast,
    alAbrirPelicula: (String) -> Unit,
    alAbrirSerie: (String) -> Unit,
    alReproducirPelicula: (ElementoCatalogo) -> Unit,
    alAbrirCategoriaTv: (CategoriaTv) -> Unit,
    alAbrirCanalesFavoritos: () -> Unit,
    alAbrirFavoritos: () -> Unit,
    alReproducir: (ObjetivoReproduccion) -> Unit,
    alAbrirMusica: () -> Unit,
    alAbrirReproductorMusica: () -> Unit,
    alAbrirBusqueda: () -> Unit,
    alAbrirCategoria: (TipoCatalogo, String, String) -> Unit,
    alAbrirConfiguracion: () -> Unit,
    alCambiarPerfil: () -> Unit,
    alCerrarSesion: () -> Unit,
) {
    val contexto = LocalContext.current
    val perfil by app.almacenSesion.perfilActivo.collectAsState(initial = null)
    val pestanaInicial by produceState<Pestana?>(initialValue = null) {
        value = runCatching { Pestana.valueOf(app.almacenPreferencias.pestanaInicial.first()) }.getOrDefault(Pestana.PELICULAS)
    }
    val inicial = pestanaInicial
    if (inicial == null) {
        Box(Modifier.fillMaxSize().background(Colores.Fondo))
        return
    }
    var pestana by rememberSaveable { mutableStateOf(inicial) }
    var menuAbierto by rememberSaveable { mutableStateOf(false) }
    var conexionesAbierto by rememberSaveable { mutableStateOf(false) }
    var menuPrincipalAbierto by rememberSaveable { mutableStateOf(false) }
    val musica by app.controladorMusica.estado.collectAsState()
    LaunchedEffect(Unit) { app.controladorMusica.conectar() }
    val alcance = rememberCoroutineScope()

    val vmPeliculas = viewModel<ModeloVistaCatalogo>(
        key = "catalogo-peliculas",
        factory = ModeloVistaCatalogo.fabrica(TipoCatalogo.PELICULAS, app.repositorioCatalogo, app.repositorioConexiones),
    )
    val vmSeries = viewModel<ModeloVistaCatalogo>(
        key = "catalogo-series",
        factory = ModeloVistaCatalogo.fabrica(TipoCatalogo.SERIES, app.repositorioCatalogo, app.repositorioConexiones),
    )
    val vmTv = viewModel<ModeloVistaTv>(
        key = "tv",
        factory = ModeloVistaTv.fabrica(app.repositorioCatalogo, app.repositorioConexiones, app.repositorioBiblioteca, app.repositorioControlParental),
    )
    val estadoTvGlobal by vmTv.estado.collectAsState()
    LaunchedEffect(estadoTvGlobal.abrir) {
        estadoTvGlobal.abrir?.let {
            vmTv.abrirConsumido()
            alAbrirCategoriaTv(it)
        }
    }
    LaunchedEffect(estadoTvGlobal.aviso) {
        estadoTvGlobal.aviso?.let {
            Toast.makeText(contexto, it, Toast.LENGTH_SHORT).show()
            vmTv.limpiarAviso()
        }
    }
    val vmHistorial = viewModel<ModeloVistaHistorial>(key = "historial", factory = ModeloVistaHistorial.fabrica(app.repositorioBiblioteca))
    val abrirElemento: (ElementoCatalogo) -> Unit = { if (it.esSerie) alAbrirSerie(it.id) else alAbrirPelicula(it.id) }
    val reproducirElemento: (ElementoCatalogo) -> Unit = { if (it.esSerie) alAbrirSerie(it.id) else alReproducirPelicula(it) }

    Box(Modifier.fillMaxSize().background(Colores.Fondo)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            BarraSuperior(
                perfil = perfil,
                alTocarAvatar = { menuAbierto = true },
                alRefrescar = {
                    when (pestana) {
                        Pestana.TV -> vmTv.cargar()
                        Pestana.PELICULAS -> vmPeliculas.cargar()
                        Pestana.SERIES -> vmSeries.cargar()
                        Pestana.HISTORIAL -> vmHistorial.cargar()
                        else -> Toast.makeText(contexto, "Nada que recargar aquí", Toast.LENGTH_SHORT).show()
                    }
                },
                alCambiarConexion = { conexionesAbierto = true },
                alFavoritos = alAbrirFavoritos,
                alMenu = { menuPrincipalAbierto = true },
                alBuscar = alAbrirBusqueda,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (pestana) {
                    Pestana.TV -> PestanaTv(
                        vmTv,
                        alAbrirCategoria = vmTv::solicitarAbrir,
                        alAbrirFavoritos = alAbrirCanalesFavoritos,
                        alMultipantalla = { Toast.makeText(contexto, "Multipantalla llegará más adelante", Toast.LENGTH_SHORT).show() },
                    )
                    Pestana.PELICULAS -> PestanaCatalogo(vmPeliculas, abrirElemento, reproducirElemento, alVerCategoria = { c -> alAbrirCategoria(TipoCatalogo.PELICULAS, c.id, c.nombre) })
                    Pestana.SERIES -> PestanaCatalogo(vmSeries, abrirElemento, reproducirElemento, alVerCategoria = { c -> alAbrirCategoria(TipoCatalogo.SERIES, c.id, c.nombre) })
                    Pestana.HISTORIAL -> PestanaHistorial(
                        vmHistorial,
                        alReproducirProgreso = { alReproducir(vmHistorial.objetivoDe(it)) },
                        alReproducirEntrada = { entrada ->
                            alcance.launch {
                                val objetivo = vmHistorial.objetivoDe(entrada)
                                if (objetivo != null) alReproducir(objetivo) else Toast.makeText(contexto, "Abre la serie para ver este episodio", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    Pestana.ALMACEN -> PestanaAlmacen(app.repositorioDescargas, alReproducir)
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter)) {
            BarraSonando(
                musica,
                alAlternar = app.controladorMusica::alternar,
                alSiguiente = { app.controladorMusica.siguiente() },
                onClick = alAbrirReproductorMusica,
            )
            if (musica.haySonando) Spacer(Modifier.height(10.dp))
            BarraInferior(activa = pestana, alSeleccionar = { pestana = it })
        }
    }

    if (menuAbierto) {
        AlertDialog(
            onDismissRequest = { menuAbierto = false },
            containerColor = Colores.Superficie,
            titleContentColor = Colores.Texto,
            title = { Text(perfil?.nombre ?: "Perfil") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OpcionMenu(Icons.Outlined.MusicNote, "Música", "Álbumes y canciones del servidor") { menuAbierto = false; alAbrirMusica() }
                    OpcionMenu(Icons.Outlined.Settings, "Configuración", "PIN parental, video y más") { menuAbierto = false; alAbrirConfiguracion() }
                    OpcionMenu(Icons.Outlined.SwitchAccount, "Cambiar perfil", "Elegir otro perfil de la cuenta") { menuAbierto = false; alCambiarPerfil() }
                    OpcionMenu(Icons.AutoMirrored.Outlined.Logout, "Cerrar sesión", "Salir de esta cuenta") {
                        menuAbierto = false
                        alcance.launch {
                            app.repositorioAutenticacion.cerrarSesion()
                            app.repositorioControlParental.olvidarDesbloqueo()
                            alCerrarSesion()
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { menuAbierto = false }) { Text("Cerrar", color = Colores.Texto2) } },
        )
    }

    if (conexionesAbierto) {
        DialogoConexiones(app, alCerrar = { conexionesAbierto = false })
    }

    DialogosParentales(
        pin = estadoTvGlobal.pin,
        alCancelar = vmTv::cancelarDesbloqueo,
        alConfirmar = vmTv::desbloquear,
        alIrConfiguracion = alAbrirConfiguracion,
        descripcion = "Esta categoría está bloqueada. Escribe tu PIN de 4 dígitos para ver sus canales.",
    )

    if (menuPrincipalAbierto) {
        val estadoTv by vmTv.estado.collectAsState()
        val estadoPeliculas by vmPeliculas.estado.collectAsState()
        val estadoSeries by vmSeries.estado.collectAsState()
        when (pestana) {
            Pestana.TV -> HojaCategorias(
                tituloTipo = "canales",
                categorias = estadoTv.categorias.map { c -> c.categoria.copy(total = c.totalCanales) },
                totalTodos = estadoTv.totalCanales,
                alElegir = { c ->
                    menuPrincipalAbierto = false
                    vmTv.solicitarAbrir(CategoriaTv(c ?: Categoria(CATEGORIA_TODAS, "Todos los canales"), c?.total ?: estadoTv.totalCanales))
                },
                alCerrar = { menuPrincipalAbierto = false },
            )
            Pestana.PELICULAS -> HojaCategorias(
                tituloTipo = "películas",
                categorias = estadoPeliculas.categorias,
                totalTodos = estadoPeliculas.categorias.sumOf { it.total ?: 0 },
                alElegir = { c -> menuPrincipalAbierto = false; alAbrirCategoria(TipoCatalogo.PELICULAS, c?.id ?: CATEGORIA_TODAS, c?.nombre ?: "Todas las películas") },
                alCerrar = { menuPrincipalAbierto = false },
            )
            Pestana.SERIES -> HojaCategorias(
                tituloTipo = "series",
                categorias = estadoSeries.categorias,
                totalTodos = estadoSeries.categorias.sumOf { it.total ?: 0 },
                alElegir = { c -> menuPrincipalAbierto = false; alAbrirCategoria(TipoCatalogo.SERIES, c?.id ?: CATEGORIA_TODAS, c?.nombre ?: "Todas las series") },
                alCerrar = { menuPrincipalAbierto = false },
            )
            else -> LaunchedEffect(Unit) {
                Toast.makeText(contexto, "Esta pestaña no tiene categorías", Toast.LENGTH_SHORT).show()
                menuPrincipalAbierto = false
            }
        }
    }
}

@Composable
private fun OpcionMenu(icono: androidx.compose.ui.graphics.vector.ImageVector, titulo: String, detalle: String, habilitada: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Colores.Superficie2)
            .clickable(enabled = habilitada, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icono, contentDescription = null, tint = if (habilitada) Colores.Lima else Colores.Texto3)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(titulo, color = if (habilitada) Colores.Texto else Colores.Texto3, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(detalle, color = Colores.Texto2, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun DialogoConexiones(app: AplicacionFlowCast, alCerrar: () -> Unit) {
    val activaId by app.repositorioConexiones.conexionActivaId.collectAsState(initial = null)
    var conexiones by remember { mutableStateOf<List<RespuestaConexion>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val alcance = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        app.repositorioConexiones.listar().onSuccess { conexiones = it }.onFailure { error = mensajeDeError(it) }
    }
    AlertDialog(
        onDismissRequest = alCerrar,
        containerColor = Colores.Superficie,
        titleContentColor = Colores.Texto,
        title = { Text("Proveedor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    error != null -> Text(error ?: "", color = Colores.Error)
                    conexiones == null -> CircularProgressIndicator(color = Colores.Lima, modifier = Modifier.size(28.dp))
                    else -> conexiones.orEmpty().forEach { conexion ->
                        val esActiva = conexion.id == activaId
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (esActiva) Colores.LimaSuave else Colores.Superficie2)
                                .clickable {
                                    alcance.launch { app.repositorioConexiones.seleccionar(conexion.id); alCerrar() }
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(conexion.etiqueta, color = Colores.Texto, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${conexion.urlBase}  •  ${conexion.estadoCuenta}",
                                    color = Colores.Texto2,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            if (esActiva) {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Outlined.CheckCircle, contentDescription = "Activa", tint = Colores.Lima)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cerrar", color = Colores.Texto2) } },
    )
}
