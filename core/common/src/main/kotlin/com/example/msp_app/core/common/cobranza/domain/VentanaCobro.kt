package com.example.msp_app.core.common.cobranza.domain

import com.example.msp_app.core.common.time.AppClock
import java.time.Instant

/**
 * El periodo de cobro sobre el que se derivan los ocho estados: `[inicio, fin]`,
 * **ambos extremos incluidos**.
 *
 * ## De dónde sale
 *
 * [inicio] es `FECHA_CARGA_INICIAL` del documento de usuario de Firestore — el
 * mismo instante que `Home.startWeekDate` ya resuelve hoy
 * (`HomeStartWeek.resolveStartWeekDate`). [fin] es *ahora*, siempre vía
 * [AppClock]: nunca `System.currentTimeMillis()` ni `Instant.now()`. Un borde
 * de periodo es justo donde alguien alcanza el reloj del sistema, y por eso
 * [ahora] recibe el reloj por parámetro y los tests usan `FakeClock`.
 *
 * Cuando `FECHA_CARGA_INICIAL` no se conoce todavía, `resolveStartWeekDate`
 * devuelve `null` y el llamador **no** debe inventar una ventana (defecto D5,
 * documentado en `HomeStartWeek.kt`): sin ventana no hay derivación, se dice
 * en pantalla que falta el dato.
 *
 * ## Por qué inclusivo en los dos extremos
 *
 * Para no discrepar del servidor. La consulta que calcula `ABONO_SEMANA`
 * (`internal/rutas/infra/rutasfb/cobranza_repo.go:104-112`) filtra
 * `FECHA >= ? AND FECHA <= ?` con `desde = fechaInicio` y `hasta = now`
 * (`internal/rutas/app/cobranza_semanal.go:51`). `AppTime` prefiere rangos
 * semiabiertos `[inicio, fin)` para rangos de *día*, y con razón; acá el
 * extremo superior es "ahora", así que incluirlo o no es una diferencia de
 * ancho cero — y copiar el predicado del servidor vale más que la coherencia
 * estilística.
 */
data class VentanaCobro(val inicio: Instant, val fin: Instant) {

    init {
        require(!fin.isBefore(inicio)) {
            "VentanaCobro invertida: fin ($fin) es anterior a inicio ($inicio)"
        }
    }

    /** ¿Cae [instante] dentro del periodo? Inclusivo en ambos extremos. */
    fun contiene(instante: Instant): Boolean = !instante.isBefore(inicio) && !instante.isAfter(fin)

    companion object {
        /**
         * Formas de cobro que cuentan como cobranza real: efectivo (157),
         * cheque (158) y transferencia (52569).
         *
         * Espeja dos filtros que ya existen y que deben seguir de acuerdo:
         * `PaymentDao.getAdjustedPaymentPercentage` (`FORMA_COBRO_ID IN (157,
         * 158, 52569)`) y el `CONCEPTO_CC_ID = 87327` del servidor. La
         * **condonación** (`Constants.CONDONACION_ID` = 137026) queda fuera a
         * propósito: no es dinero que entró, es deuda perdonada. Contarla como
         * abono pintaría "Pagó" en una cuenta donde nadie pagó nada. La
         * condonación está declarada fuera de alcance en este plan y se queda
         * exactamente igual.
         *
         * Los valores están copiados de `Constants` (`:app`) porque
         * `:core:common` no puede depender de `:app`;
         * `TipoVisitaCatalogoDriftTest` los compara y se pone rojo si se
         * despegan.
         */
        val FORMAS_COBRO_COBRANZA: Set<Int> = setOf(157, 158, 52569)

        /**
         * Ventana `[inicioSemana, ahora]`. [clock] es la ÚNICA fuente de
         * "ahora" — pásale `FakeClock` en tests.
         */
        fun desdeInicioSemana(inicioSemana: Instant, clock: AppClock): VentanaCobro =
            VentanaCobro(inicio = inicioSemana, fin = clock.now())
    }
}
