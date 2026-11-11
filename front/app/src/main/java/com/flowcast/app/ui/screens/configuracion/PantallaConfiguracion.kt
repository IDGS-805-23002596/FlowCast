package com.flowcast.app.ui.screens.configuracion

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.flowcast.app.AplicacionFlowCast
import com.flowcast.app.BuildConfig
import com.flowcast.app.data.session.AlmacenPreferencias
import com.flowcast.app.ui.components.Pestana
import com.flowcast.app.ui.screens.inicio.DialogoConexiones
import com.flowcast.app.ui.theme.Colores
import kotlinx.coroutines.launch

private enum class DialogoAjuste { PESTANA, FORMATO, ORIENTACION }

val OPCIONES_FORMATO = listOf(AlmacenPreferencias.FORMATO_AUTO to "Automático", "m3u8" to "HLS (m3u8)", "ts" to "MPEG-TS")
val OPCIONES_ORIENTACION = listOf(
    AlmacenPreferencias.ORIENTACION_AUTO to "Automática (según el sensor)",
    AlmacenPreferencias.ORIENTACION_HORIZONTAL to "Horizontal",
    AlmacenPreferencias.ORIENTACION_VERTICAL to "Vertical",
)
val OPCIONES_PESTANA = Pestana.entries.map { it.name to it.etiqueta }

fun etiquetaDe(opciones: List<Pair<String, String>>, valor: String): String = opciones.firstOrNull { it.first == valor }?.second ?: valor

@UnstableApi
@Composable
fun PantallaConfiguracion(
    modeloVista: ModeloVistaConfiguracion,
    app: AplicacionFlowCast,
    alVolver: () -> Unit,
    alActualizarContenido: () -> Unit,
    alConfigurarPin: () -> Unit,
    alCategoriasBloqueadas: () -> Unit,
    alCambiarPerfil: () -> Unit,
    alCerrarSesion: () -> Unit,
) {
    val estado by modeloVista.estado.collectAsState()
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var conexionesAbierto by rememberSaveable { mutableStateOf(false) }
    var dialogo by rememberSaveable { mutableStateOf<DialogoAjuste?>(null) }
    LaunchedEffect(Unit) { modeloVista.cargarPin() }
    LaunchedEffect(estado.aviso) {
        estado.aviso?.let {
            Toast.makeText(contexto, it, Toast.LENGTH_SHORT).show()
            modeloVista.limpiarAviso()
        }
    }

    Column(Modifier.fillMaxSize().background(Colores.Fondo).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver", tint = Colores.Texto) }
            Text("Configuración", style = MaterialTheme.typography.titleLarge, color = Colores.Texto)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            TituloSeccionAjustes("Configuración General")
            TarjetaAjustes {
                FilaAjuste(Icons.Outlined.SwapHoriz, "Cambiar servicio", "Elegir otro proveedor de IPTV") { conexionesAbierto = true }
                DivisorAjustes()
                FilaInterruptor(
                    Icons.Outlined.SkipNext,
                    "Reproducir siguiente episodio",
                    "Al terminar un episodio empieza el siguiente",
                    estado.autoSiguiente,
                    modeloVista::cambiarAutoSiguiente,
                )
                DivisorAjustes()
                FilaAjuste(Icons.Outlined.Refresh, "Actualizar contenido", "Vuelve a pedir las listas al proveedor", cargando = estado.actualizando) {
                    modeloVista.actualizarContenido(alActualizarContenido)
                }
                DivisorAjustes()
                FilaAjuste(Icons.Outlined.Home, "Pantalla inicial", etiquetaDe(OPCIONES_PESTANA, estado.pestanaInicial)) { dialogo = DialogoAjuste.PESTANA }
            }
            TituloSeccionAjustes("Configuración de Video")
            TarjetaAjustes {
                FilaAjuste(Icons.Outlined.Hd, "Formato de stream TV en vivo", etiquetaDe(OPCIONES_FORMATO, estado.formatoEnVivo)) { dialogo = DialogoAjuste.FORMATO }
                DivisorAjustes()
                FilaAjuste(Icons.Outlined.ScreenRotation, "Orientación del reproductor", etiquetaDe(OPCIONES_ORIENTACION, estado.orientacion)) {
                    dialogo = DialogoAjuste.ORIENTACION
                }
            }
            TituloSeccionAjustes("Controles Parentales")
            TarjetaAjustes {
                val detallePin = when (estado.tienePin) {
                    true -> "PIN activo · toca para cambiarlo o quitarlo"
                    false -> "Sin PIN · el contenido para adultos queda bloqueado"
                    null -> "Consultando…"
                }
                FilaAjuste(Icons.Outlined.Pin, "Configurar PIN parental", detallePin, onClick = alConfigurarPin)
                DivisorAjustes()
                FilaAjuste(Icons.Outlined.Block, "Categorías bloqueadas", "Elige qué categorías piden el PIN", onClick = alCategoriasBloqueadas)
            }
            TituloSeccionAjustes("Acerca de")
            TarjetaAjustes {
                FilaAjuste(Icons.Outlined.SwitchAccount, "Cambiar perfil", "Elegir otro perfil de la cuenta", onClick = alCambiarPerfil)
                DivisorAjustes()
                FilaAjuste(Icons.AutoMirrored.Outlined.Logout, "Cerrar sesión", "Salir de esta cuenta") {
                    alcance.launch {
                        app.repositorioAutenticacion.cerrarSesion()
                        app.repositorioControlParental.olvidarDesbloqueo()
                        alCerrarSesion()
                    }
                }
                DivisorAjustes()
                FilaAjuste(Icons.Outlined.Info, "Versión", "FlowCast ${BuildConfig.VERSION_NAME}", conFlecha = false, onClick = null)
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (conexionesAbierto) DialogoConexiones(app, alCerrar = { conexionesAbierto = false })
    when (dialogo) {
        DialogoAjuste.PESTANA -> DialogoOpciones("Pantalla inicial", OPCIONES_PESTANA, estado.pestanaInicial, alCerrar = { dialogo = null }) {
            modeloVista.cambiarPestanaInicial(it)
            dialogo = null
        }
        DialogoAjuste.FORMATO -> DialogoOpciones("Formato de stream TV en vivo", OPCIONES_FORMATO, estado.formatoEnVivo, alCerrar = { dialogo = null }) {
            modeloVista.cambiarFormatoEnVivo(it)
            dialogo = null
        }
        DialogoAjuste.ORIENTACION -> DialogoOpciones("Orientación del reproductor", OPCIONES_ORIENTACION, estado.orientacion, alCerrar = { dialogo = null }) {
            modeloVista.cambiarOrientacion(it)
            dialogo = null
        }
        null -> Unit
    }
}

@Composable
fun TituloSeccionAjustes(texto: String) {
    Spacer(Modifier.height(22.dp))
    Text(texto, style = MaterialTheme.typography.titleMedium, color = Colores.Lima, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
}

@Composable
fun TarjetaAjustes(contenido: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Colores.Superficie)
            .border(1.dp, Colores.Borde, RoundedCornerShape(18.dp)),
    ) { contenido() }
}

@Composable
fun DivisorAjustes() {
    HorizontalDivider(color = Colores.Borde, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
fun FilaAjuste(
    icono: ImageVector,
    titulo: String,
    detalle: String,
    cargando: Boolean = false,
    conFlecha: Boolean = true,
    onClick: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icono, contentDescription = null, tint = Colores.Lima, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, color = Colores.Texto, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (detalle.isNotBlank()) Text(detalle, color = Colores.Texto2, style = MaterialTheme.typography.bodySmall)
        }
        when {
            cargando -> CircularProgressIndicator(color = Colores.Lima, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            conFlecha -> Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Colores.Texto3)
        }
    }
}

@Composable
fun FilaInterruptor(icono: ImageVector, titulo: String, detalle: String, activo: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { alCambiar(!activo) }.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icono, contentDescription = null, tint = Colores.Lima, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, color = Colores.Texto, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(detalle, color = Colores.Texto2, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = activo,
            onCheckedChange = alCambiar,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Colores.SobreLima,
                checkedTrackColor = Colores.Lima,
                uncheckedThumbColor = Colores.Texto2,
                uncheckedTrackColor = Colores.Superficie2,
                uncheckedBorderColor = Colores.Borde,
            ),
        )
    }
}

@Composable
fun DialogoOpciones(titulo: String, opciones: List<Pair<String, String>>, seleccionada: String, alCerrar: () -> Unit, alElegir: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        containerColor = Colores.Superficie,
        titleContentColor = Colores.Texto,
        title = { Text(titulo) },
        text = {
            Column {
                opciones.forEach { (valor, etiqueta) ->
                    val elegida = valor == seleccionada
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .selectable(selected = elegida, onClick = { alElegir(valor) })
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = elegida, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Colores.Lima, unselectedColor = Colores.Texto2))
                        Spacer(Modifier.width(10.dp))
                        Text(etiqueta, color = Colores.Texto, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cerrar", color = Colores.Texto2) } },
    )
}
