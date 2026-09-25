package com.example.msp_app.feature.ubicacion.domain

import com.example.msp_app.core.geo.LugarAgrupado
import com.example.msp_app.core.geo.LugaresDelCliente
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * **Qué clase de lugar es, para efectos de cómo se dibuja y qué se dice de él.**
 *
 * El orden importa: es el que decide cuál rótulo gana cuando un lugar cumple
 * varias condiciones. Un lugar compartido que además es sólo de transferencias
 * se rotula **compartido**, porque eso es lo más informativo para quien está
 * parado en la calle.
 */
enum class TipoDeLugar {
    /** Se ganó el título: no compartido y con abonos que no son transferencia. */
    LA_PUERTA,

    /** Cae donde cobran a otros clientes. Se dibuja; no puede ser la puerta. */
    COMPARTIDO,

    /** Sólo transferencias: cierto como hecho, falso como domicilio. */
    SOLO_TRANSFERENCIAS,

    /** Un lugar más del cliente, sin título. */
    OTRO
}

/**
 * Un lugar listo para pintar: el grupo de `:core:geo` más lo que la pantalla
 * necesita decir de él.
 *
 * **No trae colores ni opacidades**, sólo hechos. Traducir "hace dos años" a un
 * alfa es trabajo de la capa de dibujo, y mantenerlo fuera de aquí es lo que
 * deja probar estas reglas sin Compose.
 */
data class LugarEnElMapa(
    val lugar: LugarAgrupado,
    val tipo: TipoDeLugar,
    /**
     * `true` si la medición que el cobrador tocó para llegar aquí cae en este
     * lugar. Sólo puede serlo uno.
     */
    val resaltado: Boolean
) {
    /**
     * **El rótulo, de dos a cuatro palabras.** Es lo que va sobre el marcador.
     *
     * Ninguno **nombra el lugar**. No se dice "la tienda" ni "casa del
     * cobrador": la app no lo sabe. Lo que sabe —y lo que dice [detalle]— es
     * cuántos clientes y cuántos cobradores lo comparten, y con eso el cobrador,
     * que conoce el lugar, concluye mejor que nosotros.
     */
    val rotulo: String
        get() = when (tipo) {
            TipoDeLugar.LA_PUERTA -> "La puerta"
            TipoDeLugar.COMPARTIDO -> "Punto de muchos clientes"
            TipoDeLugar.SOLO_TRANSFERENCIAS -> "Transferencias"
            TipoDeLugar.OTRO -> "Otro punto"
        }

    /**
     * El renglón de apoyo de la hoja: los hechos, sin interpretarlos.
     *
     * Para un punto compartido son **las dos cuentas** —clientes y cobradores—
     * porque son lo que de verdad se midió. Un punto de 381 clientes y 30
     * cobradores casi seguro es la tienda, y uno de 48 clientes con un solo
     * cobrador casi seguro es el punto de ese cobrador; **la app enseña los
     * números y se calla la conclusión.** Hay además un tercer caso medido —un
     * cobrador, cero transferencias, 50 celdas— que no se puede distinguir de
     * una vecindad de verdad, y ahí inventar el nombre sería mentir.
     */
    val detalle: String
        get() = when (tipo) {
            TipoDeLugar.COMPARTIDO -> detalleDeCompartido()
            TipoDeLugar.SOLO_TRANSFERENCIAS -> "Se registró donde estaba el cobrador"
            else -> "${lugar.conteo} ${if (lugar.conteo == 1) "cobro" else "cobros"} aquí"
        }

    /**
     * La frase de un punto compartido, con el verbo concordado.
     *
     * **El plural no es cosmético, y lo cazó un golden:** la primera versión
     * armaba `"Aquí cobra " + quien` y producía *"Aquí cobra 30 cobradores"*.
     * En la única frase que sustituye al nombre del lugar, un renglón
     * descuidado le resta autoridad a lo que dice — y lo que dice es justamente
     * "no le voy a inventar qué es este lugar, le doy los números".
     *
     * `"a N clientes"` no necesita rama singular: por construcción un lugar sólo
     * se marca compartido con
     * [com.example.msp_app.core.geo.IndiceDePuntosCompartidos.MINIMO_DE_CLIENTES]
     * o más, así que N nunca es 1.
     */
    private fun detalleDeCompartido(): String {
        val clientes = lugar.clientesQueLoComparten
        val cobradores = lugar.cobradoresQueCobranAqui
        return if (cobradores == 1) {
            "Aquí cobra un cobrador a $clientes clientes"
        } else {
            "Aquí cobran $cobradores cobradores a $clientes clientes"
        }
    }

    /**
     * Qué tan reciente es, de 0 (lo más viejo del cliente) a 1 (lo más nuevo).
     *
     * Es el número del que la capa de dibujo saca la opacidad —**lo reciente
     * sólido, lo viejo desvaído**—. Se calcula contra el rango del propio
     * cliente y no contra "hoy" a propósito: un cliente que dejó de pagar hace
     * dos años tendría TODO desvaído contra hoy, y entonces el degradado no
     * diría nada. Contra su propio rango, siempre se ve cuál fue antes.
     */
    fun frescura(masViejo: Instant, masNuevo: Instant): Float {
        val total = ChronoUnit.DAYS.between(masViejo, masNuevo).toFloat()
        if (total <= 0f) return 1f
        val delLugar = ChronoUnit.DAYS.between(masViejo, lugar.masReciente).toFloat()
        return (delLugar / total).coerceIn(0f, 1f)
    }
}

/**
 * Convierte los lugares de `:core:geo` en lo que la pantalla pinta.
 *
 * [pagoResaltado] es el abono desde el que se entró —una fila de la bitácora o
 * del detalle de venta—, o `null` cuando se entró desde el cuadro del detalle de
 * cliente. **El conjunto que se dibuja es el mismo en los tres casos**: todos
 * los lugares del cliente. Lo único que cambia es que uno entra destacado, y por
 * eso la bitácora sigue contestando *"¿dónde fue ESA vez?"* y además enseña si
 * esa vez fue rara.
 */
fun LugaresDelCliente.paraElMapa(pagoResaltado: String?): List<LugarEnElMapa> =
    lugares.map { lugar ->
        LugarEnElMapa(
            lugar = lugar,
            tipo = when {
                lugar === laPuerta -> TipoDeLugar.LA_PUERTA
                lugar.esCompartido -> TipoDeLugar.COMPARTIDO
                lugar.esSoloDeTransferencias -> TipoDeLugar.SOLO_TRANSFERENCIAS
                else -> TipoDeLugar.OTRO
            },
            resaltado = pagoResaltado != null && lugar.mediciones.any { it.pagoId == pagoResaltado }
        )
    }
