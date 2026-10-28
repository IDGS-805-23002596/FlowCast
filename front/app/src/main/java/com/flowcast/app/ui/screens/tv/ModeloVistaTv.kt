package com.flowcast.app.ui.screens.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.flowcast.app.data.network.Canal
import com.flowcast.app.data.network.Categoria
import com.flowcast.app.data.network.ProgramaEpg
import com.flowcast.app.data.network.mensajeDeError
import com.flowcast.app.data.repository.RepositorioBiblioteca
import com.flowcast.app.data.repository.RepositorioCatalogo
import com.flowcast.app.data.repository.EstadoPin
import com.flowcast.app.data.repository.RepositorioConexiones
import com.flowcast.app.data.repository.RepositorioControlParental
import com.flowcast.app.data.repository.bloqueoParentalDe
import com.flowcast.app.data.repository.verificarPin
import com.flowcast.app.ui.screens.categoria.CATEGORIA_TODAS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoriaTv(val categoria: Categoria, val totalCanales: Int)

data class EstadoTv(
    val cargando: Boolean = true,
    val error: String? = null,
    val busqueda: String = "",
    val categorias: List<CategoriaTv> = emptyList(),
    val totalCanales: Int = 0,
    val favoritos: Set<String> = emptySet(),
    val pin: EstadoPin = EstadoPin(),
    val abrir: CategoriaTv? = null,
    val aviso: String? = null,
) {
    val categoriasVisibles: List<CategoriaTv>
        get() = if (busqueda.isBlank()) categorias else categorias.filter { it.categoria.nombre.contains(busqueda, ignoreCase = true) }
}

class ModeloVistaTv(
    private val catalogo: RepositorioCatalogo,
    conexiones: RepositorioConexiones,
    private val biblioteca: RepositorioBiblioteca,
    private val controlParental: RepositorioControlParental,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoTv())
    val estado: StateFlow<EstadoTv> = _estado.asStateFlow()

    private var canalesPorCategoria: Map<String?, List<Canal>> = emptyMap()
    private var canalesDesbloqueados: Map<String, List<Canal>> = emptyMap()
    private var categoriaPendiente: CategoriaTv? = null

    init {
        viewModelScope.launch { conexiones.conexionActivaId.distinctUntilChanged().collect { cargar() } }
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val categorias = catalogo.categorias("en_vivo", conTotales = true).getOrElse { fallar(it); return@launch }
            val canales = catalogo.canales(categoriaId = null).getOrElse { fallar(it); return@launch }
            canalesPorCategoria = canales.groupBy { it.categoriaId }
            canalesDesbloqueados = emptyMap()
            val favoritos = biblioteca.favoritos("en_vivo").getOrDefault(emptyList()).map { f -> f.streamId }.toSet()
            _estado.update {
                it.copy(
                    cargando = false,
                    totalCanales = canales.size,
                    favoritos = favoritos,
                    categorias = categorias.map { c -> CategoriaTv(c, canalesPorCategoria[c.id]?.size ?: (if (c.bloqueada) c.total ?: 0 else 0)) },
                )
            }
        }
    }

    fun buscar(texto: String) = _estado.update { it.copy(busqueda = texto) }

    fun canalesDe(categoriaId: String): List<Canal> = canalesDesbloqueados[categoriaId]
        ?: if (categoriaId == CATEGORIA_TODAS) canalesPorCategoria.values.flatten() else canalesPorCategoria[categoriaId].orEmpty()

    fun solicitarAbrir(categoria: CategoriaTv) {
        val id = categoria.categoria.id
        val yaDisponible = !categoria.categoria.bloqueada || id == CATEGORIA_TODAS || canalesPorCategoria.containsKey(id) || canalesDesbloqueados.containsKey(id)
        if (yaDisponible) {
            _estado.update { it.copy(abrir = categoria) }
            return
        }
        categoriaPendiente = categoria
        viewModelScope.launch {
            catalogo.canales(id)
                .onSuccess { lista ->
                    canalesDesbloqueados = canalesDesbloqueados + (id to lista)
                    _estado.update { it.copy(abrir = categoria) }
                }
                .onFailure { fallo ->
                    val bloqueo = bloqueoParentalDe(fallo)
                    if (bloqueo != null) _estado.update { it.copy(pin = EstadoPin(bloqueo)) } else _estado.update { it.copy(aviso = mensajeDeError(fallo)) }
                }
        }
    }

    fun desbloquear(pin: String) {
        viewModelScope.launch { controlParental.verificarPin(pin, { nuevo -> _estado.update { it.copy(pin = nuevo) } }) { categoriaPendiente?.let(::solicitarAbrir) } }
    }

    fun cancelarDesbloqueo() = _estado.update { it.copy(pin = EstadoPin()) }
    fun abrirConsumido() = _estado.update { it.copy(abrir = null) }
    fun limpiarAviso() = _estado.update { it.copy(aviso = null) }

    fun canalesFavoritos(): List<Canal> = canalesPorCategoria.values.flatten().filter { it.id in _estado.value.favoritos }

    fun alternarFavorito(canal: Canal) {
        val eraFavorito = canal.id in _estado.value.favoritos
        _estado.update { it.copy(favoritos = if (eraFavorito) it.favoritos - canal.id else it.favoritos + canal.id) }
        viewModelScope.launch {
            if (eraFavorito) biblioteca.quitarFavorito("en_vivo", canal.id) else biblioteca.marcarFavorito("en_vivo", canal.id, canal.nombre, canal.icono)
        }
    }

    suspend fun epgDe(canalId: String): List<ProgramaEpg> = catalogo.epg(canalId).getOrDefault(emptyList())

    private fun fallar(error: Throwable) = _estado.update { it.copy(cargando = false, error = mensajeDeError(error)) }

    companion object {
        fun fabrica(catalogo: RepositorioCatalogo, conexiones: RepositorioConexiones, biblioteca: RepositorioBiblioteca, controlParental: RepositorioControlParental) =
            viewModelFactory { initializer { ModeloVistaTv(catalogo, conexiones, biblioteca, controlParental) } }
    }
}
