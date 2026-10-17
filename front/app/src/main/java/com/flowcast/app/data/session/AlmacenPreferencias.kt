package com.flowcast.app.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.almacenPreferencias: DataStore<Preferences> by preferencesDataStore(name = "preferencias")

class AlmacenPreferencias(private val contexto: Context) {
    private object Claves {
        val FORMATO_EN_VIVO = stringPreferencesKey("formato_en_vivo")
        val ORIENTACION = stringPreferencesKey("orientacion_reproductor")
        val PESTANA_INICIAL = stringPreferencesKey("pestana_inicial")
        val AUTO_SIGUIENTE = booleanPreferencesKey("auto_siguiente_episodio")
    }

    val formatoEnVivo: Flow<String> = contexto.almacenPreferencias.data.map { it[Claves.FORMATO_EN_VIVO] ?: FORMATO_AUTO }
    val orientacionReproductor: Flow<String> = contexto.almacenPreferencias.data.map { it[Claves.ORIENTACION] ?: ORIENTACION_AUTO }
    val pestanaInicial: Flow<String> = contexto.almacenPreferencias.data.map { it[Claves.PESTANA_INICIAL] ?: "PELICULAS" }
    val autoSiguienteEpisodio: Flow<Boolean> = contexto.almacenPreferencias.data.map { it[Claves.AUTO_SIGUIENTE] ?: true }

    suspend fun guardarFormatoEnVivo(valor: String) = contexto.almacenPreferencias.edit { it[Claves.FORMATO_EN_VIVO] = valor }
    suspend fun guardarOrientacion(valor: String) = contexto.almacenPreferencias.edit { it[Claves.ORIENTACION] = valor }
    suspend fun guardarPestanaInicial(valor: String) = contexto.almacenPreferencias.edit { it[Claves.PESTANA_INICIAL] = valor }
    suspend fun guardarAutoSiguiente(valor: Boolean) = contexto.almacenPreferencias.edit { it[Claves.AUTO_SIGUIENTE] = valor }

    companion object {
        const val FORMATO_AUTO = "auto"
        const val ORIENTACION_AUTO = "auto"
        const val ORIENTACION_HORIZONTAL = "horizontal"
        const val ORIENTACION_VERTICAL = "vertical"
    }
}
