package com.flowcast.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowcast.app.ui.theme.Colores

@Composable
fun LogoFlowCast(tamano: TextUnit = 24.sp, modifier: Modifier = Modifier) {
    val base = tamano.value.dp
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("FLOW", color = Colores.Texto, fontSize = tamano, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
        Spacer(Modifier.width(base * 0.25f))
        Box(
            Modifier
                .background(Colores.Lima, RoundedCornerShape(base * 0.18f))
                .padding(horizontal = base * 0.3f, vertical = base * 0.1f),
        ) {
            Text("CAST", color = Colores.SobreLima, fontSize = tamano * 0.64f, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        }
    }
}

@Composable
fun BotonLima(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    cargando: Boolean = false,
    icono: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(56.dp),
        enabled = habilitado && !cargando,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Colores.Lima,
            contentColor = Colores.SobreLima,
            disabledContainerColor = Colores.Lima.copy(alpha = 0.45f),
            disabledContentColor = Colores.SobreLima,
        ),
    ) {
        if (cargando) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Colores.SobreLima, strokeWidth = 2.5.dp)
        } else {
            if (icono != null) {
                Icon(icono, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(texto, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun BotonContorno(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Colores.Borde),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Colores.Superficie, contentColor = Colores.Texto),
    ) {
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun CampoTextoFlowCast(
    valor: String,
    alCambiar: (String) -> Unit,
    marcador: String,
    icono: ImageVector,
    modifier: Modifier = Modifier,
    esContrasena: Boolean = false,
    tipoTeclado: KeyboardType = KeyboardType.Text,
) {
    var visible by remember { mutableStateOf(false) }
    val iconoFinal: (@Composable () -> Unit)? = if (esContrasena) {
        {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Ocultar contraseña" else "Mostrar contraseña",
                    tint = Colores.Texto2,
                )
            }
        }
    } else {
        null
    }
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(marcador, color = Colores.Texto2) },
        leadingIcon = { Icon(icono, contentDescription = null, tint = Colores.Texto2) },
        trailingIcon = iconoFinal,
        visualTransformation = if (esContrasena && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (esContrasena) KeyboardType.Password else tipoTeclado),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Colores.Superficie,
            unfocusedContainerColor = Colores.Superficie,
            focusedBorderColor = Colores.Lima,
            unfocusedBorderColor = Colores.Borde,
            cursorColor = Colores.Lima,
            focusedTextColor = Colores.Texto,
            unfocusedTextColor = Colores.Texto,
        ),
    )
}

@Composable
fun TextoEnlace(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        texto,
        modifier = modifier.clickable(onClick = onClick).padding(4.dp),
        color = Colores.Lima,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun MensajeError(texto: String, modifier: Modifier = Modifier) {
    Text(texto, modifier = modifier.fillMaxWidth(), color = Colores.Error, style = MaterialTheme.typography.bodyMedium)
}
