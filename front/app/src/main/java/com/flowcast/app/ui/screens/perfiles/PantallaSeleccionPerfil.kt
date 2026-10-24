package com.flowcast.app.ui.screens.perfiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flowcast.app.data.session.PerfilGuardado
import com.flowcast.app.ui.components.AvatarPerfil
import com.flowcast.app.ui.components.BotonContorno
import com.flowcast.app.ui.theme.Colores

@Composable
fun PantallaSeleccionPerfil(perfil: PerfilGuardado, alContinuar: () -> Unit, alCambiar: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Colores.Fondo).systemBarsPadding().padding(horizontal = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(230.dp))
        AvatarPerfil(nombre = perfil.nombre, avatar = perfil.avatar, urlImagen = perfil.urlImagen, radio = 52.dp)
        Spacer(Modifier.height(28.dp))
        Text(perfil.nombre, style = MaterialTheme.typography.displaySmall, color = Colores.Texto)
        Spacer(Modifier.height(16.dp))
        Text("¿Deseas continuar con este perfil?", style = MaterialTheme.typography.titleMedium, color = Colores.Texto2)
        Spacer(Modifier.height(48.dp))
        BotonContorno("No, cambiar perfil", onClick = alCambiar)
        Spacer(Modifier.height(16.dp))
        BotonContorno("Sí, continuar", onClick = alContinuar)
    }
}
