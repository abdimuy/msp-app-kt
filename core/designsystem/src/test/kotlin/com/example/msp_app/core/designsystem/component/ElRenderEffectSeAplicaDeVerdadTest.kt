package com.example.msp_app.core.designsystem.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.testing.RobolectricTestBase
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **¿El renderizador de los goldens aplica `RenderEffect` de verdad?**
 *
 * Sí. Medido, no supuesto — y por eso esta prueba existe y se commitea.
 *
 * ## Por qué hacía falta preguntarlo
 *
 * Desde el rediseño de la barra de acciones, el desenfoque de lo que pasa por
 * detrás (`MspBackdrop`) es una capa real del producto. Los goldens de
 * `:feature:pagos` y `:feature:visitas` corren bajo Robolectric con
 * `@Config(sdk = [33])`, o sea **por encima del API 31** que `RenderEffect`
 * exige. Pero "el SDK alcanza" no es lo mismo que "el renderizador lo aplica":
 * si Robolectric lo ignorara, los goldens enseñarían sólo el degradado, el
 * desenfoque **no estaría probado por nada**, y nadie se enteraría hasta verlo
 * —o no verlo— en un teléfono.
 *
 * ## Qué se midió
 *
 * La misma caja, con y sin `Modifier.blur`, sobre un canto duro negro/blanco.
 * Sin desenfoque el renglón central salta de 0 a 255 de un píxel al siguiente;
 * con desenfoque sube por una rampa. Los valores de la corrida que cerró la
 * pregunta:
 *
 * ```
 * sin:  0,   0,   0,   0, 255, 255, 255, 255
 * con: 76,  89, 103, 117, 131, 145, 159, 172
 * ```
 *
 * ## Por qué se toca `roborazzi.test.record`
 *
 * Porque `captureRoboImage` **sólo escribe el archivo en modo grabación**: bajo
 * `testDebugUnitTest` a secas no deja nada en disco y lo que se leería es
 * `null`. Y la captura de Roborazzi es el único camino que rasteriza por el
 * mismo sitio que los goldens — dibujar la vista a un `Bitmap` a mano usa un
 * `Canvas` de software, que ignora `RenderEffect` y daría un falso negativo.
 *
 * La propiedad se restaura en [restauraElModo] pase lo que pase, y los archivos
 * salen a temporales, **nunca a `src/test/screenshots`**: esto no es un golden y
 * no tiene que aparecer en ningún `git diff` de imágenes.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
class ElRenderEffectSeAplicaDeVerdadTest : RobolectricTestBase() {

    private var modoAnterior: String? = null

    @Before
    fun grabaParaPoderLeerLosPixeles() {
        modoAnterior = System.getProperty(MODO_GRABACION)
        System.setProperty(MODO_GRABACION, "true")
    }

    @After
    fun restauraElModo() {
        modoAnterior?.let { System.setProperty(MODO_GRABACION, it) }
            ?: System.clearProperty(MODO_GRABACION)
    }

    @Test
    fun `el renderizador de los goldens SI aplica RenderEffect`() {
        val sin = renglonCentral(pinta("sin_desenfoque", radio = null))
        val con = renglonCentral(pinta("con_desenfoque", radio = RADIO))

        // Control positivo primero: sin desenfoque el canto es duro. Si esto
        // fallara, el andamio no estaría midiendo el borde y la afirmación de
        // abajo no valdría nada.
        assertEquals(
            "sin desenfoque el canto no salió duro: la muestra no cae sobre el borde " +
                "negro/blanco y esta prueba no está midiendo lo que cree",
            setOf(0, MAXIMO),
            sin.toSet()
        )
        assertTrue(
            "con `Modifier.blur` el canto sigue siendo duro (" + con.joinToString() + "): " +
                "este renderizador IGNORA RenderEffect, así que los goldens sólo están " +
                "guardando el degradado y el desenfoque de la barra no lo prueba nada",
            con.any { it != 0 && it != MAXIMO }
        )
    }

    /**
     * El renglón central de la imagen, alrededor del canto, como valores de un
     * solo canal —la muestra es gris, así que los tres coinciden—.
     */
    private fun renglonCentral(imagen: Bitmap): List<Int> {
        val y = imagen.height / 2
        val desde = imagen.width / 2 - MUESTRA
        return (desde until desde + MUESTRA * 2).map { x -> imagen.getPixel(x, y) and 0xFF }
    }

    private fun pinta(nombre: String, radio: Dp?): Bitmap {
        // Sin archivo previo, y en modo GRABACIÓN explícito: `prePushCheck`
        // corre Roborazzi en modo verificación, y ahí `captureRoboImage`
        // COMPARA contra lo que haya en esa ruta en vez de escribir. El temporal
        // vacío que deja `createTempFile` se leía como golden y reventaba con
        // `read(...) must not be null`, que es lo que tumbó el push de la 2.19.0.
        val archivo = File.createTempFile("render_effect_$nombre", ".png").apply { delete() }
        captureRoboImage(
            filePath = archivo.absolutePath,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record)
        ) {
            Box(
                modifier = Modifier
                    .size(LADO)
                    .then(if (radio != null) Modifier.blur(radio) else Modifier)
                    .background(Color.White)
            ) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color.Black))
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color.White))
                }
            }
        }
        return requireNotNull(BitmapFactory.decodeFile(archivo.absolutePath)) {
            "la captura salió vacía: Roborazzi no escribió el archivo"
        }
    }

    private companion object {
        const val MODO_GRABACION = "roborazzi.test.record"
        val LADO = 120.dp
        val RADIO = 12.dp

        /** Cuántos píxeles se miran a cada lado del canto. */
        const val MUESTRA = 6

        const val MAXIMO = 255
    }
}
