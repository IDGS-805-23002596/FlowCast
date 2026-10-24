package com.flowcast.app.ui.screens.perfiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.flowcast.app.data.network.RespuestaPerfil
import com.flowcast.app.data.network.mensajeDeError
import com.flowcast.app.data.repository.RepositorioPerfiles
import com.flowcast.app.data.session.PerfilGuardado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoPerfiles(
    val perfiles: List<RespuestaPerfil> = emptyList(),
    val perfilActivo: PerfilGuardado? = null,
    val cargando: Boolean = true,
    val error: String? = null,
    val dialogoAbierto: Boolean = false,
    val nuevoNombre: String = "",
    val creando: Boolean = false,
    val seleccionado: Boolean = false,
    val editando: Boolean = false,
)

class ModeloVistaPerfiles(private val repositorio: RepositorioPerfiles) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoPerfiles())
    val estado: StateFlow<EstadoPerfiles> = _estado.asStateFlow()

    init {
        viewModelScope.launch { repositorio.perfilActivo.collect { activo -> _estado.update { it.copy(perfilActivo = activo) } } }
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            repositorio.listar()
                .onSuccess { lista -> _estado.update { it.copy(perfiles = lista, cargando = false) } }
                .onFailure { fallo -> _estado.update { it.copy(cargando = false, error = mensajeDeError(fallo)) } }
        }
    }

    fun seleccionar(perfil: RespuestaPerfil) {
        viewModelScope.launch {
            repositorio.seleccionar(perfil)
            _estado.update { it.copy(seleccionado = true) }
        }
    }

    fun alternarEdicion() = _estado.update { it.copy(editando = !it.editando) }

    fun abrirDialogo() = _estado.update { it.copy(dialogoAbierto = true, nuevoNombre = "", error = null) }
    fun cerrarDialogo() = _estado.update { it.copy(dialogoAbierto = false) }
    fun cambiarNuevoNombre(valor: String) = _estado.update { it.copy(nuevoNombre = valor) }

    fun crear() {
        val nombre = _estado.value.nuevoNombre.trim()
        if (nombre.length < 2) return
        _estado.update { it.copy(creando = true) }
        viewModelScope.launch {
            repositorio.crear(nombre)
                .onSuccess { nuevo -> _estado.update { it.copy(creando = false, dialogoAbierto = false, perfiles = it.perfiles + nuevo) } }
                .onFailure { fallo -> _estado.update { it.copy(creando = false, error = mensajeDeError(fallo)) } }
        }
    }

    companion object {
        fun fabrica(repositorio: RepositorioPerfiles) = viewModelFactory { initializer { ModeloVistaPerfiles(repositorio) } }
    }
}
