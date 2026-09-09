package com.example.msp_app.feature.pagos.domain.port

import com.example.msp_app.core.common.cobranza.domain.VentanaCobro
import com.example.msp_app.feature.pagos.domain.model.DatosDeVenta
import com.example.msp_app.feature.pagos.domain.model.GarantiaDeLaVenta
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
 * ## Por qué existen estos puertos, y la regla que colisiona
 *
 * **Corrección.** Este KDoc afirmaba que *"cada puerto de este archivo cruza un
 * límite de módulo … no hay aquí ningún puerto con una sola implementación
 * encerrada en el propio módulo"*. Era falso —cuatro de los seis tienen su
 * adaptador en `feature/pagos/data/adapter`— y, peor, estaba redactado para que
 * el próximo revisor **no fuera a mirar**, que es la clase de premisa falsa más
 * cara: desactiva la verificación.
 *
 * Lo que sí manda acá es el **contrato hexagonal**, no el conteo de
 * implementaciones: `application/` depende de `domain/port`, `data/adapter` es
 * la única capa que importa Room, y `ui/` nunca ve `data/adapter`. Un módulo de
 * feature que tiene su propio adaptador de Room termina, **por construcción**,
 * con el puerto y su única implementación del mismo lado. El módulo de
 * referencia que el plan nombra, `:feature:collectionReport`, está construido
 * igual.
 *
 * Eso choca de frente con la regla YAGNI de `DISPATCH-CONVENTIONS.md` ("puerto
 * solo si hay ≥2 implementaciones o cruza módulo; uno solo es un DEFECTO"). Las
 * dos reglas de la rúbrica no pueden cumplirse a la vez cuando el adaptador vive
 * en el mismo módulo, y esta rama resolvió la colisión a favor del contrato
 * hexagonal. La resolución queda escrita acá para que no haya que redescubrirla,
 * y **no se sustituye una afirmación falsa por otra**: dónde está hoy cada
 * adaptador no se afirma en prosa.
 */
interface VentasPort {

    /** Las ventas del cliente [clienteId]; lista vacía si no tiene ninguna. */
    suspend fun ventasDelCliente(clienteId: Int): List<DatosDeVenta>

    /** La venta [ventaId] (`DOCTO_CC_ACR_ID`), o `null` si el teléfono no la tiene. */
    suspend fun venta(ventaId: Int): DatosDeVenta?

    /**
     * TODAS las ventas que el teléfono tiene — la fuente de la lista de
     * clientes (Task 17).
     *
     * Sin filtro de saldo, exactamente el mismo conjunto que la pantalla que
     * reemplaza: `SalesViewModel.getLocalSales()` lee `SaleDao.observeAll()`,
     * que tampoco filtra. Cambiar el conjunto al portar la lista sería cambiar
     * qué puertas ve el cobrador, y eso no es lo que esta tarea vino a hacer.
     */
    suspend fun todasLasVentas(): List<DatosDeVenta>
}

/** Historial de abonos de una venta. */
interface PagosPort {

    /**
     * Todos los abonos COBRADOS de [ventaId], del más reciente al más viejo.
     * La condonación (forma 137026) queda fuera: no es dinero que entró y su
     * lógica no se toca (fuera de alcance del plan).
     */
    suspend fun pagosDe(ventaId: Int): List<PagoDelHistorial>

    /**
     * Los abonos COBRADOS de TODAS las ventas dentro de [ventana].
     *
     * Es lo que necesita la lista de clientes para derivar el periodo de la
     * ruta entera: una sola lectura acotada por fecha, y no una por venta. El
     * recorte por ventana es legítimo porque `EstadoCuentaDeriver` solo mira
     * los pagos del periodo — fuera de él no cambian ningún estado.
     */
    suspend fun pagosDelPeriodo(ventana: VentanaCobro): List<PagoDelHistorial>

    /**
     * UN abono por su id, o `null` si el teléfono no lo tiene (o si no es
     * cobranza: la condonación no sale por aquí, igual que no sale por
     * [pagosDe]).
     *
     * Existe para el ticket de pago (Task 20): su ruta lleva SOLO el `pagoId`
     * —así sobrevive a la rotación y a la muerte del proceso sin depender de
     * quién navegó hasta ella— y la venta se resuelve desde
     * [PagoDelHistorial.ventaId].
     */
    suspend fun pago(pagoId: String): PagoDelHistorial?
}

/** Bitácora de visitas de un cliente. */
interface VisitasPort {

    /** Las visitas de [clienteId], de la más reciente a la más vieja. */
    suspend fun visitasDelCliente(clienteId: Int): List<VisitaDelCliente>

    /**
     * Las visitas de TODOS los clientes dentro de [ventana] — el par del
     * [PagosPort.pagosDelPeriodo] de arriba, y por la misma razón: el deriver
     * descarta las visitas fuera del periodo.
     */
    suspend fun visitasDelPeriodo(ventana: VentanaCobro): List<VisitaDelCliente>
}

/** La garantía abierta de una venta. */
interface GarantiasPort {

    /**
     * La garantía ligada al crédito [creditoId] (`DOCTO_CC_ID`), o `null` si esa
     * venta no tiene ninguna. Solo lectura sobre la tabla `garantias`, que ya
     * existe: esta tarea no toca la lógica de garantías.
     */
    suspend fun garantiaDe(creditoId: Int): GarantiaDeLaVenta?
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
