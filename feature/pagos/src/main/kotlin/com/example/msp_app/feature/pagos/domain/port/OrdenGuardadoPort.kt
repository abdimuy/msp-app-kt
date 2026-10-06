package com.example.msp_app.feature.pagos.domain.port

import com.example.msp_app.feature.pagos.domain.OrdenDeLaLista
import java.time.LocalDate

/**
 * El [orden] elegido en la hoja "Ordenar por" y el [dia] de negocio en que se
 * elegió — las dos piezas que la persistencia "durante el día" necesita
 * juntas (decisión del dueño, 2026-10-05).
 *
 * Guardar el orden solo no alcanza: sin el día no hay forma de distinguir "lo
 * eligió hace un minuto" de "lo eligió ayer y se quedó pegado", que es
 * exactamente el defecto que esta pareja existe para impedir.
 */
data class OrdenGuardado(val orden: OrdenDeLaLista, val dia: LocalDate)

/**
 * Puerto hacia la persistencia del orden elegido en la lista de clientes,
 * "durante el día" (decisión del dueño, 2026-10-05).
 *
 * ## Por qué un puerto y no `SavedStateHandle`
 *
 * `SavedStateHandle` sobrevive a la rotación y a que el proceso muera con la
 * pantalla en memoria, pero no a que el cobrador cierre la app y la vuelva a
 * abrir al día siguiente — y es justo ESE caso el que la regla de negocio
 * quiere cortar: el orden se recuerda "durante el día", no para siempre. Hace
 * falta una fuente que sobreviva más que el proceso (de ahí el puerto, con
 * adaptador en `:app`) y que sin embargo expire con el día (de ahí que
 * [OrdenGuardado] viaje siempre con su fecha, y quien lee decide si todavía
 * aplica).
 *
 * El adaptador real (en `:app`, siguiente tarea) resuelve dónde vive el dato
 * —`SharedPreferences`/DataStore, a su criterio—; este puerto no lo prescribe.
 *
 * SIN un tercer método "borrar": [guardar] con [com.example.msp_app.feature.pagos.domain.OrdenDeLaLista.COBRANZA]
 * ya expresa "quitar el orden" sin necesitar un segundo camino para la misma
 * intención.
 */
interface OrdenGuardadoPort {

    /** Lo último guardado, o `null` si nunca se ha elegido un orden distinto de cobranza. */
    suspend fun leer(): OrdenGuardado?

    /** Guarda [orden] junto con el día de negocio [dia] en que se eligió. */
    suspend fun guardar(orden: OrdenDeLaLista, dia: LocalDate)
}
