package com.example.msp_app.features.sales

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
 * En Room la tabla `sales` arrastra **dos** columnas, y el único escritor vivo
 * (`VentaDto.toEntity()`, usado por `CobranzaSyncManager.mergeVentas` y por
 * `CobranzaReconciler`) las llena **con el mismo campo del DTO**:
 *
 * ```kotlin
 * DOCTO_CC_ACR_ID = docto_cc_id,
 * DOCTO_CC_ID     = docto_cc_id,
 * ```
 *
 * O sea: `sales.DOCTO_CC_ACR_ID` es la **PK de Room** (nombre heredado, no un segundo
 * espacio de numeración) y `sales.DOCTO_CC_ID` es **el id del cargo**, que es el que casa
 * con `Payment.DOCTO_CC_ACR_ID`. `Payment.DOCTO_CC_ID` sí es otro espacio: es el documento
 * del **abono** en Microsip (0 hasta que el pago se aplica — ver
 * `PaymentDao.updateDoctoCcId`), y nunca identifica una venta.
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
     * Es `sales.DOCTO_CC_ID`, por el join del backend citado arriba. **No** es la PK de
     * Room: cambiarlo a `DOCTO_CC_ACR_ID` hoy no rompe nada porque el sync escribe el mismo
     * número en las dos columnas, pero contradice el contrato del servidor, que es lo único
     * que sigue siendo cierto si algún día dejan de coincidir.
     */
    fun forSalePayments(sale: Sale): Int = sale.DOCTO_CC_ID

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
     */
    fun forSaleRow(sale: Sale): Int = sale.DOCTO_CC_ACR_ID

    /**
     * [forSaleRow] para la proyección con productos, que es la que devuelve
     * `SaleDao.getByClientId` y la que pinta "otras ventas del cliente". Es la MISMA columna;
     * la sobrecarga existe para no obligar a la pantalla a mapear a [Sale] solo para navegar.
     */
    fun forSaleRow(sale: SaleWithProducts): Int = sale.DOCTO_CC_ACR_ID
}
