package com.flowcast.app.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Categoria(
    val id: String,
    val nombre: String,
    @SerialName("padre_id") val padreId: String? = null,
    val total: Int? = null,
    @SerialName("es_adulta") val esAdulta: Boolean = false,
    val bloqueada: Boolean = false,
)

@Serializable
data class Canal(
    val id: String,
    val nombre: String,
    val icono: String? = null,
    @SerialName("categoria_id") val categoriaId: String? = null,
    val numero: Int? = null,
    @SerialName("epg_id") val epgId: String? = null,
    @SerialName("dias_catchup") val diasCatchup: Int = 0,
    val bloqueada: Boolean = false,
)

@Serializable
data class Pelicula(
    val id: String,
    val nombre: String,
    val portada: String? = null,
    @SerialName("categoria_id") val categoriaId: String? = null,
    val calificacion: Double? = null,
    val extension: String? = null,
    @SerialName("agregado_en") val agregadoEn: String? = null,
    val bloqueada: Boolean = false,
)

@Serializable
data class FichaPelicula(
    val pelicula: Pelicula,
    val sinopsis: String? = null,
    val genero: String? = null,
    val director: String? = null,
    val reparto: String? = null,
    @SerialName("fecha_estreno") val fechaEstreno: String? = null,
    @SerialName("duracion_segundos") val duracionSegundos: Int? = null,
    @SerialName("portada_grande") val portadaGrande: String? = null,
    val fondo: String? = null,
    @SerialName("trailer_youtube") val trailerYoutube: String? = null,
    @SerialName("tmdb_id") val tmdbId: String? = null,
)

@Serializable
data class Serie(
    val id: String,
    val nombre: String,
    val portada: String? = null,
    @SerialName("categoria_id") val categoriaId: String? = null,
    val calificacion: Double? = null,
    val sinopsis: String? = null,
    val genero: String? = null,
    @SerialName("fecha_estreno") val fechaEstreno: String? = null,
    val bloqueada: Boolean = false,
)

@Serializable
data class Episodio(
    val id: String,
    val temporada: Int,
    val numero: Int,
    val titulo: String,
    val extension: String? = null,
    val imagen: String? = null,
    val sinopsis: String? = null,
    @SerialName("duracion_segundos") val duracionSegundos: Int? = null,
)

@Serializable
data class Temporada(val numero: Int, val nombre: String? = null, val portada: String? = null, val episodios: List<Episodio>)

@Serializable
data class FichaSerie(
    val serie: Serie,
    val director: String? = null,
    val reparto: String? = null,
    val fondo: String? = null,
    @SerialName("trailer_youtube") val trailerYoutube: String? = null,
    val temporadas: List<Temporada>,
)

@Serializable
data class ProgramaEpg(
    val titulo: String,
    val descripcion: String? = null,
    val inicio: String,
    val fin: String,
    @SerialName("en_emision") val enEmision: Boolean,
)

@Serializable
data class RespuestaReproduccion(
    val url: String,
    val formato: String,
    @SerialName("tipo_contenido") val tipoContenido: String,
    @SerialName("contenido_id") val contenidoId: String,
    @SerialName("user_agent") val userAgent: String = "FlowCast/0.1",
)

@Serializable
data class RespuestaBusqueda(
    val canales: List<Canal> = emptyList(),
    val peliculas: List<Pelicula> = emptyList(),
    val series: List<Serie> = emptyList(),
)

@Serializable
data class Destacados(val peliculas: List<Pelicula> = emptyList(), val series: List<Serie> = emptyList())

@Serializable
data class Actor(val nombre: String, val personaje: String? = null, val foto: String? = null) {
    val iniciales: String get() = nombre.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
}

@Serializable
data class Elenco(val actores: List<Actor> = emptyList(), val fuente: String = "proveedor")
