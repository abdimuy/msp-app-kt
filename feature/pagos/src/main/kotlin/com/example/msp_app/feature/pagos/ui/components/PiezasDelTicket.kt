package com.example.msp_app.feature.pagos.ui.components

/**
 * Las etiquetas de prueba del ticket de pago.
 *
 * **Las piezas ya no viven aquí.** `MspTicketBanner`, `MspTicketSummary`,
 * `MspTicketFacsimile`, `MspPrinterRow` y `MspTicketTopBar` están en
 * `:core:designsystem` (`component/TicketPieces.kt`): el ticket de pago y el de
 * visita son la misma pantalla con otro papel adentro, y tenerlas dos veces
 * significaba que un arreglo —como el del tamaño fijo del facsímil, que existe
 * porque fue un bug— tendría que aterrizar dos veces.
 *
 * Lo que sí es de este feature son sus `testTag`, porque cada pantalla los suyos:
 * los componentes compartidos no ponen ninguno y los cuelgan del `Modifier` que
 * reciben.
 */

/** `testTag` de la banda de estado del ticket (fuera del día / reimpresión / impreso). */
const val BANDA_DEL_TICKET_TAG: String = "pagos_ticket_banda"

/** `testTag` de la vista previa monoespaciada. */
const val VISTA_PREVIA_TAG: String = "pagos_ticket_previa"

/** `testTag` del resumen legible que acompaña al facsímil. */
const val RESUMEN_DEL_TICKET_TAG: String = "pagos_ticket_resumen"

/** `testTag` del CTA de imprimir. */
const val IMPRIMIR_TAG: String = "pagos_ticket_imprimir"

/** `testTag` del botón de cambiar impresora. */
const val CAMBIAR_IMPRESORA_TAG: String = "pagos_ticket_cambiar"

/** `testTag` del botón que cierra el picker o el aviso de fallo. */
const val CERRAR_IMPRESION_TAG: String = "pagos_ticket_cerrar"

/** Prefijo del `testTag` de cada renglón del picker: `pagos_ticket_impresora_<MAC>`. */
const val IMPRESORA_TAG: String = "pagos_ticket_impresora_"
