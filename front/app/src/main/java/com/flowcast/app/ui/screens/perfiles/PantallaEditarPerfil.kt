package com.flowcast.app.ui.screens.perfiles

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flowcast.app.ui.components.AVATARES_PREDEFINIDOS
import com.flowcast.app.ui.components.AvatarPerfil
import com.flowcast.app.ui.components.BotonContorno
import com.flowcast.app.ui.components.BotonLima
import com.flowcast.app.ui.components.CampoTextoFlowCast
import com.flowcast.app.ui.components.MensajeError
import com.flowcast.app.ui.theme.Colores

@Composable
fun PantallaEditarPerfil(modeloVista: ModeloVistaEditarPerfil, alVolver: () -> Unit) {
    val estado by modeloVista.estado.collectAsState()
    val contexto = LocalContext.current
    LaunchedEffect(estado.terminado) { if (estado.terminado) alVolver() }
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) modeloVista.subirImagen(contexto, uri)
    }

    Column(Modifier.fillMaxSize().background(Colores.Fondo).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver", tint = Colores.Texto) }
            Text("Editar perfil", style = MaterialTheme.typography.titleLarge, color = Colores.Texto, modifier = Modifier.weight(1f))
            if (estado.perfil != null && !estado.esUnico) {
                TextButton(onClick = { modeloVista.pedirConfirmacionBorrado(true) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = Colores.Error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Eliminar", color = Colores.Error)
                }
            }
        }
        when {
            estado.cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Colores.Lima) }
            estado.perfil == null -> Column(Modifier.padding(40.dp)) {
                MensajeError(estado.error ?: "")
                Spacer(Modifier.height(16.dp))
                BotonLima("Reintentar", onClick = modeloVista::cargar)
            }
            else -> {
                val perfil = estado.perfil!!
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(24.dp))
                    Box(contentAlignment = Alignment.BottomEnd) {
                        AvatarPerfil(nombre = estado.nombre, avatar = estado.avatar, urlImagen = perfil.urlImagen, radio = 64.dp)
                        if (estado.subiendoImagen) {
                            CircularProgressIndicator(color = Colores.Lima, modifier = Modifier.size(134.dp).align(Alignment.Center))
                        }
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(Colores.Lima).clickable {
                                selector.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.AddAPhoto, contentDescription = "Elegir foto", tint = Colores.SobreLima, modifier = Modifier.size(20.dp)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (perfil.urlImagen != null) {
                        TextButton(onClick = modeloVista::quitarImagen, enabled = !estado.subiendoImagen) { Text("Quitar foto", color = Colores.Texto2) }
                    } else {
                        Text("Toca la cámara para usar una foto de tu galería", style = MaterialTheme.typography.bodyMedium, color = Colores.Texto2)
                    }
                    Spacer(Modifier.height(24.dp))
                    CampoTextoFlowCast(estado.nombre, modeloVista::cambiarNombre, "Nombre del perfil", Icons.Outlined.Person)
                    Spacer(Modifier.height(28.dp))
                    Text(
                        if (perfil.urlImagen != null) "Avatar de respaldo si quitas la foto" else "Elige un avatar",
                        style = MaterialTheme.typography.titleMedium,
                        color = Colores.Texto,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))
                    AVATARES_PREDEFINIDOS.chunked(4).forEach { fila ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            fila.forEach { opcion ->
                                val elegido = opcion.codigo == estado.avatar
                                Box(
                                    Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .then(if (elegido) Modifier.border(3.dp, Colores.Lima, CircleShape) else Modifier)
                                        .clickable { modeloVista.elegirAvatar(opcion.codigo) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AvatarPerfil(nombre = estado.nombre, avatar = opcion.codigo, urlImagen = null, radio = 26.dp, conAnillo = false)
                                }
                            }
                            repeat(4 - fila.size) { Spacer(Modifier.size(64.dp)) }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                    estado.error?.let {
                        Spacer(Modifier.height(8.dp))
                        MensajeError(it)
                    }
                    Spacer(Modifier.height(20.dp))
                    BotonLima("Guardar cambios", onClick = modeloVista::guardar, habilitado = estado.hayCambios && !estado.guardando, cargando = estado.guardando)
                    Spacer(Modifier.height(12.dp))
                    BotonContorno("Cancelar", onClick = alVolver)
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }

    if (estado.confirmarBorrado) {
        AlertDialog(
            onDismissRequest = { modeloVista.pedirConfirmacionBorrado(false) },
            containerColor = Colores.Superficie,
            titleContentColor = Colores.Texto,
            textContentColor = Colores.Texto2,
            title = { Text("Eliminar perfil") },
            text = { Text("Se borrarán sus favoritos, historial y reseñas. Esta acción no se puede deshacer.") },
            confirmButton = { TextButton(onClick = modeloVista::borrar) { Text("Eliminar", color = Colores.Error) } },
            dismissButton = { TextButton(onClick = { modeloVista.pedirConfirmacionBorrado(false) }) { Text("Cancelar", color = Colores.Texto2) } },
        )
    }
}
