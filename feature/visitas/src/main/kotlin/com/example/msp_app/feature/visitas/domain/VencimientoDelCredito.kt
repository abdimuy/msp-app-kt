package com.example.msp_app.feature.visitas.domain

import java.time.LocalDate

/**
 * **Cuándo vence el crédito de una cuenta**, para la línea "SU FECHA DE
 * VENCIMIENTO DE SU CREDITO ES EL DIA: ..." del ticket de visita.
 *
 * ## Por qué esto no es un "+1 año" mágico
 *
 * El ticket viejo (`app/.../VisitTicketScreen.kt`) imprimía esa línea **para
 * todas las ventas**, calculada como *fecha de la venta + 1 año*, y sin nada
 * que la respaldara: Microsip **no guarda el plazo del crédito**, así que la
 * cifra era una suposición aplicada a clientes cuyo crédito podía correr a
 * otro plazo. Por eso se retiró al migrar el papel.
 *
 * Vuelve **acotada por una regla del dueño**: en las ventas cuyo corto plazo es
 * de **cuatro meses** el crédito sí corre a un año, y ahí la fecha es un hecho
 * del negocio, no una suposición. Fuera de ese caso no se afirma nada: la línea
 * **no se imprime**.
 *
 * ```
 * TIEMPO_A_CORTO_PLAZOMESES == 4  ->  fecha de la venta + 1 anio
 * cualquier otro valor            ->  null (la linea no sale en el papel)
 * ```
 *
 * **No la generalice.** Si mañana aparece otro plazo con vencimiento conocido,
 * se agrega su caso aquí con su propio respaldo; lo que no se puede es volver a
 * aplicar el año a todo el padrón, que es de donde salió el defecto.
 *
 * El día 29 de febrero no necesita un caso especial: `LocalDate.plusYears`
 * recorta al 28 en el año siguiente en vez de reventar.
 */
object VencimientoDelCredito {

    /**
     * El único `TIEMPO_A_CORTO_PLAZOMESES` cuyo vencimiento se conoce: cuatro
     * meses de corto plazo, crédito a un año.
     */
    const val PLAZO_CON_VENCIMIENTO_CONOCIDO: Int = 4

    /**
     * El vencimiento de una cuenta, o `null` cuando no se puede afirmar — sea
     * porque el plazo no es el de la regla o porque [fechaVenta] no se pudo
     * leer.
     */
    fun de(fechaVenta: LocalDate?, plazoMeses: Int): LocalDate? {
        if (plazoMeses != PLAZO_CON_VENCIMIENTO_CONOCIDO) return null
        return fechaVenta?.plusYears(1)
    }
}
