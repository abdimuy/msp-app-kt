package com.example.msp_app.features.sales

import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.data.models.sale.SaleWithProducts

/**
 * El único lugar donde está escrito **qué id de una venta pide cada consumidor**.
 *
 * ## Por qué existe
 *
 * `CLIENTE_ID`, `DOCTO_CC_ID` y `DOCTO_CC_ACR_ID` son tres `Int` pelados, así que el
 * compilador nunca avisó cuando uno se pasó donde iba otro — once defectos de esta
 * familia en esta migración. El arreglo de raíz (value classes, como `Money`) va en otra
 * rama porque toca todo el árbol. Mientras tanto, cada sitio de `:app` que entrega un id
 * de venta pasa por aquí, y aquí está el mapeo verificado **contra la columna por la que
 * filtra cada consulta**, no contra el nombre de la variable.
 *
 * ## El contrato de los ids, medido en las dos puntas
 *
 * En el servidor (`msp-api`) la venta tiene **un solo** id: `MSP_SALDOS_VENTAS.DOCTO_CC_ID`,
 * la PK del cargo en `DOCTOS_CC`. No existe una columna `DOCTO_CC_ACR_ID` del lado de la
 * venta. El abono apunta al cargo por `IMPORTES_DOCTOS_CC.DOCTO_CC_ACR_ID`, y el propio
 * backend une las dos tablas así (`internal/cobranza/infra/ventfb/pagos_repo.go`):
 *
 * ```sql
 * JOIN MSP_SALDOS_VENTAS s ON s.DOCTO_CC_ID = p.DOCTO_CC_ACR_ID
 * ```
 *
 * El DTO lo dice con todas sus letras: `docto_cc_acr_id` = *"ID del cargo acreditado
 * (= MSP_SALDOS_VENTAS.DOCTO_CC_ID)"* (`internal/cobranza/infra/cobranzahttp/dto_pagos.go`).
 *
 * En Room la tabla `sales` arrastra **dos** columnas, y **las dos llevan siempre el mismo
 * número**. No es una coincidencia del estado actual: es así por construcción, medido en los
 * cuatro escritores que existen y en los dos backends.
 *
 * Los cuatro escritores de `sales`: `CobranzaSyncManager.mergeVentas` y
 * `CobranzaReconciler.reconcileSaldosViaIds` escriben por `VentaDto.toEntity()`, que llena las
 * dos columnas **con el mismo campo del DTO**; `AuthViewModel` y `SalesLocalDataSource.replaceAll`
 * reescriben filas que ya estaban, preservando las dos.
 *
 * ```kotlin
 * // app/.../data/api/services/cobranza/VentaDto.kt
 * DOCTO_CC_ACR_ID = docto_cc_id,
 * DOCTO_CC_ID     = docto_cc_id,
 * ```
 *
 * Y el backend Node de antes del cutover, que es el único que alguna vez pudo dejar filas
 * distintas, tampoco podía: su consulta de ventas proyecta las dos columnas pero las trae de
 * un `INNER JOIN DOCTOS_CC ON DOCTOS_CC.DOCTO_CC_ID = M.DOCTO_CC_ACR_ID`
 * (`sys_msp_backend/src/components/ventas/queries.ts:514`, y las 7 variantes de ese archivo
 * llevan el mismo join). Eran iguales **por construcción SQL**. **Una fila legada con las dos
 * columnas divergentes es imposible**, y no hay nada que averiguar sobre eso.
 *
 * O sea: `sales.DOCTO_CC_ACR_ID` es la **PK de Room** (nombre heredado, no un segundo
 * espacio de numeración) y `sales.DOCTO_CC_ID` es **el id del cargo**, que es el que casa
 * con `Payment.DOCTO_CC_ACR_ID`. `Payment.DOCTO_CC_ID` sí es otro espacio: es el documento
 * del **abono** en Microsip (0 hasta que el pago se aplica — ver
 * `PaymentDao.updateDoctoCcId`), y nunca identifica una venta.
 *
 * **Entonces, ¿para qué sirve elegir bien si los números coinciden?** Para que el código diga
 * la verdad. Este archivo existe porque once defectos de esta rama salieron de leer el nombre
 * de una variable en vez del `WHERE` de la consulta, y una línea que nombra la columna
 * equivocada es la próxima trampa aunque hoy devuelva la fila correcta.
 *
 * Las funciones de abajo están nombradas por **el consumidor**, no por el id, justamente
 * para que nadie las elija por parecido de nombre.
 */
object SaleIdSpaces {

    /**
     * El id para las consultas que filtran **`Payment.DOCTO_CC_ACR_ID`** — los pagos de la
     * venta: `PaymentDao.getPaymentsBySaleId`, `getSuggestedAmountsBySaleId`,
     * `countPagosDesde`, `deleteByDoctoCcAcrId`.
     *
     * Es `sales.DOCTO_CC_ID`, por el join del backend citado arriba. **No** es la PK de Room.
     * Cambiarlo a `DOCTO_CC_ACR_ID` no rompe ninguna fila —los dos números coinciden siempre—
     * pero deja escrito un nombre de columna que contradice el contrato del servidor, que es
     * justo el error del que salieron los once defectos de esta familia.
     */
    fun forSalePayments(sale: Sale): Int = sale.DOCTO_CC_ID

    /**
     * El mismo id, cuando lo que se tiene en la mano es un **pago** y no la venta
     * — `PaymentsViewModel.getGroupedPaymentsBySaleId` (los hermanos del abono) y
     * `PaymentTicketScreen` (los pagos de la venta del recibo).
     *
     * Es `Payment.DOCTO_CC_ACR_ID`, que es el **cargo acreditado** y por tanto el
     * mismo número que `sales.DOCTO_CC_ID` — el join del backend
     * (`s.DOCTO_CC_ID = p.DOCTO_CC_ACR_ID`) es exactamente esta igualdad. **No**
     * es `Payment.DOCTO_CC_ID`, que es el documento del abono en Microsip (0
     * hasta que el pago se aplica) y nunca identifica una venta.
     *
     * Existe porque tiene dos call sites vivos, no por simetría: la sobrecarga
     * `forSaleRow(Sale)` se borró en el Arreglo A justamente por no tenerlos.
     */
    fun forSalePayments(payment: Payment): Int = payment.DOCTO_CC_ACR_ID

    /**
     * El id para todo lo que direcciona **la garantía de la venta** —
     * `GuaranteeDao.getGuaranteeByDoctoCcId` (que filtra `garantias.DOCTO_CC_ID`)
     * y la ruta `guarantee/{saleId}`, cuyo argumento el Arreglo A fijó como el
     * **crédito** y no la PK.
     *
     * Es `sales.DOCTO_CC_ID`. El Arreglo A dejó la mitad de abajo cerrada
     * —`GuaranteesScreen` resuelve la venta con `loadSaleDetailsByCreditId`— pero
     * **la mitad de arriba seguía suelta**: `GuaranteeSection` construía la ruta
     * con `sale.DOCTO_CC_ID` crudo, en dos sitios, y el guardarraíl de entonces
     * miraba cinco substrings escritos a mano, así que no los veía. Ese es el
     * sexto call site que el Arreglo B destapó al enumerar por descubrimiento.
     */
    fun forGuarantee(sale: Sale): Int = sale.DOCTO_CC_ID

    /**
     * El id para las consultas que filtran **`overdue_payments_view.DOCTO_CC_ID`** —
     * `PaymentDao.getOverduePaymentBySaleId`.
     *
     * La vista proyecta `s.DOCTO_CC_ID` de `sales` (`OVERDUE_PAYMENTS_VIEW_SQL`), así que
     * pide el id del cargo, **no** el argumento de la ruta: desde la Task 21 `SaleDetails`
     * se abre con `DOCTO_CC_ACR_ID`, y ese argumento no es el que esta vista indexa.
     */
    fun forOverdueView(sale: Sale): Int = sale.DOCTO_CC_ID

    /**
     * El id para lo que direcciona **la fila de `sales` por su PK** — `SaleDao.getById`,
     * `updateTotal`, `updateTemporaryCollectionDate`, y la ruta `Screen.SaleDetails`, que
     * resuelve su argumento con `getById`.
     *
     * Toma la proyección con productos porque es la que devuelve `SaleDao.getByClientId` y la
     * que pinta "otras ventas del cliente".
     */
    fun forSaleRow(sale: SaleWithProducts): Int = sale.DOCTO_CC_ACR_ID

    /**
     * La misma PK, desde la venta ya cargada.
     *
     * El Arreglo A borró esta sobrecarga por API muerta y dejó escrito el
     * criterio para reponerla: *"si algún día hace falta, se agrega con su call
     * site"*. Ahora hace falta y tiene dos: `DestinosDeCobranza.abonoDeUnaVenta`
     * y `.visitaDeUnaVenta`, que direccionan `pagos/abono/{ventaId}` y
     * `visitas/registrar/{clienteId}?ventaId=` — las dos rutas resuelven por la
     * PK de `sales`, y del lado de visitas lo confirma el adaptador que llena el
     * contexto (`RoomContextoDeVisitaAdapter:43`, `ventaId = it.DOCTO_CC_ACR_ID`)
     * y el que escribe la fila (`VisitFactory:63`,
     * `IMPTE_DOCTO_CC_ID = sale.DOCTO_CC_ACR_ID`).
     */
    fun forSaleRow(sale: Sale): Int = sale.DOCTO_CC_ACR_ID
}
