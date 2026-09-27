package com.example.msp_app.features.home.components.homenearbyclientssection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.msp_app.core.utils.Coord
import com.example.msp_app.core.utils.FuenteDePosicion

/**
 * **La posición del cobrador, al día, mientras la pantalla está a la vista.**
 *
 * Recolecta [fuente] con **cada** emisión, no sólo la primera. Del 2026-09-22 al
 * 2026-09-26 la pantalla principal leía una sola vez y la lista de cercanos se
 * quedaba con esa primera lectura aunque el cobrador avanzara; el dueño decidió
 * el 2026-09-26 que la lista lo siga. Lo guarda `PosicionEnVivoTest`.
 *
 * Sólo escucha con la pantalla en `STARTED`: con la app en segundo plano la
 * petición al proveedor se quita (el `awaitClose` de la fuente) y vuelve a
 * pedirse al regresar. Sin [permitido] no escucha y devuelve la última posición
 * conocida, o `null` si nunca hubo.
 */
@Composable
fun posicionEnVivo(fuente: FuenteDePosicion, permitido: Boolean): Coord? {
    var posicion by remember { mutableStateOf<Coord?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(fuente, permitido, lifecycleOwner) {
        if (!permitido) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            fuente.updates().collect { posicion = it }
        }
    }
    return posicion
}
