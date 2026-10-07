package com.example.msp_app.core.navegacion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * El estado de UNA apertura del menú: las secciones que el usuario tocó. El
 * contenedor llama [reiniciar] al cerrar, así la próxima apertura llega limpia
 * (Review Focus 3 del plan). El buscador del menú se retiró el 2026-10-07
 * (decisión del dueño) y con él la consulta que vivía aquí.
 */
@Stable
class AperturaDelMenu {
    var tocadas: Set<String> by mutableStateOf(emptySet())
        private set

    fun tocar(seccionId: String) {
        tocadas = tocadas + seccionId
    }

    fun reiniciar() {
        tocadas = emptySet()
    }
}

@Composable
fun rememberAperturaDelMenu(): AperturaDelMenu = remember { AperturaDelMenu() }
