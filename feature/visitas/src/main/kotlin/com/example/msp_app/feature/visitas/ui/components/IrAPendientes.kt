package com.example.msp_app.feature.visitas.ui.components

import androidx.compose.foundation.lazy.LazyListState

/**
 * Lleva Inicio hasta la pantalla de pendientes, que es el pie de la lista
 * (`HomeFooterSection`: "VISITAS SIN ENVIAR" y "PAGOS SIN ENVIAR"). La usa el
 * aviso "N sin enviar · no cambies de teléfono" (E-APP-096). El pie es siempre
 * el ÚLTIMO elemento, así que se pide ese y no un índice fijo que se rompa al
 * agregar una sección.
 *
 * Vive junto al aviso, en :feature:visitas, para que su prueba corra aislada y
 * no en el mismo proceso que las pruebas de navegación de :app (E-APP-111).
 */
suspend fun LazyListState.irAPendientes() {
    val ultimo = layoutInfo.totalItemsCount - 1
    if (ultimo >= 0) animateScrollToItem(ultimo)
}
