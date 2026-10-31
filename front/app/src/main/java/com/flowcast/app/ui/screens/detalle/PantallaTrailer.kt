package com.flowcast.app.ui.screens.detalle

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as ColorAndroid
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.flowcast.app.ui.theme.Colores

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PantallaTrailer(idYoutube: String, alVolver: () -> Unit) {
    val contexto = LocalContext.current
    val vistaWeb = remember {
        WebView(contexto).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            setBackgroundColor(ColorAndroid.BLACK)
            val pagina = """
                <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
                <style>html,body{margin:0;background:#000;height:100%}iframe{position:absolute;inset:0;width:100%;height:100%;border:0}</style></head>
                <body><iframe src="https://www.youtube.com/embed/$idYoutube?autoplay=1&playsinline=1&rel=0&modestbranding=1"
                allow="autoplay; encrypted-media; picture-in-picture" allowfullscreen></iframe></body></html>
            """.trimIndent()
            loadDataWithBaseURL("https://flowcast.app/", pagina, "text/html", "utf-8", null)
        }
    }
    DisposableEffect(Unit) { onDispose { vistaWeb.destroy() } }

    Column(Modifier.fillMaxSize().background(Colores.Fondo).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver", tint = Colores.Texto) }
            Text("Tráiler", style = MaterialTheme.typography.titleLarge, color = Colores.Texto, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$idYoutube")))
            }) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = Colores.Lima)
                Spacer(Modifier.padding(4.dp))
                Text("Abrir en YouTube", color = Colores.Lima)
            }
        }
        Spacer(Modifier.height(8.dp))
        AndroidView(factory = { vistaWeb }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Spacer(Modifier.height(16.dp))
        Text("Si el vídeo no arranca aquí, usa \"Abrir en YouTube\".", color = Colores.Texto2, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 24.dp))
    }
}
