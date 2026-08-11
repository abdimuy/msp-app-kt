package com.example.msp_app.core.designsystem.theme

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM puro (sin Robolectric): [mspCompressedSp] no toca `@Composable` ni
 * `LocalDensity`, y `TextUnit`/`sp` son `value class` sobre `Float`, igual
 * que [MspSpacingTest] con `Dp`. Cubre las 4 propiedades que el brief exige:
 * NORMAL ~ base, monotonicidad entre niveles, boost relativo mayor para bases
 * chicas que para bases grandes, y ratio máximo/mínimo decreciente.
 *
 * Los `baseSp` usados (9f `ringCaption`, 11f `sectionLabel`/`eyebrow`, 34f
 * `amountLarge`, 46f `heroAmount`) son valores reales de `MspType.kt` — no
 * inventados — para que estas pruebas defiendan el rango que la app
 * realmente usa.
 */
class CompressedTypeRampTest {

    // --- 1. NORMAL es esencialmente un no-op --------------------------------

    @Test
    fun `NORMAL devuelve el base sin cambios`() {
        assertEquals(9f.sp, mspCompressedSp(9f, FontSizeLevel.NORMAL))
        assertEquals(11f.sp, mspCompressedSp(11f, FontSizeLevel.NORMAL))
        assertEquals(20f.sp, mspCompressedSp(20f, FontSizeLevel.NORMAL))
        assertEquals(34f.sp, mspCompressedSp(34f, FontSizeLevel.NORMAL))
        assertEquals(46f.sp, mspCompressedSp(46f, FontSizeLevel.NORMAL))
    }

    // --- 2. Monotonicidad: subir de nivel nunca encoge un rol ---------------

    @Test
    fun `cada rol crece o se mantiene al subir de nivel, nunca encoge`() {
        val bases = listOf(9f, 10.5f, 11f, 12.5f, 13f, 15.5f, 16.5f, 20f, 22f, 26f, 34f, 44f, 46f)
        val levels = listOf(FontSizeLevel.NORMAL, FontSizeLevel.GRANDE, FontSizeLevel.MUY_GRANDE)

        for (base in bases) {
            val sizes = levels.map { mspCompressedSp(base, it).value }
            for (i in 1 until sizes.size) {
                assertTrue(
                    "baseSp=$base: ${levels[i - 1]}=${sizes[i - 1]} debería ser <= " +
                        "${levels[i]}=${sizes[i]}",
                    sizes[i - 1] <= sizes[i]
                )
            }
        }
    }

    // --- 3. Piso alto para chico, techo para grande --------------------------

    @Test
    fun `un base chico crece proporcionalmente MAS que uno grande de NORMAL a MUY_GRANDE`() {
        val smallBase = 11f
        val largeBase = 34f

        val smallGrowth = mspCompressedSp(smallBase, FontSizeLevel.MUY_GRANDE).value / smallBase
        val largeGrowth = mspCompressedSp(largeBase, FontSizeLevel.MUY_GRANDE).value / largeBase

        assertTrue(
            "el base chico (factor=$smallGrowth) debería crecer más que el grande " +
                "(factor=$largeGrowth)",
            smallGrowth > largeGrowth
        )
        // El chico crece MÁS que la escala nominal (2.0x); el grande, MENOS —
        // esa es la compresión: ninguno de los dos escala linealmente.
        assertTrue(smallGrowth > FontSizeLevel.MUY_GRANDE.nominalScale)
        assertTrue(largeGrowth < FontSizeLevel.MUY_GRANDE.nominalScale)
    }

    // --- 4. El ratio grande/chico se encoge al subir de nivel -----------------

    @Test
    fun `el ratio maximo entre minimo se encoge en MUY_GRANDE frente a NORMAL`() {
        val bases = listOf(9f, 11f, 15.5f, 20f, 26f, 34f, 46f)

        fun ratioAt(level: FontSizeLevel): Float {
            val sizes = bases.map { mspCompressedSp(it, level).value }
            return sizes.max() / sizes.min()
        }

        val ratioNormal = ratioAt(FontSizeLevel.NORMAL)
        val ratioGrande = ratioAt(FontSizeLevel.GRANDE)
        val ratioMuyGrande = ratioAt(FontSizeLevel.MUY_GRANDE)

        assertTrue(
            "GRANDE ($ratioGrande) debería tener menor ratio que NORMAL ($ratioNormal)",
            ratioGrande < ratioNormal
        )
        assertTrue(
            "MUY_GRANDE ($ratioMuyGrande) debería tener menor ratio que GRANDE ($ratioGrande)",
            ratioMuyGrande < ratioGrande
        )
    }

    // --- 5. Piso/techo absolutos siempre se respetan --------------------------

    @Test
    fun `el resultado nunca cae fuera de un rango sensato, incluso con bases extremas`() {
        val extremeBases = listOf(0f, 1f, 5f, 200f)

        for (level in FontSizeLevel.entries) {
            for (base in extremeBases) {
                val result = mspCompressedSp(base, level).value
                assertTrue(
                    "baseSp=$base nivel=$level dio $result, fuera de [0, 200]",
                    result in 0f..200f
                )
            }
        }
    }
}
