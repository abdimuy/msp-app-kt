package com.example.msp_app.feature.ventacorreccion.data

import androidx.room.withTransaction
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.localsale.LocalSaleClaimLeases
import com.example.msp_app.core.database.dao.localsale.LocalSaleComboDao
import com.example.msp_app.core.database.dao.localsale.LocalSaleDao
import com.example.msp_app.core.database.dao.localsale.LocalSaleProductDao
import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import com.example.msp_app.feature.ventacorreccion.domain.CamposVentaCorregidos
import com.example.msp_app.feature.ventacorreccion.domain.EstadoVentaLocal
import com.example.msp_app.feature.ventacorreccion.domain.VentaLocalParaCorregir
import com.example.msp_app.feature.ventacorreccion.domain.port.VentaLocalCorreccionPort

/**
 * Adapter Room de [VentaLocalCorreccionPort] sobre el DAO atómico de Task 1
 * (`LocalSaleDao`/`LocalSaleProductDao`/`LocalSaleComboDao`) — ver "El mecanismo de la carrera"
 * en el plan.
 *
 * [db] es necesaria además de los tres DAOs porque [guardarCorreccion] envuelve guardia +
 * campos + merge de líneas + `clearUploadFailure` en UNA sola transacción (`db.withTransaction`,
 * mismo patrón que `CobranzaSyncManager`/`CobranzaReconciler` en `:app`). El guardia
 * (`commitEditGuard`) va PRIMERO: si devuelve 0 filas, la función retorna `false` de inmediato
 * SIN ejecutar ninguna otra escritura — no hace falta revertir nada porque nada se alcanzó a
 * escribir. Si el guardia se moviera al final (el mutante que el brief de Task 3 pide sembrar),
 * los demás `UPDATE`/merge correrían y quedarían commiteados aunque el guardia hubiera fallado
 * — la transacción de Room sólo revierte ante una excepción, no ante un `return` con valor
 * `false`.
 */
class RoomVentaLocalCorreccionAdapter(
    private val db: AppDatabase,
    private val localSaleDao: LocalSaleDao,
    private val localSaleProductDao: LocalSaleProductDao,
    private val localSaleComboDao: LocalSaleComboDao
) : VentaLocalCorreccionPort {

    override suspend fun leerEstado(saleId: String): EstadoVentaLocal? =
        localSaleDao.getSaleById(saleId)?.let { sale ->
            EstadoVentaLocal(
                enviado = sale.ENVIADO,
                permanente = sale.LAST_UPLOAD_PERMANENT == true,
                correccionNoEnviada = sale.CORRECCION_NO_ENVIADA,
                claimKind = sale.CLAIM_KIND,
                claimedAt = sale.CLAIMED_AT
            )
        }

    override suspend fun leerVenta(saleId: String): VentaLocalParaCorregir? {
        val sale = localSaleDao.getSaleById(saleId) ?: return null
        return VentaLocalParaCorregir(
            campos = sale.aCampos(),
            productos = localSaleProductDao.getProductsForSale(saleId),
            combos = localSaleComboDao.getCombosForSale(saleId)
        )
    }

    override suspend fun reclamarParaEditar(saleId: String, claimId: String, ahora: Long): Boolean =
        localSaleDao.claimForEdit(
            saleId = saleId,
            claimId = claimId,
            now = ahora,
            uploadLeaseMs = LocalSaleClaimLeases.UPLOAD_LEASE_MS
        ) == 1

    override suspend fun soltar(saleId: String, claimId: String) {
        localSaleDao.releaseClaim(saleId, claimId)
    }

    override suspend fun guardarCorreccion(
        saleId: String,
        claimId: String,
        campos: CamposVentaCorregidos,
        productos: List<LocalSaleProductEntity>,
        combos: List<LocalSaleComboEntity>
    ): Boolean = db.withTransaction {
        // El guardia va PRIMERO: si falla, cortamos aquí sin tocar nada más. Ver el comentario
        // de clase — mover esto al final es exactamente el mutante que Task 3 pide sembrar.
        val guardiaOk = localSaleDao.commitEditGuard(saleId, claimId) == 1
        if (!guardiaOk) {
            return@withTransaction false
        }

        localSaleDao.updateSaleFields(
            localSaleId = saleId,
            nombreCliente = campos.nombreCliente,
            fechaVenta = campos.fechaVenta,
            latitud = campos.latitud,
            longitud = campos.longitud,
            direccion = campos.direccion,
            parcialidad = campos.parcialidad,
            enganche = campos.enganche,
            telefono = campos.telefono,
            frecPago = campos.frecPago,
            avalOResponsable = campos.avalOResponsable,
            nota = campos.nota,
            diaCobranza = campos.diaCobranza,
            precioTotal = campos.precioTotal,
            tiempoACortoPlazoMeses = campos.tiempoACortoPlazoMeses,
            montoACortoPlazo = campos.montoACortoPlazo,
            montoDeContado = campos.montoDeContado,
            enviado = false,
            numero = campos.numero,
            colonia = campos.colonia,
            poblacion = campos.poblacion,
            ciudad = campos.ciudad,
            tipoVenta = campos.tipoVenta,
            zonaClienteId = campos.zonaClienteId,
            zonaCliente = campos.zonaCliente,
            clienteId = campos.clienteId
        )
        localSaleProductDao.mergeProductsForSale(saleId, productos)
        localSaleComboDao.mergeCombosForSale(saleId, combos)
        // Edit-and-retry SIN rotar la Idempotency-Key (Global Constraint del plan): sólo se
        // limpia el registro de fallo previo, para que la UI no siga mostrando un error viejo
        // tras una corrección exitosa.
        localSaleDao.clearUploadFailure(saleId)

        true
    }
}

private fun LocalSaleEntity.aCampos() = CamposVentaCorregidos(
    nombreCliente = NOMBRE_CLIENTE,
    fechaVenta = FECHA_VENTA,
    latitud = LATITUD,
    longitud = LONGITUD,
    direccion = DIRECCION,
    parcialidad = PARCIALIDAD,
    enganche = ENGANCHE,
    telefono = TELEFONO,
    frecPago = FREC_PAGO,
    avalOResponsable = AVAL_O_RESPONSABLE,
    nota = NOTA,
    diaCobranza = DIA_COBRANZA,
    precioTotal = PRECIO_TOTAL,
    tiempoACortoPlazoMeses = TIEMPO_A_CORTO_PLAZOMESES,
    montoACortoPlazo = MONTO_A_CORTO_PLAZO,
    montoDeContado = MONTO_DE_CONTADO,
    numero = NUMERO,
    colonia = COLONIA,
    poblacion = POBLACION,
    ciudad = CIUDAD,
    tipoVenta = TIPO_VENTA,
    zonaClienteId = ZONA_CLIENTE_ID,
    zonaCliente = ZONA_CLIENTE,
    clienteId = CLIENTE_ID
)
