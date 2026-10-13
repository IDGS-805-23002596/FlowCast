package com.flowcast.app.data.network

import com.flowcast.app.BuildConfig
import com.flowcast.app.data.session.AlmacenSesion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class ClienteApi(val api: ApiFlowCast, val clienteHttp: OkHttpClient) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
        private val servidorBackend = BuildConfig.URL_BASE_API.toHttpUrl().host
        private val rutasSinSesion = listOf("auth/iniciar-sesion", "auth/registrar", "auth/refrescar", "auth/cerrar-sesion")

        private fun retrofit(cliente: OkHttpClient): ApiFlowCast = Retrofit.Builder()
            .baseUrl(BuildConfig.URL_BASE_API)
            .client(cliente)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiFlowCast::class.java)

        fun crear(almacenSesion: AlmacenSesion): ClienteApi {
            val apiSinSesion = retrofit(OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build())
            val candado = Any()

            val interceptorSesion = Interceptor { cadena ->
                val peticion = cadena.request()
                val token = if (peticion.url.host == servidorBackend) runBlocking { almacenSesion.tokenAcceso.first() } else null
                cadena.proceed(if (token.isNullOrBlank()) peticion else peticion.newBuilder().header("Authorization", "Bearer $token").build())
            }

            val renovador = Authenticator { _, respuesta ->
                val peticion = respuesta.request
                if (peticion.url.host != servidorBackend) return@Authenticator null
                val ruta = peticion.url.encodedPath
                if (rutasSinSesion.any { ruta.endsWith(it) } || respuesta.priorResponse != null) return@Authenticator null
                val tokenUsado = peticion.header("Authorization")?.removePrefix("Bearer ")
                val tokenNuevo = synchronized(candado) {
                    runBlocking {
                        val tokenActual = almacenSesion.tokenAcceso.first()
                        if (!tokenActual.isNullOrBlank() && tokenActual != tokenUsado) return@runBlocking tokenActual
                        val refresco = almacenSesion.tokenRefresco.first() ?: return@runBlocking null
                        runCatching { apiSinSesion.refrescar(SolicitudRefresco(refresco)) }
                            .onSuccess { almacenSesion.guardarSesion(it) }
                            .getOrNull()?.tokenAcceso
                    }
                } ?: return@Authenticator null
                peticion.newBuilder().header("Authorization", "Bearer $tokenNuevo").build()
            }

            val registro = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            val clienteHttp = OkHttpClient.Builder()
                .addInterceptor(interceptorSesion)
                .addInterceptor(registro)
                .authenticator(renovador)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            return ClienteApi(retrofit(clienteHttp), clienteHttp)
        }
    }
}
