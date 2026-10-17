package com.flowcast.app.data.repository

import com.flowcast.app.data.network.codigoBloqueoParental
import com.flowcast.app.data.network.mensajeDeError

enum class BloqueoParental { REQUIERE_PIN, SIN_PIN }

fun bloqueoParentalDe(error: Throwable): BloqueoParental? = when (codigoBloqueoParental(error)) {
    "pin_requerido" -> BloqueoParental.REQUIERE_PIN
    "pin_no_configurado" -> BloqueoParental.SIN_PIN
    else -> null
}

data class EstadoPin(val bloqueo: BloqueoParental? = null, val verificando: Boolean = false, val error: String? = null)

suspend fun RepositorioControlParental.verificarPin(pin: String, actualizar: (EstadoPin) -> Unit, alDesbloquear: () -> Unit) {
    actualizar(EstadoPin(bloqueo = BloqueoParental.REQUIERE_PIN, verificando = true))
    verificar(pin)
        .onSuccess { valido ->
            if (valido) {
                actualizar(EstadoPin())
                alDesbloquear()
            } else {
                actualizar(EstadoPin(bloqueo = BloqueoParental.REQUIERE_PIN, error = "PIN incorrecto"))
            }
        }
        .onFailure { fallo -> actualizar(EstadoPin(bloqueo = BloqueoParental.REQUIERE_PIN, error = mensajeDeError(fallo))) }
}
