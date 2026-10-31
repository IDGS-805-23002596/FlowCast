package com.flowcast.app.ui.screens.detalle

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.flowcast.app.data.descargas.Descarga
import com.flowcast.app.data.network.Episodio
import com.flowcast.app.data.network.FichaSerie
import com.flowcast.app.ui.components.BotonLima
import com.flowcast.app.ui.components.Chip
import android.widget.Toast
import com.flowcast.app.ui.components.DialogosParentales
import androidx.compose.ui.platform.LocalContext
import com.flowcast.app.ui.components.ElementoCatalogo
import com.flowcast.app.ui.components.IconoDescarga
import com.flowcast.app.ui.components.MensajeError
import com.flowcast.app.ui.components.Poster
import com.flowcast.app.ui.components.rememberEjecutarConPermisoNotificaciones
import com.flowcast.app.ui.theme.Colores

@OptIn(UnstableApi::class)
@Composable
fun PantallaSerie(
    modeloVista: ModeloVistaSerie,
    resenas: ModeloVistaResenas,
    alVolver: () -> Unit,
    alReproducir: (FichaSerie, Episodio) -> Unit,
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
            else -> estado.datos?.let { ficha -> ContenidoSerie(ficha, modeloVista, resenas, alReproducir, alAbrirTrailer, alAbrirSimilar) }
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
        descripcion = if (estado.datos == null) "Esta serie está bloqueada. Escribe tu PIN de 4 dígitos para verla." else "Esta serie está bloqueada. Escribe tu PIN de 4 dígitos para descargar el episodio.",
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun ContenidoSerie(
    ficha: FichaSerie,
    modeloVista: ModeloVistaSerie,
    resenas: ModeloVistaResenas,
    alReproducir: (FichaSerie, Episodio) -> Unit,
    alAbrirTrailer: (String) -> Unit,
    alAbrirSimilar: (ElementoCatalogo) -> Unit,
) {
    val serie = ficha.serie
    val estado by modeloVista.estado.collectAsState()
    val descargas by modeloVista.descargasEpisodios.collectAsState()
    val conPermiso = rememberEjecutarConPermisoNotificaciones()
    var pestana by rememberSaveable { mutableStateOf(PestanaFicha.INFORMACION) }
    var temporadaElegida by remember { mutableIntStateOf(0) }
    val temporada = ficha.temporadas.getOrNull(temporadaElegida)
    val primero = ficha.temporadas.firstOrNull()?.episodios?.firstOrNull()
    val idTrailer = idYoutube(ficha.trailerYoutube)
    LaunchedEffect(pestana) { if (pestana == PestanaFicha.SIMILARES) modeloVista.cargarSimilares() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(260.dp)) {
                AsyncImage(model = ficha.fondo ?: serie.portada, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Colores.Fondo)))
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Poster(serie.portada, Modifier.width(100.dp).height(150.dp), RoundedCornerShape(12.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.padding(bottom = 6.dp)) {
                        Text(serie.nombre, style = MaterialTheme.typography.headlineSmall, color = Colores.Texto, maxLines = 3)
                        if (serie.bloqueada) {
                            Spacer(Modifier.height(6.dp))
                            Chip("Requiere PIN")
                        }
                        Spacer(Modifier.height(8.dp))
                        LineaMeta(serie.calificacion, serie.fechaEstreno?.take(4), null, extra = "${ficha.temporadas.size} temporadas")
                    }
                }
                Spacer(Modifier.height(14.dp))
                FilaChips(separarGeneros(serie.genero))
                Spacer(Modifier.height(16.dp))
                if (primero != null) BotonLima("Reproducir", onClick = { alReproducir(ficha, primero) }, icono = Icons.Filled.PlayArrow)
                serie.sinopsis?.let {
                    Spacer(Modifier.height(16.dp))
                    SinopsisExpandible(it)
                }
                Spacer(Modifier.height(16.dp))
                FilaAcciones(
                    listOf(
                        AccionFicha(if (estado.favorito) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Mi Lista", activa = estado.favorito) { modeloVista.alternarFavorito() },
                        AccionFicha(Icons.Outlined.StarOutline, "Calificar") { pestana = PestanaFicha.INFORMACION },
                        AccionFicha(Icons.Outlined.PlayCircle, "Tráiler", habilitada = idTrailer != null) { idTrailer?.let(alAbrirTrailer) },
                    ),
                )
                Spacer(Modifier.height(12.dp))
            }
            PestanasFicha(pestana) { pestana = it }
            Spacer(Modifier.height(20.dp))
        }
        when (pestana) {
            PestanaFicha.INFORMACION -> {
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        SeccionElenco(estado.elenco)
                        if (estado.elenco.isNotEmpty()) Spacer(Modifier.height(24.dp))
                        val generos = separarGeneros(serie.genero)
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
                        TituloFicha("Temporadas")
                        Spacer(Modifier.height(12.dp))
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(ficha.temporadas.size) { indice ->
                            val elegida = indice == temporadaElegida
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (elegida) Colores.Lima else Colores.Superficie2)
                                    .border(1.dp, if (elegida) Colores.Lima else Colores.Borde, RoundedCornerShape(20.dp))
                                    .clickable { temporadaElegida = indice }
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    ficha.temporadas[indice].nombre ?: "Temporada ${ficha.temporadas[indice].numero}",
                                    color = if (elegida) Colores.SobreLima else Colores.Texto,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                temporada?.let { t ->
                    items(t.episodios, key = { it.id }) { episodio ->
                        FilaEpisodio(
                            episodio,
                            descarga = descargas[episodio.id],
                            onClick = { alReproducir(ficha, episodio) },
                            alDescargar = { conPermiso { modeloVista.descargarEpisodio(episodio) } },
                            alEliminar = { modeloVista.eliminarDescarga(episodio) },
                        )
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Spacer(Modifier.height(28.dp))
                        SeccionResenas(resenas, serie.nombre)
                    }
                }
            }
            PestanaFicha.SIMILARES -> item {
                Box(Modifier.padding(horizontal = 20.dp)) { SeccionSimilares(estado.similares, estado.cargandoSimilares, alAbrirSimilar) }
            }
            PestanaFicha.TRAILER -> item {
                Box(Modifier.padding(horizontal = 20.dp)) { SeccionTrailer(ficha.trailerYoutube, alAbrirTrailer) }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun FilaEpisodio(episodio: Episodio, descarga: Descarga?, onClick: () -> Unit, alDescargar: () -> Unit, alEliminar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Poster(episodio.imagen, Modifier.width(128.dp).height(72.dp), RoundedCornerShape(10.dp))
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Colores.Lima, modifier = Modifier.align(Alignment.Center).size(32.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("T${episodio.temporada} · E${episodio.numero}", style = MaterialTheme.typography.labelSmall, color = Colores.Lima)
            Spacer(Modifier.height(4.dp))
            Text(episodio.titulo, style = MaterialTheme.typography.bodyMedium, color = Colores.Texto, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            episodio.duracionSegundos?.let {
                Spacer(Modifier.height(4.dp))
                Text("${it / 60} min", style = MaterialTheme.typography.bodyMedium, color = Colores.Texto2)
            }
        }
        Spacer(Modifier.width(8.dp))
        episodio.extension?.let { Chip(it.uppercase()) }
        IconoDescarga(descarga, alDescargar = alDescargar, alEliminar = alEliminar)
    }
}
