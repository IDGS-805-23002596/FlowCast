package com.flowcast.app.ui.screens.musica

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.unit.dp
import com.flowcast.app.data.musica.ControladorMusica
import com.flowcast.app.ui.components.BotonLima
import com.flowcast.app.ui.components.MensajeError
import com.flowcast.app.ui.components.rememberEjecutarConPermisoNotificaciones
import com.flowcast.app.ui.theme.Colores

@Composable
fun PantallaAlbum(modeloVista: ModeloVistaAlbum, controlador: ControladorMusica, alVolver: () -> Unit, alAbrirReproductor: () -> Unit) {
    val estado by modeloVista.estado.collectAsState()
    val sonando by controlador.estado.collectAsState()
    val conPermiso = rememberEjecutarConPermisoNotificaciones()
    LaunchedEffect(Unit) { controlador.conectar() }

    Box(Modifier.fillMaxSize().background(Colores.Fondo)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver", tint = Colores.Texto) }
                Text("Álbum", style = MaterialTheme.typography.titleLarge, color = Colores.Texto)
            }
            when {
                estado.cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Colores.Lima) }
                estado.error != null -> Column(Modifier.padding(40.dp)) {
                    MensajeError(estado.error ?: "")
                    Spacer(Modifier.height(16.dp))
                    BotonLima("Reintentar", onClick = modeloVista::cargar)
                }
                else -> estado.datos?.let { datos ->
                    val album = datos.album
                    LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp)) {
                        item {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Portada(album.urlPortada, 220.dp, esquina = 20.dp)
                                Spacer(Modifier.height(18.dp))
                                Text(album.titulo, style = MaterialTheme.typography.headlineSmall, color = Colores.Texto)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    listOfNotNull(album.artista, album.anio?.toString()).joinToString("  •  "),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Colores.Texto2,
                                )
                                Text(
                                    "${album.totalCanciones} canciones  •  ${formatoDuracion(album.duracionSegundos)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Colores.Texto3,
                                )
                                Spacer(Modifier.height(20.dp))
                                BotonLima("Reproducir todo", onClick = { conPermiso { controlador.reproducir(datos.canciones, 0) } }, icono = Icons.Filled.PlayArrow)
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                        itemsIndexed(datos.canciones, key = { _, c -> c.id }) { indice, cancion ->
                            FilaCancion(
                                cancion,
                                sonando = sonando.cancionId == cancion.id,
                                onClick = { conPermiso { controlador.reproducir(datos.canciones, indice) } },
                                numero = cancion.numeroPista ?: (indice + 1),
                            )
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

@Suppress("unused")
private val pesoTitulo = FontWeight.SemiBold
