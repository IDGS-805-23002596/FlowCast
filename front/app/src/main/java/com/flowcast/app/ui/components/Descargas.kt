package com.flowcast.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.flowcast.app.data.descargas.Descarga
import com.flowcast.app.data.descargas.EstadoDescarga
import com.flowcast.app.ui.theme.Colores

@Composable
fun rememberEjecutarConPermisoNotificaciones(): (() -> Unit) -> Unit {
    val contexto = LocalContext.current
    var pendiente by remember { mutableStateOf<(() -> Unit)?>(null) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendiente?.invoke()
        pendiente = null
    }
    return { accion ->
        val faltaPermiso = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (faltaPermiso) {
            pendiente = accion
            lanzador.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            accion()
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun BotonDescarga(descarga: Descarga?, alDescargar: () -> Unit, alEliminar: () -> Unit, modifier: Modifier = Modifier) {
    var confirmarBorrado by remember { mutableStateOf(false) }
    val estado = descarga?.estado
    val (texto, accion) = when (estado) {
        null -> "Descargar" to alDescargar
        EstadoDescarga.EN_COLA -> "En cola…" to alEliminar
        EstadoDescarga.DESCARGANDO -> "Descargando ${descarga.porcentaje.coerceAtLeast(0f).toInt()} %" to alEliminar
        EstadoDescarga.COMPLETADA -> "Descargado · ${formatearBytes(descarga.bytes)}" to { confirmarBorrado = true }
        EstadoDescarga.FALLIDA -> "Error al descargar · Reintentar" to alDescargar
        EstadoDescarga.DETENIDA -> "Descarga detenida · Reanudar" to alDescargar
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .border(1.5.dp, if (estado == EstadoDescarga.COMPLETADA) Colores.Lima else Colores.Borde, RoundedCornerShape(28.dp))
            .clickable(onClick = accion)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (estado) {
            EstadoDescarga.DESCARGANDO, EstadoDescarga.EN_COLA -> Box(contentAlignment = Alignment.Center) {
                if (descarga.porcentaje >= 0 && estado == EstadoDescarga.DESCARGANDO) {
                    CircularProgressIndicator(progress = { descarga.porcentaje / 100f }, modifier = Modifier.size(24.dp), color = Colores.Lima, trackColor = Colores.Superficie2, strokeWidth = 3.dp)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Colores.Lima, strokeWidth = 3.dp)
                }
            }
            EstadoDescarga.COMPLETADA -> Icon(Icons.Outlined.DownloadDone, contentDescription = null, tint = Colores.Lima, modifier = Modifier.size(24.dp))
            EstadoDescarga.FALLIDA -> Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Colores.Error, modifier = Modifier.size(24.dp))
            else -> Icon(Icons.Outlined.Download, contentDescription = null, tint = Colores.Texto, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(texto, color = Colores.Texto, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (estado == EstadoDescarga.DESCARGANDO || estado == EstadoDescarga.EN_COLA) {
            Icon(Icons.Outlined.Close, contentDescription = "Cancelar", tint = Colores.Texto2, modifier = Modifier.size(20.dp))
        }
        if (estado == EstadoDescarga.COMPLETADA) {
            Icon(Icons.Outlined.Delete, contentDescription = "Eliminar", tint = Colores.Texto2, modifier = Modifier.size(20.dp))
        }
    }
    if (confirmarBorrado) {
        DialogoEliminarDescarga(alCancelar = { confirmarBorrado = false }, alConfirmar = { confirmarBorrado = false; alEliminar() })
    }
}

@OptIn(UnstableApi::class)
@Composable
fun IconoDescarga(descarga: Descarga?, alDescargar: () -> Unit, alEliminar: () -> Unit) {
    var confirmarBorrado by remember { mutableStateOf(false) }
    when (descarga?.estado) {
        null, EstadoDescarga.FALLIDA, EstadoDescarga.DETENIDA -> IconButton(onClick = alDescargar) {
            Icon(
                if (descarga?.estado == EstadoDescarga.FALLIDA) Icons.Outlined.ErrorOutline else Icons.Outlined.Download,
                contentDescription = "Descargar",
                tint = if (descarga?.estado == EstadoDescarga.FALLIDA) Colores.Error else Colores.Texto2,
            )
        }
        EstadoDescarga.EN_COLA, EstadoDescarga.DESCARGANDO -> IconButton(onClick = alEliminar) {
            if (descarga.porcentaje >= 0 && descarga.estado == EstadoDescarga.DESCARGANDO) {
                CircularProgressIndicator(progress = { descarga.porcentaje / 100f }, modifier = Modifier.size(22.dp), color = Colores.Lima, trackColor = Colores.Superficie2, strokeWidth = 2.5.dp)
            } else {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Colores.Lima, strokeWidth = 2.5.dp)
            }
        }
        EstadoDescarga.COMPLETADA -> IconButton(onClick = { confirmarBorrado = true }) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = "Descargado", tint = Colores.Lima)
        }
    }
    if (confirmarBorrado) {
        DialogoEliminarDescarga(alCancelar = { confirmarBorrado = false }, alConfirmar = { confirmarBorrado = false; alEliminar() })
    }
}

@Composable
fun DialogoEliminarDescarga(alCancelar: () -> Unit, alConfirmar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCancelar,
        containerColor = Colores.Superficie,
        titleContentColor = Colores.Texto,
        textContentColor = Colores.Texto2,
        title = { Text("Eliminar descarga") },
        text = { Text("El vídeo se borrará del teléfono. Podrás volver a descargarlo cuando quieras.") },
        confirmButton = { TextButton(onClick = alConfirmar) { Text("Eliminar", color = Colores.Error) } },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar", color = Colores.Texto2) } },
    )
}

fun formatearBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(java.util.Locale.US, "%.0f MB", bytes / (1L shl 20).toDouble())
    else -> "${bytes / 1024} KB"
}
