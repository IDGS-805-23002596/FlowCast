package com.flowcast.app.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flowcast.app.data.network.RespuestaPerfil
import com.flowcast.app.data.network.RespuestaTokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.almacenDatos: DataStore<Preferences> by preferencesDataStore(name = "sesion")

data class PerfilGuardado(val id: String, val nombre: String, val avatar: String, val urlImagen: String? = null) {
    val inicial: String get() = nombre.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
}

class AlmacenSesion(private val contexto: Context) {
    private object Claves {
        val TOKEN_ACCESO = stringPreferencesKey("token_acceso")
        val TOKEN_REFRESCO = stringPreferencesKey("token_refresco")
        val NOMBRE = stringPreferencesKey("nombre")
        val CORREO = stringPreferencesKey("correo")
        val PERFIL_ID = stringPreferencesKey("perfil_id")
        val PERFIL_NOMBRE = stringPreferencesKey("perfil_nombre")
        val PERFIL_AVATAR = stringPreferencesKey("perfil_avatar")
        val PERFIL_IMAGEN = stringPreferencesKey("perfil_imagen")
        val CONEXION_ID = stringPreferencesKey("conexion_id")
    }

    val tokenAcceso: Flow<String?> = contexto.almacenDatos.data.map { it[Claves.TOKEN_ACCESO] }
    val tokenRefresco: Flow<String?> = contexto.almacenDatos.data.map { it[Claves.TOKEN_REFRESCO] }
    val nombre: Flow<String?> = contexto.almacenDatos.data.map { it[Claves.NOMBRE] }
    val conexionActivaId: Flow<String?> = contexto.almacenDatos.data.map { it[Claves.CONEXION_ID] }
    val perfilActivo: Flow<PerfilGuardado?> = contexto.almacenDatos.data.map { prefs ->
        val id = prefs[Claves.PERFIL_ID]
        val nombre = prefs[Claves.PERFIL_NOMBRE]
        if (id == null || nombre == null) null else PerfilGuardado(id, nombre, prefs[Claves.PERFIL_AVATAR] ?: "01", prefs[Claves.PERFIL_IMAGEN])
    }

    suspend fun guardarSesion(tokens: RespuestaTokens) {
        contexto.almacenDatos.edit { preferencias ->
            preferencias[Claves.TOKEN_ACCESO] = tokens.tokenAcceso
            preferencias[Claves.TOKEN_REFRESCO] = tokens.tokenRefresco
            preferencias[Claves.NOMBRE] = tokens.usuario.nombre
            preferencias[Claves.CORREO] = tokens.usuario.correo
        }
    }

    suspend fun guardarPerfil(perfil: RespuestaPerfil) {
        contexto.almacenDatos.edit { preferencias ->
            preferencias[Claves.PERFIL_ID] = perfil.id
            preferencias[Claves.PERFIL_NOMBRE] = perfil.nombre
            preferencias[Claves.PERFIL_AVATAR] = perfil.avatar
            val imagen = perfil.urlImagen
            if (imagen == null) preferencias.remove(Claves.PERFIL_IMAGEN) else preferencias[Claves.PERFIL_IMAGEN] = imagen
        }
    }

    suspend fun limpiarPerfil() {
        contexto.almacenDatos.edit { preferencias ->
            preferencias.remove(Claves.PERFIL_ID)
            preferencias.remove(Claves.PERFIL_NOMBRE)
            preferencias.remove(Claves.PERFIL_AVATAR)
            preferencias.remove(Claves.PERFIL_IMAGEN)
        }
    }

    suspend fun guardarConexionActiva(conexionId: String) {
        contexto.almacenDatos.edit { it[Claves.CONEXION_ID] = conexionId }
    }

    suspend fun limpiar() {
        contexto.almacenDatos.edit { it.clear() }
    }
}
