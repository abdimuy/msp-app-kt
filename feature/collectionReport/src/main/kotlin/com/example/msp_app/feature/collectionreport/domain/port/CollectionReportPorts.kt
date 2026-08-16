package com.example.msp_app.feature.collectionreport.domain.port

import com.example.msp_app.feature.collectionreport.domain.model.CollectionPayment
import com.example.msp_app.feature.collectionreport.domain.model.CollectionVisit
import com.example.msp_app.feature.collectionreport.domain.model.DateRange
import com.example.msp_app.feature.collectionreport.domain.model.Forgiveness
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.SaleForCobranza
import java.time.Instant

/**
 * Puertos OUTBOUND del reporte de cobranza (arquitectura hexagonal): lo que el
 * feature NECESITA del exterior, expresado en tipos de dominio ([Money],
 * [CollectionPayment], ...) — nunca en entidades Room ni `Double`. Los adapters
 * en `data/adapter` los implementan sobre los DAOs de `:core:database` y
 * convierten `IMPORTE: Double` -> [Money] en el borde (único puente `Double`).
 *
 * **Contrato de datos AUDITADO contra el schema Room v27 (ver task-4-report.md):**
 * - Dinero: columna `Payment.IMPORTE` es `Double` -> `Money.of(Double)`.
 * - Fecha: `Payment.FECHA_HORA_PAGO` / `Visit.FECHA` en wire RFC3339 UTC; las
 *   queries de rango son medio-abiertas `>= :start AND < :end` (verificado por
 *   `PaymentDateRangeHalfOpenTest` / `VisitDateRangeHalfOpenTest`).
 * - Ruteo de forma de cobro (`FORMA_COBRO_ID`, fuente de verdad
 *   `core.utils.Constants`): 157 efectivo, 158 cheque, 52569 transferencia —
 *   los tres únicos que entran a [PaymentsPort.paymentsIn]; 137026 condonación
 *   sale SOLO por [PaymentsPort.forgivenessIn], NUNCA como cobro.
 */
interface PaymentsPort {

    /**
     * Pagos COBRADOS del rango (formas 157/158/52569; el DAO excluye la
     * condonación 137026 en SQL). Medio-abierto `[start, end)`.
     */
    suspend fun paymentsIn(range: DateRange): List<CollectionPayment>

    /**
     * Condonaciones del rango (SOLO forma 137026). Se devuelven aparte porque
     * condonar NO es cobrar: jamás suman al total cobrado.
     */
    suspend fun forgivenessIn(range: DateRange): List<Forgiveness>

    /**
     * Pagos cobrados desde [startIso] (wire RFC3339 UTC), agrupados por día de
     * negocio (`yyyy-MM-dd`, zona `America/Mexico_City`). Alimenta el resumen
     * por día del ciclo.
     */
    suspend fun paymentsGroupedByDaySince(startIso: String): Map<String, List<CollectionPayment>>

    /**
     * Número de pagos pendientes de subir (`GUARDADO_EN_MICROSIP = 0`) — la
     * píldora de sincronización del tablero.
     */
    suspend fun pendingCount(): Int
}

/** Puerto de visitas de cobranza (no mueven dinero). */
interface VisitsPort {

    /** Visitas del rango medio-abierto `[start, end)` por `Visit.FECHA`. */
    suspend fun visitsIn(range: DateRange): List<CollectionVisit>
}

/**
 * Ventas de crédito no-contado ACTIVAS del cobrador — insumo de
 * [com.example.msp_app.feature.collectionreport.domain.CobranzaPorcentaje] para la tarjeta
 * "Meta de la semana" (porcentaje cobro ponderado + porcentaje cuentas/cobertura). Puerto
 * OUTBOUND deliberadamente simple (una sola función): a diferencia de [PaymentsPort], no toma
 * [DateRange] — la ventana del cálculo la fija [CobranzaPorcentaje] (`[fechaInicio, hoy]`), no
 * la consulta de ventas (una venta "activa" no depende del rango del reporte, solo su
 * `abonoSemana` — resuelto aparte agrupando [CollectionPayment.saleId]).
 */
interface SalesPort {

    /** Ventas de crédito no-contado activas (`sales.SALDO_REST > 0`) — ver el adapter Room. */
    suspend fun nonContadoActiveSales(): List<SaleForCobranza>
}

/**
 * Resultado de leer el inicio de semana del cobrador (`FECHA_CARGA_INICIAL`).
 *
 * Las tres ramas existen porque **"no hay dato" y "no se pudo leer" exigen
 * respuestas distintas** y el `Instant?` anterior las aplanaba en `null`: el
 * adapter de Firestore degradaba cualquier excepción a `null`, el cálculo del
 * rango leía ese `null` como "sin carga" y caía al día de hoy — y el cobrador
 * veía $0.00 en la semana sin una sola pista de por qué. Con este tipo:
 *  - [Missing] es una respuesta REAL y estable (el documento existe, el campo
 *    no): se le dice al usuario y no se reintenta en vano;
 *  - [Unavailable] es un fallo transitorio: **se reintenta**, y de ahí sale la
 *    auto-reparación cuando el dato llega tarde.
 */
sealed interface CycleStart {

    /** Hay inicio de semana. */
    data class Known(val instant: Instant) : CycleStart

    /** La fuente respondió y NO hay `FECHA_CARGA_INICIAL` — no hay semana que reportar. */
    data object Missing : CycleStart

    /** No se pudo leer la fuente (Firestore caído/sin red). Reintentable. */
    data object Unavailable : CycleStart

    /** El instante cuando se conoce; `null` en las dos ramas sin dato. */
    val instantOrNull: Instant?
        get() = (this as? Known)?.instant
}

/**
 * Ciclo del cobrador: inicio de la ventana visible (`FECHA_CARGA_INICIAL`) y
 * nombre para el encabezado del reporte.
 *
 * **No se cablea un adapter en este módulo (ver task-4-report.md):** el
 * `userData` (documento de usuario) vive en Firestore dentro de `:app`, fuera
 * del alcance de `:feature:collectionReport`. Igual que [
 * `com.example.msp_app.core.network.AuthTokenProvider`], cuya única
 * implementación real vive en `:app`, la implementación de este puerto se
 * provee en el composition root de `:app`; aquí solo se define el contrato y su
 * fake. Como sostiene una fuente de red/sesión, su implementación NO debe ser
 * `@Singleton` (kill-switch de baseURL/sesión).
 */
interface UserCyclePort {

    /**
     * Inicio de semana del cobrador (UTC), clasificado — ver [CycleStart].
     *
     * Sigue siendo `suspend` one-shot a propósito: la política de reintento vive
     * en el ViewModel (donde se puede probar con fakes y tiempo virtual), no en
     * el adapter de Firestore. El contrato que sí se le exige a la
     * implementación es NO aplanar un fallo en "no hay dato".
     */
    suspend fun cycleStart(): CycleStart

    /** Nombre del cobrador para el encabezado. */
    suspend fun cobradorNombre(): String
}

/**
 * Totales diarios históricos (cobrado por día) para calcular la meta sugerida
 * (`SuggestedGoal`). Devuelve un [Money] por día CON dinero cobrado, en orden
 * cronológico ascendente (el más reciente al final), de modo que
 * `takeLast(window)` seleccione los días recientes.
 */
interface HistoricalTotalsPort {

    /** Totales por día en los últimos [days] días de negocio; `days <= 0` -> vacío. */
    suspend fun dailyTotals(days: Int): List<Money>
}

/**
 * **Parked (ver task-4-brief.md — "Parked for user").** Traspasos de efectivo
 * cobrador -> oficina/almacén, propiedad del módulo `:feature:transfers`. El
 * mockup del reporte NO los pide: el "Transferencia" del tablero es un MÉTODO
 * de pago Room-local (forma 52569), no un traspaso de efectivo. Se define el
 * contrato por si el reporte debe mostrarlos en el futuro, pero **no se cablea**
 * (sin adapter ni binding, YAGNI) hasta que exista un consumidor real.
 */
interface TransfersPort {

    /** Total de efectivo traspasado a oficina/almacén dentro del rango. */
    suspend fun transferredOut(range: DateRange): Money
}
