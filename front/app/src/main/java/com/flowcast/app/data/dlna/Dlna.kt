package com.flowcast.app.data.dlna

import java.io.StringReader
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

data class TelevisorDlna(val id: String, val nombre: String, val fabricante: String?, val modelo: String?, val urlControl: String) {
    val descripcion: String get() = listOfNotNull(fabricante, modelo ?: "Smart TV").joinToString(" · ")
}

object AnalizadorDlna {
    const val SERVICIO_AVTRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"

    fun cabeceras(respuesta: String): Map<String, String> = respuesta.lineSequence()
        .mapNotNull { linea ->
            val separador = linea.indexOf(':')
            if (separador <= 0) null else linea.substring(0, separador).trim().uppercase() to linea.substring(separador + 1).trim()
        }
        .toMap()

    fun describir(xml: String, urlDescripcion: String): TelevisorDlna? {
        val documento = runCatching {
            DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        }.getOrNull() ?: return null
        val base = documento.primero("URLBase")?.takeIf { it.isNotBlank() } ?: urlDescripcion
        val servicios = documento.getElementsByTagName("service")
        var controlRelativo: String? = null
        for (indice in 0 until servicios.length) {
            val servicio = servicios.item(indice) as? Element ?: continue
            val tipo = servicio.primero("serviceType").orEmpty()
            if (tipo.contains("AVTransport", ignoreCase = true)) {
                controlRelativo = servicio.primero("controlURL")
                break
            }
        }
        val control = controlRelativo?.takeIf { it.isNotBlank() } ?: return null
        val nombre = documento.primero("friendlyName")?.takeIf { it.isNotBlank() } ?: "Televisor"
        val udn = documento.primero("UDN")?.takeIf { it.isNotBlank() } ?: urlDescripcion
        return TelevisorDlna(
            id = udn,
            nombre = nombre,
            fabricante = documento.primero("manufacturer")?.takeIf { it.isNotBlank() },
            modelo = documento.primero("modelName")?.takeIf { it.isNotBlank() },
            urlControl = resolver(base, control),
        )
    }

    fun resolver(base: String, ruta: String): String = runCatching { URL(URL(base), ruta).toString() }.getOrDefault(ruta)

    fun tipoMimeDe(url: String): String {
        val extension = url.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "").lowercase()
        return when (extension) {
            "mkv" -> "video/x-matroska"
            "ts", "mpg", "mpeg" -> "video/mpeg"
            "m3u8" -> "application/vnd.apple.mpegurl"
            "avi" -> "video/x-msvideo"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            else -> "video/mp4"
        }
    }

    fun didl(titulo: String, url: String, tipoMime: String): String =
        "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
            "<item id=\"flowcast\" parentID=\"0\" restricted=\"1\"><dc:title>${escapar(titulo)}</dc:title><upnp:class>object.item.videoItem</upnp:class>" +
            "<res protocolInfo=\"http-get:*:$tipoMime:*\">${escapar(url)}</res></item></DIDL-Lite>"

    fun sobre(accion: String, argumentos: List<Pair<String, String>>): String {
        val cuerpo = argumentos.joinToString("") { (nombre, valor) -> "<$nombre>${escapar(valor)}</$nombre>" }
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body><u:$accion xmlns:u=\"$SERVICIO_AVTRANSPORT\">$cuerpo</u:$accion></s:Body></s:Envelope>"
    }

    fun valorDeRespuesta(xml: String, etiqueta: String): String? =
        Regex("<$etiqueta>(.*?)</$etiqueta>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.trim()

    fun escapar(texto: String): String = texto
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun org.w3c.dom.Node.primero(etiqueta: String): String? {
        val lista = when (this) {
            is Element -> getElementsByTagName(etiqueta)
            is org.w3c.dom.Document -> getElementsByTagName(etiqueta)
            else -> return null
        }
        return lista.item(0)?.textContent?.trim()
    }
}
