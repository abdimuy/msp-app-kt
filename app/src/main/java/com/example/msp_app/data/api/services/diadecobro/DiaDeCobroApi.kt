package com.example.msp_app.data.api.services.diadecobro

import com.google.gson.JsonElement
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * El día de cobro por zona (contrato del spec `2026-11-11-dia-de-cobro`, §10).
 *
 * Es de **solo lectura** y va por su propia interfaz, aparte de [V2CobranzaApi]:
 * ningún canal del sync de pagos y saldos lo comparte ni lo espera.
 */
interface DiaDeCobroApi {

    @GET("v2/cobranza/dia-de-cobro")
    suspend fun diaDeCobro(@Query("zona") zonaId: Int): DiaDeCobroResponse
}

/**
 * **Todo nullable, sin confiar en los defaults de Kotlin**: Gson construye por
 * `Unsafe` y un campo ausente llega `null` aunque el constructor diga otra
 * cosa. Un tipo no nulo reventaría con NPE al primer uso (incidente ya pagado
 * una vez en producción).
 */
data class DiaDeCobroResponse(
    val calculado_en: String? = null,
    val ventas: List<DiaDeCobroVentaDto>? = null
)

data class DiaDeCobroVentaDto(
    val docto_cc_id: Int? = null,
    val acordado: AcordadoDto? = null,
    val estimacion: EstimacionDto? = null
)

/**
 * `tipo = semana` → [dia] es texto (`SABADO`); `tipo = mes` → [dia] es número.
 * Por eso es [JsonElement] y no `String`/`Int`.
 */
data class AcordadoDto(
    val tipo: String? = null,
    val dia: JsonElement? = null
)

data class EstimacionDto(
    val pagos_considerados: Int? = null,
    val principal: String? = null,
    val dias: List<DiaPagosDto>? = null
)

data class DiaPagosDto(
    val dia: String? = null,
    val pagos: Int? = null
)
