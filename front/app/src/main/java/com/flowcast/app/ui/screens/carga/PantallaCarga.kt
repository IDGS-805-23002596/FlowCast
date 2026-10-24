package com.flowcast.app.ui.screens.carga

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowcast.app.ui.components.BotonLima
import com.flowcast.app.ui.components.LogoFlowCast
import com.flowcast.app.ui.components.MensajeError
import com.flowcast.app.ui.components.TextoEnlace
import com.flowcast.app.ui.theme.Colores

@Composable
fun PantallaCarga(modeloVista: ModeloVistaCarga, alTerminar: (DestinoCarga) -> Unit) {
    val estado by modeloVista.estado.collectAsState()
    LaunchedEffect(estado.destino) { estado.destino?.let(alTerminar) }
    val progresoAnimado by animateFloatAsState(targetValue = estado.progreso, label = "progreso")

    Column(
        modifier = Modifier.fillMaxSize().background(Colores.Fondo).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        LogoFlowCast(tamano = 34.sp)

        Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val centro = center
                listOf(70f to 0.5f, 120f to 0.35f, 170f to 0.25f, 220f to 0.15f, 270f to 0.08f).forEach { (radio, alfa) ->
                    drawCircle(Colores.Lima.copy(alpha = alfa), radius = radio * density, center = centro, style = Stroke(1.5f * density))
                }
                drawCircle(Colores.Lima.copy(alpha = 0.18f), radius = 70f * density, center = centro)
            }
            Box(Modifier.size(112.dp).clip(CircleShape).background(Colores.Lima), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.LiveTv, contentDescription = null, tint = Colores.SobreLima, modifier = Modifier.size(52.dp))
            }
        }

        Text(estado.mensaje, style = MaterialTheme.typography.titleLarge, color = Colores.Texto)
        Spacer(Modifier.height(18.dp))
        PuntosDeProgreso(pasoActual = estado.paso, total = 5)

        estado.error?.let { mensaje ->
            Spacer(Modifier.height(28.dp))
            MensajeError(mensaje, Modifier.padding(horizontal = 40.dp))
            Spacer(Modifier.height(16.dp))
            BotonLima("Reintentar", onClick = modeloVista::cargar, modifier = Modifier.padding(horizontal = 40.dp))
            Spacer(Modifier.height(8.dp))
            TextoEnlace("Cerrar sesión", onClick = modeloVista::cerrarSesion)
        }

        Spacer(Modifier.weight(1f))
        LinearProgressIndicator(
            progress = { progresoAnimado },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Colores.Lima,
            trackColor = Colores.Superficie2,
            strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.height(14.dp))
        Text("${(progresoAnimado * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = Colores.Lima)
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun PuntosDeProgreso(pasoActual: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { indice ->
            when {
                indice == pasoActual -> Box(Modifier.width(40.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Colores.Lima))
                indice < pasoActual -> Box(Modifier.size(10.dp).clip(CircleShape).background(Colores.Lima))
                else -> Box(Modifier.size(10.dp).clip(CircleShape).background(Colores.Texto3))
            }
        }
    }
}
