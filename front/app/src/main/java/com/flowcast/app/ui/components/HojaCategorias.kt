package com.flowcast.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowcast.app.data.network.Categoria
import com.flowcast.app.ui.screens.tv.CampoBusqueda
import com.flowcast.app.ui.theme.Colores

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaCategorias(
    tituloTipo: String,
    categorias: List<Categoria>,
    totalTodos: Int,
    alElegir: (Categoria?) -> Unit,
    alCerrar: () -> Unit,
) {
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var busqueda by remember { mutableStateOf("") }
    val visibles = remember(busqueda, categorias) {
        if (busqueda.isBlank()) categorias else categorias.filter { it.nombre.contains(busqueda, ignoreCase = true) }
    }

    ModalBottomSheet(onDismissRequest = alCerrar, sheetState = estadoHoja, containerColor = Colores.Superficie, dragHandle = null) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Colores.Texto3))
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Colores.Lima), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Category, contentDescription = null, tint = Colores.SobreLima)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Categorías", style = MaterialTheme.typography.titleLarge, color = Colores.Texto)
                    Text("${categorias.size} categorías", style = MaterialTheme.typography.labelSmall, color = Colores.Texto2)
                }
                IconButton(onClick = alCerrar) { Icon(Icons.Outlined.Close, contentDescription = "Cerrar", tint = Colores.Texto) }
            }
            Spacer(Modifier.height(14.dp))
            CampoBusqueda(busqueda, { busqueda = it }, "Buscar categoría...", Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            Spacer(Modifier.height(14.dp))
            LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
                if (busqueda.isBlank()) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Colores.LimaSuave)
                                .border(1.dp, Colores.Lima, RoundedCornerShape(16.dp))
                                .clickable { alElegir(null) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Colores.LimaChip), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.GridView, contentDescription = null, tint = Colores.Lima)
                            }
                            Spacer(Modifier.width(14.dp))
                            Text("Todos", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Colores.Texto, modifier = Modifier.weight(1f))
                            Text("$totalTodos", style = MaterialTheme.typography.bodyMedium, color = Colores.Texto2)
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                items(visibles, key = { it.id }) { categoria ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Colores.Superficie2)
                            .clickable { alElegir(categoria) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Colores.Borde), contentAlignment = Alignment.Center) {
                            Icon(if (categoria.bloqueada) Icons.Outlined.Lock else Icons.Outlined.Folder, contentDescription = null, tint = if (categoria.bloqueada) Colores.Lima else Colores.Texto)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(categoria.nombre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Colores.Texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            categoria.total?.let { Text("$it $tituloTipo", style = MaterialTheme.typography.labelSmall, color = Colores.Texto2) }
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Colores.Texto2)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                if (visibles.isEmpty()) {
                    item { Text("Ninguna categoría coincide", color = Colores.Texto2, modifier = Modifier.padding(8.dp)) }
                }
            }
        }
    }
}
