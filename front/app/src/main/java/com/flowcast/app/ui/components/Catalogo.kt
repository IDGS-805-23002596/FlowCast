package com.flowcast.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.flowcast.app.ui.theme.Colores

data class ElementoCatalogo(
    val id: String,
    val titulo: String,
    val portada: String?,
    val categoria: String,
    val anio: String?,
    val calificacion: Double?,
    val esSerie: Boolean,
    val bloqueada: Boolean = false,
)

@Composable
fun Poster(url: String?, modifier: Modifier = Modifier, forma: RoundedCornerShape = RoundedCornerShape(14.dp)) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        placeholder = ColorPainter(Colores.Superficie2),
        error = ColorPainter(Colores.Superficie2),
        modifier = modifier.clip(forma).background(Colores.Superficie2),
    )
}

@Composable
fun Chip(texto: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Colores.LimaChip)
            .border(1.dp, Color(0xFF4A5A1E), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(texto, color = Colores.Lima, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun TarjetaContenido(elemento: ElementoCatalogo, onClick: () -> Unit, numero: Int? = null) {
    Column(
        Modifier
            .width(170.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Colores.Superficie)
            .border(1.dp, Colores.Borde, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box {
            Poster(elemento.portada, Modifier.fillMaxWidth().height(215.dp))
            if (elemento.bloqueada) InsigniaBloqueo(Modifier.align(Alignment.TopEnd).padding(6.dp))
            if (numero != null) {
                Text(
                    numero.toString(),
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 4.dp),
                    color = Colores.Lima,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 72.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            elemento.titulo,
            color = Colores.Texto,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Chip(elemento.categoria, Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun SelloTendencia(modifier: Modifier = Modifier) {
    Box(
        modifier
            .rotate(-4f)
            .background(Colores.Lima, RoundedCornerShape(6.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text("EN TENDENCIA!", color = Colores.SobreLima, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
    }
}

@Composable
fun EncabezadoSeccion(titulo: String, alVerMas: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.headlineSmall, color = Colores.Texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.width(60.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Colores.Lima))
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Colores.Superficie2)
                .border(1.dp, Color(0xFF4A5A1E), RoundedCornerShape(20.dp))
                .clickable(onClick = alVerMas)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Ver más", color = Colores.Texto, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = Colores.Lima, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun CarruselDestacado(
    elementos: List<ElementoCatalogo>,
    alAbrir: (ElementoCatalogo) -> Unit,
    alReproducir: (ElementoCatalogo) -> Unit,
) {
    if (elementos.isEmpty()) return
    val estadoPager = rememberPagerState(pageCount = { elementos.size })
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = estadoPager,
            contentPadding = PaddingValues(horizontal = 44.dp),
            pageSpacing = 12.dp,
        ) { pagina ->
            val elemento = elementos[pagina]
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Colores.Superficie)
                    .border(1.dp, Colores.Borde, RoundedCornerShape(24.dp))
                    .clickable { alAbrir(elemento) },
            ) {
                Poster(elemento.portada, Modifier.fillMaxSize().padding(8.dp), RoundedCornerShape(20.dp))
                if (elemento.bloqueada) InsigniaBloqueo(Modifier.align(Alignment.TopEnd).padding(20.dp))
                Box(
                    Modifier.fillMaxSize().padding(8.dp).clip(RoundedCornerShape(20.dp)).background(
                        Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color.Transparent, 1f to Color(0xEE111111)),
                    ),
                )
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        elemento.titulo,
                        color = Colores.Texto,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))
                    BotonLima("Reproducir", onClick = { alReproducir(elemento) }, modifier = Modifier.width(222.dp), icono = Icons.Filled.PlayArrow)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        listOfNotNull(elemento.anio, elemento.categoria).joinToString("  •  "),
                        color = Colores.Texto2,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(elementos.size) { indice ->
                if (indice == estadoPager.currentPage) {
                    Box(Modifier.width(28.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Colores.Lima))
                } else {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Colores.Texto3))
                }
            }
        }
    }
}
