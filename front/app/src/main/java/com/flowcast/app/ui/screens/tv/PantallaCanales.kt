package com.flowcast.app.ui.screens.tv

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.flowcast.app.data.network.Canal
import com.flowcast.app.data.network.ProgramaEpg
import com.flowcast.app.ui.components.EstadoVacio
import com.flowcast.app.ui.theme.Colores
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PantallaCanales(
    titulo: String,
    canales: List<Canal>,
    epgDe: suspend (String) -> List<ProgramaEpg>,
    favoritos: Set<String>,
    alAlternarFavorito: (Canal) -> Unit,
    alVolver: () -> Unit,
    alReproducir: (Canal) -> Unit,
) {
    var busqueda by rememberSaveable { mutableStateOf("") }
    val visibles = remember(busqueda, canales) {
        if (busqueda.isBlank()) canales else canales.filter { it.nombre.contains(busqueda, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().background(Colores.Fondo).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 24.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver", tint = Colores.Texto) }
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleLarge, color = Colores.Texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${canales.size} Canales", style = MaterialTheme.typography.labelSmall, color = Colores.Texto2)
            }
        }
        Spacer(Modifier.height(12.dp))
        CampoBusqueda(busqueda, { busqueda = it }, "Buscar canal...", Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        Spacer(Modifier.height(12.dp))

        if (visibles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EstadoVacio(Icons.Outlined.Tv, "No hay canales") }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
                items(visibles, key = { it.id }) { canal ->
                    FilaCanal(canal, epgDe, esFavorito = canal.id in favoritos, onClick = { alReproducir(canal) }, alAlternarFavorito = { alAlternarFavorito(canal) })
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun FilaCanal(
    canal: Canal,
    epgDe: suspend (String) -> List<ProgramaEpg>,
    esFavorito: Boolean,
    onClick: () -> Unit,
    alAlternarFavorito: () -> Unit,
) {
    val programas by produceState<List<ProgramaEpg>?>(initialValue = null, key1 = canal.id) { value = epgDe(canal.id) }
    val actual = programas?.firstOrNull { it.enEmision } ?: programas?.firstOrNull()
    val siguiente = programas?.let { lista -> lista.getOrNull(lista.indexOf(actual) + 1) }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Colores.Superficie)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Colores.Superficie2), contentAlignment = Alignment.Center) {
            if (canal.icono.isNullOrBlank()) {
                Icon(Icons.Outlined.Tv, contentDescription = null, tint = Colores.Texto3, modifier = Modifier.size(28.dp))
            } else {
                AsyncImage(model = canal.icono, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                canal.numero?.let {
                    Text("$it", style = MaterialTheme.typography.labelSmall, color = Colores.Lima, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                }
                Text(canal.nombre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Colores.Texto, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (canal.bloqueada) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.Lock, contentDescription = "Requiere PIN", tint = Colores.Lima, modifier = Modifier.size(16.dp))
                }
            }
            if (actual != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${hora(actual.inicio)}  ${actual.titulo}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Colores.Texto2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progreso(actual) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = Colores.Lima,
                    trackColor = Colores.Superficie2,
                    drawStopIndicator = {},
                )
                if (siguiente != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Después: ${hora(siguiente.inicio)}  ${siguiente.titulo}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Colores.Texto3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else if (programas != null) {
                Spacer(Modifier.height(4.dp))
                Text("Sin guía de programación", style = MaterialTheme.typography.labelSmall, color = Colores.Texto3)
            }
        }
        IconButton(onClick = alAlternarFavorito, modifier = Modifier.size(40.dp)) {
            Icon(
                if (esFavorito) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (esFavorito) "Quitar de favoritos" else "Añadir a favoritos",
                tint = if (esFavorito) Colores.Lima else Colores.Texto3,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private val formatoHora: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun hora(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(formatoHora)
}.getOrDefault("")

private fun progreso(programa: ProgramaEpg): Float = runCatching {
    val inicio = OffsetDateTime.parse(programa.inicio).toInstant()
    val fin = OffsetDateTime.parse(programa.fin).toInstant()
    val total = (fin.toEpochMilli() - inicio.toEpochMilli()).coerceAtLeast(1)
    ((Instant.now().toEpochMilli() - inicio.toEpochMilli()).toFloat() / total).coerceIn(0f, 1f)
}.getOrDefault(0f)
