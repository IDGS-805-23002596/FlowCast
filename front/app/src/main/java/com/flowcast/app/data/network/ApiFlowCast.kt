package com.flowcast.app.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import okhttp3.MultipartBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.Part
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiFlowCast {
    @POST("auth/registrar")
    suspend fun registrar(@Body datos: SolicitudRegistro): RespuestaTokens

    @POST("auth/iniciar-sesion")
    suspend fun iniciarSesion(@Body datos: SolicitudInicioSesion): RespuestaTokens

    @POST("auth/refrescar")
    suspend fun refrescar(@Body datos: SolicitudRefresco): RespuestaTokens

    @POST("auth/cerrar-sesion")
    suspend fun cerrarSesion(@Body datos: SolicitudRefresco): Response<Unit>

    @GET("auth/yo")
    suspend fun yo(): RespuestaUsuario

    @GET("perfiles")
    suspend fun listarPerfiles(): List<RespuestaPerfil>

    @POST("perfiles")
    suspend fun crearPerfil(@Body datos: SolicitudCrearPerfil): RespuestaPerfil

    @PATCH("perfiles/{perfilId}")
    suspend fun actualizarPerfil(@Path("perfilId") perfilId: String, @Body datos: SolicitudActualizarPerfil): RespuestaPerfil

    @DELETE("perfiles/{perfilId}")
    suspend fun borrarPerfil(@Path("perfilId") perfilId: String): Response<Unit>

    @Multipart
    @PUT("perfiles/{perfilId}/imagen")
    suspend fun subirImagenPerfil(@Path("perfilId") perfilId: String, @Part archivo: MultipartBody.Part): RespuestaPerfil

    @DELETE("perfiles/{perfilId}/imagen")
    suspend fun quitarImagenPerfil(@Path("perfilId") perfilId: String): RespuestaPerfil

    @GET("conexiones")
    suspend fun listarConexiones(): List<RespuestaConexion>

    @GET("conexiones/{conexionId}/categorias")
    suspend fun listarCategorias(
        @Path("conexionId") conexionId: String,
        @Query("tipo") tipo: String,
        @Query("con_totales") conTotales: Boolean = false,
    ): List<Categoria>

    @GET("conexiones/{conexionId}/canales")
    suspend fun listarCanales(
        @Path("conexionId") conexionId: String,
        @Query("categoria_id") categoriaId: String?,
        @Header("X-Desbloqueo-Parental") desbloqueo: String? = null,
    ): List<Canal>

    @GET("conexiones/{conexionId}/canales/{canalId}/epg")
    suspend fun epgCanal(@Path("conexionId") conexionId: String, @Path("canalId") canalId: String, @Query("limite") limite: Int = 10): List<ProgramaEpg>

    @GET("conexiones/{conexionId}/peliculas")
    suspend fun listarPeliculas(
        @Path("conexionId") conexionId: String,
        @Query("categoria_id") categoriaId: String?,
        @Query("limite") limite: Int? = null,
        @Header("X-Desbloqueo-Parental") desbloqueo: String? = null,
    ): List<Pelicula>

    @GET("conexiones/{conexionId}/peliculas/{peliculaId}")
    suspend fun fichaPelicula(
        @Path("conexionId") conexionId: String,
        @Path("peliculaId") peliculaId: String,
        @Header("X-Desbloqueo-Parental") desbloqueo: String? = null,
    ): FichaPelicula

    @GET("conexiones/{conexionId}/series")
    suspend fun listarSeries(
        @Path("conexionId") conexionId: String,
        @Query("categoria_id") categoriaId: String?,
        @Query("limite") limite: Int? = null,
        @Header("X-Desbloqueo-Parental") desbloqueo: String? = null,
    ): List<Serie>

    @GET("conexiones/{conexionId}/series/{serieId}")
    suspend fun fichaSerie(
        @Path("conexionId") conexionId: String,
        @Path("serieId") serieId: String,
        @Header("X-Desbloqueo-Parental") desbloqueo: String? = null,
    ): FichaSerie

    @GET("conexiones/{conexionId}/destacados")
    suspend fun destacados(@Path("conexionId") conexionId: String, @Header("X-Desbloqueo-Parental") desbloqueo: String? = null): Destacados

    @GET("conexiones/{conexionId}/peliculas/{peliculaId}/elenco")
    suspend fun elencoPelicula(@Path("conexionId") conexionId: String, @Path("peliculaId") peliculaId: String): Elenco

    @GET("conexiones/{conexionId}/series/{serieId}/elenco")
    suspend fun elencoSerie(@Path("conexionId") conexionId: String, @Path("serieId") serieId: String): Elenco

    @GET("conexiones/{conexionId}/buscar")
    suspend fun buscar(
        @Path("conexionId") conexionId: String,
        @Query("q") texto: String,
        @Header("X-Desbloqueo-Parental") desbloqueo: String? = null,
    ): RespuestaBusqueda

    @GET("conexiones/{conexionId}/reproducir/en-vivo/{canalId}")
    suspend fun urlEnVivo(
        @Path("conexionId") conexionId: String,
        @Path("canalId") canalId: String,
        @Query("formato") formato: String? = null,
        @Header("X-Desbloqueo-Parental") desbloqueo: String? = null,
    ): RespuestaReproduccion

    @GET("conexiones/{conexionId}/reproducir/peliculas/{peliculaId}")
    suspend fun urlPelicula(
        @Path("conexionId") conexionId: String,
        @Path("peliculaId") peliculaId: String,
        @Header("X-Desbloqueo-Parental") desbloqueo: String? = null,
    ): RespuestaReproduccion

    @GET("conexiones/{conexionId}/reproducir/series/{serieId}/episodios/{episodioId}")
    suspend fun urlEpisodio(
        @Path("conexionId") conexionId: String,
        @Path("serieId") serieId: String,
        @Path("episodioId") episodioId: String,
        @Header("X-Desbloqueo-Parental") desbloqueo: String? = null,
    ): RespuestaReproduccion

    @POST("conexiones/{conexionId}/actualizar")
    suspend fun actualizarCatalogo(@Path("conexionId") conexionId: String): Response<Unit>

    @GET("control-parental")
    suspend fun estadoControlParental(): EstadoControlParental

    @PUT("control-parental/pin")
    suspend fun guardarPinParental(@Body datos: SolicitudPin): EstadoControlParental

    @POST("control-parental/pin/quitar")
    suspend fun quitarPinParental(@Body datos: SolicitudQuitarPin): EstadoControlParental

    @POST("control-parental/pin/verificar")
    suspend fun verificarPinParental(@Body datos: SolicitudVerificarPin): RespuestaVerificarPin

    @GET("control-parental/categorias")
    suspend fun categoriasControlParental(@Query("conexion_id") conexionId: String): List<CategoriaControl>

    @PUT("control-parental/categorias")
    suspend fun bloquearCategoria(@Body datos: SolicitudBloqueoCategoria): RespuestaBloqueoCategoria

    @POST("conexiones/{conexionId}/eventos-reproduccion")
    suspend fun registrarEventoReproduccion(
        @Path("conexionId") conexionId: String,
        @Body datos: SolicitudEventoReproduccion,
    ): Response<Unit>

    @POST("perfiles/{perfilId}/historial")
    suspend fun registrarReproduccion(@Path("perfilId") perfilId: String, @Body datos: SolicitudHistorial): RespuestaHistorial

    @PUT("perfiles/{perfilId}/progreso")
    suspend fun guardarProgreso(@Path("perfilId") perfilId: String, @Body datos: SolicitudProgreso): RespuestaProgreso

    @GET("perfiles/{perfilId}/progreso/{conexionId}/{tipo}/{streamId}")
    suspend fun verProgreso(
        @Path("perfilId") perfilId: String,
        @Path("conexionId") conexionId: String,
        @Path("tipo") tipo: String,
        @Path("streamId") streamId: String,
    ): RespuestaProgreso

    @GET("perfiles/{perfilId}/favoritos")
    suspend fun listarFavoritos(
        @Path("perfilId") perfilId: String,
        @Query("conexion_id") conexionId: String?,
        @Query("tipo_contenido") tipo: String? = null,
    ): List<RespuestaFavorito>

    @PUT("perfiles/{perfilId}/favoritos")
    suspend fun marcarFavorito(@Path("perfilId") perfilId: String, @Body datos: SolicitudFavorito): RespuestaFavorito

    @DELETE("perfiles/{perfilId}/favoritos/{conexionId}/{tipo}/{streamId}")
    suspend fun quitarFavorito(
        @Path("perfilId") perfilId: String,
        @Path("conexionId") conexionId: String,
        @Path("tipo") tipo: String,
        @Path("streamId") streamId: String,
    ): Response<Unit>

    @GET("perfiles/{perfilId}/progreso")
    suspend fun listarContinuarViendo(@Path("perfilId") perfilId: String, @Query("conexion_id") conexionId: String?): List<RespuestaProgreso>

    @DELETE("perfiles/{perfilId}/progreso/{conexionId}/{tipo}/{streamId}")
    suspend fun borrarProgreso(
        @Path("perfilId") perfilId: String,
        @Path("conexionId") conexionId: String,
        @Path("tipo") tipo: String,
        @Path("streamId") streamId: String,
    ): Response<Unit>

    @GET("perfiles/{perfilId}/historial")
    suspend fun listarHistorial(
        @Path("perfilId") perfilId: String,
        @Query("conexion_id") conexionId: String?,
        @Query("limite") limite: Int = 100,
    ): List<RespuestaHistorial>

    @DELETE("perfiles/{perfilId}/historial/{entradaId}")
    suspend fun borrarEntradaHistorial(@Path("perfilId") perfilId: String, @Path("entradaId") entradaId: Long): Response<Unit>

    @DELETE("perfiles/{perfilId}/historial")
    suspend fun vaciarHistorial(@Path("perfilId") perfilId: String): Response<Unit>

    @GET("perfiles/{perfilId}/resenas/{conexionId}/{tipo}/{streamId}")
    suspend fun verResenas(
        @Path("perfilId") perfilId: String,
        @Path("conexionId") conexionId: String,
        @Path("tipo") tipo: String,
        @Path("streamId") streamId: String,
    ): ResumenResenas

    @PUT("perfiles/{perfilId}/resenas")
    suspend fun guardarResena(@Path("perfilId") perfilId: String, @Body datos: SolicitudResena): RespuestaResena

    @DELETE("perfiles/{perfilId}/resenas/{conexionId}/{tipo}/{streamId}")
    suspend fun borrarResena(
        @Path("perfilId") perfilId: String,
        @Path("conexionId") conexionId: String,
        @Path("tipo") tipo: String,
        @Path("streamId") streamId: String,
    ): Response<Unit>

    @GET("musica")
    suspend fun resumenMusica(): ResumenMusica

    @GET("musica/albumes")
    suspend fun listarAlbumes(): List<Album>

    @GET("musica/albumes/{albumId}")
    suspend fun verAlbum(@Path("albumId") albumId: String): AlbumConCanciones

    @GET("musica/canciones")
    suspend fun listarCanciones(@Query("buscar") buscar: String? = null, @Query("album_id") albumId: String? = null): List<Cancion>
}
