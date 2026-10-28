package com.flowcast.app.ui.screens.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowcast.app.ui.components.BotonLima
import com.flowcast.app.ui.components.MensajeError
import com.flowcast.app.ui.components.Pildora
import com.flowcast.app.ui.theme.Colores

@Composable
fun PestanaTv(
    modeloVista: ModeloVistaTv,
    alAbrirCategoria: (CategoriaTv) -> Unit,
    alAbrirFavoritos: () -> Unit,
    alMultipantalla: () -> Unit,
) {
    val estado by modeloVista.estado.collectAsState()

    when {
        estado.cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Colores.Lima)
        }
        estado.error != null -> Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.Center) {
            MensajeError(estado.error ?: "")
            Spacer(Modifier.height(16.dp))
            BotonLima("Reintentar", onClick = modeloVista::cargar)
        }
        else -> LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 120.dp)) {
            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("TV en vivo", style = MaterialTheme.typography.headlineSmall, color = Colores.Texto)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${estado.totalCanales} Canales",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Colores.Texto2,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CampoBusqueda(estado.busqueda, modeloVista::buscar, "Buscar", Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Pildora("Multipantalla", onClick = alMultipantalla, icono = Icons.Outlined.GridView)
                }
                Spacer(Modifier.height(20.dp))
                FilaFavoritos(totalCanales = estado.favoritos.size, onClick = alAbrirFavoritos)
                Spacer(Modifier.height(24.dp))
                TituloConMarca("Categorías")
                Spacer(Modifier.height(12.dp))
            }
            items(estado.categoriasVisibles, key = { it.categoria.id }) { categoria ->
                FilaCategoria(categoria, onClick = { alAbrirCategoria(categoria) })
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun CampoBusqueda(valor: String, alCambiar: (String) -> Unit, marcador: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        placeholder = { Text(marcador, color = Colores.Texto3) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Colores.Texto2) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Colores.Texto,
            unfocusedTextColor = Colores.Texto,
            cursorColor = Colores.Lima,
            focusedBorderColor = Colores.Lima,
            unfocusedBorderColor = Colores.Borde,
            focusedContainerColor = Colores.Superficie,
            unfocusedContainerColor = Colores.Superficie,
        ),
        modifier = modifier.height(52.dp),
    )
}

@Composable
fun TituloConMarca(texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(Colores.Lima))
        Spacer(Modifier.width(12.dp))
        Text(texto, style = MaterialTheme.typography.titleMedium, color = Colores.Texto)
    }
}

@Composable
private fun FilaFavoritos(totalCanales: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Colores.LimaSuave)
            .border(1.dp, Color(0xFF5A6A22), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Star, contentDescription = null, tint = Colores.Lima, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text("Mis Favoritos", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Colores.Texto, modifier = Modifier.weight(1f))
        Text("$totalCanales Canales", style = MaterialTheme.typography.bodyMedium, color = Colores.Texto2)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Colores.Texto2, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FilaCategoria(categoria: CategoriaTv, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Colores.Superficie)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).border(1.5.dp, Colores.Lima, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (categoria.categoria.bloqueada) Icons.Outlined.Lock else Icons.Outlined.Tv, contentDescription = null, tint = Colores.Lima, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                categoria.categoria.nombre,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Colores.Texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (categoria.categoria.bloqueada) "${categoria.totalCanales} Canales · Requiere PIN" else "${categoria.totalCanales} Canales",
                style = MaterialTheme.typography.labelSmall,
                color = if (categoria.categoria.bloqueada) Colores.Lima else Colores.Texto2,
            )
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Colores.Texto2, modifier = Modifier.size(24.dp))
    }
}
