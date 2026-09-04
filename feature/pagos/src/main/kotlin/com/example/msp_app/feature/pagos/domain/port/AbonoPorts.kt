package com.example.msp_app.feature.pagos.domain.port

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro

/**
 * El abono que se va a escribir. Viaja en tipos de dominio: el [importe] es
 * [Money] hasta el adaptador, que es el ÚNICO lugar donde cruza al `Double` de
 * `PaymentEntity.IMPORTE` (REGLA DE DINERO, `global-constraints.md`).
 *
 * [abonoId] es a la vez el id del pago y la **clave de idempotencia** que ya
 * usa la app: `NewPaymentDialog` la acuña una vez por apertura del diálogo
 * justo para que un segundo toque no entre como cobro nuevo. Aquí vive en el
 * `SavedStateHandle` del destino, así que además sobrevive a la rotación y a la
 * muerte del proceso.
 */
data class AbonoARegistrar(
    val abonoId: String,
    val ventaId: Int,
    val importe: Money,
    val metodo: MetodoDeCobro
)

/**
 * Cómo terminó el registro. Un enum y no una excepción: los cuatro finales son
 * distintos para el cobrador —y para el guard anti-duplicado, que solo se
 * libera cuando NADA se escribió.
 */
enum class ResultadoDelAbono {
    /** El abono quedó guardado y el saldo de la venta bajó. */
    REGISTRADO,

    /** El teléfono ya no tiene esa venta. Nada se escribió. */
    VENTA_NO_ESTA_EN_EL_TELEFONO,

    /**
     * No se pudo saber qué cobrador está registrando. Nada se escribió — un
     * abono sin `COBRADOR_ID` es dinero que nadie entregó (defecto ya conocido:
     * `NewPaymentDialog` lo valida antes de guardar).
     */
    SIN_COBRADOR,

    /** El guardado falló. Nada quedó escrito: la escritura es una transacción. */
    FALLO_EL_GUARDADO,

    /**
     * El cinturón de `application/` rechazó el monto (sobrepago, no positivo o
     * venta sin saldo). **Nunca debería llegar aquí:** la pantalla ya lo
     * bloquea. Que exista este valor es lo que hace que el segundo cinturón sea
     * observable en vez de silencioso.
     */
    BLOQUEADO_POR_SEGURIDAD
}

/**
 * Escribe un abono.
 *
 * **El puerto se queda en el módulo, el adaptador se va a `:app`** (precedente
 * `LiquidacionPort` → `SettlementLiquidacionAdapter`): la escritura de dinero
 * ya existe y corre en producción —`PaymentFactory` + `PaymentsLocalDataSource.
 * saveAndEnqueue`, que inserta el pago y baja el `SALDO_REST` en UNA
 * transacción—, y esta tarea la CONSUME. Reimplementarla sería tocar dinero que
 * esta tarea no vino a tocar.
 *
 * **El pago sigue soberano:** este puerto no conoce visitas, ni fotos, ni
 * ubicación. Nada del camino del abono depende del estado de una visita.
 */
interface RegistroDeAbonoPort {

    /** Registra [abono]. Total: no lanza, contesta con un [ResultadoDelAbono]. */
    suspend fun registrar(abono: AbonoARegistrar): ResultadoDelAbono
}
