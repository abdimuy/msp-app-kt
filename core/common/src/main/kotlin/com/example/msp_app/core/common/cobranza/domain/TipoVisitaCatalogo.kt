package com.example.msp_app.core.common.cobranza.domain

/**
 * El catálogo CERRADO de `TIPO_VISITA` y su estado en el catálogo de ocho.
 *
 * ## La reconciliación 8 / 10 / 11 / 14
 *
 * Los cuatro números del plan son cuatro conjuntos distintos y todos son
 * correctos:
 *
 * - **14** — el catálogo cerrado del servidor
 *   (`internal/visitas/domain/tipo_visita.go`, Task 7). No fue adivinado: es la
 *   unión medida sobre las 226,746 filas de `MSP_VISITAS` (`SELECT TIPO_VISITA,
 *   COUNT(*) … GROUP BY TIPO_VISITA` devolvió 15 grupos = 14 valores no vacíos
 *   + la cadena vacía). Es lo que **puede llegar**, y por lo tanto lo que este
 *   objeto tiene que cubrir.
 * - **11** — los literales que define `Constants.kt:31-41` en `:app`, o sea lo
 *   que **manda** el build actual.
 * - **10** — los que ofrecía el diálogo medido
 *   (`NewVisitDialog.visitConditionForm`, retirado por la Task 21; su sucesor
 *   es `CatalogoDeResultados`). El que faltaba es
 *   `PIDE_TIEMPO` ("Pidió que regrese otro día"): sigue definido en
 *   `Constants`, sigue aceptado por el servidor y sigue existiendo en filas
 *   históricas, pero ya no aparecía en el diálogo. La tabla de agrupación del
 *   brief habla de las diez ofrecidas, por eso no lo menciona.
 * - **8** — los estados de [EstadoCuenta]. No es una partición de los
 *   literales: [EstadoCuenta.PAGO] y [EstadoCuenta.ABONO_PARCIAL] salen del
 *   dinero, [EstadoCuenta.SIN_TOCAR] sale de la ausencia de todo, y
 *   [EstadoCuenta.CITA_A_UNA_HORA] todavía no sale de ningún literal.
 *
 * 14 = 11 + 3 retirados. Los 3 retirados no son basura: `"Dijo que no va a
 * pagar"` —el valor viejo de `NO_VA_A_DAR_PAGO`, renombrado en el commit
 * f7de5f02— seguía llegando **852 veces en los últimos 90 días** del dataset,
 * de teléfonos que nunca tomaron la actualización. Por eso [estadoDe] es
 * TOTAL: todo literal que pueda llegar aterriza en exactamente un estado, y lo
 * que no está en el catálogo tampoco cae en la nada (ver [ESTADO_DESCONOCIDO]).
 *
 * ## Por qué hay una copia de los literales aquí
 *
 * `Constants` vive en `:app` y `:core:common` no puede depender de `:app`. La
 * copia es deliberada y está amarrada por
 * `TipoVisitaCatalogoDriftTest` en `:app`, que compara literal por literal
 * contra `Constants` y se pone rojo si alguno se mueve. La clasificación, en
 * cambio, NO está duplicada: `VisitScopeMapper` (`:app`, Task 13) delega en
 * [alcanceDe].
 */
object TipoVisitaCatalogo {

    /** Cuántos de [LITERALES] son literales retirados (van al final de la lista). */
    private const val LITERALES_RETIRADOS_COUNT = 3

    // ── Los 11 literales que manda el build actual (Constants.kt:31-41) ──

    /** `Constants.NO_SE_ENCONTRABA`. El desenlace más común (118,514 filas). */
    const val NO_SE_ENCONTRABA: String = "No se encontraba"

    /** `Constants.CASA_CERRADA`. */
    const val CASA_CERRADA: String = "Casa cerrada con candado"

    /** `Constants.SOLO_MENORES`. */
    const val SOLO_MENORES: String = "Solo había menores"

    /** `Constants.NO_VA_A_DAR_PAGO`, valor vigente desde el commit f7de5f02. */
    const val NO_VA_A_DAR_PAGO: String = "No pagará esta ocasión"

    /** `Constants.PIDE_TIEMPO`. Definido y aceptado, pero ya no ofrecido en el diálogo. */
    const val PIDE_TIEMPO: String = "Pidió que regrese otro día"

    /** `Constants.TIENE_PERO_NO_PAGA`. */
    const val TIENE_PERO_NO_PAGA: String = "Tiene dinero pero no quiso pagar"

    /** `Constants.FUE_GROSERO`. */
    const val FUE_GROSERO: String = "Fue grosero o agresivo"

    /** `Constants.SE_ESCONDE`, valor vigente. */
    const val SE_ESCONDE: String = "Se asomó pero no salió"

    /** `Constants.NO_RESPONDE`. */
    const val NO_RESPONDE: String = "No responde aunque está"

    /** `Constants.SE_ESCUCHAN_RUIDOS`. */
    const val SE_ESCUCHAN_RUIDOS: String = "Se escuchan ruidos pero no abre"

    /** `Constants.PIDE_REAGENDAR`. */
    const val PIDE_REAGENDAR: String = "Pidió reagendar visita"

    // ── Los 3 retirados que SIGUEN llegando de teléfonos viejos ──

    /** Redacción pre-2025-09 de `NO_VA_A_DAR_PAGO`. 38,855 filas, última 2025-08-12. */
    const val LEGACY_NO_VA_A_DAR_PAGO: String = "No va a dar pago"

    /** El literal que f7de5f02 renombró. 16,925 filas, seguía llegando el 2026-02-18. */
    const val LEGACY_DIJO_QUE_NO_VA_A_PAGAR: String = "Dijo que no va a pagar"

    /** Redacción pre-rename de `SE_ESCONDE`. 3,682 filas, última 2025-08-10. */
    const val LEGACY_SE_ESCONDE_Y_NO_SALE: String = "Se esconde y no sale"

    /**
     * Estado con el que aterriza un `TIPO_VISITA` fuera del catálogo — incluida
     * la cadena vacía de las 200 filas de 2024-09 y cualquier texto libre de un
     * build anterior al cierre del catálogo.
     *
     * Es [EstadoCuenta.VISITE_VUELVO] por la misma razón por la que Task 13
     * eligió [VisitScope.VENTA] como default: es el estado **más angosto** que
     * sigue siendo cierto. Alguien pasó y no se resolvió, así que la cuenta se
     * queda en la lista de este periodo. Nunca [EstadoCuenta.NO_ESTABA] (se
     * propagaría a ventas que el cobrador no visitó) ni
     * [EstadoCuenta.SIN_TOCAR] (borraría el hecho de que sí hubo visita).
     *
     * Que un literal caiga aquí NO es normal y se reporta: ver
     * [DerivacionPeriodo] y `CODE_TIPO_VISITA_FUERA_DE_CATALOGO`.
     */
    val ESTADO_DESCONOCIDO: EstadoCuenta = EstadoCuenta.VISITE_VUELVO

    /**
     * Literal → estado. Es la tabla de agrupación del `task-14-brief.md`,
     * extendida con `PIDE_TIEMPO` y con los tres retirados.
     *
     * Cada retirado va al MISMO estado que el literal que lo reemplazó, que es
     * lo que lo vuelve una migración de nombre y no un cambio de significado.
     */
    private val estadoPorTipo: Map<String, EstadoCuenta> = mapOf(
        // "No estaba" ← no se encontraba · casa cerrada con candado · solo había menores
        NO_SE_ENCONTRABA to EstadoCuenta.NO_ESTABA,
        CASA_CERRADA to EstadoCuenta.NO_ESTABA,
        SOLO_MENORES to EstadoCuenta.NO_ESTABA,

        // "Visité — vuelvo" ← se asomó pero no salió · no responde aunque está · se escuchan ruidos
        SE_ESCONDE to EstadoCuenta.VISITE_VUELVO,
        NO_RESPONDE to EstadoCuenta.VISITE_VUELVO,
        SE_ESCUCHAN_RUIDOS to EstadoCuenta.VISITE_VUELVO,
        // `PIDE_TIEMPO` no está en la tabla del brief porque el diálogo ya no lo
        // ofrece. Va aquí y no a PROMETIO_PROXIMA: "regrese otro día" no trae
        // fecha ni monto, y es exactamente lo que hoy escribe VisitStatusMapper
        // como VOLVER_VISITAR. Mandarlo a "prometió" diferiría la cuenta al
        // siguiente periodo con base en nada.
        PIDE_TIEMPO to EstadoCuenta.VISITE_VUELVO,

        // "Se negó / problema" ← no pagará esta ocasión · tiene dinero pero no quiso · fue grosero
        NO_VA_A_DAR_PAGO to EstadoCuenta.SE_NEGO,
        TIENE_PERO_NO_PAGA to EstadoCuenta.SE_NEGO,
        FUE_GROSERO to EstadoCuenta.SE_NEGO,

        // "Prometió" ← pidió reagendar visita
        PIDE_REAGENDAR to EstadoCuenta.PROMETIO_PROXIMA,

        // Retirados → el estado de su sucesor.
        LEGACY_NO_VA_A_DAR_PAGO to EstadoCuenta.SE_NEGO,
        LEGACY_DIJO_QUE_NO_VA_A_PAGAR to EstadoCuenta.SE_NEGO,
        LEGACY_SE_ESCONDE_Y_NO_SALE to EstadoCuenta.VISITE_VUELVO
    )

    /**
     * Los 14, en el orden estable del servidor (vigentes primero, retirados
     * después). Sirve para barrer el catálogo completo en un test y para
     * documentación; no es el orden en que se ofrecen al cobrador.
     */
    val LITERALES: List<String> = listOf(
        NO_SE_ENCONTRABA,
        CASA_CERRADA,
        SOLO_MENORES,
        NO_VA_A_DAR_PAGO,
        PIDE_TIEMPO,
        TIENE_PERO_NO_PAGA,
        FUE_GROSERO,
        SE_ESCONDE,
        NO_RESPONDE,
        SE_ESCUCHAN_RUIDOS,
        PIDE_REAGENDAR,
        LEGACY_NO_VA_A_DAR_PAGO,
        LEGACY_DIJO_QUE_NO_VA_A_PAGAR,
        LEGACY_SE_ESCONDE_Y_NO_SALE
    )

    /**
     * Los 11 que manda el build actual. `:app` los compara contra `Constants`
     * para que esta copia no se despegue del cable.
     */
    val LITERALES_VIGENTES: List<String> = LITERALES.dropLast(LITERALES_RETIRADOS_COUNT)

    /** Los 3 retirados que todavía llegan de teléfonos sin actualizar. */
    val LITERALES_RETIRADOS: List<String> = LITERALES.takeLast(LITERALES_RETIRADOS_COUNT)

    /**
     * ¿Está [tipoVisita] en el catálogo cerrado de 14? Comparación exacta:
     * byte por byte, con acentos, igual que `TipoVisita.IsValid` en el servidor.
     */
    fun esConocido(tipoVisita: String): Boolean = estadoPorTipo.containsKey(tipoVisita)

    /**
     * Estado de [tipoVisita]. Función TOTAL: un literal fuera del catálogo cae
     * en [ESTADO_DESCONOCIDO], nunca en `null` y nunca en la nada.
     *
     * ## [tieneCita]: el único dato que gana al literal
     *
     * [EstadoCuenta.CITA_A_UNA_HORA] **no sale de ningún literal** y nunca va a
     * salir: el catálogo de `TIPO_VISITA` está cerrado en el servidor
     * (Ruling D) y ninguno de sus 14 valores significa "quedamos a tal hora".
     * Lo que sí significa eso es un DATO: `Visit.CITA_FECHA`, la columna que la
     * Task 26 agregó y que la Task 19 empezó a escribir. Cuando esa columna
     * trae día, la visita **es** una cita, venga con el literal que venga.
     *
     * La Task 19 escribe [PIDE_TIEMPO] ("Pidió que regrese otro día") como
     * literal de cable de una cita, porque es el valor del catálogo cerrado que
     * más se le parece y el servidor lo acepta. Eso le da a ese literal un
     * segundo significado — *"regresa otro día"* a secas, o *"regresa el jueves
     * a las 4"*—, y **el día es lo que los separa**: una fila histórica de
     * [PIDE_TIEMPO] no tiene `CITA_FECHA`, así que sigue derivando
     * [EstadoCuenta.VISITE_VUELVO] exactamente como antes. Ninguna visita ya
     * capturada cambia de estado por este parámetro.
     *
     * Las diez etiquetas que el cobrador elige **no cambian de estado**: la
     * cita no es una de ellas, es una rama propia de la pantalla.
     *
     * @param tieneCita `Visit.CITA_FECHA != null` — el DÍA de la cita, no la
     *   hora. Una cita sin hora sigue siendo una cita (el mock contempla "otro
     *   día sin hora"); lo que no existe es una cita sin día, y por eso la
     *   guarda pregunta por el día.
     */
    fun estadoDe(tipoVisita: String, tieneCita: Boolean = false): EstadoCuenta = when {
        tieneCita -> EstadoCuenta.CITA_A_UNA_HORA
        else -> estadoPorTipo[tipoVisita] ?: ESTADO_DESCONOCIDO
    }

    /**
     * Alcance de [tipoVisita]. **Única** clasificación cliente/venta del repo:
     * `VisitScopeMapper` (`:app`, Task 13) delega aquí y `VisitsLocalDataSource`
     * lo consume a través suyo. Se deriva del estado, así que no puede
     * desincronizarse de [estadoDe] — incluida la rama de [tieneCita]: una cita
     * es [VisitScope.CLIENTE] porque [EstadoCuenta.CITA_A_UNA_HORA] lo es, sin
     * una segunda tabla que pudiera contradecirlo.
     */
    fun alcanceDe(tipoVisita: String, tieneCita: Boolean = false): VisitScope =
        estadoDe(tipoVisita, tieneCita).alcance
}
