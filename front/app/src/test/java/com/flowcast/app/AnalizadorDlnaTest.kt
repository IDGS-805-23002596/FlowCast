package com.flowcast.app

import com.flowcast.app.data.dlna.AnalizadorDlna
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalizadorDlnaTest {
    private val descripcion = """
        <?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <device>
            <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
            <friendlyName>[TV] Samsung Serif TV (55)</friendlyName>
            <manufacturer>Samsung Electronics</manufacturer>
            <modelName>QE55LS01</modelName>
            <UDN>uuid:1234-abcd</UDN>
            <serviceList>
              <service>
                <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                <controlURL>/upnp/control/RenderingControl1</controlURL>
              </service>
              <service>
                <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                <controlURL>/upnp/control/AVTransport1</controlURL>
              </service>
            </serviceList>
          </device>
        </root>
    """.trimIndent()

    @Test
    fun describe_un_televisor_y_resuelve_la_url_de_control() {
        val televisor = AnalizadorDlna.describir(descripcion, "http://192.168.1.66:9197/dmr")!!
        assertEquals("[TV] Samsung Serif TV (55)", televisor.nombre)
        assertEquals("Samsung Electronics", televisor.fabricante)
        assertEquals("uuid:1234-abcd", televisor.id)
        assertEquals("http://192.168.1.66:9197/upnp/control/AVTransport1", televisor.urlControl)
        assertEquals("Samsung Electronics · QE55LS01", televisor.descripcion)
    }

    @Test
    fun ignora_dispositivos_sin_avtransport() {
        val sinServicio = descripcion.replace("AVTransport", "ConnectionManager")
        assertNull(AnalizadorDlna.describir(sinServicio, "http://192.168.1.66:9197/dmr"))
    }

    @Test
    fun lee_cabeceras_ssdp_sin_importar_mayusculas() {
        val cabeceras = AnalizadorDlna.cabeceras("HTTP/1.1 200 OK\r\nLocation: http://192.168.1.66:9197/dmr\r\nST: urn:x\r\n\r\n")
        assertEquals("http://192.168.1.66:9197/dmr", cabeceras["LOCATION"])
    }

    @Test
    fun escapa_el_sobre_soap_y_el_didl() {
        val sobre = AnalizadorDlna.sobre("SetAVTransportURI", listOf("CurrentURI" to "http://h/a.mp4?x=1&y=2"))
        assertTrue(sobre.contains("<CurrentURI>http://h/a.mp4?x=1&amp;y=2</CurrentURI>"))
        val didl = AnalizadorDlna.didl("Tom & Jerry", "http://h/a.mkv", AnalizadorDlna.tipoMimeDe("http://h/a.mkv"))
        assertTrue(didl.contains("<dc:title>Tom &amp; Jerry</dc:title>"))
        assertTrue(didl.contains("http-get:*:video/x-matroska:*"))
        assertEquals("video/mpeg", AnalizadorDlna.tipoMimeDe("http://h/live/u/p/5.ts"))
        assertEquals("application/vnd.apple.mpegurl", AnalizadorDlna.tipoMimeDe("http://h/live/u/p/5.m3u8?token=1"))
        assertEquals("PLAYING", AnalizadorDlna.valorDeRespuesta("<x><CurrentTransportState>PLAYING</CurrentTransportState></x>", "CurrentTransportState"))
    }
}
