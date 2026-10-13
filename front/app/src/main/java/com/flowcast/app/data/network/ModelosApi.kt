package com.flowcast.app.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SolicitudInicioSesion(
    val correo: String,
    val contrasena: String,
    @SerialName("nombre_dispositivo") val nombreDispositivo: String? = null,
)

@Serializable
data class SolicitudRegistro(
    val correo: String,
    val contrasena: String,
    val nombre: String,
    @SerialName("nombre_dispositivo") val nombreDispositivo: String? = null,
)

@Serializable
data class SolicitudRefresco(@SerialName("token_refresco") val tokenRefresco: String)

@Serializable
data class RespuestaUsuario(
    val id: String,
    val correo: String,
    val nombre: String,
    @SerialName("creado_en") val creadoEn: String,
)

@Serializable
data class RespuestaTokens(
    @SerialName("token_acceso") val tokenAcceso: String,
    @SerialName("token_refresco") val tokenRefresco: String,
    @SerialName("tipo_token") val tipoToken: String = "bearer",
    @SerialName("expira_en_segundos") val expiraEnSegundos: Int,
    val usuario: RespuestaUsuario,
)

@Serializable
data class RespuestaPerfil(
    val id: String,
    val nombre: String,
    val avatar: String,
    @SerialName("es_infantil") val esInfantil: Boolean = false,
    @SerialName("tiene_pin") val tienePin: Boolean = false,
    @SerialName("url_imagen") val urlImagen: String? = null,
    @SerialName("creado_en") val creadoEn: String,
)

@Serializable
data class SolicitudActualizarPerfil(val nombre: String? = null, val avatar: String? = null)

@Serializable
data class SolicitudCrearPerfil(
    val nombre: String,
    val avatar: String = "01",
    @SerialName("es_infantil") val esInfantil: Boolean = false,
    val pin: String? = null,
)

@Serializable
data class RespuestaConexion(
    val id: String,
    val etiqueta: String,
    @SerialName("url_base") val urlBase: String,
    @SerialName("nombre_usuario") val nombreUsuario: String,
    @SerialName("estado_cuenta") val estadoCuenta: String,
    @SerialName("expira_en") val expiraEn: String? = null,
    @SerialName("max_conexiones") val maxConexiones: Int? = null,
    @SerialName("zona_horaria_servidor") val zonaHorariaServidor: String? = null,
    @SerialName("formatos_salida_permitidos") val formatosSalidaPermitidos: List<String>? = null,
    @SerialName("ultima_autenticacion_en") val ultimaAutenticacionEn: String? = null,
    @SerialName("creado_en") val creadoEn: String,
)

@Serializable
data class SolicitudEventoReproduccion(
    val resultado: String,
    @SerialName("tipo_contenido") val tipoContenido: String,
    @SerialName("contenido_id") val contenidoId: String,
    @SerialName("serie_id") val serieId: String? = null,
    @SerialName("perfil_id") val perfilId: String? = null,
    val titulo: String? = null,
    val dispositivo: String,
    val modelo: String? = null,
    @SerialName("version_app") val versionApp: String? = null,
    @SerialName("codigo_error") val codigoError: String? = null,
    val mensaje: String? = null,
    @SerialName("segundos_inicio") val segundosInicio: Double? = null,
)

@Serializable
data class SolicitudHistorial(
    @SerialName("conexion_id") val conexionId: String,
    @SerialName("tipo_contenido") val tipoContenido: String,
    @SerialName("stream_id") val streamId: String,
    val titulo: String,
    @SerialName("url_portada") val urlPortada: String? = null,
)

@Serializable
data class RespuestaHistorial(
    val id: Long,
    @SerialName("conexion_id") val conexionId: String,
    @SerialName("tipo_contenido") val tipoContenido: String,
    @SerialName("stream_id") val streamId: String,
    val titulo: String,
    @SerialName("url_portada") val urlPortada: String? = null,
    @SerialName("reproducido_en") val reproducidoEn: String,
)

@Serializable
data class SolicitudProgreso(
    @SerialName("conexion_id") val conexionId: String,
    @SerialName("tipo_contenido") val tipoContenido: String,
    @SerialName("stream_id") val streamId: String,
    @SerialName("serie_id") val serieId: String? = null,
    val titulo: String,
    @SerialName("url_portada") val urlPortada: String? = null,
    @SerialName("posicion_segundos") val posicionSegundos: Double,
    @SerialName("duracion_segundos") val duracionSegundos: Double? = null,
    val completado: Boolean? = null,
)

@Serializable
data class RespuestaProgreso(
    @SerialName("conexion_id") val conexionId: String,
    @SerialName("tipo_contenido") val tipoContenido: String,
    @SerialName("stream_id") val streamId: String,
    @SerialName("serie_id") val serieId: String? = null,
    val titulo: String,
    @SerialName("url_portada") val urlPortada: String? = null,
    @SerialName("posicion_segundos") val posicionSegundos: Double,
    @SerialName("duracion_segundos") val duracionSegundos: Double? = null,
    val completado: Boolean,
    @SerialName("actualizado_en") val actualizadoEn: String,
)

@Serializable
data class SolicitudFavorito(
    @SerialName("conexion_id") val conexionId: String,
    @SerialName("tipo_contenido") val tipoContenido: String,
    @SerialName("stream_id") val streamId: String,
    val titulo: String,
    @SerialName("url_portada") val urlPortada: String? = null,
)

@Serializable
data class RespuestaFavorito(
    @SerialName("conexion_id") val conexionId: String,
    @SerialName("tipo_contenido") val tipoContenido: String,
    @SerialName("stream_id") val streamId: String,
    val titulo: String,
    @SerialName("url_portada") val urlPortada: String? = null,
    @SerialName("creado_en") val creadoEn: String,
)

@Serializable
data class SolicitudResena(
    @SerialName("conexion_id") val conexionId: String,
    @SerialName("tipo_contenido") val tipoContenido: String,
    @SerialName("stream_id") val streamId: String,
    val titulo: String,
    val calificacion: Int,
    val texto: String? = null,
)

@Serializable
data class RespuestaResena(
    val id: Long,
    @SerialName("tipo_contenido") val tipoContenido: String,
    @SerialName("stream_id") val streamId: String,
    val titulo: String,
    val autor: String,
    val avatar: String,
    val calificacion: Int,
    val texto: String? = null,
    @SerialName("actualizado_en") val actualizadoEn: String,
    @SerialName("es_mia") val esMia: Boolean,
)

@Serializable
data class ResumenResenas(
    val promedio: Double? = null,
    val total: Int,
    val distribucion: Map<String, Int> = emptyMap(),
    val mia: RespuestaResena? = null,
    val resenas: List<RespuestaResena> = emptyList(),
)

@Serializable
data class Cancion(
    val id: String,
    val titulo: String,
    val artista: String,
    val album: String,
    @SerialName("album_id") val albumId: String,
    @SerialName("numero_pista") val numeroPista: Int? = null,
    @SerialName("duracion_segundos") val duracionSegundos: Double? = null,
    val anio: Int? = null,
    val genero: String? = null,
    @SerialName("url_audio") val urlAudio: String,
    @SerialName("url_portada") val urlPortada: String? = null,
)

@Serializable
data class Album(
    val id: String,
    val titulo: String,
    val artista: String,
    val anio: Int? = null,
    @SerialName("total_canciones") val totalCanciones: Int,
    @SerialName("duracion_segundos") val duracionSegundos: Double,
    @SerialName("url_portada") val urlPortada: String? = null,
)

@Serializable
data class AlbumConCanciones(val album: Album, val canciones: List<Cancion>)

@Serializable
data class ResumenMusica(
    @SerialName("carpeta_configurada") val carpetaConfigurada: Boolean,
    @SerialName("total_canciones") val totalCanciones: Int,
    @SerialName("total_albumes") val totalAlbumes: Int,
)

@Serializable
data class EstadoControlParental(@SerialName("tiene_pin") val tienePin: Boolean)

@Serializable
data class SolicitudPin(
    @SerialName("pin_nuevo") val pinNuevo: String,
    @SerialName("pin_actual") val pinActual: String? = null,
    val contrasena: String? = null,
)

@Serializable
data class SolicitudQuitarPin(@SerialName("pin_actual") val pinActual: String? = null, val contrasena: String? = null)

@Serializable
data class SolicitudVerificarPin(val pin: String)

@Serializable
data class RespuestaVerificarPin(
    val valido: Boolean,
    @SerialName("token_desbloqueo") val tokenDesbloqueo: String? = null,
    @SerialName("segundos_validez") val segundosValidez: Int = 0,
)

@Serializable
data class CategoriaControl(
    @SerialName("tipo_contenido") val tipoContenido: String,
    val id: String,
    val nombre: String,
    @SerialName("es_adulta") val esAdulta: Boolean = false,
    val bloqueada: Boolean = false,
)

@Serializable
data class SolicitudBloqueoCategoria(
    @SerialName("conexion_id") val conexionId: String,
    @SerialName("tipo_contenido") val tipoContenido: String,
    @SerialName("categoria_id") val categoriaId: String,
    val bloqueada: Boolean,
)

@Serializable
data class RespuestaBloqueoCategoria(
    @SerialName("tipo_contenido") val tipoContenido: String,
    @SerialName("categoria_id") val categoriaId: String,
    val bloqueada: Boolean,
)
