package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.TipoDeContacto
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Un tramo de la bitácora: su encabezado y lo que cuelga de él.
 *
 * [cobrado] es la suma de lo que **entró** en el tramo, o `null` cuando no hay
 * ningún cobro adentro. `null` y no `Money.ZERO` a propósito: "en este mes no
 * cobré nada" y "en este mes sólo hubo visitas" se ven igual con un cero, y son
 * cosas distintas — un cero en una pantalla de dinero se lee como una medición.
 *
 * `null` también cuando el tramo **no es la historia completa**: sólo lo llena
 * quien agrupa la lista entera. Ver [GruposDeContactos.porCercania].
 */
data class GrupoDeContactos(
    val titulo: String,
    val contactos: List<ContactoDeCobranza>,
    val cobrado: Money? = null
)

/**
 * **Cómo se parte la bitácora en tramos.** Dos formas, y cuál se usa depende de
 * cuántas filas hay debajo, no del gusto:
 *
 * - [porMes] para las listas largas —"ver los N contactos" y el detalle de
 *   venta—, donde el mes separa de verdad y su subtotal contesta *"¿cuánto
 *   entró ese mes?"* sin sumar de cabeza. Es el mismo criterio que
 *   [RielDePagos] ya usaba para el historial de pagos; aquí trabaja sobre la
 *   línea mezclada de cobros y visitas en vez de sólo pagos.
 * - [porCercania] para el detalle de cliente, que enseña tres. Con tres filas
 *   el mes casi siempre da **un solo encabezado**, o sea un separador que no
 *   separa nada; *"Hoy / Esta semana / Antes"* sí parte, y además es como se
 *   habla parado en la puerta. **No lleva subtotal**, y ésa es la diferencia
 *   que importa entre las dos: ver su KDoc.
 *
 * Las dos son puras y reciben el `hoy` por parámetro: pedirle la fecha al reloj
 * aquí adentro haría que la misma lista se agrupara distinto según la hora a la
 * que se abrió la pantalla, y que ningún golden fuera estable.
 */
object GruposDeContactos {

    /**
     * Tramos de un mes de calendario, del más reciente al más viejo.
     *
     * Éste **sí** lleva subtotal: sus dos llamadores —la bitácora completa y el
     * detalle de venta— le pasan la lista entera, así que la suma del tramo es
     * de verdad todo lo que entró en ese mes. Comparar con [porCercania].
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
     * Tramos por cercanía a [hoy]: **Hoy · Esta semana · Este mes · Antes**.
     *
     * Los tramos vacíos no se emiten: un encabezado "Esta semana" sin filas
     * debajo es un hueco que el cobrador lee como un error de carga.
     *
     * ## Nunca calcula [GrupoDeContactos.cobrado], y es el arreglo de un defecto
     *
     * Esto agrupa una **MUESTRA** —los tres contactos que caben en el detalle de
     * cliente, `BitacoraDelCliente.VISIBLES_EN_EL_DETALLE`—, no la historia
     * completa. Cualquier suma suya sería parcial presentada como total: el
     * detalle pintaba *"Antes ——— $350"* con *"Ver los 27 contactos"* debajo, o
     * sea la suma de tres filas puesta en el lugar donde se lee "esto es lo que
     * entró en el tramo". Una cifra falsa en una pantalla de dinero.
     *
     * No se arregla en la pantalla —que podría olvidarse de ocultarlo— sino
     * aquí: quien agrupa una muestra **no puede** producir un subtotal. [porMes],
     * que siempre recibe la lista entera, sí conserva el suyo.
     */
    fun porCercania(contactos: List<ContactoDeCobranza>, hoy: LocalDate): List<GrupoDeContactos> =
        contactos
            .sortedByDescending { it.fecha }
            .groupBy { cercaniaDe(AppTime.toBusinessDate(it.fecha), hoy) }
            .toList()
            .sortedBy { (cercania, _) -> cercania.ordinal }
            .map { (cercania, delTramo) ->
                GrupoDeContactos(titulo = cercania.titulo, contactos = delTramo)
            }

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

    private fun cercaniaDe(fecha: LocalDate, hoy: LocalDate): Cercania = when {
        fecha >= hoy -> Cercania.HOY
        ChronoUnit.DAYS.between(fecha, hoy) < DIAS_DE_LA_SEMANA -> Cercania.ESTA_SEMANA
        YearMonth.from(fecha) == YearMonth.from(hoy) -> Cercania.ESTE_MES
        else -> Cercania.ANTES
    }

    /**
     * Los cuatro tramos, **en el orden en que se pintan** — el `ordinal` es el
     * orden, así que agregar uno en medio lo coloca solo.
     */
    private enum class Cercania(val titulo: String) {
        HOY("Hoy"),
        ESTA_SEMANA("Esta semana"),
        ESTE_MES("Este mes"),
        ANTES("Antes")
    }

    /**
     * Siete días, contados hacia atrás desde hoy y no desde el lunes.
     *
     * Es deliberado: el cobrador pregunta *"¿fui esta semana?"* queriendo decir
     * "en los últimos días", y un corte en el lunes haría que el viernes una
     * visita del jueves anterior saliera de "esta semana" por calendario aunque
     * fue hace un día.
     */
    private const val DIAS_DE_LA_SEMANA = 7L

    private val MES_Y_ANIO: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMMM yyyy", BUSINESS_LOCALE)
}
