package com.flowcast.app.data.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.WeakHashMap

private val cuerpos = WeakHashMap<HttpException, String>()

private fun cuerpoDe(error: HttpException): String = synchronized(cuerpos) {
    cuerpos.getOrPut(error) { runCatching { error.response()?.errorBody()?.string() }.getOrNull().orEmpty() }
}

private fun detalleDe(error: HttpException): JsonElement? =
    runCatching { Json.parseToJsonElement(cuerpoDe(error)).jsonObject["detail"] }.getOrNull()

fun mensajeDeError(error: Throwable): String = when (error) {
    is HttpException -> mensajeDelBackend(error)
    is UnknownHostException, is ConnectException -> "No se pudo conectar con el servidor"
    is SocketTimeoutException -> "El servidor tardó demasiado en responder"
    is IOException -> "Error de red: revisa tu conexión"
    else -> error.message ?: "Error inesperado"
}

private fun mensajeDelBackend(error: HttpException): String = when (val detalle = detalleDe(error)) {
    is JsonPrimitive -> detalle.content
    is JsonObject -> detalle["mensaje"]?.jsonPrimitive?.content ?: "Error ${error.code()} del servidor"
    is JsonArray -> detalle.mapNotNull { elemento ->
        val campo = elemento.jsonObject["loc"]?.let { (it as? JsonArray)?.lastOrNull()?.jsonPrimitive?.content }
        val mensaje = elemento.jsonObject["msg"]?.jsonPrimitive?.content
        if (campo != null && mensaje != null) "$campo: $mensaje" else mensaje
    }.joinToString("\n").ifBlank { "Datos inválidos" }
    else -> "Error ${error.code()} del servidor"
}

fun esSesionInvalida(error: Throwable): Boolean = error is HttpException && error.code() == 401

fun codigoBloqueoParental(error: Throwable): String? {
    val http = error as? HttpException ?: return null
    if (http.code() != 423) return null
    return (detalleDe(http) as? JsonObject)?.get("codigo")?.jsonPrimitive?.content
}
