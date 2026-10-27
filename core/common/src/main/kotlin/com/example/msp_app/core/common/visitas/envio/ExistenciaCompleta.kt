package com.example.msp_app.core.common.visitas.envio

/**
 * Spec visitas §4.2-3: "existencia COMPLETA", no existencia a secas.
 *
 * Solo [COMPLETA] puede soltar una visita por existencia. Las otras tres
 * significan lo mismo para el worker (no se suelta por esta vía) y se
 * distinguen solo para el diagnóstico.
 */
enum class Existencia { COMPLETA, INCOMPLETA, NO_EXISTE, INDETERMINADA }

/**
 * Lo que devolvió `GET /v2/visitas/{id}`.
 *
 * `imagenIds` nulo = el servidor no lo dijo (servidor viejo que omite el
 * campo; Gson no aplica defaults de Kotlin). El servidor nuevo lo manda
 * SIEMPRE, `[]` si no hay fotos, y lista solo las fotos con fila Y archivo en
 * disco al momento del GET (E-VIS-022).
 */
data class VisitaEnServidor(
    val id: String?,
    val imagenIds: List<String>?,
    /**
     * Los bloques de compromiso que el servidor dice tener (contrato 3,
     * spec visitas §5.2.4). `null` = no lo dijo: con Gson un servidor viejo
     * (clave ausente) y una fila sin bloques (`null` explícito) se leen igual,
     * así que el teléfono nunca distingue "sin bloque" de "no sé"; por eso
     * nulo NUNCA prueba un bloque local.
     */
    val compromiso: CompromisoLocal? = null
)

/**
 * Los cinco campos del compromiso de una visita, con los mismos nombres y
 * formatos que Room y el cable (spec visitas §5.2.2): fechas civiles
 * `yyyy-MM-dd`, hora `HH:mm`, monto en centavos. Dos bloques independientes:
 * la promesa (venta, fecha, monto) y la cita (fecha, hora).
 */
data class CompromisoLocal(
    val promesaVentaId: Int?,
    val promesaFecha: String?,
    val promesaMontoCentavos: Long?,
    val citaFecha: String?,
    val citaHora: String?
) {
    /** Hay promesa si viene cualquiera de sus campos (el servidor exige la fecha). */
    val tienePromesa: Boolean
        get() = promesaVentaId != null || promesaFecha != null || promesaMontoCentavos != null

    /** Hay cita si viene cualquiera de sus campos (el servidor exige la fecha). */
    val tieneCita: Boolean get() = citaFecha != null || citaHora != null

    val vacio: Boolean get() = !tienePromesa && !tieneCita

    companion object {
        val NINGUNO = CompromisoLocal(null, null, null, null, null)
    }
}

/**
 * ¿[enServidor] prueba el compromiso [local]? Cada bloque que el teléfono tiene
 * (promesa, cita) debe estar en el servidor IGUAL, campo por campo (spec
 * visitas §5.2.3: el bloque se compara entero). Un bloque de más en el
 * servidor no estorba (I10: la ausencia nunca borra). Sin bloques locales no
 * hay nada que probar. Comparación exacta de texto: un desajuste solo deja el
 * compromiso por rellenar, nunca lo da por entregado (E-APP-098).
 */
fun compromisoProbado(local: CompromisoLocal, enServidor: CompromisoLocal?): Boolean {
    if (local.vacio) return true
    if (enServidor == null) return false
    val promesaIgual = !local.tienePromesa ||
        (
            enServidor.promesaVentaId == local.promesaVentaId &&
                enServidor.promesaFecha == local.promesaFecha &&
                enServidor.promesaMontoCentavos == local.promesaMontoCentavos
            )
    val citaIgual = !local.tieneCita ||
        (enServidor.citaFecha == local.citaFecha && enServidor.citaHora == local.citaHora)
    return promesaIgual && citaIgual
}

/**
 * ¿El servidor tiene la visita [enviada] completa? Función pura y total.
 *
 * COMPLETA exige las tres cosas: GET 200, cuerpo con el MISMO id (no vacío),
 * y cada foto local pendiente presente en `imagen_ids`. Un 200 con otro id o
 * sin cuerpo (proxy, portal cautivo) no prueba nada: INDETERMINADA. Una foto
 * pendiente que el servidor no lista, o un servidor que no dice cuáles tiene,
 * es INCOMPLETA: la visita no se suelta (Review Focus 4 del plan P1).
 *
 * La comparación de ids es exacta, a propósito: un desajuste solo puede
 * dejar la visita reintentando, nunca soltarla sin prueba.
 *
 * Con [compromisoLocal] (el que viajó con contrato 3), COMPLETA exige además
 * que [compromisoProbado] lo encuentre igual en el servidor. Nulo = no se
 * mandó compromiso: la regla de antes, intacta.
 */
fun existenciaCompleta(
    enviada: String,
    respuesta: VisitaEnServidor?,
    fotosPendientes: Set<String>,
    codigoGet: Int?,
    compromisoLocal: CompromisoLocal? = null
): Existencia = when {
    codigoGet == HTTP_NOT_FOUND -> Existencia.NO_EXISTE
    codigoGet != HTTP_OK || respuesta == null -> Existencia.INDETERMINADA
    enviada.isBlank() || respuesta.id != enviada -> Existencia.INDETERMINADA
    // Contrato 3 (spec §4.2-3/-5, E-APP-098): existencia sin el compromiso no suelta.
    compromisoLocal != null && !compromisoProbado(compromisoLocal, respuesta.compromiso) ->
        Existencia.INCOMPLETA
    fotosPendientes.isEmpty() -> Existencia.COMPLETA
    respuesta.imagenIds == null -> Existencia.INCOMPLETA
    respuesta.imagenIds.containsAll(fotosPendientes) -> Existencia.COMPLETA
    else -> Existencia.INCOMPLETA
}

private const val HTTP_OK = 200
private const val HTTP_NOT_FOUND = 404
