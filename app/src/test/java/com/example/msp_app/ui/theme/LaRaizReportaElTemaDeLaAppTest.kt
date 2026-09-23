package com.example.msp_app.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **El composition root tiene que reportar el tema de la app.**
 *
 * `MspTheme` sigue a `LocalAppDarkTheme` (ver
 * `MspThemeSigueAlTemaDeLaAppTest`), pero eso solo sirve si alguien lo provee, y
 * el único que puede es `MainActivity`: `ThemeController` vive en `:app` y los
 * módulos de feature no lo ven. **Esa provisión es una línea**, y una línea que
 * falta no la nota nadie — es literalmente la forma del defecto que originó todo
 * este arreglo: `:app` nunca proveía `MspTheme` y el contrato vivía en un KDoc.
 *
 * Es un escaneo de fuente y no una composición **por elección de alcance**, no
 * porque componerla sea imposible: hoy ningún test de la JVM monta `MainActivity`
 * (levanta Firebase, Hilt y los observadores de sync) y nadie ha intentado
 * hacerlo. La medida en vidrio existe y es
 * `:app:connectedDevlocalDebugAndroidTest` — ver la compuerta manual 0.4 de
 * `DEPLOY.md`. Lo que este escaneo prueba sin montar nada es que la línea siga
 * ahí **y diga lo que tiene que decir**, que es más que lo que había.
 *
 * Control positivo incluido: se busca con el MISMO método un local hermano que
 * también tiene que estar (`LocalFontSizeLevel`), así un `File` mal armado o un
 * archivo movido se ve como lo que es y no como un verde.
 */
class LaRaizReportaElTemaDeLaAppTest {

    private val raiz: File =
        if (File("src/main/java").isDirectory) File(".") else File("app")

    private val mainActivity: String =
        File(raiz, "src/main/java/com/example/msp_app/MainActivity.kt").readText()

    @Test
    fun `MainActivity provee LocalAppDarkTheme desde el tema de la app`() {
        assertTrue(
            "control positivo: no se está leyendo MainActivity.kt",
            PROVEE_TAMANO_DE_LETRA.containsMatchIn(mainActivity)
        )
        assertTrue(
            "MainActivity dejó de proveer LocalAppDarkTheme: las pantallas Msp que se " +
                "envuelven solas vuelven a seguir al tema del SISTEMA en vez del de la app " +
                "— pantalla blanca de noche y barra de estado ilegible",
            PROVEE_TEMA.containsMatchIn(mainActivity)
        )
    }

    private companion object {
        /**
         * **Anclada por la derecha con `\s*,`.** Sin ese ancla, `containsMatchIn`
         * aceptaba cualquier cosa pegada al identificador. Medido: sembrando
         * `LocalAppDarkTheme provides ThemeController.isDarkMode.not(),` —el tema de
         * la app INVERTIDO, que es el defecto original pero peor— la suite entera de
         * `:app` pasaba con **1569 pruebas verdes**. Con el ancla la misma semilla no
         * matchea y esta prueba se pone roja: el argumento tiene que **terminar** en
         * `isDarkMode`, y lo que sigue en un `CompositionLocalProvider` es la coma.
         *
         * Lo que el ancla NO cubre —y un escaneo de fuente no puede— es mover la
         * línea entera a un `CompositionLocalProvider` muerto. Esa forma no se ha
         * visto; la del booleano equivocado sí.
         */
        val PROVEE_TEMA =
            Regex("""LocalAppDarkTheme\s+provides\s+ThemeController\.isDarkMode\s*,""")
        val PROVEE_TAMANO_DE_LETRA = Regex("""LocalFontSizeLevel\s+provides\s+""")
    }
}
