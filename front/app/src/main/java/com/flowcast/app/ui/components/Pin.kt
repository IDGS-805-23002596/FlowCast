package com.flowcast.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowcast.app.data.repository.BloqueoParental
import com.flowcast.app.data.repository.EstadoPin
import com.flowcast.app.ui.theme.Colores

@Composable
fun CampoPin(valor: String, alCambiar: (String) -> Unit, marcador: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = valor,
        onValueChange = { nuevo -> if (nuevo.length <= 4 && nuevo.all { it.isDigit() }) alCambiar(nuevo) },
        placeholder = { Text(marcador, color = Colores.Texto3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = TextStyle(color = Colores.Texto, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 12.sp, textAlign = TextAlign.Center),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Colores.Texto,
            unfocusedTextColor = Colores.Texto,
            cursorColor = Colores.Lima,
            focusedBorderColor = Colores.Lima,
            unfocusedBorderColor = Colores.Borde,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun DialogoPin(
    cargando: Boolean,
    error: String?,
    alCancelar: () -> Unit,
    alConfirmar: (String) -> Unit,
    titulo: String = "PIN parental",
    descripcion: String = "Este contenido está bloqueado. Escribe tu PIN de 4 dígitos para verlo.",
) {
    var pin by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = alCancelar,
        containerColor = Colores.Superficie,
        titleContentColor = Colores.Texto,
        textContentColor = Colores.Texto2,
        icon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = Colores.Lima) },
        title = { Text(titulo) },
        text = {
            Column {
                Text(descripcion)
                Spacer(Modifier.height(16.dp))
                CampoPin(pin, { pin = it }, "••••")
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Colores.Error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { alConfirmar(pin) }, enabled = pin.length == 4 && !cargando) {
                if (cargando) CircularProgressIndicator(color = Colores.Lima, modifier = Modifier.size(18.dp)) else Text("Desbloquear", color = Colores.Lima)
            }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar", color = Colores.Texto2) } },
    )
}

@Composable
fun DialogoSinPin(alIrConfiguracion: () -> Unit, alCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCancelar,
        containerColor = Colores.Superficie,
        titleContentColor = Colores.Texto,
        textContentColor = Colores.Texto2,
        icon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = Colores.Lima) },
        title = { Text("Contenido bloqueado") },
        text = { Text("Este contenido es para adultos. Crea un PIN parental en Configuración para poder verlo.") },
        confirmButton = { TextButton(onClick = alIrConfiguracion) { Text("Ir a Configuración", color = Colores.Lima) } },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar", color = Colores.Texto2) } },
    )
}

@Composable
fun InsigniaBloqueo(modifier: Modifier = Modifier) {
    Box(modifier.size(26.dp).clip(CircleShape).background(Color(0xCC111111)), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Lock, contentDescription = "Bloqueado", tint = Colores.Lima, modifier = Modifier.size(15.dp))
    }
}

@Composable
fun DialogosParentales(
    pin: EstadoPin,
    alCancelar: () -> Unit,
    alConfirmar: (String) -> Unit,
    alIrConfiguracion: () -> Unit,
    descripcion: String = "Este contenido está bloqueado. Escribe tu PIN de 4 dígitos para verlo.",
) {
    when (pin.bloqueo) {
        BloqueoParental.REQUIERE_PIN -> DialogoPin(pin.verificando, pin.error, alCancelar, alConfirmar, descripcion = descripcion)
        BloqueoParental.SIN_PIN -> DialogoSinPin(alIrConfiguracion = { alCancelar(); alIrConfiguracion() }, alCancelar = alCancelar)
        null -> Unit
    }
}
