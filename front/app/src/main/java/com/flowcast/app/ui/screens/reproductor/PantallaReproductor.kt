package com.flowcast.app.ui.screens.reproductor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.widget.Toast
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.StayCurrentLandscape
import androidx.compose.material.icons.outlined.StayCurrentPortrait
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.media3.common.C
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.flowcast.app.data.session.AlmacenPreferencias
import com.flowcast.app.ui.components.BotonLima
import com.flowcast.app.ui.components.DialogosParentales
import com.flowcast.app.ui.components.MensajeError
import com.flowcast.app.ui.theme.Colores

@UnstableApi
@Composable
fun PantallaReproductor(
    modeloVista: ModeloVistaReproductor,
    orientacionPreferida: String,
    alVolver: () -> Unit,
    alIrConfiguracion: () -> Unit,
    alReproducirSiguiente: (ObjetivoReproduccion) -> Unit,
) {
    val estado by modeloVista.estado.collectAsState()
    val contexto = LocalContext.current
    LaunchedEffect(estado.siguiente) {
        estado.siguiente?.let { objetivo ->
            modeloVista.siguienteConsumido()
            alReproducirSiguiente(objetivo)
        }
    }

    var orientacion by rememberSaveable { mutableIntStateOf(orientacionInicial(orientacionPreferida)) }
    var controlesVisibles by remember { mutableStateOf(true) }
    var dialogoPistas by remember { mutableStateOf<Int?>(null) }
    var hojaTvAbierta by remember { mutableStateOf(false) }
    LaunchedEffect(estado.aviso) {
        estado.aviso?.let {
            Toast.makeText(contexto, it, Toast.LENGTH_SHORT).show()
            modeloVista.limpiarAviso()
        }
    }
    LaunchedEffect(orientacion) { contexto.actividad()?.requestedOrientation = orientacion }
    DisposableEffect(Unit) {
        val actividad = contexto.actividad()
        val ventana = actividad?.window
        val controlador = ventana?.let { WindowCompat.getInsetsController(it, it.decorView) }
        ventana?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controlador?.hide(WindowInsetsCompat.Type.systemBars())
        controlador?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            actividad?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ventana?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controlador?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = modeloVista.reproductor
                    useController = true
                    controllerShowTimeoutMs = 3000
                    setShowPreviousButton(false)
                    setShowNextButton(false)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibilidad -> controlesVisibles = visibilidad == View.VISIBLE })
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (controlesVisibles) Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color(0xCC000000), 1f to Color.Transparent))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = alVolver) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver", tint = Colores.Texto)
            }
            Spacer(Modifier.width(4.dp))
            Text(estado.titulo, color = Colores.Texto, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                hojaTvAbierta = true
                modeloVista.buscarTelevisores()
            }) {
                Icon(
                    if (estado.televisorActivo != null) Icons.Outlined.CastConnected else Icons.Outlined.Cast,
                    contentDescription = "Reproducir en",
                    tint = if (estado.televisorActivo != null) Colores.Lima else Colores.Texto,
                )
            }
            IconButton(onClick = { dialogoPistas = C.TRACK_TYPE_AUDIO }) {
                Icon(Icons.Outlined.MusicNote, contentDescription = "Pistas de audio", tint = Colores.Texto)
            }
            IconButton(onClick = { dialogoPistas = C.TRACK_TYPE_TEXT }) {
                Icon(Icons.Outlined.ClosedCaption, contentDescription = "Subtítulos", tint = if (estado.pistasSubtitulos.any { it.seleccionada }) Colores.Lima else Colores.Texto)
            }
            IconButton(onClick = {
                orientacion = when (orientacion) {
                    ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                }
            }) {
                Icon(
                    when (orientacion) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> Icons.Outlined.StayCurrentLandscape
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> Icons.Outlined.StayCurrentPortrait
                        else -> Icons.Outlined.ScreenRotation
                    },
                    contentDescription = "Girar pantalla",
                    tint = if (orientacion == ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR) Colores.Texto else Colores.Lima,
                )
            }
        }

        if (modeloVista.esEnVivo && estado.totalCanales > 1 && controlesVisibles) {
            BotonCanal(Icons.Outlined.ChevronLeft, "Canal anterior", Modifier.align(Alignment.CenterStart), onClick = modeloVista::canalAnterior)
            BotonCanal(Icons.Outlined.ChevronRight, "Canal siguiente", Modifier.align(Alignment.CenterEnd), onClick = modeloVista::canalSiguiente)
            Text(
                "Canal ${estado.indiceCanal + 1} de ${estado.totalCanales}",
                color = Colores.Texto,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 92.dp)
                    .clip(CircleShape)
                    .background(Color(0x99111111))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        estado.televisorActivo?.let { televisor ->
            Column(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp)).background(Color(0xDD1F1F1F)).padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.CastConnected, contentDescription = null, tint = Colores.Lima, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text("Reproduciendo en", color = Colores.Texto2, style = MaterialTheme.typography.bodyMedium)
                Text(televisor.nombre, color = Colores.Texto, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                BotonLima("Volver a este teléfono", onClick = modeloVista::volverAlTelefono, modifier = Modifier.width(260.dp))
            }
        }
        if (estado.cargando) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Colores.Lima)
        }
        estado.error?.let { mensaje ->
            Column(Modifier.align(Alignment.Center).padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                MensajeError(mensaje)
                Spacer(Modifier.height(16.dp))
                BotonLima("Reintentar", onClick = modeloVista::cargar, modifier = Modifier.width(220.dp))
            }
        }
    }
    if (hojaTvAbierta) {
        HojaReproducirEn(
            televisores = estado.televisores,
            buscando = estado.buscandoTelevisores,
            activo = estado.televisorActivo,
            enviandoA = estado.enviandoA,
            alElegirTelefono = {
                modeloVista.volverAlTelefono()
                hojaTvAbierta = false
            },
            alElegirTelevisor = { televisor ->
                modeloVista.enviarATelevisor(televisor)
                hojaTvAbierta = false
            },
            alCerrar = { hojaTvAbierta = false },
        )
    }
    when (dialogoPistas) {
        C.TRACK_TYPE_AUDIO -> DialogoPistas(
            titulo = "Pistas de Audio",
            opciones = estado.pistasAudio,
            vacio = "Este contenido no tiene pistas de audio para elegir",
            alElegir = { clave -> modeloVista.elegirPista(C.TRACK_TYPE_AUDIO, clave); dialogoPistas = null },
            alCerrar = { dialogoPistas = null },
        )
        C.TRACK_TYPE_TEXT -> DialogoPistas(
            titulo = "Subtítulos",
            opciones = listOf(PistaOpcion("", "Desactivar", estado.pistasSubtitulos.none { it.seleccionada })) + estado.pistasSubtitulos,
            vacio = "",
            alElegir = { clave ->
                if (clave.isEmpty()) modeloVista.desactivarSubtitulos() else modeloVista.elegirPista(C.TRACK_TYPE_TEXT, clave)
                dialogoPistas = null
            },
            alCerrar = { dialogoPistas = null },
        )
        else -> Unit
    }
    DialogosParentales(
        pin = estado.pin,
        alCancelar = modeloVista::cancelarDesbloqueo,
        alConfirmar = modeloVista::desbloquear,
        alIrConfiguracion = alIrConfiguracion,
    )
}

@Composable
private fun DialogoPistas(titulo: String, opciones: List<PistaOpcion>, vacio: String, alElegir: (String) -> Unit, alCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        containerColor = Colores.Superficie,
        titleContentColor = Colores.Texto,
        textContentColor = Colores.Texto2,
        title = { Text(titulo, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                if (opciones.isEmpty()) Text(vacio)
                opciones.forEach { opcion ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (opcion.seleccionada) Colores.Lima else Color.Transparent)
                            .clickable { alElegir(opcion.clave) }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        Text(
                            opcion.etiqueta,
                            color = if (opcion.seleccionada) Colores.SobreLima else Colores.Texto,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (opcion.seleccionada) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cerrar", color = Colores.Lima) } },
    )
}

@Composable
private fun BotonCanal(icono: ImageVector, descripcion: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .padding(12.dp)
            .size(56.dp)
            .clip(CircleShape)
            .background(Color(0x99111111))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, contentDescription = descripcion, tint = Colores.Lima, modifier = Modifier.size(36.dp))
    }
}

private fun orientacionInicial(preferida: String): Int = when (preferida) {
    AlmacenPreferencias.ORIENTACION_HORIZONTAL -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    AlmacenPreferencias.ORIENTACION_VERTICAL -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
}

private fun Context.actividad(): Activity? {
    var actual: Context? = this
    while (actual is ContextWrapper) {
        if (actual is Activity) return actual
        actual = actual.baseContext
    }
    return null
}
