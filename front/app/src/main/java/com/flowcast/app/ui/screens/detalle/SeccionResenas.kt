package com.flowcast.app.ui.screens.detalle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flowcast.app.data.network.RespuestaResena
import com.flowcast.app.ui.components.Avatar
import com.flowcast.app.ui.components.BotonContorno
import com.flowcast.app.ui.components.MensajeError
import com.flowcast.app.ui.theme.Colores
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SeccionResenas(modeloVista: ModeloVistaResenas, titulo: String) {
    val estado by modeloVista.estado.collectAsState()
    var editando by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Text("Reseñas", style = MaterialTheme.typography.titleMedium, color = Colores.Texto)
        Spacer(Modifier.height(12.dp))
        when {
            estado.cargando -> CircularProgressIndicator(color = Colores.Lima, modifier = Modifier.size(28.dp))
            estado.error != null -> MensajeError(estado.error ?: "")
            else -> estado.resumen?.let { resumen ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            resumen.promedio?.let { String.format(Locale.US, "%.1f", it) } ?: "–",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = Colores.Texto,
                        )
                        Estrellas(resumen.promedio ?: 0.0, tamano = 18.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when (resumen.total) {
                                0 -> "Sin reseñas"
                                1 -> "1 reseña"
                                else -> "${resumen.total} reseñas"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Colores.Texto2,
                        )
                    }
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        (5 downTo 1).forEach { estrellas ->
                            val cuantas = resumen.distribucion[estrellas.toString()] ?: 0
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("$estrellas", style = MaterialTheme.typography.labelSmall, color = Colores.Texto2, modifier = Modifier.width(12.dp))
                                Spacer(Modifier.width(6.dp))
                                LinearProgressIndicator(
                                    progress = { if (resumen.total == 0) 0f else cuantas.toFloat() / resumen.total },
                                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = Colores.Lima,
                                    trackColor = Colores.Superficie2,
                                    drawStopIndicator = {},
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))

                val mia = resumen.mia
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Colores.Superficie).padding(16.dp),
                ) {
                    Text(if (mia == null) "Califica este contenido" else "Tu reseña", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Colores.Texto)
                    Spacer(Modifier.height(10.dp))
                    EstrellasSeleccionables(
                        valor = mia?.calificacion ?: 0,
                        alElegir = { estrellas -> modeloVista.guardar(titulo, estrellas, mia?.texto) },
                    )
                    mia?.texto?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = Colores.Texto2)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BotonContorno(if (mia?.texto == null) "Escribir reseña" else "Editar reseña", onClick = { editando = true }, modifier = Modifier.weight(1f))
                        if (mia != null) {
                            TextButton(onClick = modeloVista::borrar) { Text("Borrar", color = Colores.Error) }
                        }
                    }
                }

                val ajenas = resumen.resenas.filter { !it.esMia }
                if (ajenas.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ajenas.forEach { resena -> TarjetaResena(resena) }
                    }
                }
            }
        }
    }

    if (editando) {
        DialogoResena(
            calificacionInicial = estado.resumen?.mia?.calificacion ?: 0,
            textoInicial = estado.resumen?.mia?.texto.orEmpty(),
            guardando = estado.guardando,
            alCancelar = { editando = false },
            alGuardar = { estrellas, texto -> modeloVista.guardar(titulo, estrellas, texto) { error -> if (error == null) editando = false } },
        )
    }
}

@Composable
private fun TarjetaResena(resena: RespuestaResena) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Colores.Superficie).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(resena.autor.take(1).uppercase(), radio = 16.dp, conAnillo = false)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(resena.autor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Colores.Texto)
                Text(fechaCorta(resena.actualizadoEn), style = MaterialTheme.typography.labelSmall, color = Colores.Texto3)
            }
            Estrellas(resena.calificacion.toDouble(), tamano = 16.dp)
        }
        resena.texto?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Colores.Texto2)
        }
    }
}

@Composable
fun Estrellas(valor: Double, tamano: Dp) {
    Row {
        (1..5).forEach { indice ->
            Icon(
                if (valor >= indice - 0.5) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = Colores.Lima,
                modifier = Modifier.size(tamano),
            )
        }
    }
}

@Composable
private fun EstrellasSeleccionables(valor: Int, alElegir: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..5).forEach { indice ->
            Icon(
                if (indice <= valor) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = "$indice estrellas",
                tint = if (indice <= valor) Colores.Lima else Colores.Texto3,
                modifier = Modifier.size(36.dp).clickable { alElegir(indice) },
            )
        }
    }
}

@Composable
private fun DialogoResena(
    calificacionInicial: Int,
    textoInicial: String,
    guardando: Boolean,
    alCancelar: () -> Unit,
    alGuardar: (Int, String) -> Unit,
) {
    var estrellas by remember { mutableIntStateOf(calificacionInicial) }
    var texto by remember { mutableStateOf(textoInicial) }
    AlertDialog(
        onDismissRequest = alCancelar,
        containerColor = Colores.Superficie,
        titleContentColor = Colores.Texto,
        title = { Text("Tu reseña") },
        text = {
            Column {
                EstrellasSeleccionables(estrellas) { estrellas = it }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = texto,
                    onValueChange = { if (it.length <= 2000) texto = it },
                    placeholder = { Text("¿Qué te pareció?", color = Colores.Texto3) },
                    minLines = 3,
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Colores.Texto,
                        unfocusedTextColor = Colores.Texto,
                        cursorColor = Colores.Lima,
                        focusedBorderColor = Colores.Lima,
                        unfocusedBorderColor = Colores.Borde,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (estrellas == 0) {
                    Spacer(Modifier.height(6.dp))
                    Text("Elige al menos una estrella", style = MaterialTheme.typography.labelSmall, color = Colores.Texto2)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { alGuardar(estrellas, texto) }, enabled = estrellas > 0 && !guardando) {
                if (guardando) CircularProgressIndicator(color = Colores.Lima, modifier = Modifier.size(18.dp)) else Text("Guardar", color = Colores.Lima)
            }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar", color = Colores.Texto2) } },
    )
}

private val formatoFecha: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es"))

private fun fechaCorta(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(formatoFecha)
}.getOrDefault("")

@Composable
internal fun Hueco(alto: Dp) = Box(Modifier.height(alto))
