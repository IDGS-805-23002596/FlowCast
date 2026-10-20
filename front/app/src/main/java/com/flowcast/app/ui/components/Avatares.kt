package com.flowcast.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.flowcast.app.R
import com.flowcast.app.data.repository.RepositorioMusica
import com.flowcast.app.ui.theme.Colores

data class AvatarPredefinido(val codigo: String, val color: Color, @DrawableRes val imagen: Int?)

val AVATARES_PREDEFINIDOS = listOf(
    AvatarPredefinido("01", Colores.Naranja, null),
    AvatarPredefinido("02", Color(0xFF4FC3F7), R.drawable.avatar_02),
    AvatarPredefinido("03", Color(0xFFBA68C8), R.drawable.avatar_03),
    AvatarPredefinido("04", Color(0xFF81C784), R.drawable.avatar_04),
    AvatarPredefinido("05", Color(0xFFE57373), R.drawable.avatar_05),
    AvatarPredefinido("06", Color(0xFFFFD54F), R.drawable.avatar_06),
    AvatarPredefinido("07", Color(0xFFF06292), R.drawable.avatar_07),
    AvatarPredefinido("08", Color(0xFF7986CB), R.drawable.avatar_08),
    AvatarPredefinido("09", Color(0xFF4DB6AC), R.drawable.avatar_09),
    AvatarPredefinido("10", Color(0xFFFF8A65), R.drawable.avatar_10),
    AvatarPredefinido("11", Color(0xFFD9F64B), R.drawable.avatar_11),
    AvatarPredefinido("12", Color(0xFFA1887F), R.drawable.avatar_12),
    AvatarPredefinido("13", Color(0xFF90CAF9), R.drawable.avatar_13),
    AvatarPredefinido("14", Color(0xFFFFB300), R.drawable.avatar_14),
    AvatarPredefinido("15", Color(0xFF66BB6A), R.drawable.avatar_15),
    AvatarPredefinido("16", Color(0xFFFFCC80), R.drawable.avatar_16),
    AvatarPredefinido("17", Color(0xFFB0BEC5), R.drawable.avatar_17),
    AvatarPredefinido("18", Color(0xFFFFAB91), R.drawable.avatar_18),
    AvatarPredefinido("19", Color(0xFFCE93D8), R.drawable.avatar_19),
    AvatarPredefinido("20", Color(0xFFFFE082), R.drawable.avatar_20),
    AvatarPredefinido("21", Color(0xFF80CBC4), R.drawable.avatar_21),
    AvatarPredefinido("22", Color(0xFF81D4FA), R.drawable.avatar_22),
    AvatarPredefinido("23", Color(0xFFC5E1A5), R.drawable.avatar_23),
    AvatarPredefinido("24", Color(0xFF9FA8DA), R.drawable.avatar_24),
    AvatarPredefinido("25", Color(0xFF4DD0E1), R.drawable.avatar_25),
    AvatarPredefinido("26", Color(0xFFBCAAA4), R.drawable.avatar_26),
    AvatarPredefinido("27", Color(0xFFB39DDB), R.drawable.avatar_27),
    AvatarPredefinido("28", Color(0xFFA5D6A7), R.drawable.avatar_28),
    AvatarPredefinido("29", Color(0xFFFFD54F), R.drawable.avatar_29),
    AvatarPredefinido("30", Color(0xFFEF9A9A), R.drawable.avatar_30),
)

fun avatarPredefinido(codigo: String): AvatarPredefinido =
    AVATARES_PREDEFINIDOS.firstOrNull { it.codigo == codigo } ?: AVATARES_PREDEFINIDOS.first()

@Composable
fun AvatarPerfil(
    nombre: String,
    avatar: String,
    urlImagen: String?,
    radio: Dp,
    modifier: Modifier = Modifier,
    conAnillo: Boolean = true,
) {
    val predefinido = avatarPredefinido(avatar)
    Box(
        modifier = modifier
            .size(radio * 2 + 6.dp)
            .then(if (conAnillo) Modifier.border(2.5.dp, Colores.Lima, CircleShape) else Modifier)
            .padding(3.dp)
            .clip(CircleShape)
            .background(predefinido.color),
        contentAlignment = Alignment.Center,
    ) {
        when {
            urlImagen != null -> AsyncImage(
                model = RepositorioMusica.urlAbsoluta(urlImagen),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            predefinido.imagen != null -> Image(
                painter = painterResource(predefinido.imagen),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(radio * 1.45f),
            )
            else -> Text(
                nombre.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = Colores.SobreLima,
                fontSize = (radio.value * 1.1f).sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}
