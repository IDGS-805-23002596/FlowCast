package com.flowcast.app.ui.screens.detalle

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.flowcast.app.data.descargas.EstadoDescarga
import com.flowcast.app.data.network.FichaPelicula
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.flowcast.app.ui.components.BotonLima
import com.flowcast.app.ui.components.Chip
import com.flowcast.app.ui.components.DialogosParentales
import com.flowcast.app.ui.components.DialogoEliminarDescarga
import com.flowcast.app.ui.components.ElementoCatalogo
import com.flowcast.app.ui.components.MensajeError
import com.flowcast.app.ui.components.Poster
import com.flowcast.app.ui.components.rememberEjecutarConPermisoNotificaciones
import com.flowcast.app.ui.theme.Colores

@OptIn(UnstableApi::class)
@Composable
fun PantallaPelicula(
    modeloVista: ModeloVistaPelicula,
    resenas: ModeloVistaResenas,
    alVolver: () -> Unit,
    alReproducir: (FichaPelicula) -> Unit,
    alAbrirTrailer: (String) -> Unit,
    alAbrirSimilar: (ElementoCatalogo) -> Unit,
    alIrConfiguracion: () -> Unit,
) {
    val estado by modeloVista.estado.collectAsState()
    val contexto = LocalContext.current
    LaunchedEffect(estado.aviso) {
        estado.aviso?.let {
            Toast.makeText(contexto, it, Toast.LENGTH_LONG).show()
            modeloVista.limpiarAviso()
        }
    }
    Box(Modifier.fillMaxSize().background(Colores.Fondo)) {
        when {
            estado.cargando -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Colores.Lima)
            estado.error != null -> Column(Modifier.align(Alignment.Center).padding(40.dp)) {
                MensajeError(estado.error ?: "")
                Spacer(Modifier.height(16.dp))
                BotonLima("Reintentar", onClick = modeloVista::cargar)
            }
            else -> estado.datos?.let { ficha -> ContenidoPelicula(ficha, modeloVista, resenas, alReproducir, alAbrirTrailer, alAbrirSimilar) }
        }
        IconButton(onClick = alVolver, modifier = Modifier.statusBarsPadding().padding(8.dp).clip(CircleShape).background(Color(0x99111111))) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver", tint = Colores.Texto)
        }
        if (estado.datos != null) {
            IconButton(
                onClick = modeloVista::alternarFavorito,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp).clip(CircleShape).background(Color(0x99111111)),
            ) {
                Icon(
                    if (estado.favorito) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (estado.favorito) "Quitar de Mi Lista" else "Añadir a Mi Lista",
                    tint = if (estado.favorito) Colores.Lima else Colores.Texto,
                )
            }
        }
    }
    DialogosParentales(
        pin = estado.pin,
        alCancelar = {
            modeloVista.cancelarDesbloqueo()
            if (estado.datos == null) alVolver()
        },
        alConfirmar = modeloVista::desbloquear,
        alIrConfiguracion = alIrConfiguracion,
        descripcion = if (estado.datos == null) "Esta película está bloqueada. Escribe tu PIN de 4 dígitos para verla." else "Esta película está bloqueada. Escribe tu PIN de 4 dígitos para descargarla.",
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun ContenidoPelicula(
    ficha: FichaPelicula,
    modeloVista: ModeloVistaPelicula,
    resenas: ModeloVistaResenas,
    alReproducir: (FichaPelicula) -> Unit,
    alAbrirTrailer: (String) -> Unit,
    alAbrirSimilar: (ElementoCatalogo) -> Unit,
) {
    val pelicula = ficha.pelicula
    val estado by modeloVista.estado.collectAsState()
    val descarga by modeloVista.descarga.collectAsState()
    val conPermiso = rememberEjecutarConPermisoNotificaciones()
    var pestana by rememberSaveable { mutableStateOf(PestanaFicha.INFORMACION) }
    var confirmarBorrado by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pestana) { if (pestana == PestanaFicha.SIMILARES) modeloVista.cargarSimilares() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(260.dp)) {
            AsyncImage(model = ficha.fondo ?: ficha.portadaGrande ?: pelicula.portada, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Colores.Fondo)))
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Poster(pelicula.portada, Modifier.width(100.dp).height(150.dp), RoundedCornerShape(12.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.padding(bottom = 6.dp)) {
                    Text(pelicula.nombre, style = MaterialTheme.typography.headlineSmall, color = Colores.Texto, maxLines = 3)
                    if (pelicula.bloqueada) {
                        Spacer(Modifier.height(6.dp))
                        Chip("Requiere PIN")
                    }
                    Spacer(Modifier.height(8.dp))
                    LineaMeta(pelicula.calificacion, ficha.fechaEstreno?.take(4) ?: pelicula.agregadoEn?.take(4), ficha.duracionSegundos)
                }
            }
            Spacer(Modifier.height(14.dp))
            FilaChips(listOfNotNull(pelicula.extension?.uppercase()) + separarGeneros(ficha.genero))
            Spacer(Modifier.height(16.dp))
            BotonLima("Reproducir", onClick = { alReproducir(ficha) }, icono = Icons.Filled.PlayArrow)
            ficha.sinopsis?.let {
                Spacer(Modifier.height(16.dp))
                SinopsisExpandible(it)
            }
            Spacer(Modifier.height(16.dp))

            val idTrailer = idYoutube(ficha.trailerYoutube)
            val (iconoDescarga, etiquetaDescarga) = when (descarga?.estado) {
                EstadoDescarga.COMPLETADA -> Icons.Outlined.CheckCircle to "Descargado"
                EstadoDescarga.DESCARGANDO, EstadoDescarga.EN_COLA -> Icons.Outlined.Downloading to "${descarga?.porcentaje?.coerceAtLeast(0f)?.toInt() ?: 0} %"
                else -> Icons.Outlined.Download to "Descargar"
            }
            FilaAcciones(
                listOf(
                    AccionFicha(if (estado.favorito) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Mi Lista", activa = estado.favorito) { modeloVista.alternarFavorito() },
                    AccionFicha(Icons.Outlined.StarOutline, "Calificar") { pestana = PestanaFicha.INFORMACION },
                    AccionFicha(iconoDescarga, etiquetaDescarga, activa = descarga?.estado == EstadoDescarga.COMPLETADA) {
                        when (descarga?.estado) {
                            EstadoDescarga.COMPLETADA -> confirmarBorrado = true
                            EstadoDescarga.DESCARGANDO, EstadoDescarga.EN_COLA -> modeloVista.eliminarDescarga()
                            else -> conPermiso(modeloVista::descargar)
                        }
                    },
                    AccionFicha(Icons.Outlined.PlayCircle, "Tráiler", habilitada = idTrailer != null) { idTrailer?.let(alAbrirTrailer) },
                ),
            )
            Spacer(Modifier.height(12.dp))
        }

        PestanasFicha(pestana) { pestana = it }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(20.dp))
            when (pestana) {
                PestanaFicha.INFORMACION -> {
                    SeccionElenco(estado.elenco)
                    if (estado.elenco.isNotEmpty()) Spacer(Modifier.height(24.dp))
                    val generos = separarGeneros(ficha.genero)
                    if (generos.isNotEmpty()) {
                        TituloFicha("Géneros")
                        Spacer(Modifier.height(10.dp))
                        FilaChips(generos)
                        Spacer(Modifier.height(24.dp))
                    }
                    ficha.director?.let {
                        Row {
                            Text("Director: ", style = MaterialTheme.typography.bodyMedium, color = Colores.Texto, fontWeight = FontWeight.Bold)
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = Colores.Texto2)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                    SeccionResenas(resenas, pelicula.nombre)
                }
                PestanaFicha.SIMILARES -> SeccionSimilares(estado.similares, estado.cargandoSimilares, alAbrirSimilar)
                PestanaFicha.TRAILER -> SeccionTrailer(ficha.trailerYoutube, alAbrirTrailer)
            }
            Spacer(Modifier.height(48.dp))
        }
    }
    if (confirmarBorrado) {
        DialogoEliminarDescarga(alCancelar = { confirmarBorrado = false }, alConfirmar = { confirmarBorrado = false; modeloVista.eliminarDescarga() })
    }
}
