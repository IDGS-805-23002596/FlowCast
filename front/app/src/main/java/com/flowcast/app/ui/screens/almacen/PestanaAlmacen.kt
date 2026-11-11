package com.flowcast.app.ui.screens.almacen

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.flowcast.app.data.descargas.Descarga
import com.flowcast.app.data.descargas.EstadoDescarga
import com.flowcast.app.data.descargas.RepositorioDescargas
import com.flowcast.app.ui.components.EstadoVacio
import com.flowcast.app.ui.components.DialogoEliminarDescarga
import com.flowcast.app.ui.components.Poster
import com.flowcast.app.ui.components.formatearBytes
import com.flowcast.app.ui.screens.reproductor.ObjetivoReproduccion
import com.flowcast.app.ui.theme.Colores

@OptIn(UnstableApi::class)
@Composable
fun PestanaAlmacen(descargas: RepositorioDescargas, alReproducir: (ObjetivoReproduccion) -> Unit) {
    val lista by descargas.descargas.collectAsState()
    val contexto = LocalContext.current

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Almacén", style = MaterialTheme.typography.headlineSmall, color = Colores.Texto)
            Spacer(Modifier.width(12.dp))
            if (lista.isNotEmpty()) {
                Text(
                    "${lista.size} ${if (lista.size == 1) "descarga" else "descargas"}  •  ${formatearBytes(lista.sumOf { it.bytes })}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Colores.Texto2,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        if (lista.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EstadoVacio(Icons.Outlined.Inventory2, "No hay contenido sincronizado", Modifier.padding(bottom = 100.dp))
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 20.dp, bottom = 120.dp)) {
                items(lista, key = { it.id }) { descarga ->
                    FilaDescarga(
                        descarga,
                        onClick = {
                            if (descarga.estado == EstadoDescarga.COMPLETADA) alReproducir(descarga.objetivo)
                            else Toast.makeText(contexto, "Todavía se está descargando", Toast.LENGTH_SHORT).show()
                        },
                        alReintentar = { descargas.descargar(descarga.uri, descarga.metadatos) },
                        alEliminar = { descargas.eliminar(descarga.id) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun FilaDescarga(descarga: Descarga, onClick: () -> Unit, alReintentar: () -> Unit, alEliminar: () -> Unit) {
    var confirmarBorrado by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Colores.Superficie)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(descarga.metadatos.portada, Modifier.size(width = 56.dp, height = 72.dp), RoundedCornerShape(10.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(descarga.metadatos.titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Colores.Texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            descarga.metadatos.subtitulo?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Colores.Texto2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            when (descarga.estado) {
                EstadoDescarga.COMPLETADA -> Text("Listo para ver sin conexión  •  ${formatearBytes(descarga.bytes)}", style = MaterialTheme.typography.labelSmall, color = Colores.Lima)
                EstadoDescarga.FALLIDA -> Text("Falló la descarga. Toca el icono para reintentar", style = MaterialTheme.typography.labelSmall, color = Colores.Error)
                EstadoDescarga.DETENIDA -> Text("Detenida", style = MaterialTheme.typography.labelSmall, color = Colores.Texto2)
                else -> {
                    Text(
                        if (descarga.porcentaje >= 0) "Descargando ${descarga.porcentaje.toInt()} %  •  ${formatearBytes(descarga.bytes)}" else "Preparando…",
                        style = MaterialTheme.typography.labelSmall,
                        color = Colores.Texto2,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (descarga.porcentaje / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                        color = Colores.Lima,
                        trackColor = Colores.Superficie2,
                        drawStopIndicator = {},
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        when (descarga.estado) {
            EstadoDescarga.COMPLETADA -> Icon(Icons.Outlined.CheckCircle, contentDescription = "Descargado", tint = Colores.Lima, modifier = Modifier.size(24.dp))
            EstadoDescarga.FALLIDA, EstadoDescarga.DETENIDA -> IconButton(onClick = alReintentar, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Reintentar", tint = Colores.Texto2)
            }
            else -> if (descarga.porcentaje >= 0 && descarga.estado == EstadoDescarga.DESCARGANDO) {
                CircularProgressIndicator(progress = { descarga.porcentaje / 100f }, modifier = Modifier.size(22.dp), color = Colores.Lima, trackColor = Colores.Superficie2, strokeWidth = 2.5.dp)
            } else {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Colores.Lima, strokeWidth = 2.5.dp)
            }
        }
        IconButton(onClick = { confirmarBorrado = true }) {
            Icon(Icons.Outlined.Delete, contentDescription = "Eliminar descarga", tint = Colores.Error)
        }
    }
    if (confirmarBorrado) {
        DialogoEliminarDescarga(alCancelar = { confirmarBorrado = false }, alConfirmar = { confirmarBorrado = false; alEliminar() })
    }
}
