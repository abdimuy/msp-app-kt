package com.example.msp_app.feature.ventacorreccion.domain

/**
 * Estado de corregibilidad de una venta local, la ÚNICA regla que decide si
 * el botón "Corregir venta" se ofrece — no depende de Android ni de Room
 * (plan "Corregir una venta antes de que suba", invariante de la Task 2).
 *
 * Cinco estados, no tres (corrección del orquestador sobre el brief
 * original, que sólo tenía `Corregible`/`YaSeEnvio`/`LaRevisaLaOficina`):
 * - [Corregible]: sin candado, candado vencido (de cualquier tipo), o
 *   candado `EDIT` vivo. Un `EDIT` vivo se toma como corregible porque en el
 *   alcance de este plan (un teléfono, una venta que nunca salió) sólo
 *   puede ser una sesión anterior del editor en el MISMO teléfono — si la
 *   app murió con el editor abierto, el dueño no debe quedar 30 min sin
 *   poder corregir su propia venta.
 * - [SeEstaEnviando]: candado `UPLOAD` vivo — el subidor tiene la venta en
 *   vuelo ahora mismo.
 * - [YaSeEnvio]: `ENVIADO = 1` y `CORRECCION_NO_ENVIADA = 0` — el servidor
 *   tiene exactamente lo que el teléfono cree que tiene.
 * - [LaRevisaLaOficina]: un fallo permanente del subidor, o `ENVIADO = 1`
 *   con `CORRECCION_NO_ENVIADA = 1` — el servidor tiene la venta pero NO la
 *   última corrección (se coló mientras el POST anterior seguía en vuelo).
 */
sealed interface EstadoCorreccion {
    data object Corregible : EstadoCorreccion
    data object SeEstaEnviando : EstadoCorreccion
    data object YaSeEnvio : EstadoCorreccion
    data object LaRevisaLaOficina : EstadoCorreccion
}

/**
 * Decide el [EstadoCorreccion] de una venta. Los dos arrendamientos que
 * deciden si el candado vigente venció NO son parámetros de esta función:
 * vienen de la fuente única ([com.example.msp_app.core.database.dao.localsale.LocalSaleClaimLeases],
 * vía [Reclamo]) para que nadie pueda llamar esto con un arrendamiento
 * inventado a mano.
 *
 * Precedencia, exhaustiva (ver la tabla espejo en
 * `EstadoCorreccionTest.kt`): `enviado` manda sobre todo; dentro de enviada,
 * `correccionNoEnviada` decide entre [YaSeEnvio] y [LaRevisaLaOficina]; sin
 * enviar, el fallo permanente manda sobre el candado; luego el candado
 * `UPLOAD` vivo; el resto es [Corregible].
 *
 * @param enviado columna `ENVIADO` de la fila.
 * @param permanente columna `LAST_UPLOAD_PERMANENT` (`true`/`false`, nunca
 *   se le pasa `null` — un `null` en la fila real significa "sin intento
 *   fallido", equivalente a `false` para este predicado).
 * @param correccionNoEnviada columna `CORRECCION_NO_ENVIADA`.
 * @param claimKind columna `CLAIM_KIND` cruda (`"EDIT"`, `"UPLOAD"`, `null`,
 *   vacía o cualquier otro valor — todo lo que no sea `EDIT`/`UPLOAD` cuenta
 *   como sin candado reconocible, ver [tipoCandadoDe]).
 * @param claimedAt columna `CLAIMED_AT` cruda.
 * @param ahora epoch millis del reloj inyectado (nunca reloj real en pruebas).
 */
fun evaluarCorregibilidad(
    enviado: Boolean,
    permanente: Boolean,
    correccionNoEnviada: Boolean,
    claimKind: String?,
    claimedAt: Long?,
    ahora: Long
): EstadoCorreccion {
    if (enviado) {
        return if (correccionNoEnviada) {
            EstadoCorreccion.LaRevisaLaOficina
        } else {
            EstadoCorreccion.YaSeEnvio
        }
    }

    if (permanente) {
        return EstadoCorreccion.LaRevisaLaOficina
    }

    val reclamo = Reclamo(tipoCandadoDe(claimKind), claimedAt)
    if (reclamo.kind == TipoCandado.UPLOAD && reclamo.estaVivo(ahora)) {
        return EstadoCorreccion.SeEstaEnviando
    }

    return EstadoCorreccion.Corregible
}
