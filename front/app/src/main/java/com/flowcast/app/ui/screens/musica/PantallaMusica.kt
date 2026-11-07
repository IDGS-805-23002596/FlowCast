package com.flowcast.app.ui.screens.musica

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowcast.app.data.musica.ControladorMusica
import com.flowcast.app.data.network.Album
import com.flowcast.app.ui.components.BotonLima
import com.flowcast.app.ui.components.EstadoVacio
import com.flowcast.app.ui.components.MensajeError
import com.flowcast.app.ui.components.rememberEjecutarConPermisoNotificaciones
import com.flowcast.app.ui.screens.tv.CampoBusqueda
import com.flowcast.app.ui.screens.tv.TituloConMarca
import com.flowcast.app.ui.theme.Colores

@Composable
fun PantallaMusica(
    modeloVista: ModeloVistaMusica,
    controlador: ControladorMusica,
    alVolver: () -> Unit,
    alAbrirAlbum: (Album) -> Unit,
    alAbrirReproductor: () -> Unit,
) {
    val estado by modeloVista.estado.collectAsState()
    val sonando by controlador.estado.collectAsState()
    val conPermiso = rememberEjecutarConPermisoNotificaciones()
    LaunchedEffect(Unit) { controlador.conectar() }

    Box(Modifier.fillMaxSize().background(Colores.Fondo)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 24.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver", tint = Colores.Texto) }
                Column(Modifier.weight(1f)) {
                    Text("Música", style = MaterialTheme.typography.titleLarge, color = Colores.Texto)
                    if (estado.canciones.isNotEmpty()) {
                        Text("${estado.canciones.size} canciones  •  ${estado.albumes.size} álbumes", style = MaterialTheme.typography.labelSmall, color = Colores.Texto2)
                    }
                }
            }
            when {
                estado.cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Colores.Lima) }
                estado.error != null -> Column(Modifier.padding(40.dp)) {
                    MensajeError(estado.error ?: "")
                    Spacer(Modifier.height(16.dp))
                    BotonLima("Reintentar", onClick = modeloVista::cargar)
                }
                !estado.carpetaConfigurada -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EstadoVacio(Icons.Outlined.MusicOff, "El backend no tiene una carpeta de música\n(MUSICA_CARPETA en .env)")
                }
                estado.canciones.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EstadoVacio(Icons.Outlined.MusicOff, "La carpeta de música está vacía")
                }
                else -> {
                    Spacer(Modifier.height(12.dp))
                    CampoBusqueda(estado.busqueda, modeloVista::buscar, "Buscar canción, artista o álbum", Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                    val visibles = estado.cancionesVisibles
                    LazyColumn(contentPadding = PaddingValues(top = 20.dp, bottom = 32.dp)) {
                        if (estado.busqueda.isBlank()) {
                            item {
                                Box(Modifier.padding(horizontal = 24.dp)) { TituloConMarca("Álbumes") }
                                Spacer(Modifier.height(12.dp))
                                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(estado.albumes, key = { it.id }) { album -> TarjetaAlbum(album, onClick = { alAbrirAlbum(album) }) }
                                }
                                Spacer(Modifier.height(28.dp))
                            }
                        }
                        item {
                            Box(Modifier.padding(horizontal = 24.dp)) { TituloConMarca(if (estado.busqueda.isBlank()) "Canciones" else "Resultados") }
                            Spacer(Modifier.height(12.dp))
                        }
                        if (visibles.isEmpty()) {
                            item { Text("Nada coincide con la búsqueda", color = Colores.Texto2, modifier = Modifier.padding(horizontal = 24.dp)) }
                        }
                        itemsIndexed(visibles, key = { _, c -> c.id }) { indice, cancion ->
                            Box(Modifier.padding(horizontal = 24.dp)) {
                                FilaCancion(cancion, sonando = sonando.cancionId == cancion.id, onClick = { conPermiso { controlador.reproducir(visibles, indice) } })
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        item { EspacioBarraSonando(sonando) }
                    }
                }
            }
        }
        BarraSonando(
            sonando,
            alAlternar = controlador::alternar,
            alSiguiente = { controlador.siguiente() },
            onClick = alAbrirReproductor,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun TarjetaAlbum(album: Album, onClick: () -> Unit) {
    Column(Modifier.width(140.dp).clickable(onClick = onClick)) {
        Portada(album.urlPortada, 140.dp, esquina = 14.dp)
        Spacer(Modifier.height(8.dp))
        Text(album.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Colores.Texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(album.artista, style = MaterialTheme.typography.labelSmall, color = Colores.Texto2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
