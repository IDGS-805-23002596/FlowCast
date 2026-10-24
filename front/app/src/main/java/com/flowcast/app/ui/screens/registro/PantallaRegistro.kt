package com.flowcast.app.ui.screens.registro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowcast.app.ui.components.BotonLima
import com.flowcast.app.ui.components.CampoTextoFlowCast
import com.flowcast.app.ui.components.LogoFlowCast
import com.flowcast.app.ui.components.MensajeError
import com.flowcast.app.ui.components.TextoEnlace
import com.flowcast.app.ui.theme.Colores

@Composable
fun PantallaRegistro(
    modeloVista: ModeloVistaRegistro,
    alRegistrar: () -> Unit,
    alVolver: () -> Unit,
) {
    val estado by modeloVista.estado.collectAsState()
    LaunchedEffect(estado.exito) { if (estado.exito) alRegistrar() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Colores.Fondo)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(90.dp))
        LogoFlowCast(tamano = 36.sp)
        Spacer(Modifier.height(40.dp))
        Text("Crear cuenta", style = MaterialTheme.typography.displaySmall, color = Colores.Texto)
        Spacer(Modifier.height(8.dp))
        Text("Empieza a ver contenido en segundos", style = MaterialTheme.typography.bodyMedium, color = Colores.Texto2)

        Spacer(Modifier.height(32.dp))
        CampoTextoFlowCast(estado.nombre, modeloVista::cambiarNombre, "Nombre", Icons.Outlined.Person)
        Spacer(Modifier.height(14.dp))
        CampoTextoFlowCast(estado.correo, modeloVista::cambiarCorreo, "Correo electrónico", Icons.Outlined.Email, tipoTeclado = KeyboardType.Email)
        Spacer(Modifier.height(14.dp))
        CampoTextoFlowCast(estado.contrasena, modeloVista::cambiarContrasena, "Contraseña (mínimo 8 caracteres)", Icons.Outlined.Lock, esContrasena = true)
        estado.error?.let {
            Spacer(Modifier.height(12.dp))
            MensajeError(it)
        }

        Spacer(Modifier.height(28.dp))
        BotonLima("Registrarme", onClick = modeloVista::registrar, habilitado = estado.puedeEnviar, cargando = estado.cargando)

        Spacer(Modifier.height(28.dp))
        Text("¿Ya tienes cuenta?", style = MaterialTheme.typography.bodyMedium, color = Colores.Texto2)
        TextoEnlace("Inicia sesión", onClick = alVolver)
        Spacer(Modifier.height(40.dp))
    }
}
