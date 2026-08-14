package com.example.msp_app.feature.collectionreport.ui

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.feature.collectionreport.domain.model.CollectionPayment
import java.time.Instant
import java.time.LocalDate

/**
 * Arma la tira de días del ciclo (periodo Día) a partir de lo que ya resolvió
 * [CollectionReportViewModel]: los días elegibles ([com.example.msp_app.feature.collectionreport.domain.RangeCalculator.cycleDays]),
 * el día pedido por el usuario y el resumen de cobros por día.
 *
 * Vive aparte de [CollectionReportStateBuilder] por la misma razón que
 * [CollectionReportMetaBuilder]: mantener cada tipo bajo el umbral `TooManyFunctions` de detekt
 * — convención del proyecto (dividir, no suprimir).
 *
 * ## Qué NO hace
 *
 * No inventa días. La ventana la fija el dominio: de la carga de ruta a hoy, inclusive. Nada
 * anterior a la carga (eso pertenece a un ciclo ya cerrado) y nada posterior a hoy.
 */
internal object CollectionReportDayStripBuilder {

    /**
     * Debajo de este número de días no se pinta tira: con un solo día elegible no hay nada que
     * elegir y una tira de un chip sería ruido que además desplaza el tablero.
     *
     * Efecto lateral querido: un cobrador SIN fecha de carga (`fechaCargaInicial == null`, el
     * fallback documentado de `RangeCalculator` = solo hoy) ve exactamente la pantalla de
     * siempre, sin controles nuevos.
     */
    private const val MIN_DAYS_FOR_STRIP = 2

    /**
     * Día que de verdad se va a mostrar, dado el ciclo VIGENTE y el que el usuario pidió.
     *
     * - Sin petición (`null`, arranque) -> hoy, el último día del ciclo.
     * - Petición que sigue dentro del ciclo -> se respeta.
     * - Petición fuera del ciclo -> **hoy**. Es el caso del cambio de ciclo: el cobrador vuelve
     *   a cargar ruta, `FECHA_CARGA_INICIAL` se recorre y la tira pasa a ser otra; si se
     *   conservara la petición vieja, la pantalla quedaría apuntando a un día que ya no existe
     *   en la tira — un rango vacío permanente (`RangeCalculator.dayRange` devuelve rango vacío
     *   fuera del ciclo) que se leería como "no cobré nada" en vez de como "ese día ya no es
     *   tuyo". La validación es por PERTENENCIA a [cycleDays], no por comparar fechas de carga:
     *   cubre igual el ciclo nuevo, el reloj corrido y un dato sucio de Firestore.
     * - Ciclo vacío (carga en el futuro, guarda defensiva del dominio) -> `null`.
     */
    fun resolveSelectedDay(cycleDays: List<LocalDate>, requested: LocalDate?): LocalDate? {
        val today = cycleDays.lastOrNull() ?: return null
        return if (requested != null && requested in cycleDays) requested else today
    }

    /**
     * Chips de la tira, en el mismo orden ascendente que [cycleDays] (carga -> hoy).
     *
     * [dayGroups] es el mapa `yyyy-MM-dd -> pagos` de
     * [com.example.msp_app.feature.collectionreport.domain.port.PaymentsPort.paymentsGroupedByDaySince]
     * consultado DESDE el inicio real del ciclo (la hora de la carga, no la medianoche): por eso
     * los pagos del ciclo anterior no reviven el día de la carga.
     *
     * **Un día sin cobros sale marcado, no ausente** ([DayChipUi.hasCollections] `false`). Es la
     * decisión de transparencia del dueño: un día que desaparece de la tira se lee como dato
     * faltante ("¿se perdió mi cobranza?"); uno atenuado se lee como lo que es, un día sin
     * cobrar. El caso canónico es el propio día de la carga cuando el cobrador cargó de noche.
     *
     * Devuelve lista VACÍA con menos de [MIN_DAYS_FOR_STRIP] días — ver esa constante.
     */
    fun chips(
        cycleDays: List<LocalDate>,
        selectedDay: LocalDate?,
        today: LocalDate,
        dayGroups: Map<String, List<CollectionPayment>>
    ): List<DayChipUi> {
        if (cycleDays.size < MIN_DAYS_FOR_STRIP) return emptyList()
        return cycleDays.map { day ->
            DayChipUi(
                date = day,
                isToday = day == today,
                isSelected = day == selectedDay,
                hasCollections = dayGroups[AppTime.toWireDate(day)].orEmpty().isNotEmpty()
            )
        }
    }

    /**
     * Nota del día de la carga: "desde las 7:33 p.m. · inicio del ciclo". Vacía en cualquier
     * otro día.
     *
     * Sin esta línea, el día de la carga en $0 se lee como una FALLA del reporte; con ella se lee
     * como lo que es, un corte: el ciclo abrió a esa hora y antes de eso el dinero era del ciclo
     * pasado. Es el complemento honesto de la regla `inicioEfectivo(día) = max(startOfDay(día),
     * fechaCargaInicial)` — el dominio recorta el rango, esta línea explica por qué.
     *
     * La hora se formatea en zona de negocio (`America/Mexico_City`) vía [AppTime], nunca con la
     * zona del dispositivo: el cobrador con el teléfono en otra zona vería una hora de arranque
     * que no corresponde al corte que el dominio aplicó.
     */
    fun startNote(selectedDay: LocalDate?, fechaCargaInicial: Instant?): String {
        val carga = fechaCargaInicial ?: return ""
        if (selectedDay == null || AppTime.toBusinessDate(carga) != selectedDay) return ""
        val hora = AppTime.formatForDisplay(carga, CYCLE_START_TIME_FORMAT)
        return "desde las $hora · inicio de semana"
    }

    /** `h:mm a` -> "7:33 p.m." — sin cero a la izquierda, como lo diría el cobrador. */
    private const val CYCLE_START_TIME_FORMAT = "h:mm a"
}
