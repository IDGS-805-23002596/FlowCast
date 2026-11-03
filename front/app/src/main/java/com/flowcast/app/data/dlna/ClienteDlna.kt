package com.flowcast.app.data.dlna

import android.content.Context
import android.net.wifi.WifiManager
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ClienteDlna(private val contexto: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun buscar(duracionMs: Long = 4000, direccionesExtra: List<String> = emptyList()): List<TelevisorDlna> = withContext(Dispatchers.IO) {
        val wifi = contexto.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val candado = runCatching { wifi?.createMulticastLock("flowcast-dlna")?.apply { setReferenceCounted(false); acquire() } }.getOrNull()
        try {
            val ubicaciones = linkedSetOf<String>()
            DatagramSocket().use { socket ->
                socket.soTimeout = 800
                val mensaje = M_SEARCH.toByteArray()
                val destinos = (listOf(GRUPO_SSDP) + direccionesExtra).mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }
                repeat(2) {
                    destinos.forEach { destino -> runCatching { socket.send(DatagramPacket(mensaje, mensaje.size, destino, PUERTO_SSDP)) } }
                }
                val fin = System.currentTimeMillis() + duracionMs
                val buffer = ByteArray(4096)
                while (System.currentTimeMillis() < fin) {
                    val paquete = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(paquete)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val cabeceras = AnalizadorDlna.cabeceras(String(paquete.data, 0, paquete.length))
                    cabeceras["LOCATION"]?.let { ubicaciones += it }
                }
            }
            coroutineScope { ubicaciones.map { ubicacion -> async { describir(ubicacion) } }.awaitAll() }
                .filterNotNull()
                .distinctBy { it.id }
        } finally {
            runCatching { candado?.release() }
        }
    }

    private fun describir(url: String): TelevisorDlna? = runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { respuesta ->
            if (!respuesta.isSuccessful) null else AnalizadorDlna.describir(respuesta.body?.string().orEmpty(), url)
        }
    }.getOrNull()

    suspend fun reproducir(televisor: TelevisorDlna, url: String, titulo: String) = withContext(Dispatchers.IO) {
        val metadatos = AnalizadorDlna.didl(titulo, url, AnalizadorDlna.tipoMimeDe(url))
        accion(televisor, "SetAVTransportURI", listOf("InstanceID" to "0", "CurrentURI" to url, "CurrentURIMetaData" to metadatos))
        accion(televisor, "Play", listOf("InstanceID" to "0", "Speed" to "1"))
    }

    suspend fun detener(televisor: TelevisorDlna) = withContext(Dispatchers.IO) {
        accion(televisor, "Stop", listOf("InstanceID" to "0"))
    }

    suspend fun estado(televisor: TelevisorDlna): String? = withContext(Dispatchers.IO) {
        AnalizadorDlna.valorDeRespuesta(accion(televisor, "GetTransportInfo", listOf("InstanceID" to "0")), "CurrentTransportState")
    }

    private fun accion(televisor: TelevisorDlna, nombre: String, argumentos: List<Pair<String, String>>): String {
        val peticion = Request.Builder()
            .url(televisor.urlControl)
            .header("SOAPACTION", "\"${AnalizadorDlna.SERVICIO_AVTRANSPORT}#$nombre\"")
            .post(AnalizadorDlna.sobre(nombre, argumentos).toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
            .build()
        http.newCall(peticion).execute().use { respuesta ->
            if (!respuesta.isSuccessful) throw IOException("La TV rechazó la orden $nombre (${respuesta.code})")
            return respuesta.body?.string().orEmpty()
        }
    }

    companion object {
        private const val GRUPO_SSDP = "239.255.255.250"
        private const val PUERTO_SSDP = 1900
        private val M_SEARCH = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"
    }
}
