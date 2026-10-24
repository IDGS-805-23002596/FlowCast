package com.flowcast.app.ui.screens.inicio_sesion

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
fun PantallaInicioSesion(
    modeloVista: ModeloVistaInicioSesion,
    alEntrar: () -> Unit,
    alIrARegistro: () -> Unit,
) {
    val estado by modeloVista.estado.collectAsState()

    LaunchedEffect(estado.exito) { if (estado.exito) alEntrar() }

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
        Spacer(Modifier.height(110.dp))
        LogoFlowCast(tamano = 36.sp)
        Spacer(Modifier.height(44.dp))
        Text("Bienvenido", style = MaterialTheme.typography.displaySmall, color = Colores.Texto)
        Spacer(Modifier.height(8.dp))
        Text("Ingresa tus credenciales para continuar", style = MaterialTheme.typography.bodyMedium, color = Colores.Texto2)

        Spacer(Modifier.height(32.dp))
        CampoTextoFlowCast(
            valor = estado.correo,
            alCambiar = modeloVista::cambiarCorreo,
            marcador = "Correo electrónico",
            icono = Icons.Outlined.Email,
            tipoTeclado = KeyboardType.Email,
        )
        Spacer(Modifier.height(14.dp))
        CampoTextoFlowCast(
            valor = estado.contrasena,
            alCambiar = modeloVista::cambiarContrasena,
            marcador = "Contraseña",
            icono = Icons.Outlined.Lock,
            esContrasena = true,
        )
        estado.error?.let {
            Spacer(Modifier.height(8.dp))
            MensajeError(it)
        }

        Spacer(Modifier.height(20.dp))
        BotonLima("Iniciar sesión", onClick = modeloVista::iniciarSesion, habilitado = estado.puedeEnviar, cargando = estado.cargando)

        Spacer(Modifier.height(32.dp))
        Text("¿No tienes cuenta?", style = MaterialTheme.typography.bodyMedium, color = Colores.Texto2)
        TextoEnlace("Regístrate", onClick = alIrARegistro)

        Spacer(Modifier.height(40.dp))
    }
}
