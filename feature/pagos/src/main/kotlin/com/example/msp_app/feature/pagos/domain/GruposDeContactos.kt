package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.TipoDeContacto
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Un tramo de la bitácora: su encabezado y lo que cuelga de él.
 *
 * [cobrado] es la suma de lo que **entró** en el tramo, o `null` cuando no hay
 * ningún cobro adentro. `null` y no `Money.ZERO` a propósito: "en este mes no
 * cobré nada" y "en este mes sólo hubo visitas" se ven igual con un cero, y son
 * cosas distintas — un cero en una pantalla de dinero se lee como una medición.
 *
 * `null` también cuando el tramo **no es la historia completa**: sólo lo llena
 * quien agrupa la lista entera. Ver [GruposDeContactos.muestraPorMes].
 */
data class GrupoDeContactos(
    val titulo: String,
    val contactos: List<ContactoDeCobranza>,
    val cobrado: Money? = null
)

/**
 * **Cómo se parte la bitácora en tramos**: por mes de calendario, del más
 * reciente al más viejo, con dos variantes que se distinguen por si reciben la
 * historia entera o un pedazo:
 *
 * - [porMes] para la lista entera —"ver los N contactos"—, donde el mes separa
 *   de verdad y su subtotal contesta *"¿cuánto entró ese mes?"* sin sumar de
 *   cabeza. Es el mismo criterio que [RielDePagos] ya usaba para el historial
 *   de pagos; aquí trabaja sobre la línea mezclada de cobros y visitas.
 * - [muestraPorMes] para los últimos contactos del detalle de venta. **No lleva
 *   subtotal**, y ésa es la diferencia que importa entre las dos: ver su KDoc.
 */
object GruposDeContactos {

    /**
     * Tramos de un mes de calendario, del más reciente al más viejo.
     *
     * Éste **sí** lleva subtotal: su llamador —la bitácora completa— le pasa
     * la lista entera, así que la suma del tramo es de verdad todo lo que entró
     * en ese mes. Comparar con [muestraPorMes].
     */
    fun porMes(contactos: List<ContactoDeCobranza>): List<GrupoDeContactos> = contactos
        .sortedByDescending { it.fecha }
        .groupBy { YearMonth.from(AppTime.toBusinessDate(it.fecha)) }
        .map { (mes, delMes) ->
            GrupoDeContactos(
                titulo = nombreDe(mes),
                contactos = delMes,
                cobrado = sumaDe(delMes)
            )
        }
        .sortedByDescending { grupo -> grupo.contactos.first().fecha }

    /**
     * Los mismos tramos por mes que [porMes], **sin subtotal**, para una
     * MUESTRA: los últimos contactos que caben en el detalle de venta
     * (`BitacoraDelCliente.VISIBLES_EN_LA_VENTA`), no la historia completa.
     *
     * ## Nunca calcula [GrupoDeContactos.cobrado], y es el arreglo de un defecto
     *
     * Cualquier suma sobre una muestra es parcial presentada como total. Ya
     * pasó: el detalle de cliente pintaba *"Antes ——— $350"* con *"Ver los 27
     * contactos"* debajo, o sea la suma de tres filas puesta donde se lee "esto
     * es lo que entró en el tramo". Una cifra falsa en una pantalla de dinero.
     *
     * No se arregla en la pantalla —que podría olvidarse de ocultarlo— sino
     * aquí: quien agrupa una muestra **no puede** producir un subtotal. El total
     * del mes lo da la bitácora completa, que agrupa con [porMes].
     */
    fun muestraPorMes(contactos: List<ContactoDeCobranza>): List<GrupoDeContactos> =
        porMes(contactos).map { it.copy(cobrado = null) }

    /** `SEPTIEMBRE 2026` — el mismo formato que el riel de la venta. */
    fun nombreDe(mes: YearMonth): String = MES_Y_ANIO.format(mes).uppercase(BUSINESS_LOCALE)

    /**
     * Lo que entró en el tramo, o `null` si no hubo un solo cobro.
     *
     * Suma **sólo** los [TipoDeContacto.COBRO]: una promesa trae monto
     * prometido y sumarlo diría que ese dinero entró.
     */
    private fun sumaDe(contactos: List<ContactoDeCobranza>): Money? {
        val importes = contactos
            .filter { it.tipo == TipoDeContacto.COBRO }
            .mapNotNull { it.importe }
        return if (importes.isEmpty()) null else Money.sum(importes)
    }

    private val MES_Y_ANIO: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMMM yyyy", BUSINESS_LOCALE)
}
