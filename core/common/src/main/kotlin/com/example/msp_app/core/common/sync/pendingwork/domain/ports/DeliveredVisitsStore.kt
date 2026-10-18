package com.example.msp_app.core.common.sync.pendingwork.domain.ports

/**
 * El lado local de la reconciliación inversa (plan visitas P1, Tarea 13;
 * spec §4.6-1): las visitas que el teléfono ya soltó y las dos únicas
 * decisiones que el servidor puede provocar sobre ellas.
 *
 * Es el espejo de [PendingVisitsStore]: aquel pregunta por lo pendiente y lo
 * suelta; este pregunta por lo soltado y lo vuelve a la cola si el servidor no
 * lo tiene. Ninguno de los dos decide: decide el use case, y solo con una
 * respuesta del servidor a la tanda en la que se preguntó.
 */
interface DeliveredVisitsStore {

    /**
     * Las soltadas a revisar: confirmadas en o después de [desde] (formato de
     * cable de `AppTime`) y **todas** las legadas con `GUARDADO_EN_MICROSIP = 1`
     * y `CONFIRMADA_EN` nulo (anteriores a Room v35). Las resguardadas no: están
     * en la bandeja del servidor, no en `MSP_VISITAS`.
     */
    suspend fun entregadasDesde(desde: String): List<String>

    /**
     * El servidor nombró [ids] en `by-ids`: estampa `CONFIRMADA_EN` con `ahora`
     * solo si no la tenía; la que ya estaba confirmada conserva su instante
     * (E-VIS-028), o la poda nunca la soltaría.
     * Una visita con un comprobante sin subir no se toca (retención por foto).
     *
     * @return cuántas cambiaron de verdad.
     */
    suspend fun confirmar(ids: List<String>, ahora: String): Int

    /**
     * El servidor contestó la tanda y no las nombró: vuelven a pendiente
     * (`GUARDADO_EN_MICROSIP = 0`, marcas en nulo) para que el worker las
     * reenvíe; el servidor deduplica por UUID.
     *
     * @return cuántas cambiaron de verdad.
     */
    suspend fun devolverAPendiente(ids: List<String>): Int

    /**
     * Las fotos sin `SUBIDA_EN` de visitas YA CONFIRMADAS que todavía podrían
     * viajar: su archivo local existe y su tipo es de los que el envío manda
     * (barrido del HALLAZGO de E-APP-076; E-APP-079). Solo el adaptador puede
     * mirar el disco; la decisión (ventana, devolver o abandonar) es del use
     * case.
     */
    suspend fun fotosSinEnviarDeConfirmadas(): List<FotoSinEnviar>
}

/**
 * Una foto sin enviar de una visita confirmada: a qué visita pertenece y
 * cuándo se creó (`CREADA_EN`, formato de cable de `AppTime`).
 */
data class FotoSinEnviar(val visitaId: String, val creadaEn: String)
