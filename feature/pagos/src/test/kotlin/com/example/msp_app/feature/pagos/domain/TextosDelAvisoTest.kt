package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las cadenas de [TextosDelAviso], fijadas por reflexión — mismo molde que
 * `TextosCorreccionTest` en `:feature:ventaCorreccion`.
 *
 * Dos cosas distintas se miden aquí, y hacen falta las dos:
 *
 *  - **la forma** (mayúscula inicial, sin punto final, sin "ciclo") se barre
 *    sobre los campos `String` que el objeto tenga, así que una cadena nueva
 *    entra a la compuerta el día que se escribe sin que nadie se acuerde;
 *  - **el texto exacto** va a mano, porque esta prueba existe justo para
 *    pescar la paráfrasis "casi igual" — y estos textos son los que el dueño
 *    aprobó palabra por palabra.
 *
 * **No se mide "2 a 4 palabras".** Estos avisos no son botones: "Los pagos van
 * de 50 en 50" tiene siete palabras y es exactamente lo que tiene que decir. Un
 * criterio que hay que aflojar el primer día no es un criterio — el mismo
 * razonamiento que `CadaTextoDeUsuarioEmpiezaEnMayusculaTest` ya dejó escrito.
 *
 * ## Los mensajes dicen el HECHO, no un regaño
 *
 * Ninguno pregunta "¿estás seguro?" ni califica el monto de "inusual": esas
 * frases piden dudar sin dar con qué. `ningun mensaje regaña` lo mide.
 */
class TextosDelAvisoTest {

    private val cadenasFijas: List<Pair<String, String>> =
        TextosDelAviso::class.java.declaredFields
            .filter { it.type == String::class.java }
            .onEach { it.isAccessible = true }
            .map { it.name to (it.get(null) as String) }

    /** Las fijas más las armadas, que son las que llevan cifras. */
    private val todosLosMensajes: List<Pair<String, String>> = cadenasFijas + listOf(
        "sonCuotas" to TextosDelAviso.sonCuotas(4, dinero("250")),
        "sueleDar" to TextosDelAviso.sueleDar(dinero("200")),
        "sueleDarYEstoEs" to TextosDelAviso.sueleDarYEstoEs(dinero("200"), 30)
    )

    @Test
    fun `hay exactamente dos cadenas fijas`() {
        assertEquals(2, cadenasFijas.size)
    }

    @Test
    fun `cada mensaje arranca con mayuscula`() {
        todosLosMensajes.forEach { (nombre, texto) ->
            assertTrue("$nombre ('$texto') no arranca con mayúscula", texto.first().isUpperCase())
        }
    }

    @Test
    fun `ningun mensaje termina en punto`() {
        todosLosMensajes.forEach { (nombre, texto) ->
            assertFalse("$nombre ('$texto') termina en punto", texto.endsWith("."))
        }
    }

    @Test
    fun `ningun mensaje dice ciclo`() {
        todosLosMensajes.forEach { (nombre, texto) ->
            assertFalse(
                "$nombre ('$texto') dice 'ciclo' — la UI dice 'semana'",
                texto.contains("ciclo", ignoreCase = true)
            )
        }
    }

    @Test
    fun `ningun mensaje regana al cobrador`() {
        val frasesQueNoInforman = listOf("seguro", "inusual", "cuidado", "error", "verifica")
        todosLosMensajes.forEach { (nombre, texto) ->
            frasesQueNoInforman.forEach { palabra ->
                assertFalse(
                    "$nombre ('$texto') contiene '$palabra': el aviso dice el hecho, no regaña",
                    texto.contains(palabra, ignoreCase = true)
                )
            }
        }
    }

    @Test
    fun `los textos exactos, palabra por palabra`() {
        assertEquals(
            mapOf(
                "DE_CINCUENTA_EN_CINCUENTA" to "Los pagos van de 50 en 50",
                "NADIE_HA_PAGADO_TANTO" to "Nadie en la ruta ha pagado tanto",
                "sonCuotas" to "Son 4 cuotas de \$250",
                "sueleDar" to "Suele dar \$200",
                "sueleDarYEstoEs" to "Suele dar \$200, esto es 30 veces más"
            ),
            todosLosMensajes.toMap()
        )
    }

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))
}
