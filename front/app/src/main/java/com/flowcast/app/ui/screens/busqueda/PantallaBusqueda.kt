package com.flowcast.app.ui.screens.busqueda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as itemsFila
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.flowcast.app.data.network.Canal
import com.flowcast.app.data.network.Pelicula
import com.flowcast.app.data.network.Serie
import com.flowcast.app.ui.components.BotonLima
import com.flowcast.app.ui.components.EstadoVacio
import com.flowcast.app.ui.components.InsigniaBloqueo
import com.flowcast.app.ui.components.MensajeError
import com.flowcast.app.ui.components.Poster
import com.flowcast.app.ui.screens.tv.CampoBusqueda
import com.flowcast.app.ui.theme.Colores

@Composable
fun PantallaBusqueda(
    modeloVista: ModeloVistaBusqueda,
    alVolver: () -> Unit,
    alReproducirCanal: (Canal) -> Unit,
    alAbrirPelicula: (Pelicula) -> Unit,
    alAbrirSerie: (Serie) -> Unit,
) {
    val estado by modeloVista.estado.collectAsState()
    val foco = remember { FocusRequester() }
    var menuOrden by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { foco.requestFocus() }

    Column(Modifier.fillMaxSize().background(Colores.Fondo).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver", tint = Colores.Texto) }
            CampoBusqueda(estado.texto, modeloVista::escribir, "Buscar canales, películas...", Modifier.weight(1f).focusRequester(foco))
            Spacer(Modifier.width(10.dp))
            Box {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Colores.Lima).clickable { menuOrden = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Tune, contentDescription = "Ordenar", tint = Colores.SobreLima) }
                DropdownMenu(expanded = menuOrden, onDismissRequest = { menuOrden = false }) {
                    OrdenBusqueda.entries.forEach { orden ->
                        DropdownMenuItem(
                            text = { Text(orden.etiqueta) },
                            trailingIcon = { if (estado.orden == orden) Icon(Icons.Outlined.Check, contentDescription = null, tint = Colores.Lima) },
                            onClick = { modeloVista.ordenar(orden); menuOrden = false },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsFila(FiltroBusqueda.entries) { filtro ->
                val activo = estado.filtro == filtro
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (activo) Colores.Lima else Colores.Superficie)
                        .border(1.dp, if (activo) Colores.Lima else Colores.Borde, RoundedCornerShape(20.dp))
                        .clickable { modeloVista.filtrar(filtro) }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(filtro.etiqueta, color = if (activo) Colores.SobreLima else Colores.Texto, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        val resultados = estado.resultados
        val muestraPeliculas = estado.filtro == FiltroBusqueda.TODO || estado.filtro == FiltroBusqueda.PELICULAS
        val muestraSeries = estado.filtro == FiltroBusqueda.TODO || estado.filtro == FiltroBusqueda.SERIES
        val muestraCanales = estado.filtro == FiltroBusqueda.TODO || estado.filtro == FiltroBusqueda.CANALES
        when {
            estado.cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Colores.Lima) }
            estado.error != null -> Column(Modifier.padding(40.dp)) {
                MensajeError(estado.error ?: "")
                Spacer(Modifier.height(16.dp))
                BotonLima("Reintentar", onClick = modeloVista::reintentar)
            }
            !estado.enBusqueda -> Populares(estado, muestraPeliculas, muestraSeries, alAbrirPelicula, alAbrirSerie)
            estado.sinResultados -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EstadoVacio(Icons.Outlined.SearchOff, "Nada coincide con \"${estado.texto.trim()}\"")
            }
            resultados != null -> {
                val peliculas = ordenarPeliculas(resultados.peliculas, estado.orden)
                val series = ordenarSeries(resultados.series, estado.orden)
                val canales = if (estado.orden == OrdenBusqueda.NOMBRE) resultados.canales.sortedBy { it.nombre.lowercase() } else resultados.canales
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (muestraPeliculas && peliculas.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { TituloSeccion("Películas", peliculas.size) }
                        items(peliculas, key = { "p-${it.id}" }) { p -> CartelConTitulo(p.portada, p.nombre, bloqueada = p.bloqueada, onClick = { alAbrirPelicula(p) }) }
                    }
                    if (muestraSeries && series.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { TituloSeccion("Series", series.size) }
                        items(series, key = { "s-${it.id}" }) { s -> CartelConTitulo(s.portada, s.nombre, bloqueada = s.bloqueada, onClick = { alAbrirSerie(s) }) }
                    }
                    if (muestraCanales && canales.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { TituloSeccion("Canales", canales.size) }
                        items(canales, key = { "c-${it.id}" }) { c -> LogoCanalConTitulo(c, onClick = { alReproducirCanal(c) }) }
                    }
                }
            }
        }
    }
}

private fun ordenarPeliculas(lista: List<Pelicula>, orden: OrdenBusqueda) = when (orden) {
    OrdenBusqueda.RELEVANCIA -> lista
    OrdenBusqueda.NOMBRE -> lista.sortedBy { it.nombre.lowercase() }
    OrdenBusqueda.ANIO -> lista.sortedByDescending { it.agregadoEn ?: "" }
}

private fun ordenarSeries(lista: List<Serie>, orden: OrdenBusqueda) = when (orden) {
    OrdenBusqueda.RELEVANCIA -> lista
    OrdenBusqueda.NOMBRE -> lista.sortedBy { it.nombre.lowercase() }
    OrdenBusqueda.ANIO -> lista.sortedByDescending { it.fechaEstreno ?: "" }
}

@Composable
private fun Populares(estado: EstadoBusqueda, peliculas: Boolean, series: Boolean, alAbrirPelicula: (Pelicula) -> Unit, alAbrirSerie: (Serie) -> Unit) {
    val destacados = estado.destacados
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        if (destacados == null) {
            item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Colores.Lima) } }
            return@LazyColumn
        }
        if (peliculas && destacados.peliculas.isNotEmpty()) {
            item { Text("Películas Populares", style = MaterialTheme.typography.titleLarge, color = Colores.Texto, modifier = Modifier.padding(horizontal = 20.dp)) }
            item {
                Spacer(Modifier.height(12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsFila(destacados.peliculas, key = { it.id }) { p -> CartelConTitulo(p.portada, p.nombre, Modifier.width(128.dp), bloqueada = p.bloqueada, onClick = { alAbrirPelicula(p) }) }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        if (series && destacados.series.isNotEmpty()) {
            item { Text("Series Populares", style = MaterialTheme.typography.titleLarge, color = Colores.Texto, modifier = Modifier.padding(horizontal = 20.dp)) }
            item {
                Spacer(Modifier.height(12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsFila(destacados.series, key = { it.id }) { s -> CartelConTitulo(s.portada, s.nombre, Modifier.width(128.dp), bloqueada = s.bloqueada, onClick = { alAbrirSerie(s) }) }
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = Colores.Texto3, modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun TituloSeccion(titulo: String, total: Int) {
    Row(Modifier.padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
        Text(titulo, style = MaterialTheme.typography.titleLarge, color = Colores.Texto)
        Spacer(Modifier.width(8.dp))
        Text("$total", style = MaterialTheme.typography.bodyMedium, color = Colores.Texto2, modifier = Modifier.padding(bottom = 3.dp))
    }
}

@Composable
fun CartelConTitulo(portada: String?, titulo: String, modifier: Modifier = Modifier, bloqueada: Boolean = false, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        Box {
            Poster(portada, Modifier.fillMaxWidth().aspectRatio(2f / 3f), RoundedCornerShape(12.dp))
            if (bloqueada) InsigniaBloqueo(Modifier.align(Alignment.TopEnd).padding(5.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(titulo, style = MaterialTheme.typography.bodyMedium, color = Colores.Texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LogoCanalConTitulo(canal: Canal, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Colores.Superficie2), contentAlignment = Alignment.Center) {
            if (canal.icono.isNullOrBlank()) {
                Icon(Icons.Outlined.Tv, contentDescription = null, tint = Colores.Texto3, modifier = Modifier.size(36.dp))
            } else {
                AsyncImage(model = canal.icono, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(10.dp))
            }
            if (canal.bloqueada) InsigniaBloqueo(Modifier.align(Alignment.TopEnd).padding(5.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(canal.nombre, style = MaterialTheme.typography.bodyMedium, color = Colores.Texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
