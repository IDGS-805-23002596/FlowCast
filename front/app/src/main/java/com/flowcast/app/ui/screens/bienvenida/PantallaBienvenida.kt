package com.flowcast.app.ui.screens.bienvenida

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowcast.app.ui.components.LogoFlowCast
import com.flowcast.app.ui.theme.Colores
import kotlinx.coroutines.delay

@Composable
fun PantallaBienvenida(alTerminar: () -> Unit) {
    val revelado = remember { Animatable(0f) }
    val opacidad = remember { Animatable(1f) }
    var mostrarLogo by remember { mutableStateOf(false) }
    var mostrarEspera by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(350)
        mostrarLogo = true
        revelado.animateTo(1f, tween(durationMillis = 750, easing = FastOutSlowInEasing))
        delay(900)
        opacidad.animateTo(0f, tween(durationMillis = 400, easing = LinearEasing))
        mostrarLogo = false
        mostrarEspera = true
        delay(700)
        alTerminar()
    }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (mostrarLogo) {
            Box(
                Modifier
                    .graphicsLayer {
                        alpha = opacidad.value
                        val escala = 0.55f + 0.45f * revelado.value
                        scaleX = escala
                        scaleY = escala
                    }
                    .drawWithContent {
                        val mitad = size.width / 2f
                        val apertura = mitad * revelado.value
                        clipRect(left = mitad - apertura, right = mitad + apertura) {
                            this@drawWithContent.drawContent()
                        }
                    },
            ) {
                LogoFlowCast(tamano = 40.sp)
            }
        }
        if (mostrarEspera) {
            CircularProgressIndicator(color = Colores.Lima, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        }
    }
}
