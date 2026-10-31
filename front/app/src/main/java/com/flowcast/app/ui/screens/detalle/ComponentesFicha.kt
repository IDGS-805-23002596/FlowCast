package com.flowcast.app.ui.screens.detalle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.flowcast.app.data.network.Actor
import com.flowcast.app.ui.components.ElementoCatalogo
import com.flowcast.app.ui.components.TarjetaContenido
import com.flowcast.app.ui.theme.Colores
import java.util.Locale

enum class PestanaFicha(val etiqueta: String) { INFORMACION("Información"), SIMILARES("Similares"), TRAILER("Tráiler") }

data class AccionFicha(val icono: ImageVector, val etiqueta: String, val activa: Boolean = false, val habilitada: Boolean = true, val onClick: () -> Unit)

@Composable
fun FilaAcciones(acciones: List<AccionFicha>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        acciones.forEach { accion ->
            Column(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = accion.habilitada, onClick = accion.onClick).padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(accion.icono, contentDescription = accion.etiqueta, tint = when { !accion.habilitada -> Colores.Texto3; accion.activa -> Colores.Lima; else -> Colores.Texto }, modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(6.dp))
                Text(accion.etiqueta, style = MaterialTheme.typography.labelSmall, color = if (accion.habilitada) Colores.Texto else Colores.Texto3, maxLines = 1)
            }
        }
    }
}

@Composable
fun PestanasFicha(seleccionada: PestanaFicha, alSeleccionar: (PestanaFicha) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            PestanaFicha.entries.forEach { pestana ->
                val activa = pestana == seleccionada
                Column(Modifier.weight(1f).clickable { alSeleccionar(pestana) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(pestana.etiqueta, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = if (activa) Colores.Texto else Colores.Texto2, modifier = Modifier.padding(vertical = 12.dp))
                    Box(Modifier.fillMaxWidth().height(3.dp).background(if (activa) Colores.Lima else Color.Transparent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Colores.Borde))
    }
}

@Composable
fun LineaMeta(calificacion: Double?, anio: String?, duracionSegundos: Int?, extra: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (calificacion != null && calificacion > 0) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = Colores.Naranja, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(String.format(Locale.US, "%.1f", calificacion), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Colores.Naranja)
            Text("  ·  ", color = Colores.Texto2)
        }
        Text(
            listOfNotNull(anio, duracionSegundos?.let { formatoDuracionLarga(it) }, extra).joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium,
            color = Colores.Texto2,
        )
    }
}

fun formatoDuracionLarga(segundos: Int): String {
    val horas = segundos / 3600
    val minutos = (segundos % 3600) / 60
    return if (horas > 0) "${horas}h ${minutos}min" else "${minutos}min"
}

@Composable
fun FilaChips(textos: List<String>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(textos) { texto ->
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Colores.Superficie2).border(1.dp, Colores.Borde, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(texto, style = MaterialTheme.typography.labelSmall, color = Colores.Texto)
            }
        }
    }
}

fun separarGeneros(genero: String?): List<String> =
    genero?.split("/", ",", "|")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

@Composable
fun SinopsisExpandible(texto: String) {
    var expandida by rememberSaveable { mutableStateOf(false) }
    Column {
        Text(texto, style = MaterialTheme.typography.bodyLarge, color = Colores.Texto2, maxLines = if (expandida) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
        if (texto.length > 120) {
            Text(
                if (expandida) "Ver menos" else "Ver más",
                color = Colores.Lima,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp).clickable { expandida = !expandida },
            )
        }
    }
}

@Composable
fun TituloFicha(texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(Colores.Lima))
        Spacer(Modifier.width(10.dp))
        Text(texto, style = MaterialTheme.typography.titleMedium, color = Colores.Texto)
    }
}

@Composable
fun SeccionElenco(actores: List<Actor>) {
    if (actores.isEmpty()) return
    TituloFicha("Elenco")
    Spacer(Modifier.height(12.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 8.dp)) {
        items(actores, key = { it.nombre }) { actor ->
            Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Colores.Superficie2), contentAlignment = Alignment.Center) {
                    if (actor.foto != null) {
                        AsyncImage(model = actor.foto, contentDescription = null, contentScale = ContentScale.Crop, placeholder = ColorPainter(Colores.Superficie2), error = ColorPainter(Colores.Superficie2), modifier = Modifier.fillMaxSize())
                    } else {
                        Text(actor.iniciales, color = Colores.Texto2, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(actor.nombre, style = MaterialTheme.typography.labelSmall, color = Colores.Texto, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                actor.personaje?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Colores.Texto3, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
fun SeccionSimilares(similares: List<ElementoCatalogo>, cargando: Boolean, alAbrir: (ElementoCatalogo) -> Unit) {
    when {
        cargando -> Text("Buscando títulos parecidos…", color = Colores.Texto2)
        similares.isEmpty() -> Text("No hay títulos parecidos en esta categoría", color = Colores.Texto2)
        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            similares.chunked(2).forEach { fila ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    fila.forEach { elemento -> TarjetaContenido(elemento, onClick = { alAbrir(elemento) }) }
                }
            }
        }
    }
}

fun idYoutube(valor: String?): String? {
    val texto = valor?.trim().orEmpty()
    if (texto.isEmpty()) return null
    val patron = Regex("(?:v=|youtu\\.be/|embed/)([A-Za-z0-9_-]{6,})")
    return patron.find(texto)?.groupValues?.get(1) ?: texto.takeIf { Regex("^[A-Za-z0-9_-]{6,}$").matches(it) }
}

@Composable
fun SeccionTrailer(trailer: String?, alAbrir: (String) -> Unit) {
    val id = idYoutube(trailer)
    if (id == null) {
        Text("Este título no tiene tráiler", color = Colores.Texto2)
        return
    }
    Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Colores.Superficie).clickable { alAbrir(id) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
            AsyncImage(model = "https://img.youtube.com/vi/$id/hqdefault.jpg", contentDescription = null, contentScale = ContentScale.Crop, placeholder = ColorPainter(Colores.Superficie2), error = ColorPainter(Colores.Superficie2), modifier = Modifier.fillMaxSize())
            Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xAA111111)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir tráiler", tint = Colores.Texto, modifier = Modifier.size(40.dp))
            }
        }
        Column(Modifier.padding(14.dp)) {
            Text("Tráiler oficial", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Colores.Texto)
            Text("YouTube", style = MaterialTheme.typography.labelSmall, color = Colores.Texto2)
        }
    }
}
