package com.example.msp_app.navigation

import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.data.models.sale.SaleWithProducts
import com.example.msp_app.feature.pagos.ui.PagosRutas
import com.example.msp_app.feature.visitas.ui.VisitasRutas

/**
 * **La regla del origen, hecha código** (Task 21).
 *
 * > Desde la **lista** y el **mapa** se entra al **cliente**: ahí el contexto es
 * > la persona, y una puerta puede tener dos cuentas.
 * > Desde un **pago** o un **recibo existente** se entra directo a **su venta**:
 * > ahí el contexto ya es esa venta, y un abono nombra una sola cuenta.
 *
 * Existe como objeto y no como literales sueltos por una razón concreta: los
 * puntos de entrada viven en cinco archivos distintos (`Home`, `RouteMapScreen`,
 * `PaymentItem`, `PaymentCard`, `SaleActionsSection`, `SaleItem`) y la regla es
 * una sola. Con la regla repartida, el día que alguien cambie un destino solo lo
 * cambia en el archivo que está tocando y los otros cuatro se quedan diciendo
 * otra cosa — que es exactamente el defecto que este plan encontró en las dos
 * listas de cobranza.
 *
 * Son funciones puras sobre los modelos que el punto de entrada ya tiene en la
 * mano: se prueban sin `NavController`, sin Compose y sin Robolectric, y el test
 * afirma **el destino y sus argumentos**, no que "se navegó".
 *
 * ## Los dos espacios de id, que no son el mismo
 *
 * `DOCTO_CC_ACR_ID` es la llave primaria de `sales` y es la que entienden
 * `pagos/venta/{ventaId}`, `pagos/abono/{ventaId}` y `SaleDao.getById`.
 * `DOCTO_CC_ID` es el **crédito** y es la llave por la que están indexadas las
 * garantías. Confundirlos ya costó un defecto de producción en este mismo plan
 * (commit `721c5551`), así que cada función de aquí dice cuál usa y por qué.
 */
object DestinosDeCobranza {

    /**
     * Un **pago** → **su venta**. El pago nombra una sola cuenta
     * (`DOCTO_CC_ACR_ID`), así que no hay nada que elegir.
     */
    fun ventaDeUnPago(payment: Payment): String = PagosRutas.detalleVenta(payment.DOCTO_CC_ACR_ID)

    /**
     * El **cliente** de un pago — el "ver cliente" del menú, que es como se
     * entra a la persona desde el mapa de rutas y desde los pagos del día.
     *
     * Antes ese mismo menú abría el detalle de **venta** legado: decía "cliente"
     * y llevaba a una cuenta. Ahora dice lo que hace.
     */
    fun clienteDeUnPago(payment: Payment): String = PagosRutas.detalleCliente(payment.CLIENTE_ID)

    /**
     * Un **recibo existente** → **su venta**. Es la fila del historial de pagos:
     * el recibo ya está emitido y lo único que falta saber es de cuál cuenta.
     */
    fun ventaDeUnRecibo(payment: Payment): String = PagosRutas.detalleVenta(payment.DOCTO_CC_ACR_ID)

    /**
     * Registrar visita desde una venta abierta: se visita la **puerta**
     * (`CLIENTE_ID`) con esa cuenta como contexto (`DOCTO_CC_ACR_ID`).
     */
    fun visitaDeUnaVenta(sale: Sale): String =
        VisitasRutas.registrar(clienteId = sale.CLIENTE_ID, ventaId = sale.DOCTO_CC_ACR_ID)

    /** Igual que la anterior, para la tarjeta de venta de las listas. */
    fun visitaDeUnaVenta(sale: SaleWithProducts): String =
        VisitasRutas.registrar(clienteId = sale.CLIENTE_ID, ventaId = sale.DOCTO_CC_ACR_ID)
}
