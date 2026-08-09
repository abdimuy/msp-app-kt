package com.example.msp_app.feature.collectionreport.domain.port

import com.example.msp_app.feature.collectionreport.domain.model.CollectionPayment
import com.example.msp_app.feature.collectionreport.domain.model.CollectionVisit
import com.example.msp_app.feature.collectionreport.domain.model.DateRange
import com.example.msp_app.feature.collectionreport.domain.model.Forgiveness
import com.example.msp_app.feature.collectionreport.domain.model.Money
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

    /** Inicio del ciclo (UTC), o `null` si el usuario aún no tiene contexto. */
    suspend fun fechaCargaInicial(): Instant?

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
