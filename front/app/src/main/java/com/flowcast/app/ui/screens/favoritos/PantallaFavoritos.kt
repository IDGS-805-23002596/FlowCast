package com.flowcast.app.ui.screens.favoritos

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowcast.app.data.network.RespuestaFavorito
import com.flowcast.app.ui.components.BotonLima
import com.flowcast.app.ui.components.EstadoVacio
import com.flowcast.app.ui.components.MensajeError
import com.flowcast.app.ui.components.Poster
import com.flowcast.app.ui.screens.tv.TituloConMarca
import com.flowcast.app.ui.theme.Colores

@Composable
fun PantallaFavoritos(modeloVista: ModeloVistaFavoritos, alVolver: () -> Unit, alAbrir: (RespuestaFavorito) -> Unit) {
    val estado by modeloVista.estado.collectAsState()
    Column(Modifier.fillMaxSize().background(Colores.Fondo).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 24.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver", tint = Colores.Texto) }
            Text("Mis Favoritos", style = MaterialTheme.typography.titleLarge, color = Colores.Texto)
        }
        when {
            estado.cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Colores.Lima) }
            estado.error != null -> Column(Modifier.padding(40.dp)) {
                MensajeError(estado.error ?: "")
                Spacer(Modifier.height(16.dp))
                BotonLima("Reintentar", onClick = modeloVista::cargar)
            }
            estado.favoritos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EstadoVacio(Icons.Outlined.FavoriteBorder, "Aún no tienes favoritos")
            }
            else -> LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp)) {
                listOf("en_vivo" to "Canales", "pelicula" to "Películas", "serie" to "Series").forEach { (tipo, titulo) ->
                    val lista = estado.deTipo(tipo)
                    if (lista.isEmpty()) return@forEach
                    item {
                        TituloConMarca(titulo)
                        Spacer(Modifier.height(12.dp))
                    }
                    items(lista, key = { "${it.tipoContenido}-${it.streamId}" }) { favorito ->
                        FilaFavorito(favorito, onClick = { alAbrir(favorito) }, alQuitar = { modeloVista.quitar(favorito) })
                        Spacer(Modifier.height(10.dp))
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FilaFavorito(favorito: RespuestaFavorito, onClick: () -> Unit, alQuitar: () -> Unit) {
    val esCanal = favorito.tipoContenido == "en_vivo"
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Colores.Superficie)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(favorito.urlPortada, if (esCanal) Modifier.size(56.dp) else Modifier.size(width = 56.dp, height = 72.dp), RoundedCornerShape(10.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            favorito.titulo,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = Colores.Texto,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = alQuitar) { Icon(Icons.Filled.Favorite, contentDescription = "Quitar de favoritos", tint = Colores.Lima) }
    }
}
