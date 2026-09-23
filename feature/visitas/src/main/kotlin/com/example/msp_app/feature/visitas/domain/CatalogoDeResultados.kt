package com.example.msp_app.feature.visitas.domain

import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita

/**
 * Las **diez etiquetas de hoy, agrupadas bajo el resultado que les toca** — y
 * el literal de `TIPO_VISITA` que cada elección escribe al cable.
 *
 * Es la tabla del `task-14-brief.md` leída al revés: aquel objeto contesta
 * *"¿en qué estado cae este literal?"* y este contesta *"¿qué literales ofrezco
 * bajo este resultado?"*. **No hay una segunda clasificación**: cada renglón de
 * [ETIQUETAS] se comprueba contra [TipoVisitaCatalogo.estadoDe] en
 * `CatalogoDeResultadosTest`, así que las dos no pueden despegarse.
 *
 * El cobrador no reaprende nada y el historial sigue siendo comparable: son las
 * mismas diez cadenas que ofrecía `NewVisitDialog.visitConditionForm` —el
 * diálogo al que reemplaza, retirado por la Task 21—, con el mismo texto y el
 * mismo valor guardado.
 *
 * ## Los dos grupos de una sola etiqueta
 *
 * [ResultadoDeVisita.PROMETIO] y [ResultadoDeVisita.CITA] tienen **una** sola
 * etiqueta cada uno, y por eso la pantalla no pinta chips para ellos (el mock
 * tampoco: su estado 2, "Prometió", no muestra fila de etiquetas — muestra
 * venta, fecha y monto). Elegir el resultado ya elige el literal.
 *
 * ## Por qué la cita escribe `PIDE_TIEMPO`
 *
 * El catálogo de `TIPO_VISITA` está **cerrado en el servidor** (Ruling D):
 * inventar un literal "cita" sería un cambio de contrato que este plan no hace.
 * De los 14 valores aceptados, `Pidió que regrese otro día` es el que significa
 * eso — y es además el único del build actual que el diálogo ya **no** ofrece,
 * así que reusarlo no le quita su etiqueta a nadie. Lo que convierte esa visita
 * en una cita no es el literal sino el DATO: `CITA_FECHA`. Una fila histórica
 * de `PIDE_TIEMPO` no lo trae y sigue derivando "visité, vuelvo", igual que
 * siempre (ver `TipoVisitaCatalogo.estadoDe`).
 */
object CatalogoDeResultados {

    /**
     * Resultado → sus etiquetas, en el orden en que se ofrecen. Los valores son
     * los literales que van a `TIPO_VISITA`, no textos de pantalla: se muestran
     * tal cual, que es lo que los hace comparables con el histórico.
     */
    val ETIQUETAS: Map<ResultadoDeVisita, List<String>> = mapOf(
        ResultadoDeVisita.NO_ESTABA to listOf(
            TipoVisitaCatalogo.NO_SE_ENCONTRABA,
            TipoVisitaCatalogo.CASA_CERRADA,
            TipoVisitaCatalogo.SOLO_MENORES
        ),
        ResultadoDeVisita.VISITE_VUELVO to listOf(
            TipoVisitaCatalogo.SE_ESCONDE,
            TipoVisitaCatalogo.NO_RESPONDE,
            TipoVisitaCatalogo.SE_ESCUCHAN_RUIDOS
        ),
        ResultadoDeVisita.PROMETIO to listOf(
            TipoVisitaCatalogo.PIDE_REAGENDAR
        ),
        ResultadoDeVisita.CITA to listOf(
            TipoVisitaCatalogo.PIDE_TIEMPO
        ),
        ResultadoDeVisita.SE_NEGO to listOf(
            TipoVisitaCatalogo.NO_VA_A_DAR_PAGO,
            TipoVisitaCatalogo.TIENE_PERO_NO_PAGA,
            TipoVisitaCatalogo.FUE_GROSERO
        )
    )

    /**
     * Las **diez** que el cobrador elige, en el orden de la pantalla. No incluye
     * el literal de cable de la cita: ese no se elige, lo pone el resultado.
     */
    val ETIQUETAS_OFRECIDAS: List<String> =
        ETIQUETAS.filterKeys { it != ResultadoDeVisita.CITA }.values.flatten()

    /** Las etiquetas de [resultado]. Función TOTAL: ningún resultado se queda sin lista. */
    fun etiquetasDe(resultado: ResultadoDeVisita): List<String> =
        ETIQUETAS[resultado] ?: error("resultado sin etiquetas: $resultado")

    /**
     * ¿Este resultado pinta la fila de etiquetas? Solo cuando hay de dónde
     * escoger: con una sola, la fila sería un control de una opción ya elegida.
     */
    fun pideEtiqueta(resultado: ResultadoDeVisita): Boolean = etiquetasDe(resultado).size > 1

    /**
     * La etiqueta con la que arranca [resultado] al elegirlo: la primera de su
     * lista. Nunca `null`, así que no existe un estado "resultado elegido, pero
     * sin literal que escribir".
     */
    fun etiquetaPorDefecto(resultado: ResultadoDeVisita): String = etiquetasDe(resultado).first()

    /** ¿[etiqueta] pertenece a [resultado]? Comparación exacta, byte por byte. */
    fun perteneceA(resultado: ResultadoDeVisita, etiqueta: String): Boolean =
        etiqueta in etiquetasDe(resultado)
}
