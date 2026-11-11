package com.flowcast.app.ui.screens.configuracion

import android.widget.Toast
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowcast.app.data.network.CategoriaControl
import com.flowcast.app.ui.components.BotonLima
import com.flowcast.app.ui.components.DialogoPin
import com.flowcast.app.ui.components.EstadoVacio
import com.flowcast.app.ui.components.MensajeError
import com.flowcast.app.ui.screens.tv.CampoBusqueda
import com.flowcast.app.ui.theme.Colores

@Composable
fun PantallaCategoriasBloqueadas(modeloVista: ModeloVistaCategoriasBloqueadas, alVolver: () -> Unit, alCrearPin: () -> Unit) {
    val estado by modeloVista.estado.collectAsState()
    val contexto = LocalContext.current
    LaunchedEffect(estado.aviso) {
        estado.aviso?.let {
            Toast.makeText(contexto, it, Toast.LENGTH_SHORT).show()
            modeloVista.limpiarAviso()
        }
    }

    Column(Modifier.fillMaxSize().background(Colores.Fondo).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 24.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver", tint = Colores.Texto) }
            Column(Modifier.weight(1f)) {
                Text("Categorías bloqueadas", style = MaterialTheme.typography.titleLarge, color = Colores.Texto)
                if (estado.categorias.isNotEmpty()) {
                    Text("${estado.totalBloqueadas} bloqueadas de ${estado.categorias.size}", style = MaterialTheme.typography.labelSmall, color = Colores.Texto2)
                }
            }
        }
        when {
            estado.error != null -> Column(Modifier.padding(40.dp)) {
                MensajeError(estado.error ?: "")
                Spacer(Modifier.height(16.dp))
                BotonLima("Reintentar", onClick = { if (estado.desbloqueado) modeloVista.cargar() else modeloVista.iniciar() })
            }
            estado.tienePin == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Colores.Lima) }
            estado.tienePin == false -> Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(60.dp))
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Colores.Texto3, modifier = Modifier.size(80.dp))
                Spacer(Modifier.height(24.dp))
                Text(
                    "Primero crea un PIN parental. Las categorías para adultos ya quedan bloqueadas por defecto.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Colores.Texto2,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                BotonLima("Crear PIN", onClick = alCrearPin)
            }
            !estado.desbloqueado -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DialogoPin(
                    cargando = estado.verificando,
                    error = estado.errorPin,
                    alCancelar = alVolver,
                    alConfirmar = modeloVista::verificar,
                    descripcion = "Escribe tu PIN para administrar las categorías bloqueadas.",
                )
            }
            else -> ListaCategorias(estado, modeloVista)
        }
    }
}

@Composable
private fun ListaCategorias(estado: EstadoCategoriasBloqueadas, modeloVista: ModeloVistaCategoriasBloqueadas) {
    Spacer(Modifier.height(8.dp))
    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(FiltroTipo.entries) { filtro ->
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
    Spacer(Modifier.height(12.dp))
    CampoBusqueda(estado.busqueda, modeloVista::buscar, "Buscar categoría...", Modifier.fillMaxWidth().padding(horizontal = 24.dp))
    Spacer(Modifier.height(12.dp))
    when {
        estado.cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Colores.Lima) }
        estado.visibles.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EstadoVacio(Icons.Outlined.SearchOff, "No hay categorías") }
        else -> LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(estado.visibles, key = { "${it.tipoContenido}-${it.id}" }) { categoria ->
                FilaCategoria(categoria, alAlternar = { modeloVista.alternar(categoria) })
            }
        }
    }
}

@Composable
private fun FilaCategoria(categoria: CategoriaControl, alAlternar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Colores.Superficie)
            .clickable(onClick = alAlternar)
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = if (categoria.bloqueada) Colores.Lima else Colores.Texto3, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(categoria.nombre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Colores.Texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (categoria.esAdulta) {
                Text("Detectada como contenido adulto", style = MaterialTheme.typography.labelSmall, color = Colores.Lima)
            }
        }
        Switch(
            checked = categoria.bloqueada,
            onCheckedChange = { alAlternar() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Colores.SobreLima,
                checkedTrackColor = Colores.Lima,
                uncheckedThumbColor = Colores.Texto2,
                uncheckedTrackColor = Colores.Superficie2,
                uncheckedBorderColor = Colores.Borde,
            ),
        )
    }
}
