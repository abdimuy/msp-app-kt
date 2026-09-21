package com.example.msp_app.feature.ventacorreccion.domain

import com.example.msp_app.core.database.dao.localsale.LocalSaleClaimLeases
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private const val AHORA = 10_000_000_000L

/**
 * Un renglón de la tabla exhaustiva. Agrupado en una sola clase (en vez de
 * siete parámetros sueltos en el constructor del test) para no chocar con
 * `LongParameterList` de detekt sin perder ningún caso de la tabla.
 */
data class CasoEvaluarCorregibilidad(
    val nombre: String,
    val enviado: Boolean,
    val permanente: Boolean,
    val correccionNoEnviada: Boolean,
    val claimKind: String?,
    val claimedAt: Long?,
    val esperado: EstadoCorreccion
) {
    override fun toString() = nombre
}

/**
 * Barrido paramétrico EXHAUSTIVO de [evaluarCorregibilidad] sobre las tablas
 * del plan "Corregir una venta antes de que suba" (Task 2, corrección del
 * orquestador sobre el brief original: 5 estados, no 3).
 *
 * Precedencia, en el orden que la tabla cubre:
 * 1. `enviado` manda sobre TODO — incluido un `permanente=true` y un candado
 *    `UPLOAD` vivo ([casosEnviada]).
 * 2. Dentro de enviada, `correccionNoEnviada` decide entre `YaSeEnvio` y
 *    `LaRevisaLaOficina` ([casosEnviada]).
 * 3. Sin enviar, el fallo permanente manda sobre CUALQUIER candado, vivo o
 *    no ([casosPermanente]).
 * 4. Sin enviar y sin permanente, sólo un candado `UPLOAD` VIVO produce
 *    `SeEstaEnviando`; vencido, `EDIT`, o con datos incompletos/corruptos,
 *    caen todos en `Corregible` ([casosCandadoFrontera],
 *    [casosCandadoDatosIncompletos]).
 */
@RunWith(Parameterized::class)
class EstadoCorreccionExhaustivoTest(private val caso: CasoEvaluarCorregibilidad) {

    @Test
    fun `precedencia exhaustiva`() {
        val estado = evaluarCorregibilidad(
            enviado = caso.enviado,
            permanente = caso.permanente,
            correccionNoEnviada = caso.correccionNoEnviada,
            claimKind = caso.claimKind,
            claimedAt = caso.claimedAt,
            ahora = AHORA
        )

        assertEquals(caso.nombre, caso.esperado, estado)
    }

    companion object {
        private val editLease = LocalSaleClaimLeases.EDIT_LEASE_MS
        private val uploadLease = LocalSaleClaimLeases.UPLOAD_LEASE_MS

        // Casos 1-4: `enviado` manda sobre todo, y dentro de enviada la marca decide.
        private fun casosEnviada() = listOf(
            CasoEvaluarCorregibilidad(
                "enviada sin marca, sin candado, sin permanente -> YaSeEnvio",
                enviado = true,
                permanente = false,
                correccionNoEnviada = false,
                claimKind = null,
                claimedAt = null,
                esperado = EstadoCorreccion.YaSeEnvio
            ),
            CasoEvaluarCorregibilidad(
                "enviada con marca -> LaRevisaLaOficina",
                enviado = true,
                permanente = false,
                correccionNoEnviada = true,
                claimKind = null,
                claimedAt = null,
                esperado = EstadoCorreccion.LaRevisaLaOficina
            ),
            CasoEvaluarCorregibilidad(
                "enviada con marca, permanente y candado UPLOAD vivo -> LaRevisaLaOficina",
                enviado = true,
                permanente = true,
                correccionNoEnviada = true,
                claimKind = "UPLOAD",
                claimedAt = AHORA,
                esperado = EstadoCorreccion.LaRevisaLaOficina
            ),
            CasoEvaluarCorregibilidad(
                "enviada sin marca aunque permanente sea true -> YaSeEnvio",
                enviado = true,
                permanente = true,
                correccionNoEnviada = false,
                claimKind = null,
                claimedAt = null,
                esperado = EstadoCorreccion.YaSeEnvio
            )
        )

        // Casos 5-7: sin enviar, el fallo permanente manda sobre CUALQUIER candado.
        private fun casosPermanente() = listOf(
            CasoEvaluarCorregibilidad(
                "sin enviar, permanente, sin candado -> LaRevisaLaOficina",
                enviado = false,
                permanente = true,
                correccionNoEnviada = false,
                claimKind = null,
                claimedAt = null,
                esperado = EstadoCorreccion.LaRevisaLaOficina
            ),
            CasoEvaluarCorregibilidad(
                "sin enviar, permanente, candado UPLOAD vivo -> LaRevisaLaOficina",
                enviado = false,
                permanente = true,
                correccionNoEnviada = false,
                claimKind = "UPLOAD",
                claimedAt = AHORA,
                esperado = EstadoCorreccion.LaRevisaLaOficina
            ),
            CasoEvaluarCorregibilidad(
                "sin enviar, permanente, candado EDIT vivo -> LaRevisaLaOficina",
                enviado = false,
                permanente = true,
                correccionNoEnviada = false,
                claimKind = "EDIT",
                claimedAt = AHORA,
                esperado = EstadoCorreccion.LaRevisaLaOficina
            )
        )

        // Casos 8-12: sin enviar y sin permanente, la frontera de cada arrendamiento.
        private fun casosCandadoFrontera() = listOf(
            CasoEvaluarCorregibilidad(
                "sin enviar, sin permanente, sin candado -> Corregible",
                enviado = false,
                permanente = false,
                correccionNoEnviada = false,
                claimKind = null,
                claimedAt = null,
                esperado = EstadoCorreccion.Corregible
            ),
            CasoEvaluarCorregibilidad(
                "candado UPLOAD vivo (lease-1ms) -> SeEstaEnviando",
                enviado = false,
                permanente = false,
                correccionNoEnviada = false,
                claimKind = "UPLOAD",
                claimedAt = AHORA - uploadLease + 1,
                esperado = EstadoCorreccion.SeEstaEnviando
            ),
            CasoEvaluarCorregibilidad(
                "candado UPLOAD vencido justo en el lease -> Corregible",
                enviado = false,
                permanente = false,
                correccionNoEnviada = false,
                claimKind = "UPLOAD",
                claimedAt = AHORA - uploadLease,
                esperado = EstadoCorreccion.Corregible
            ),
            CasoEvaluarCorregibilidad(
                "candado EDIT vivo (lease-1ms) -> Corregible",
                enviado = false,
                permanente = false,
                correccionNoEnviada = false,
                claimKind = "EDIT",
                claimedAt = AHORA - editLease + 1,
                esperado = EstadoCorreccion.Corregible
            ),
            CasoEvaluarCorregibilidad(
                "candado EDIT vencido justo en el lease -> Corregible",
                enviado = false,
                permanente = false,
                correccionNoEnviada = false,
                claimKind = "EDIT",
                claimedAt = AHORA - editLease,
                esperado = EstadoCorreccion.Corregible
            )
        )

        // Casos 13-17: candado con datos incompletos o corruptos — defensa en
        // profundidad, mismo criterio que el DAO real (siempre Corregible).
        private fun casosCandadoDatosIncompletos() = listOf(
            CasoEvaluarCorregibilidad(
                "candado UPLOAD sin claimedAt -> Corregible",
                enviado = false,
                permanente = false,
                correccionNoEnviada = false,
                claimKind = "UPLOAD",
                claimedAt = null,
                esperado = EstadoCorreccion.Corregible
            ),
            CasoEvaluarCorregibilidad(
                "candado EDIT sin claimedAt -> Corregible",
                enviado = false,
                permanente = false,
                correccionNoEnviada = false,
                claimKind = "EDIT",
                claimedAt = null,
                esperado = EstadoCorreccion.Corregible
            ),
            CasoEvaluarCorregibilidad(
                "CLAIM_KIND vacio con timestamp reciente -> Corregible",
                enviado = false,
                permanente = false,
                correccionNoEnviada = false,
                claimKind = "",
                claimedAt = AHORA,
                esperado = EstadoCorreccion.Corregible
            ),
            CasoEvaluarCorregibilidad(
                "CLAIM_KIND desconocido con timestamp reciente -> Corregible",
                enviado = false,
                permanente = false,
                correccionNoEnviada = false,
                claimKind = "borrado",
                claimedAt = AHORA,
                esperado = EstadoCorreccion.Corregible
            ),
            CasoEvaluarCorregibilidad(
                "CLAIM_KIND nulo con claimedAt puesto -> Corregible",
                enviado = false,
                permanente = false,
                correccionNoEnviada = false,
                claimKind = null,
                claimedAt = AHORA,
                esperado = EstadoCorreccion.Corregible
            )
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun casos(): List<CasoEvaluarCorregibilidad> =
            casosEnviada() + casosPermanente() + casosCandadoFrontera() + casosCandadoDatosIncompletos()
    }
}
