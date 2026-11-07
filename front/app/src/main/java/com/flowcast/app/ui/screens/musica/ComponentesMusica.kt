package com.flowcast.app.ui.screens.musica

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.flowcast.app.data.musica.EstadoMusica
import com.flowcast.app.data.network.Cancion
import com.flowcast.app.data.repository.RepositorioMusica
import com.flowcast.app.ui.theme.Colores
import java.util.Locale

@Composable
fun Portada(urlRelativa: String?, tamano: Dp, modifier: Modifier = Modifier, esquina: Dp = 10.dp) {
    Box(modifier.size(tamano).clip(RoundedCornerShape(esquina)).background(Colores.Superficie2), contentAlignment = Alignment.Center) {
        if (urlRelativa == null) {
            Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = Colores.Texto3, modifier = Modifier.size(tamano / 2))
        } else {
            AsyncImage(
                model = RepositorioMusica.urlAbsoluta(urlRelativa),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(Colores.Superficie2),
                error = ColorPainter(Colores.Superficie2),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun FilaCancion(cancion: Cancion, sonando: Boolean, onClick: () -> Unit, numero: Int? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (sonando) Colores.LimaSuave else Colores.Superficie)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (numero != null) {
            Text(
                "$numero",
                style = MaterialTheme.typography.bodyMedium,
                color = if (sonando) Colores.Lima else Colores.Texto2,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(28.dp),
            )
        } else {
            Portada(cancion.urlPortada, 48.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                cancion.titulo,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (sonando) Colores.Lima else Colores.Texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (numero != null) cancion.artista else "${cancion.artista}  •  ${cancion.album}",
                style = MaterialTheme.typography.labelSmall,
                color = Colores.Texto2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(formatoDuracion(cancion.duracionSegundos), style = MaterialTheme.typography.labelSmall, color = Colores.Texto2)
    }
}

@Composable
fun BarraSonando(estado: EstadoMusica, alAlternar: () -> Unit, alSiguiente: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (!estado.haySonando) return
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Colores.Superficie)
            .border(1.dp, Colores.Borde, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PortadaUri(estado.portada?.toString(), 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(estado.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Colores.Texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(estado.artista, style = MaterialTheme.typography.labelSmall, color = Colores.Texto2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = alAlternar) {
            if (estado.cargando) {
                CircularProgressIndicator(color = Colores.Lima, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
            } else {
                Icon(if (estado.reproduciendo) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Reproducir o pausar", tint = Colores.Lima, modifier = Modifier.size(28.dp))
            }
        }
        IconButton(onClick = alSiguiente) { Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente", tint = Colores.Texto) }
    }
}

@Composable
fun PortadaUri(url: String?, tamano: Dp, modifier: Modifier = Modifier, esquina: Dp = 10.dp) {
    Box(modifier.size(tamano).clip(RoundedCornerShape(esquina)).background(Colores.Superficie2), contentAlignment = Alignment.Center) {
        if (url == null) {
            Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = Colores.Texto3, modifier = Modifier.size(tamano / 2))
        } else {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, placeholder = ColorPainter(Colores.Superficie2), error = ColorPainter(Colores.Superficie2), modifier = Modifier.fillMaxSize())
        }
    }
}

fun formatoDuracion(segundos: Double?): String {
    val total = (segundos ?: 0.0).toLong()
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}

fun formatoMs(ms: Long): String = formatoDuracion(ms / 1000.0)

val ALTO_BARRA_SONANDO: Dp = 72.dp

@Composable
fun EspacioBarraSonando(estado: EstadoMusica) {
    if (estado.haySonando) Spacer(Modifier.height(ALTO_BARRA_SONANDO))
}
