package com.example.msp_app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **La compuerta del defecto que el dueño vio en vidrio.**
 *
 * Tocó el sol/luna en el detalle de cliente y el tema cambió **en seco**. La
 * lista de clientes, con el MISMO `MspThemeToggle`, anima una reveal circular.
 * El mecanismo era uno solo (`MspThemeRevealHost`, `:core:designsystem`) y lo
 * instalaban **dos archivos de todo el repo**: `ThemeRevealRoot.kt:88` del
 * reporte de cobranza y `ListaDeClientesScreen.kt:106`. Las otras ocho pantallas
 * montaban `MspTheme { }` a secas.
 *
 * Su regla, textual: *"todas las pantallas que estamos haciendo nuevas sí o sí
 * las debe tener"*.
 *
 * ## Por qué esto es un barrido de fuentes y no una lista
 *
 * Porque la lista es justamente lo que falló. `MspThemeRevealHost` ya existía,
 * su KDoc ya explicaba que "el mismo control se sienta distinto según la
 * pantalla" no es aceptable, y aun así ocho pantallas nacieron sin él: una nota
 * en un KDoc no impidió la novena. Este test deriva el conjunto de las fuentes,
 * así que **la pantalla número diez entra al escaneo el día que se escribe** —
 * si nace con `MspTheme` pelado, se pone rojo ese mismo día.
 *
 * Mismo molde que [CadaPantallaSeAlcanzaDesdeElGrafoTest] y
 * [CadaPantallaMspProveeSuTemaTest], con el parser compartido de
 * `DeclaracionesDeFuente.kt` para que las tres decidan por **función** y no por
 * archivo — ver ahí el verde falso que costó decidir por archivo.
 *
 * ## Qué cuenta como "anima"
 *
 * Instalar [com.example.msp_app.core.designsystem.component.MspThemeRevealHost],
 * directa o **transitivamente**. La transitividad no es lujo: el reporte de
 * cobranza lo instala a dos saltos (`CollectionReportScreen` → `ThemeRevealRoot`
 * → `MspThemeRevealHost`) y sin cierre transitivo este test lo marcaría en rojo
 * estando bien — medido, igual que en la red hermana.
 *
 * El host **respeta movimiento reducido por su cuenta** (principio 13): con
 * `reducedMotion = true` hace un `return` temprano que ni instala el controller
 * ni compone `Animatable`/`GraphicsLayer`/`toImageBitmap`. Por eso este test
 * exige el host y no exige una animación: la animación es condicional, el
 * mecanismo no.
 *
 * ## Las DOS exclusiones, escritas y no omitidas
 *
 * Una exclusión acá es una decisión con su razón, no un nombre que alguien
 * agregó para que el test dejara de molestar. Son dos y ninguna es de cobranza.
 *
 * **[VERSION_BLOCKED]** — `VersionBlockedScreen`, de `:core:appgate`.
 *
 *  1. **No es una de las pantallas nuevas.** La regla del dueño habla del
 *     rediseño de cobranza; ésta es la de bloqueo por versión mínima, anterior.
 *  2. **No puede cambiar de tema y nunca va a poder.** Bloquea la app entera, se
 *     llega con `popUpTo(0) { inclusive = true }`, no tiene cajón, no tiene
 *     salida y no pinta ningún `MspThemeToggle`. Instalarle el host sería montar
 *     un `GraphicsLayer` por frame para un flip que nada puede pedir.
 *  3. **`:core:appgate` no tiene puerto de tema** y dárselo para esto sería
 *     arquitectura nueva al servicio de un lambda inerte.
 *
 * **[CONFIGURACION]** — `ConfiguracionScreen`, de `:feature:configuracion`. Ésta
 * es la interesante, porque sí cambia el tema: es **la dueña del tema real** y la
 * única pantalla con los TRES modos (claro / oscuro / automático).
 *
 *  1. **No cambia el tema con un `MspThemeToggle`**, que es el control que
 *     reporta su centro en pantalla por `LocalThemeReveal` y pide la reveal. Un
 *     segmentado de tres opciones no tiene "el punto desde el que crece el
 *     círculo", y con tres estados la reveal ni siquiera está definida: desde
 *     *Automático* no se sabe hacia qué se anima.
 *  2. Instalarle el host hoy, sin cablear eso, sería la forma que miente
 *     (principio 2): el mecanismo puesto y nada que lo dispare.
 *  3. Tampoco es una pantalla del rediseño.
 *
 * Ésta es la exclusión que vale la pena reabrir: si algún día el segmentado
 * aprende a pedir la reveal desde la opción que se tocó, el nombre se borra de
 * [EXCLUIDAS] y este test lo cobra solo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class CadaPantallaConTemaAnimaElCambioTest {

    private val escaner = EscanerDeFuentes()

    @Test
    fun `toda pantalla que provee el tema lo cambia con la reveal circular`() {
        val pantallas = pantallasQueProveenElTema()

        // Control positivo: una ausencia no vale hasta probar que el método
        // habría encontrado la cosa. Si el barrido no ve las pantallas que SÍ
        // proveen el tema, tampoco vería la que lo provee mal.
        assertTrue(
            "el barrido no encontró ninguna pantalla que provea el tema: no probaría nada",
            pantallas.isNotEmpty()
        )
        assertTrue(
            "el barrido no vio las pantallas de cobranza (${pantallas.map { it.nombre }.sorted()}): " +
                "no probaría nada",
            pantallas.map { it.nombre }.containsAll(PANTALLAS_DE_COBRANZA)
        )

        val enSeco = pantallas
            .filterNot { it.nombre in EXCLUIDAS }
            .filterNot(::animaElCambio)
            .map { it.nombre }
            .toSortedSet()
        assertEquals(
            "estas pantallas montan MspTheme a secas: su sol/luna cambia el tema en seco " +
                "mientras el de la lista anima una reveal circular, y el mismo control no " +
                "puede sentirse distinto en dos pantallas de la misma app. Envolvelas con " +
                "MspThemeRevealHost — ver ListaDeClientesScreen",
            emptySet<String>(),
            enSeco
        )
    }

    /**
     * El control positivo de arriba sirve si el barrido de verdad puede ponerse
     * rojo. Esto lo prueba desde el otro lado: [SIN_REVEAL] es el texto de una
     * pantalla escrita como estaban las ocho antes de este arreglo, y el mismo
     * criterio que usa el test tiene que rechazarla.
     *
     * Sin esto, un [animaElCambio] que devolviera `true` siempre —por un regex
     * que dejó de coincidir, por ejemplo— daría verde para siempre y nadie se
     * enteraría.
     */
    @Test
    fun `el criterio rechaza una pantalla escrita como estaban las ocho`() {
        val enSeco = Declaracion("PantallaDePruebaScreen", SIN_REVEAL)
        val animada = Declaracion("PantallaDePruebaScreen", CON_REVEAL)
        assertEquals(
            "una pantalla con MspTheme pelado tiene que dar falso",
            false,
            animaElCambio(enSeco)
        )
        assertEquals(
            "una pantalla con el host tiene que dar verdadero",
            true,
            animaElCambio(animada)
        )
    }

    // -----------------------------------------------------------------------

    /** Las pantallas de producción que envuelven su contenido en `MspTheme`. */
    private fun pantallasQueProveenElTema(): List<Declaracion> = escaner.archivos
        .flatMap { declaracionesDe(codigoDe(it)) }
        .filter { ES_PANTALLA.matches(it.nombre) }
        .filter { INVOCA_TEMA.containsMatchIn(it.texto) || llamaAUnAnimador(it) }

    private fun animaElCambio(pantalla: Declaracion): Boolean =
        INSTALA_EL_HOST.containsMatchIn(pantalla.texto) || llamaAUnAnimador(pantalla)

    private fun llamaAUnAnimador(pantalla: Declaracion): Boolean =
        (animadores - pantalla.nombre).any { invocacionDe(it).containsMatchIn(pantalla.texto) }

    /**
     * Cierre transitivo de los envoltorios que terminan instalando el host. Se
     * calcula igual que el de proveedores de tema de la red hermana: se parte de
     * los composables **con ranura de contenido** —una hoja sin ranura no puede
     * envolver a nadie— y se crece hasta que deja de crecer.
     */
    private val animadores: Set<String> by lazy {
        val candidatos = escaner.archivos
            .flatMap { declaracionesDe(codigoDe(it)) }
            .filter { RANURA.containsMatchIn(it.texto) }
        val encontrados = mutableSetOf<String>()
        var crecio = true
        while (crecio) {
            val nuevos = candidatos.filter { it.nombre !in encontrados }.filter { candidato ->
                INSTALA_EL_HOST.containsMatchIn(candidato.texto) ||
                    (encontrados - candidato.nombre).any {
                        invocacionDe(it).containsMatchIn(candidato.texto)
                    }
            }
            encontrados += nuevos.map { it.nombre }
            crecio = nuevos.isNotEmpty()
        }
        encontrados
    }

    private companion object {

        /** Ver el KDoc de la clase: bloquea la app, no tiene ni puede tener sol/luna. */
        const val VERSION_BLOCKED = "VersionBlockedScreen"

        /** Ver el KDoc de la clase: cambia el tema, pero con un segmentado de tres modos. */
        const val CONFIGURACION = "ConfiguracionScreen"

        val EXCLUIDAS = setOf(VERSION_BLOCKED, CONFIGURACION)

        /**
         * Control positivo: las nueve del grafo de cobranza. No es la lista que
         * gobierna el test —ésa sale del barrido— sino la prueba de que el
         * barrido ve algo real. Se mantiene igual que en la red hermana, que
         * además la ata al número de destinos que `destinosDeCobranza` registra.
         */
        val PANTALLAS_DE_COBRANZA = setOf(
            "ListaDeClientesScreen",
            "DetalleClienteScreen",
            "BitacoraScreen",
            "DetalleVentaScreen",
            "RegistrarAbonoScreen",
            "TicketDePagoScreen",
            "RegistrarVisitaScreen",
            "TicketDeVisitaScreen",
            "DescargaDelDictadoScreen",
            "UbicacionDelClienteScreen"
        )

        /** `MspThemeRevealHost(` — la instalación del mecanismo. */
        val INSTALA_EL_HOST = Regex("""(?<![.A-Za-z0-9_])MspThemeRevealHost\s*[({]""")

        /** Una pantalla como estaban las ocho: el tema sin el host. */
        val SIN_REVEAL = """
            fun PantallaDePruebaScreen(viewModel: V) {
                MspTheme {
                    PantallaDePruebaContent(state = viewModel.state)
                }
            }
        """.trimIndent()

        /** La misma pantalla, arreglada. */
        val CON_REVEAL = """
            fun PantallaDePruebaScreen(viewModel: V) {
                MspThemeRevealHost(
                    onToggleTheme = viewModel::alternarTema,
                    reducedMotion = reduceMotion,
                    tema = { animateColors, contenido ->
                        MspTheme(animateColors = animateColors, content = contenido)
                    }
                ) {
                    PantallaDePruebaContent(state = viewModel.state)
                }
            }
        """.trimIndent()
    }
}
