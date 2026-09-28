package com.example.msp_app.data.pagos

import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.SaleEntity

/**
 * Fixtures compartidas por las pruebas de la condonación (escritura, ViewModel y
 * worker). La venta lleva **el mismo número** en la PK y en `DOCTO_CC_ID`, que es
 * como existe en producción (ver el KDoc de `SaleIdSpaces`): así la condonación
 * se escribe por la PK y el historial la lee por el cargo, sin fixture que lo
 * disimule.
 */
internal object CondonacionFixtures {

    const val VENTA: Int = 13_662_829
    const val CONDONACION: Int = 137_026
    const val EFECTIVO: Int = 157

    fun venta(saldo: Double, id: Int = VENTA) = SaleEntity(
        DOCTO_CC_ACR_ID = id,
        DOCTO_CC_ID = id,
        FOLIO = "Y00013662",
        CLIENTE_ID = 4821,
        APLICADO = "S",
        COBRADOR_ID = 7,
        CLIENTE = "Guadalupe Hernandez Soto",
        ZONA_CLIENTE_ID = 21,
        LIMITE_CREDITO = 0.0,
        NOTAS = "",
        ZONA_NOMBRE = "Centro",
        IMPORTE_PAGO_PROMEDIO = 350.0,
        TOTAL_IMPORTE = 3500.0,
        NUM_IMPORTES = 10,
        FECHA = "2026-01-01T00:00:00Z",
        PARCIALIDAD = 350,
        ENGANCHE = 500.0,
        TIEMPO_A_CORTO_PLAZOMESES = 0,
        MONTO_A_CORTO_PLAZO = 0.0,
        VENDEDOR_1 = "",
        VENDEDOR_2 = "",
        VENDEDOR_3 = "",
        PRECIO_TOTAL = 3500.0,
        IMPTE_REST = saldo,
        SALDO_REST = saldo,
        FECHA_ULT_PAGO = null,
        CALLE = "Av. Reforma 100",
        CIUDAD = "Tehuacan",
        ESTADO = "Puebla",
        TELEFONO = "2381234567",
        NOMBRE_COBRADOR = "Rosa Elena Martinez Vazquez",
        ESTADO_COBRANZA = "PENDIENTE",
        DIA_COBRANZA = "LUNES",
        DIA_TEMPORAL_COBRANZA = "",
        PRECIO_DE_CONTADO = 3000.0,
        AVAL_O_RESPONSABLE = "",
        FREC_PAGO = "SEMANAL"
    )

    fun condonacion(
        id: String,
        importe: Double,
        venta: Int = VENTA,
        formaCobro: Int = CONDONACION,
        guardado: Boolean = false
    ) = PaymentEntity(
        ID = id,
        COBRADOR = "Rosa Elena Martinez Vazquez",
        DOCTO_CC_ACR_ID = venta,
        DOCTO_CC_ID = 0,
        FECHA_HORA_PAGO = "2026-09-26T21:27:25Z",
        GUARDADO_EN_MICROSIP = guardado,
        IMPORTE = importe,
        LAT = 0.0,
        LNG = 0.0,
        CLIENTE_ID = 4821,
        COBRADOR_ID = 7,
        FORMA_COBRO_ID = formaCobro,
        ZONA_CLIENTE_ID = 21,
        NOMBRE_CLIENTE = "Guadalupe Hernandez Soto"
    )

    suspend fun saldoDe(db: AppDatabase, venta: Int = VENTA): Double =
        checkNotNull(db.saleDao().getById(venta)).SALDO_REST

    suspend fun condonacionesEn(db: AppDatabase, venta: Int = VENTA): List<PaymentEntity> =
        db.paymentDao().getPaymentsBySaleId(venta).filter { it.FORMA_COBRO_ID == CONDONACION }
}
