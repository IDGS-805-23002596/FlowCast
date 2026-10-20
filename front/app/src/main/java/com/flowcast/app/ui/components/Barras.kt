package com.flowcast.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowcast.app.data.session.PerfilGuardado
import com.flowcast.app.ui.theme.Colores

enum class Pestana(val etiqueta: String, val icono: ImageVector) {
    TV("TV en vivo", Icons.Outlined.LiveTv),
    PELICULAS("Películas", Icons.Outlined.Movie),
    SERIES("Serie", Icons.Outlined.VideoLibrary),
    HISTORIAL("Historial", Icons.Outlined.History),
    ALMACEN("Almacén", Icons.Outlined.Inventory2),
}

@Composable
fun Avatar(inicial: String, radio: Dp, modifier: Modifier = Modifier, conAnillo: Boolean = true) {
    Box(
        modifier = modifier
            .size(radio * 2 + 6.dp)
            .then(if (conAnillo) Modifier.border(2.5.dp, Colores.Lima, CircleShape) else Modifier)
            .padding(3.dp)
            .clip(CircleShape)
            .background(Colores.Naranja),
        contentAlignment = Alignment.Center,
    ) {
        Text(inicial, color = Colores.SobreLima, fontSize = (radio.value * 1.1f).sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun BarraSuperior(
    perfil: PerfilGuardado?,
    alTocarAvatar: () -> Unit,
    alRefrescar: () -> Unit = {},
    alCambiarConexion: () -> Unit = {},
    alFavoritos: () -> Unit = {},
    alMenu: () -> Unit = {},
    alBuscar: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoFlowCast(tamano = 17.sp)
        Spacer(Modifier.weight(1f))
        val acciones = listOf(
            Icons.Outlined.Refresh to alRefrescar,
            Icons.Outlined.SwapHoriz to alCambiarConexion,
            Icons.Outlined.FavoriteBorder to alFavoritos,
            Icons.Outlined.Menu to alMenu,
            Icons.Outlined.Search to alBuscar,
        )
        acciones.forEach { (icono, accion) ->
            IconButton(onClick = accion, modifier = Modifier.size(40.dp)) {
                Icon(icono, contentDescription = null, tint = Colores.Texto, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(4.dp))
        AvatarPerfil(nombre = perfil?.nombre ?: "?", avatar = perfil?.avatar ?: "01", urlImagen = perfil?.urlImagen, radio = 16.dp, modifier = Modifier.clickable(onClick = alTocarAvatar))
    }
}

@Composable
fun BarraInferior(activa: Pestana, alSeleccionar: (Pestana) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Colores.Superficie)
            .border(1.dp, Colores.Borde, RoundedCornerShape(24.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pestana.entries.forEach { pestana ->
            val esActiva = pestana == activa
            Column(
                modifier = Modifier.weight(1f).clickable { alSeleccionar(pestana) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 50.dp, height = 36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (esActiva) Colores.LimaSuave else Colores.Superficie),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(pestana.icono, contentDescription = pestana.etiqueta, tint = if (esActiva) Colores.Lima else Colores.Texto2, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    pestana.etiqueta,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (esActiva) Colores.Lima else Colores.Texto2,
                    fontWeight = if (esActiva) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun Pildora(texto: String, onClick: () -> Unit, icono: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Colores.Superficie2)
            .clickable(onClick = onClick)
            .height(40.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icono != null) Icon(icono, contentDescription = null, tint = Colores.Texto, modifier = Modifier.size(20.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = Colores.Texto, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EstadoVacio(icono: ImageVector, mensaje: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icono, contentDescription = null, tint = Colores.Texto3, modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(24.dp))
        Text(mensaje, style = MaterialTheme.typography.bodyLarge, color = Colores.Texto2)
    }
}
