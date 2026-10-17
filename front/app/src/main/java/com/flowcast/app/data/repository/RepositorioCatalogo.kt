package com.flowcast.app.data.repository

import com.flowcast.app.data.network.ApiFlowCast
import com.flowcast.app.data.network.Canal
import com.flowcast.app.data.network.Categoria
import com.flowcast.app.data.network.Destacados
import com.flowcast.app.data.network.Elenco
import com.flowcast.app.data.network.FichaPelicula
import com.flowcast.app.data.network.FichaSerie
import com.flowcast.app.data.network.Pelicula
import com.flowcast.app.data.network.ProgramaEpg
import com.flowcast.app.data.network.RespuestaBusqueda
import com.flowcast.app.data.network.RespuestaReproduccion
import com.flowcast.app.data.network.Serie
import com.flowcast.app.data.session.AlmacenPreferencias
import com.flowcast.app.data.session.AlmacenSesion
import kotlinx.coroutines.flow.first

class RepositorioCatalogo(
    private val api: ApiFlowCast,
    private val almacen: AlmacenSesion,
    private val controlParental: RepositorioControlParental,
    private val preferencias: AlmacenPreferencias,
) {

    private suspend fun conexion(): String =
        almacen.conexionActivaId.first() ?: throw IllegalStateException("No hay ningún proveedor configurado")

    suspend fun categorias(tipo: String, conTotales: Boolean = false): Result<List<Categoria>> =
        runCatching { api.listarCategorias(conexion(), tipo, conTotales) }
    private fun desbloqueo(): String? = controlParental.tokenVigente()

    suspend fun canales(categoriaId: String?): Result<List<Canal>> = runCatching { api.listarCanales(conexion(), categoriaId, desbloqueo()) }
    suspend fun epg(canalId: String): Result<List<ProgramaEpg>> = runCatching { api.epgCanal(conexion(), canalId) }
    suspend fun peliculas(categoriaId: String?, limite: Int? = null): Result<List<Pelicula>> =
        runCatching { api.listarPeliculas(conexion(), categoriaId, limite, desbloqueo()) }
    suspend fun fichaPelicula(id: String): Result<FichaPelicula> = runCatching { api.fichaPelicula(conexion(), id, desbloqueo()) }
    suspend fun series(categoriaId: String?, limite: Int? = null): Result<List<Serie>> =
        runCatching { api.listarSeries(conexion(), categoriaId, limite, desbloqueo()) }
    suspend fun fichaSerie(id: String): Result<FichaSerie> = runCatching { api.fichaSerie(conexion(), id, desbloqueo()) }
    suspend fun buscar(texto: String): Result<RespuestaBusqueda> = runCatching { api.buscar(conexion(), texto, desbloqueo()) }
    suspend fun destacados(): Result<Destacados> = runCatching { api.destacados(conexion(), desbloqueo()) }
    suspend fun elencoPelicula(id: String): Result<Elenco> = runCatching { api.elencoPelicula(conexion(), id) }
    suspend fun elencoSerie(id: String): Result<Elenco> = runCatching { api.elencoSerie(conexion(), id) }

    suspend fun urlEnVivo(canalId: String, formato: String? = null): Result<RespuestaReproduccion> = runCatching {
        val elegido = formato ?: preferencias.formatoEnVivo.first().takeIf { it != AlmacenPreferencias.FORMATO_AUTO }
        api.urlEnVivo(conexion(), canalId, elegido, controlParental.tokenVigente())
    }
    suspend fun urlPelicula(id: String): Result<RespuestaReproduccion> = runCatching { api.urlPelicula(conexion(), id, controlParental.tokenVigente()) }
    suspend fun urlEpisodio(serieId: String, episodioId: String): Result<RespuestaReproduccion> =
        runCatching { api.urlEpisodio(conexion(), serieId, episodioId, controlParental.tokenVigente()) }
    suspend fun actualizarCatalogo(): Result<Unit> = runCatching { api.actualizarCatalogo(conexion()); Unit }
}
