package com.example.msp_app.feature.garantias.domain.port

/**
 * Lo que dejó una pasada de la cola de garantías.
 *
 * El trabajador lo usa para decidir si vuelve a intentar: mientras quede un
 * comando o una foto pendiente, `Result.retry()`; cuando no quede nada, el
 * trabajo terminó. Los contadores no son telemetría: [comandosEnviados] y
 * [fotosSubidas] permiten que la prueba de la cola afirme qué salió en una
 * pasada sin leer la base, y [sinRed] distingue "no había señal" de "el
 * servidor dijo que no".
 *
 * No expone el detalle de cada fila: la pantalla lee la cola por su puerto y
 * no depende de este resumen.
 */
data class ResultadoDeLaCola(
    val comandosEnviados: Int = 0,
    val comandosRechazados: Int = 0,
    val fotosSubidas: Int = 0,
    val fotosRechazadas: Int = 0,
    val comandosPendientes: Int = 0,
    val fotosPendientes: Int = 0,
    val sinRed: Boolean = false
) {

    /** Todavía hay trabajo que un reintento puede resolver. */
    val quedaTrabajo: Boolean
        get() = comandosPendientes > 0 || fotosPendientes > 0
}

/**
 * La pasada completa de la cola: comandos en orden por folio y después las
 * fotos que ya tienen su evento.
 *
 * Es un puerto y no una llamada directa del trabajador a `data/queue` porque
 * el trabajador vive en `:app` y el procesador es una pieza interna del
 * módulo. También deja que la prueba del trabajador use un procesador falso sin
 * tocar Room ni la red.
 */
interface ProcesadorDeGarantias {
    suspend fun procesar(): ResultadoDeLaCola
}
