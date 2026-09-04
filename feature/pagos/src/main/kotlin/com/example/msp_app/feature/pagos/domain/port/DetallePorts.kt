package com.example.msp_app.feature.pagos.domain.port

import com.example.msp_app.feature.pagos.domain.model.DatosDeVenta
import com.example.msp_app.feature.pagos.domain.model.Liquidacion
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.model.VisitaDelCliente
import java.time.Instant

/**
 * Puertos OUTBOUND de las pantallas de detalle (cliente y venta): lo que el
 * feature NECESITA del exterior, expresado en tipos de dominio con [
 * com.example.msp_app.core.common.money.Money] — nunca en entidades Room, nunca
 * en `Double`. Los adaptadores de `data/adapter` los implementan sobre los DAOs
 * de `:core:database` y son la ÚNICA capa que ve Room.
 *
 * Cada puerto de este archivo cruza un límite de módulo (a `:core:database` o a
 * `:app`), que es lo que lo justifica frente a YAGNI — no hay aquí ningún
 * puerto con una sola implementación encerrada en el propio módulo.
 */
interface VentasPort {

    /** Las ventas del cliente [clienteId]; lista vacía si no tiene ninguna. */
    suspend fun ventasDelCliente(clienteId: Int): List<DatosDeVenta>

    /** La venta [ventaId] (`DOCTO_CC_ACR_ID`), o `null` si el teléfono no la tiene. */
    suspend fun venta(ventaId: Int): DatosDeVenta?
}

/** Historial de abonos de una venta. */
interface PagosPort {

    /**
     * Todos los abonos COBRADOS de [ventaId], del más reciente al más viejo.
     * La condonación (forma 137026) queda fuera: no es dinero que entró y su
     * lógica no se toca (fuera de alcance del plan).
     */
    suspend fun pagosDe(ventaId: Int): List<PagoDelHistorial>
}

/** Bitácora de visitas de un cliente. */
interface VisitasPort {

    /** Las visitas de [clienteId], de la más reciente a la más vieja. */
    suspend fun visitasDelCliente(clienteId: Int): List<VisitaDelCliente>
}

/**
 * "Hoy liquida con" de una venta.
 *
 * **El puerto se queda en el módulo, el adaptador vive en `:app`** (precedente
 * `UserCyclePort` → `FirebaseUserCycleAdapter`, `ReportThemePort`): el cálculo
 * de liquidación ya existe y es lógica de dinero probada en producción
 * (`app/.../features/sales/domain/models/SettlementCalculator.kt`). Esta tarea
 * NO lo reimplementa ni lo toca — lo consume detrás de este contrato.
 */
interface LiquidacionPort {

    /** Cuánto liquida hoy [ventaId], o `null` si esa venta no admite liquidación. */
    suspend fun liquidacionDe(ventaId: Int): Liquidacion?
}

/**
 * Inicio del periodo de cobro del cobrador (`FECHA_CARGA_INICIAL`).
 *
 * Mismo reparto que [LiquidacionPort]: el dato vive en el documento de usuario
 * de Firestore, dentro de `:app`, así que el adaptador se va allá. Como sostiene
 * sesión, su implementación NO debe ser `@Singleton` (kill-switch de baseURL).
 *
 * Devuelve `null` cuando el dato todavía no se conoce. El llamador **no** debe
 * inventar una ventana (defecto D5, `HomeStartWeek.kt`): sin ventana no hay
 * derivación y se dice en pantalla que falta el dato.
 */
interface PeriodoDeCobroPort {

    /** Inicio del periodo, o `null` si aún no se conoce. */
    suspend fun inicioDelPeriodo(): Instant?
}
