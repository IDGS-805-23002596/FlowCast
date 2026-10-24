package com.flowcast.app.ui.screens.perfiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.flowcast.app.ui.components.AvatarPerfil
import com.flowcast.app.ui.components.BotonLima
import com.flowcast.app.ui.components.CampoTextoFlowCast
import com.flowcast.app.ui.components.LogoFlowCast
import com.flowcast.app.ui.components.MensajeError
import com.flowcast.app.ui.theme.Colores
import androidx.compose.ui.unit.sp

@Composable
fun PantallaListaPerfiles(modeloVista: ModeloVistaPerfiles, alSeleccionar: () -> Unit, alEditar: (String) -> Unit) {
    val estado by modeloVista.estado.collectAsState()
    LaunchedEffect(Unit) { modeloVista.cargar() }
    LaunchedEffect(estado.seleccionado) { if (estado.seleccionado) alSeleccionar() }

    Column(
        modifier = Modifier.fillMaxSize().background(Colores.Fondo).systemBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(60.dp))
        LogoFlowCast(tamano = 28.sp)
        Spacer(Modifier.height(40.dp))
        Text("¿Quién está viendo?", style = MaterialTheme.typography.displaySmall, color = Colores.Texto)
        Spacer(Modifier.height(8.dp))
        Text(if (estado.editando) "Toca un perfil para editarlo" else "Elige un perfil para continuar", style = MaterialTheme.typography.bodyMedium, color = Colores.Texto2)
        Spacer(Modifier.height(36.dp))

        if (estado.cargando) {
            CircularProgressIndicator(color = Colores.Lima)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(estado.perfiles, key = { it.id }) { perfil ->
                    val esActivo = perfil.id == estado.perfilActivo?.id
                    Column(
                        modifier = Modifier.clickable { if (estado.editando) alEditar(perfil.id) else modeloVista.seleccionar(perfil) }.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box {
                            AvatarPerfil(nombre = perfil.nombre, avatar = perfil.avatar, urlImagen = perfil.urlImagen, radio = 40.dp, conAnillo = esActivo && !estado.editando)
                            if (estado.editando) {
                                Box(Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(Colores.Lima), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Editar", tint = Colores.SobreLima, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(perfil.nombre, style = MaterialTheme.typography.titleMedium, color = Colores.Texto)
                    }
                }
                item {
                    Column(
                        modifier = Modifier.clickable { modeloVista.abrirDialogo() }.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(86.dp).clip(CircleShape).background(Colores.Superficie2),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "Agregar perfil", tint = Colores.Lima, modifier = Modifier.size(40.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Agregar perfil", style = MaterialTheme.typography.titleMedium, color = Colores.Texto2)
                    }
                }
            }
        }
        estado.error?.let {
            Spacer(Modifier.height(16.dp))
            MensajeError(it)
        }
        if (!estado.cargando) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = modeloVista::alternarEdicion) {
                Icon(if (estado.editando) Icons.Outlined.Check else Icons.Outlined.Edit, contentDescription = null, tint = Colores.Lima, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (estado.editando) "Listo" else "Administrar perfiles", color = Colores.Lima)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (estado.dialogoAbierto) {
        AlertDialog(
            onDismissRequest = modeloVista::cerrarDialogo,
            containerColor = Colores.Superficie,
            titleContentColor = Colores.Texto,
            title = { Text("Nuevo perfil") },
            text = {
                Column {
                    CampoTextoFlowCast(estado.nuevoNombre, modeloVista::cambiarNuevoNombre, "Nombre del perfil", Icons.Outlined.Person)
                    Spacer(Modifier.height(16.dp))
                    BotonLima("Crear", onClick = modeloVista::crear, habilitado = estado.nuevoNombre.trim().length >= 2, cargando = estado.creando)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = modeloVista::cerrarDialogo) { Text("Cancelar", color = Colores.Texto2) } },
        )
    }
}
