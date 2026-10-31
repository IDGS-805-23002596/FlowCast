package com.flowcast.app.ui.screens.historial

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowcast.app.data.network.RespuestaHistorial
import com.flowcast.app.data.network.RespuestaProgreso
import com.flowcast.app.ui.components.BotonLima
import com.flowcast.app.ui.components.Chip
import com.flowcast.app.ui.components.EstadoVacio
import com.flowcast.app.ui.components.MensajeError
import com.flowcast.app.ui.components.Pildora
import com.flowcast.app.ui.components.Poster
import com.flowcast.app.ui.screens.tv.TituloConMarca
import com.flowcast.app.ui.theme.Colores
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PestanaHistorial(
    modeloVista: ModeloVistaHistorial,
    alReproducirProgreso: (RespuestaProgreso) -> Unit,
    alReproducirEntrada: (RespuestaHistorial) -> Unit,
) {
    val estado by modeloVista.estado.collectAsState()
    LaunchedEffect(Unit) { modeloVista.cargar() }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Historial", style = MaterialTheme.typography.headlineSmall, color = Colores.Texto)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pildora(if (estado.masRecientePrimero) "Más reciente" else "Más antiguo", onClick = modeloVista::alternarOrden, icono = Icons.Outlined.SwapVert)
                Pildora(
                    if (estado.editando) "Listo" else "Editar",
                    onClick = modeloVista::alternarEdicion,
                    icono = if (estado.editando) Icons.Outlined.Check else Icons.Outlined.Edit,
                )
            }
        }
        when {
            estado.cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Colores.Lima) }
            estado.error != null -> Column(Modifier.fillMaxSize().padding(top = 40.dp)) {
                MensajeError(estado.error ?: "")
                Spacer(Modifier.height(16.dp))
                BotonLima("Reintentar", onClick = modeloVista::cargar)
            }
            estado.vacio -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EstadoVacio(Icons.Outlined.History, "No hay historial", Modifier.padding(bottom = 100.dp))
            }
            else -> LazyColumn(contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp)) {
                if (estado.continuar.isNotEmpty()) {
                    item {
                        TituloConMarca("Continuar viendo")
                        Spacer(Modifier.height(14.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(estado.continuar, key = { "${it.tipoContenido}-${it.streamId}" }) { progreso ->
                                TarjetaContinuar(progreso, estado.editando, onClick = { alReproducirProgreso(progreso) }, alBorrar = { modeloVista.borrarProgreso(progreso) })
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                }
                if (estado.historial.isNotEmpty()) {
                    item {
                        TituloConMarca("Visto recientemente")
                        Spacer(Modifier.height(14.dp))
                    }
                    items(estado.historialOrdenado, key = { it.id }) { entrada ->
                        FilaHistorial(entrada, estado.editando, onClick = { alReproducirEntrada(entrada) }, alBorrar = { modeloVista.borrarEntrada(entrada) })
                        Spacer(Modifier.height(10.dp))
                    }
                    if (estado.editando) {
                        item {
                            TextButton(onClick = modeloVista::vaciar, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.Delete, contentDescription = null, tint = Colores.Error, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Vaciar historial", color = Colores.Error, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaContinuar(progreso: RespuestaProgreso, editando: Boolean, onClick: () -> Unit, alBorrar: () -> Unit) {
    val fraccion = progreso.duracionSegundos?.let { (progreso.posicionSegundos / it).toFloat().coerceIn(0f, 1f) } ?: 0f
    val restante = progreso.duracionSegundos?.let { ((it - progreso.posicionSegundos) / 60).toInt().coerceAtLeast(1) }
    Column(
        Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Colores.Superficie)
            .clickable(onClick = onClick),
    ) {
        Box {
            Poster(progreso.urlPortada, Modifier.fillMaxWidth().height(124.dp), RoundedCornerShape(0.dp))
            if (editando) {
                IconButton(onClick = alBorrar, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(CircleShape).background(Color(0xCC111111))) {
                    Icon(Icons.Outlined.Close, contentDescription = "Quitar", tint = Colores.Texto, modifier = Modifier.size(16.dp))
                }
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Colores.Lima, modifier = Modifier.align(Alignment.Center).size(40.dp))
            }
            LinearProgressIndicator(
                progress = { fraccion },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                color = Colores.Lima,
                trackColor = Color(0x66FFFFFF),
                drawStopIndicator = {},
            )
        }
        Column(Modifier.padding(12.dp)) {
            Text(progreso.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Colores.Texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(etiquetaTipo(progreso.tipoContenido), restante?.let { "$it min restantes" }).joinToString("  •  "),
                style = MaterialTheme.typography.labelSmall,
                color = Colores.Texto2,
            )
        }
    }
}

@Composable
private fun FilaHistorial(entrada: RespuestaHistorial, editando: Boolean, onClick: () -> Unit, alBorrar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Colores.Superficie)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(entrada.urlPortada, Modifier.size(width = 56.dp, height = 72.dp), RoundedCornerShape(10.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(entrada.titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Colores.Texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(etiquetaTipo(entrada.tipoContenido))
                Spacer(Modifier.width(10.dp))
                Text(fechaCorta(entrada.reproducidoEn), style = MaterialTheme.typography.labelSmall, color = Colores.Texto2)
            }
        }
        Spacer(Modifier.width(8.dp))
        if (editando) {
            IconButton(onClick = alBorrar) { Icon(Icons.Outlined.Delete, contentDescription = "Borrar", tint = Colores.Error) }
        } else {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Colores.Lima, modifier = Modifier.size(28.dp))
        }
    }
}

private fun etiquetaTipo(tipo: String) = when (tipo) {
    "en_vivo" -> "TV"
    "pelicula" -> "Película"
    "episodio" -> "Episodio"
    else -> tipo
}

private val formatoFecha: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale("es"))

private fun fechaCorta(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(formatoFecha)
}.getOrDefault("")
