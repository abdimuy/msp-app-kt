package com.example.msp_app.feature.pagos.domain.port

import com.example.msp_app.feature.pagos.domain.model.FichaDelCliente

/**
 * Cómo terminó el guardado de la ficha.
 *
 * **[Guardada] carga la ficha que quedó escrita**, y no un simple "sí": la
 * pantalla la pinta tal cual, con su `ACTUALIZADA_EN` real, en vez de recargar
 * el detalle entero —cinco lecturas y una consulta a Firestore— para reflejar
 * una nota. Que el dato venga de quien lo escribió es lo que impide que lo
 * pintado se despegue de lo guardado.
 *
 * No existe un `SIN_COBRADOR` aquí, y esa ausencia es la decisión: la ficha
 * **no es dinero**. `cliente_ficha.COBRADOR_ID` es nullable a propósito, así
 * que no saber quién edita no puede impedir que el conocimiento se guarde —
 * exactamente al revés que un abono, que sin cobrador *"es dinero que nadie
 * entregó"*.
 */
sealed interface ResultadoDeLaFicha {

    /** Quedó guardada: la nota y el conjunto de señales, juntos. */
    data class Guardada(val ficha: FichaDelCliente) : ResultadoDeLaFicha

    /** Falló la escritura. Nada quedó a medias: es una transacción. */
    data object FalloElGuardado : ResultadoDeLaFicha
}

/**
 * La ficha del cliente — el catálogo cerrado y la nota libre.
 *
 * **El puerto se queda en el módulo y el adaptador se va a `:app`**
 * (precedente `LiquidacionPort` → `SettlementLiquidacionAdapter`): escribir la
 * ficha necesita el `COBRADOR_ID` del usuario autenticado, que se resuelve
 * contra Firestore y solo existe en `:app`. Cruza módulo, así que el puerto
 * está justificado frente a YAGNI.
 *
 * **La ficha no bloquea nada del pago.** Ni su lectura ni su escritura entran
 * al camino del abono: el pago sigue soberano.
 */
interface FichaDelClientePort {

    /**
     * La ficha de [clienteId], o **`null` si no se pudo leer**.
     *
     * Los dos casos NO se aplanan, y ésta es la razón concreta: una ficha vacía
     * se pinta editable, y si un fallo de lectura se disfrazara de "no hay
     * nada", el cobrador vería la ficha en blanco, escribiría encima y
     * **borraría el conocimiento que sí estaba guardado**. Es el mismo defecto
     * D5 que separó "no hay dato" de "no se pudo leer" en `CycleStart`, pero
     * aquí el precio es información irrecuperable.
     *
     * Un cliente sin ficha devuelve una [FichaDelCliente] vacía, no `null`.
     *
     * **Total: no lanza.** El detalle de cliente es una pantalla de dinero y no
     * puede caerse porque falle una nota.
     */
    suspend fun fichaDe(clienteId: Int): FichaDelCliente?

    /**
     * Guarda la ficha de [clienteId]. Total: no lanza, contesta con un
     * [ResultadoDeLaFicha].
     *
     * [FichaDelCliente.actualizada] del argumento **se ignora**: la fecha la
     * pone el adaptador con el reloj de la app (`AppTime`/`AppClock` es la
     * única fuente), no quien llama.
     */
    suspend fun guardar(clienteId: Int, ficha: FichaDelCliente): ResultadoDeLaFicha
}
