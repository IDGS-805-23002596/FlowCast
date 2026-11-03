package com.flowcast.app.ui.screens.reproductor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowcast.app.data.dlna.TelevisorDlna
import com.flowcast.app.ui.theme.Colores

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaReproducirEn(
    televisores: List<TelevisorDlna>,
    buscando: Boolean,
    activo: TelevisorDlna?,
    enviandoA: String?,
    alElegirTelefono: () -> Unit,
    alElegirTelevisor: (TelevisorDlna) -> Unit,
    alCerrar: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = alCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Colores.Superficie) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reproducir en", style = MaterialTheme.typography.headlineSmall, color = Colores.Texto, modifier = Modifier.weight(1f))
                if (buscando) CircularProgressIndicator(color = Colores.Lima, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(18.dp))
            FilaDestino(
                icono = { Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = Colores.Lima, modifier = Modifier.size(30.dp)) },
                titulo = "Este teléfono",
                detalle = if (activo == null) "Reproduciendo ahora" else "Toca para volver aquí",
                seleccionado = activo == null,
                enviando = false,
                onClick = alElegirTelefono,
            )
            Spacer(Modifier.height(24.dp))
            Text("TELEVISORES DETECTADOS", style = MaterialTheme.typography.labelMedium, color = Colores.Texto2, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(12.dp))
            if (televisores.isEmpty()) {
                Text(
                    if (buscando) "Buscando televisores en tu red…" else "No se encontraron televisores. Enciende la TV y vuelve a intentarlo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Colores.Texto2,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            televisores.forEach { televisor ->
                FilaDestino(
                    icono = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Tv, contentDescription = null, tint = Colores.Texto, modifier = Modifier.size(26.dp))
                            televisor.fabricante?.let { Text(it.uppercase(), fontSize = 9.sp, color = Colores.Texto2, fontWeight = FontWeight.Bold, maxLines = 1) }
                        }
                    },
                    titulo = televisor.nombre,
                    detalle = televisor.descripcion,
                    seleccionado = activo?.id == televisor.id,
                    enviando = enviandoA == televisor.id,
                    onClick = { alElegirTelevisor(televisor) },
                )
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Wifi, contentDescription = null, tint = Colores.Texto3, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("La TV debe estar en la misma red WiFi que el teléfono", style = MaterialTheme.typography.bodySmall, color = Colores.Texto3)
            }
        }
    }
}

@Composable
private fun FilaDestino(
    icono: @Composable () -> Unit,
    titulo: String,
    detalle: String,
    seleccionado: Boolean,
    enviando: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (seleccionado) Colores.Superficie2 else Colores.Superficie)
            .border(1.dp, if (seleccionado) Colores.Lima else Colores.Borde, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) { icono() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge, color = Colores.Texto, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detalle, style = MaterialTheme.typography.bodySmall, color = Colores.Texto2)
        }
        when {
            enviando -> CircularProgressIndicator(color = Colores.Lima, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            seleccionado -> Icon(Icons.Outlined.CheckCircle, contentDescription = "Seleccionado", tint = Colores.Lima, modifier = Modifier.size(26.dp))
        }
    }
}
