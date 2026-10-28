package com.flowcast.app.ui.screens.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as itemsCuadricula
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowcast.app.data.network.Categoria
import com.flowcast.app.ui.components.BotonLima
import com.flowcast.app.ui.components.CarruselDestacado
import com.flowcast.app.ui.components.ElementoCatalogo
import com.flowcast.app.ui.components.EstadoVacio
import com.flowcast.app.ui.components.EncabezadoSeccion
import com.flowcast.app.ui.components.MensajeError
import com.flowcast.app.ui.components.SelloTendencia
import com.flowcast.app.ui.components.TarjetaContenido
import com.flowcast.app.ui.screens.tv.CampoBusqueda
import com.flowcast.app.ui.theme.Colores

@Composable
fun PestanaCatalogo(
    modeloVista: ModeloVistaCatalogo,
    alAbrir: (ElementoCatalogo) -> Unit,
    alReproducir: (ElementoCatalogo) -> Unit,
    alVerCategoria: (Categoria) -> Unit,
) {
    val estado by modeloVista.estado.collectAsState()

    Column(Modifier.fillMaxSize()) {
        if (!estado.cargando && estado.error == null) {
            Spacer(Modifier.height(12.dp))
            CampoBusqueda(
                estado.busqueda,
                modeloVista::buscar,
                if (modeloVista.tipo == TipoCatalogo.PELICULAS) "Buscar película" else "Buscar serie",
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
        }
        when {
            estado.enBusqueda -> ResultadosBusqueda(estado, alAbrir)
            else -> ContenidoCatalogo(estado, modeloVista, alAbrir, alReproducir, alVerCategoria)
        }
    }
}

@Composable
private fun ResultadosBusqueda(estado: EstadoCatalogo, alAbrir: (ElementoCatalogo) -> Unit) {
    val resultados = estado.resultados
    when {
        estado.buscando || resultados == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Colores.Lima)
        }
        resultados.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EstadoVacio(Icons.Outlined.SearchOff, "Nada coincide con \"${estado.busqueda.trim()}\"", Modifier.padding(bottom = 100.dp))
        }
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsCuadricula(resultados, key = { it.id }) { elemento -> TarjetaContenido(elemento, onClick = { alAbrir(elemento) }) }
        }
    }
}

@Composable
private fun ContenidoCatalogo(
    estado: EstadoCatalogo,
    modeloVista: ModeloVistaCatalogo,
    alAbrir: (ElementoCatalogo) -> Unit,
    alReproducir: (ElementoCatalogo) -> Unit,
    alVerCategoria: (Categoria) -> Unit,
) {
    when {
        estado.cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Colores.Lima)
        }
        estado.error != null -> Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.Center) {
            MensajeError(estado.error ?: "")
            Spacer(Modifier.height(16.dp))
            BotonLima("Reintentar", onClick = modeloVista::cargar)
        }
        else -> LazyColumn(contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)) {
            item { CarruselDestacado(estado.destacados, alAbrir, alReproducir) }
            if (estado.tendencia.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(28.dp))
                    SelloTendencia(Modifier.padding(start = 30.dp))
                    Spacer(Modifier.height(24.dp))
                }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(estado.tendencia, key = { _, e -> "tendencia-${e.id}" }) { indice, elemento ->
                            TarjetaContenido(elemento, onClick = { alAbrir(elemento) }, numero = indice + 1)
                        }
                    }
                }
            }
            estado.secciones.forEach { seccion ->
                item {
                    Spacer(Modifier.height(36.dp))
                    EncabezadoSeccion(seccion.categoria.nombre, alVerMas = { alVerCategoria(seccion.categoria) }, modifier = Modifier.padding(horizontal = 24.dp))
                    Spacer(Modifier.height(16.dp))
                }
                if (seccion.bloqueada) {
                    item { FilaBloqueada(onClick = { alVerCategoria(seccion.categoria) }) }
                } else {
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(seccion.elementos, key = { "${seccion.categoria.id}-${it.id}" }) { elemento ->
                                TarjetaContenido(elemento, onClick = { alAbrir(elemento) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaBloqueada(onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Colores.Superficie)
            .border(1.dp, Colores.Borde, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = Colores.Lima, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Contenido bloqueado", color = Colores.Texto, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text("Toca para desbloquearlo con tu PIN parental", color = Colores.Texto2, style = MaterialTheme.typography.bodySmall)
        }
    }
}
