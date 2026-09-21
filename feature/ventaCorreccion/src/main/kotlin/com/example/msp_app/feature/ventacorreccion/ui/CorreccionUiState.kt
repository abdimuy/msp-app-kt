package com.example.msp_app.feature.ventacorreccion.ui

import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import com.example.msp_app.feature.ventacorreccion.domain.CamposVentaCorregidos

/**
 * Estado observable de la pantalla de corrección. La UI (Task 5) es una cáscara sobre esto: no
 * decide corregibilidad ni maneja el candado, sólo refleja el estado que devuelven los casos de
 * uso.
 */
sealed interface CorreccionUiState {
    /** Sin reclamo todavía — la pantalla acaba de entrar, aún no corre `reclamar()`. */
    data object Inicial : CorreccionUiState

    /** El candado se tomó; el formulario muestra estos campos/líneas. */
    data class Editando(
        val claimId: String,
        val campos: CamposVentaCorregidos,
        val productos: List<LocalSaleProductEntity>,
        val combos: List<LocalSaleComboEntity>
    ) : CorreccionUiState

    /**
     * No se pudo reclamar, o el guardado se rechazó. [mensaje] es una de las cadenas de
     * [com.example.msp_app.feature.ventacorreccion.domain.TextosCorreccion].
     */
    data class NoCorregible(val mensaje: String) : CorreccionUiState

    /** Guardado exitoso — la UI muestra `Corrección guardada` (Task 5) y sale de la pantalla. */
    data object Guardada : CorreccionUiState
}
