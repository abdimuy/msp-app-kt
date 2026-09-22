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
 * (`commitEditGuard`) va PRIMERO y, si falla, LANZA [GuardiaCommitFallidoException] dentro de
 * la transacción — capturada afuera para devolver `false` (Important #4 de la ronda 1 de
 * arreglo). No basta con `return@withTransaction false` sin lanzar: eso deja el invariante "el
 * guardia revierte todo" sostenido SÓLO por el orden en que está escrito el código de hoy — una
 * escritura que alguien agregue por delante del guardia en el futuro commitearía en silencio,
 * porque Room únicamente revierte una transacción ante una excepción, nunca ante un valor de
 * retorno. Lanzar hace que el invariante se sostenga por la MECÁNICA de la transacción, no por
 * la disciplina de quien edite este archivo después.
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
                claimedAt = sale.CLAIMED_AT,
                // Los dos campos del nivel 2 tienen valor por omisión en `EstadoVentaLocal` (para
                // no tocar las pruebas del nivel 1), así que omitirlos aquí no rompe la
                // compilación: la regla simplemente dejaría de ver la cola y la marca terminal, y
                // ofrecería corregir una venta cuya corrección ya está en vuelo o ya fue cerrada
                // por el servidor. Este es el único punto donde esas dos columnas entran al
                // dominio.
                correccionRemotaPendiente = sale.CORRECCION_REMOTA_PENDIENTE,
                correccionRemotaEstado = sale.CORRECCION_REMOTA_ESTADO
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
            uploadLeaseMs = LocalSaleClaimLeases.UPLOAD_LEASE_MS,
            remoteLeaseMs = LocalSaleClaimLeases.REMOTE_LEASE_MS
        ) == 1

    override suspend fun soltar(saleId: String, claimId: String) {
        localSaleDao.releaseClaim(saleId, claimId)
    }

    // `GuardiaCommitFallidoException` es una señal interna sin información propia (no lleva
    // mensaje ni causa) — convertirla a `false` ES el manejo completo, no hay nada que loguear
    // ni una causa real que perder. Mismo criterio que otros `@Suppress("SwallowedException")`
    // deliberados del repo (p. ej. `DurableTelemetryQueue.enqueue`).
    @Suppress("SwallowedException")
    override suspend fun guardarCorreccion(
        saleId: String,
        claimId: String,
        campos: CamposVentaCorregidos,
        productos: List<LocalSaleProductEntity>,
        combos: List<LocalSaleComboEntity>
    ): Boolean = try {
        guardarCorreccionOLanzar(saleId, claimId, campos, productos, combos)
    } catch (rechazo: GuardiaCommitFallidoException) {
        false
    }

    private suspend fun guardarCorreccionOLanzar(
        saleId: String,
        claimId: String,
        campos: CamposVentaCorregidos,
        productos: List<LocalSaleProductEntity>,
        combos: List<LocalSaleComboEntity>
    ): Boolean = db.withTransaction {
        // Qué camino es este. La lectura va DENTRO de la transacción, junto al guardia, para
        // que no haya ventana entre decidir el camino y tomarlo: si la subida terminara justo
        // en medio, el guardia que se eligió ya no sería el que corresponde y devolvería 0 —
        // que es exactamente lo que debe pasar, no una escritura por el camino equivocado.
        val yaEnviada = localSaleDao.getSaleById(saleId)?.ENVIADO == true

        // El guardia va PRIMERO: si falla, LANZA — ver el comentario de clase. Mover esto al
        // final es exactamente el mutante que Task 3 pide sembrar; quitar el `throw` (dejando
        // sólo un `return` temprano) es el mutante de la ronda 1 de arreglo.
        //
        // Los dos guardias son excluyentes por `ENVIADO` (`= 0` uno, `= 1` el otro), así que
        // elegir mal no abre un agujero: abre un 0 filas y la transacción entera se revierte.
        val guardiaOk = if (yaEnviada) {
            localSaleDao.commitEditGuardEnviada(saleId, claimId) == 1
        } else {
            localSaleDao.commitEditGuard(saleId, claimId) == 1
        }
        if (!guardiaOk) {
            throw GuardiaCommitFallidoException()
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
            // Minor #2 de la ronda 1 de arreglo: a diferencia de
            // `EditLocalSaleViewModel.kt:290` (que ponía `ENVIADO = false` A
            // CIEGAS, sin ningún guardia, pudiendo pisar una venta que el
            // servidor YA tenía), este `false` es la única rama alcanzable:
            // el guardia de arriba ya exigió el valor que aquí se reescribe
            // en su propio `WHERE`, DENTRO de la misma transacción, sin
            // ninguna ventana entre leer y escribir. No es una suposición —
            // es lo que el guardia acaba de confirmar.
            //
            // Y por eso se reescribe con el MISMO valor en vez de con `false`
            // a secas: en el camino de la venta ya subida, bajarlo la
            // devolvería a la cola de alta con su `Idempotency-Key` original,
            // el servidor contestaría con la respuesta que ya tenía guardada,
            // no se actualizaría nada, y el teléfono se quedaría creyendo que
            // reenvió. Esa corrección viaja por su propia cola, más abajo.
            enviado = yaEnviada,
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

        if (yaEnviada) {
            // La marca de cola va DENTRO de la misma transacción que la corrección, no después:
            // es lo único que hace que alguien la entregue. Commitear los campos y levantar la
            // bandera por separado deja una ventana en la que el proceso puede morir con la
            // corrección ya guardada y sin nadie que la reclame — el usuario vería su venta
            // corregida en el teléfono y el servidor nunca se enteraría. El silencio es
            // exactamente el modo de falla que esta cola existe para evitar, así que o commitean
            // las dos cosas o no commitea ninguna.
            //
            // El `AND ENVIADO = 1` de su `WHERE` no puede fallar aquí: `commitEditGuardEnviada`
            // acaba de exigir ese mismo valor en esta transacción.
            localSaleDao.marcarCorreccionRemotaPendiente(saleId)
        }

        // Edit-and-retry SIN rotar la Idempotency-Key (Global Constraint del plan): sólo se
        // limpia el registro de fallo previo, para que la UI no siga mostrando un error viejo
        // tras una corrección exitosa.
        localSaleDao.clearUploadFailure(saleId)

        true
    }
}

/**
 * Señal interna de que `commitEditGuard` devolvió 0 filas — nunca sale de [RoomVentaLocalCorreccionAdapter];
 * existe únicamente para que Room revierta la transacción por MECÁNICA (una excepción), no por
 * la disciplina de quien escriba el código después. Ver el comentario de clase de
 * [RoomVentaLocalCorreccionAdapter].
 */
private class GuardiaCommitFallidoException : Exception()

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
