package com.flowcast.app.ui.screens.musica

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowcast.app.data.musica.ControladorMusica
import com.flowcast.app.ui.theme.Colores

@Composable
fun PantallaReproductorMusica(controlador: ControladorMusica, alVolver: () -> Unit) {
    val estado by controlador.estado.collectAsState()
    LaunchedEffect(Unit) { controlador.conectar() }
    var arrastrando by remember { mutableStateOf<Float?>(null) }
    val posicion = arrastrando ?: estado.posicionMs.toFloat()
    val duracion = estado.duracionMs.toFloat().coerceAtLeast(1f)

    Column(
        Modifier.fillMaxSize().background(Colores.Fondo).statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = alVolver) { Icon(Icons.Outlined.ExpandMore, contentDescription = "Cerrar", tint = Colores.Texto, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.weight(1f))
            if (estado.total > 0) {
                Text("${estado.indice + 1} de ${estado.total}", style = MaterialTheme.typography.labelSmall, color = Colores.Texto2)
            }
            IconButton(onClick = { controlador.detener(); alVolver() }) { Icon(Icons.Outlined.Stop, contentDescription = "Detener", tint = Colores.Texto2) }
        }
        Spacer(Modifier.weight(1f))
        PortadaUri(estado.portada?.toString(), 300.dp, esquina = 24.dp)
        Spacer(Modifier.height(32.dp))
        Text(estado.titulo.ifBlank { "Nada en reproducción" }, style = MaterialTheme.typography.headlineSmall, color = Colores.Texto, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(listOf(estado.artista, estado.album).filter { it.isNotBlank() }.joinToString("  •  "), style = MaterialTheme.typography.bodyLarge, color = Colores.Texto2, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(28.dp))
        Slider(
            value = posicion.coerceIn(0f, duracion),
            onValueChange = { arrastrando = it },
            onValueChangeFinished = {
                arrastrando?.let { controlador.saltarA(it.toLong()) }
                arrastrando = null
            },
            valueRange = 0f..duracion,
            colors = SliderDefaults.colors(thumbColor = Colores.Lima, activeTrackColor = Colores.Lima, inactiveTrackColor = Colores.Superficie2),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatoMs(posicion.toLong()), style = MaterialTheme.typography.labelSmall, color = Colores.Texto2)
            Text(formatoMs(estado.duracionMs), style = MaterialTheme.typography.labelSmall, color = Colores.Texto2)
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            IconButton(onClick = { controlador.anterior() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", tint = Colores.Texto, modifier = Modifier.size(40.dp))
            }
            Box(Modifier.size(80.dp).clip(CircleShape).background(Colores.Lima), contentAlignment = Alignment.Center) {
                IconButton(onClick = controlador::alternar, modifier = Modifier.size(80.dp)) {
                    Icon(if (estado.reproduciendo) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Reproducir o pausar", tint = Colores.SobreLima, modifier = Modifier.size(44.dp))
                }
            }
            IconButton(onClick = { controlador.siguiente() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente", tint = Colores.Texto, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.weight(1.2f))
    }
}

@Suppress("unused")
private val pesoNegrita = FontWeight.Bold
