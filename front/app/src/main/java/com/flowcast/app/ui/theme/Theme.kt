package com.flowcast.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val EsquemaOscuro = darkColorScheme(
    primary = Colores.Lima,
    onPrimary = Colores.SobreLima,
    secondary = Colores.LimaSuave,
    onSecondary = Colores.Lima,
    background = Colores.Fondo,
    onBackground = Colores.Texto,
    surface = Colores.Superficie,
    onSurface = Colores.Texto,
    surfaceVariant = Colores.Superficie2,
    onSurfaceVariant = Colores.Texto2,
    outline = Colores.Borde,
    error = Colores.Error,
    onError = Colores.Texto,
)

val FormasFlowCast = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun TemaFlowCast(contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EsquemaOscuro,
        typography = TipografiaFlowCast,
        shapes = FormasFlowCast,
        content = contenido,
    )
}
