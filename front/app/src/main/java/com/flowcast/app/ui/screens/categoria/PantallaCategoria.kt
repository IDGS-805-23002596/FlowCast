package com.flowcast.app.ui.screens.categoria

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.flowcast.app.data.network.mensajeDeError
import com.flowcast.app.data.repository.EstadoPin
import com.flowcast.app.data.repository.RepositorioCatalogo
import com.flowcast.app.data.repository.RepositorioControlParental
import com.flowcast.app.data.repository.bloqueoParentalDe
import com.flowcast.app.data.repository.verificarPin
import com.flowcast.app.ui.components.BotonLima
import com.flowcast.app.ui.components.DialogosParentales
import com.flowcast.app.ui.components.ElementoCatalogo
import com.flowcast.app.ui.components.EstadoVacio
import com.flowcast.app.ui.components.MensajeError
import com.flowcast.app.ui.components.TarjetaContenido
import com.flowcast.app.ui.screens.inicio.TipoCatalogo
import com.flowcast.app.ui.screens.tv.CampoBusqueda
import com.flowcast.app.ui.theme.Colores
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val CATEGORIA_TODAS = "todas"

data class EstadoCategoria(
    val cargando: Boolean = true,
    val error: String? = null,
    val elementos: List<ElementoCatalogo> = emptyList(),
    val busqueda: String = "",
    val pin: EstadoPin = EstadoPin(),
) {
    val visibles: List<ElementoCatalogo>
        get() = if (busqueda.isBlank()) elementos else elementos.filter { it.titulo.contains(busqueda, ignoreCase = true) }
}

class ModeloVistaCategoria(
    private val tipo: TipoCatalogo,
    private val categoriaId: String,
    private val nombreCategoria: String,
    private val catalogo: RepositorioCatalogo,
    private val controlParental: RepositorioControlParental,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoCategoria())
    val estado: StateFlow<EstadoCategoria> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null, pin = EstadoPin()) }
        viewModelScope.launch {
            val id = categoriaId.takeIf { it != CATEGORIA_TODAS }
            val resultado = when (tipo) {
                TipoCatalogo.PELICULAS -> catalogo.peliculas(id).map { lista ->
                    lista.map { p -> ElementoCatalogo(p.id, p.nombre, p.portada, nombreCategoria, p.agregadoEn?.take(4), p.calificacion, esSerie = false, bloqueada = p.bloqueada) }
                }
                TipoCatalogo.SERIES -> catalogo.series(id).map { lista ->
                    lista.map { s -> ElementoCatalogo(s.id, s.nombre, s.portada, s.genero ?: nombreCategoria, s.fechaEstreno?.take(4), s.calificacion, esSerie = true, bloqueada = s.bloqueada) }
                }
            }
            resultado
                .onSuccess { lista -> _estado.update { it.copy(cargando = false, elementos = lista.distinctBy { e -> e.id }) } }
                .onFailure { fallo ->
                    val bloqueo = bloqueoParentalDe(fallo)
                    _estado.update { it.copy(cargando = false, error = if (bloqueo == null) mensajeDeError(fallo) else null, pin = EstadoPin(bloqueo)) }
                }
        }
    }

    fun buscar(texto: String) = _estado.update { it.copy(busqueda = texto) }

    fun desbloquear(pin: String) {
        viewModelScope.launch { controlParental.verificarPin(pin, { nuevo -> _estado.update { it.copy(pin = nuevo) } }, ::cargar) }
    }

    fun cancelarDesbloqueo() = _estado.update { it.copy(pin = EstadoPin()) }

    companion object {
        fun fabrica(tipo: TipoCatalogo, categoriaId: String, nombre: String, catalogo: RepositorioCatalogo, controlParental: RepositorioControlParental) =
            viewModelFactory { initializer { ModeloVistaCategoria(tipo, categoriaId, nombre, catalogo, controlParental) } }
    }
}

@Composable
fun PantallaCategoria(
    modeloVista: ModeloVistaCategoria,
    titulo: String,
    alVolver: () -> Unit,
    alAbrir: (ElementoCatalogo) -> Unit,
    alIrConfiguracion: () -> Unit,
) {
    val estado by modeloVista.estado.collectAsState()
    Column(Modifier.fillMaxSize().background(Colores.Fondo).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 24.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver", tint = Colores.Texto) }
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleLarge, color = Colores.Texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!estado.cargando) Text("${estado.elementos.size} títulos", style = MaterialTheme.typography.labelSmall, color = Colores.Texto2)
            }
        }
        Spacer(Modifier.height(8.dp))
        CampoBusqueda(estado.busqueda, modeloVista::buscar, "Buscar en esta categoría", Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        when {
            estado.cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Colores.Lima) }
            estado.error != null -> Column(Modifier.padding(40.dp)) {
                MensajeError(estado.error ?: "")
                Spacer(Modifier.height(16.dp))
                BotonLima("Reintentar", onClick = modeloVista::cargar)
            }
            estado.pin.bloqueo != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EstadoVacio(Icons.Outlined.Lock, "Categoría bloqueada")
            }
            estado.visibles.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EstadoVacio(Icons.Outlined.SearchOff, "No hay títulos que mostrar")
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(estado.visibles, key = { it.id }) { elemento -> TarjetaContenido(elemento, onClick = { alAbrir(elemento) }) }
            }
        }
    }
    DialogosParentales(
        pin = estado.pin,
        alCancelar = {
            modeloVista.cancelarDesbloqueo()
            alVolver()
        },
        alConfirmar = modeloVista::desbloquear,
        alIrConfiguracion = alIrConfiguracion,
        descripcion = "Esta categoría está bloqueada. Escribe tu PIN de 4 dígitos para ver su contenido.",
    )
}
