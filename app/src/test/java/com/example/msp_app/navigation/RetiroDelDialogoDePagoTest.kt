package com.example.msp_app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **`NewPaymentDialog` está retirado, y esto lo mide.**
 *
 * ## Por qué el diálogo del dinero tenía que morir en esta tarea
 *
 * Los dos capturadores escribían la MISMA tabla por el MISMO `PaymentFactory`,
 * pero **divergían en las dos direcciones**, y el legado perdía en las tres que
 * son dinero:
 *
 * - **idempotencia**: `remember { UUID.randomUUID() }` muere al rotar y al morir
 *   el proceso, y ahí el reintento entra al servidor como un cobro nuevo. Es el
 *   defecto que el 2026-08-17 costó 3 cobros duplicados y $600. La pantalla
 *   nueva la persiste en el `SavedStateHandle`, con un guard `yaSeEncolo`.
 * - **atomicidad**: `PaymentsLocalDataSource.saveAndEnqueue` lleva un
 *   `@Transaction` **inerte fuera de un `@Dao`**, así que el insert del pago y el
 *   descuento del saldo eran dos escrituras sueltas; un fallo entre las dos
 *   dejaba el pago contado con el saldo intacto. La pantalla nueva escribe
 *   dentro de `db.withTransaction`.
 * - **errores**: `e.printStackTrace()` contra telemetría con código grepeable.
 *
 * Y la razón de cerrarlo en la 21 y no después: la Task 22 cuelga la foto de la
 * pantalla de la Task 18 **nada más**. Con los dos vivos, que un cobro llevara
 * comprobante habría dependido de qué botón tocó el cobrador.
 *
 * ## El diff de afordancias se hizo ANTES de borrar
 *
 * Cada cosa que el cobrador podía hacer en el diálogo se buscó en
 * `RegistrarAbonoScreen`: cliente, saldo, productos, forma de cobro, captura del
 * monto, montos sugeridos, validación, confirmación en dos pasos, ticket al
 * terminar y captura de coordenadas. Está enumerado en
 * `task-21-fix-1-report.md`; lo único que no viaja es el aviso informativo "el
 * pago es menor a la parcialidad acordada", anotado ahí como pérdida.
 *
 * ## Cómo se prueba una ausencia
 *
 * Con [EscanerDeFuentes], que desde esta ronda recorre `:app` **y** cada
 * `:feature:*` / `:core:*` — la versión anterior solo miraba
 * `:app/src/main/java` y un sobreviviente en el feature no la habría puesto
 * roja. Cada cero viene con su control positivo: el mismo método, sobre el mismo
 * árbol, encontrando un símbolo hermano que sí existe.
 */
class RetiroDelDialogoDePagoTest {

    private val escaner = EscanerDeFuentes()

    /**
     * **Nada enruta al diálogo de pago, y nada lo compone.** Ni el archivo
     * queda: no es "sin llamadas", es que no existe. Se comprueban también las
     * dos piezas que vivían dentro de él (`ConfirmPaymentDialog` y
     * `rememberPaymentIdempotencyKey`), porque un `import` sobreviviente a
     * cualquiera de las dos sería el diálogo entrando por la puerta de atrás.
     */
    @Test
    fun `nada en el repo menciona NewPaymentDialog`() {
        assertEquals(emptyList<String>(), escaner.archivosQueMencionan("NewPaymentDialog"))
        assertEquals(emptyList<String>(), escaner.archivosQueMencionan("ConfirmPaymentDialog"))
        assertEquals(
            emptyList<String>(),
            escaner.archivosQueMencionan("rememberPaymentIdempotencyKey")
        )
        assertFalse(
            escaner.existe(
                "app/src/main/java/com/example/msp_app/features/payments/components/" +
                    "newpaymentdialog/NewPaymentDialog.kt"
            )
        )
    }

    /**
     * **Control positivo del test de arriba.** El mismo escáner encuentra la
     * condonación —el otro diálogo del mismo bloque de acciones, que este plan
     * NO toca, y que sigue siendo la única puerta de la condonación— en al menos
     * dos archivos: su definición y su llamador. O sea que el cero de arriba es
     * una ausencia medida, no un método ciego.
     */
    @Test
    fun `control positivo, el escaner SI encuentra el dialogo de condonacion`() {
        val condonacion = escaner.archivosQueMencionan("NewForgivenessDialog")
        assertTrue("el escáner no encontró la condonación: $condonacion", condonacion.size >= 2)
    }

    /**
     * **El reemplazo está cableado, no solo el borrado hecho.** Los dos
     * llamadores del diálogo —`SaleActionsSection` y `SaleItem`, los dos dentro
     * de `SaleDetailsScreen`— van ahora al destino de la Task 18 por
     * [DestinosDeCobranza], que es donde vive la regla del origen.
     *
     * Sin esto, borrar el diálogo habría dejado dos botones muertos y todos los
     * demás tests en verde.
     */
    @Test
    fun `los dos llamadores navegan al destino de abono`() {
        val llamadores = escaner.archivosQueMencionan("abonoDeUnaVenta")
        assertTrue(
            "SaleActionsSection no navega al abono: $llamadores",
            llamadores.any { it.endsWith("SaleActionsSection.kt") }
        )
        assertTrue(
            "SaleItem no navega al abono: $llamadores",
            llamadores.any { it.endsWith("SaleItem.kt") }
        )
    }

    /**
     * **El control positivo del escáner ampliado**, y a la vez la prueba de que
     * la ampliación sirve: `RegistrarAbonoScreen` —la pantalla que reemplaza al
     * diálogo— vive en `:feature:pagos`, o sea **fuera** de
     * `:app/src/main/java`. El escáner viejo no la habría visto; éste sí.
     *
     * Sin esta afirmación, "nada menciona `NewPaymentDialog`" podría estar
     * midiendo un árbol que no incluye el feature entero.
     */
    @Test
    fun `control positivo del alcance, el escaner SI ve dentro de feature pagos`() {
        val pantallaNueva = escaner.archivosQueMencionan("RegistrarAbonoScreen")
        assertTrue(
            "el escáner no encontró la pantalla nueva: $pantallaNueva",
            pantallaNueva.any { it.contains("feature/pagos") }
        )
    }

    /**
     * **El ticket de pago legado sigue vivo y no se tocó.** Lo usan la
     * condonación (`NewForgivenessDialog`) y la tarjeta de pago (`PaymentCard`),
     * ninguna de las dos en alcance. Es la contraparte del retiro: se fue el
     * capturador duplicado, no la ruta que otros siguen usando.
     */
    @Test
    fun `el ticket de pago legado sigue en el codigo`() {
        val ticket = escaner.archivosQueMencionan("payment_ticket/")
        assertTrue("el escáner no encontró el ticket de pago: $ticket", ticket.isNotEmpty())
    }

    /**
     * **`PaymentFactory` no se fue con el diálogo.** Es la escritura de dinero
     * que ya corre en producción y que el camino nuevo CONSUME tal cual: tenerla
     * dos veces garantizaría que las dos versiones se despeguen.
     */
    @Test
    fun `la fabrica de pagos sigue viva y la usa el camino nuevo`() {
        val usos = escaner.archivosQueMencionan("PaymentFactory")
        assertTrue(
            "el adaptador del abono ya no usa PaymentFactory: $usos",
            usos.any { it.endsWith("RegistroDeAbonoAdapter.kt") }
        )
    }
}
