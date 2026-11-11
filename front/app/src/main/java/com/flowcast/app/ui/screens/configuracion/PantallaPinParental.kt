package com.flowcast.app.ui.screens.configuracion

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flowcast.app.ui.components.BotonContorno
import com.flowcast.app.ui.components.BotonLima
import com.flowcast.app.ui.components.CampoPin
import com.flowcast.app.ui.components.CampoTextoFlowCast
import com.flowcast.app.ui.components.MensajeError
import com.flowcast.app.ui.components.TextoEnlace
import com.flowcast.app.ui.theme.Colores

@Composable
fun PantallaPinParental(modeloVista: ModeloVistaPinParental, alVolver: () -> Unit) {
    val estado by modeloVista.estado.collectAsState()
    val contexto = LocalContext.current
    LaunchedEffect(estado.mensajeFinal) {
        estado.mensajeFinal?.let {
            Toast.makeText(contexto, it, Toast.LENGTH_SHORT).show()
            alVolver()
        }
    }

    Column(Modifier.fillMaxSize().background(Colores.Fondo).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver", tint = Colores.Texto) }
            Text("PIN parental", style = MaterialTheme.typography.titleLarge, color = Colores.Texto)
        }
        if (estado.cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Colores.Lima) }
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Colores.Lima, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                if (estado.tienePin) "Tu cuenta ya tiene un PIN. Aquí puedes cambiarlo o quitarlo." else "Crea un PIN de 4 dígitos. Se pedirá antes de reproducir o descargar contenido para adultos.",
                style = MaterialTheme.typography.bodyMedium,
                color = Colores.Texto2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            if (estado.tienePin) {
                if (estado.usarContrasena) {
                    EtiquetaCampo("Contraseña de la cuenta")
                    CampoTextoFlowCast(estado.contrasena, modeloVista::cambiarContrasena, "Contraseña", Icons.Outlined.Password, esContrasena = true)
                } else {
                    EtiquetaCampo("PIN actual")
                    CampoPin(estado.pinActual, modeloVista::cambiarPinActual, "••••")
                }
                Spacer(Modifier.height(6.dp))
                TextoEnlace(
                    if (estado.usarContrasena) "Usar el PIN actual" else "Olvidé mi PIN, usar la contraseña",
                    onClick = modeloVista::alternarUsarContrasena,
                    modifier = Modifier.align(Alignment.End),
                )
                Spacer(Modifier.height(16.dp))
            }
            EtiquetaCampo("Nuevo PIN")
            CampoPin(estado.pinNuevo, modeloVista::cambiarPinNuevo, "••••")
            Spacer(Modifier.height(14.dp))
            EtiquetaCampo("Confirmar PIN")
            CampoPin(estado.pinConfirmacion, modeloVista::cambiarConfirmacion, "••••")
            estado.error?.let {
                Spacer(Modifier.height(12.dp))
                MensajeError(it)
            }
            Spacer(Modifier.height(28.dp))
            BotonLima(if (estado.tienePin) "Guardar nuevo PIN" else "Crear PIN", onClick = modeloVista::guardar, cargando = estado.guardando)
            if (estado.tienePin) {
                Spacer(Modifier.height(12.dp))
                BotonContorno("Quitar PIN", onClick = modeloVista::pedirQuitar)
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (estado.confirmarQuitar) {
        AlertDialog(
            onDismissRequest = { modeloVista.pedirConfirmacionQuitar(false) },
            containerColor = Colores.Superficie,
            titleContentColor = Colores.Texto,
            textContentColor = Colores.Texto2,
            title = { Text("Quitar PIN parental") },
            text = { Text("Sin PIN, el contenido para adultos quedará bloqueado por completo hasta que crees uno nuevo.") },
            confirmButton = { TextButton(onClick = modeloVista::quitar) { Text("Quitar", color = Colores.Error) } },
            dismissButton = { TextButton(onClick = { modeloVista.pedirConfirmacionQuitar(false) }) { Text("Cancelar", color = Colores.Texto2) } },
        )
    }
}

@Composable
private fun EtiquetaCampo(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.labelLarge,
        color = Colores.Texto,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    )
}
