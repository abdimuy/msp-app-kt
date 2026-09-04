package com.example.msp_app.feature.visitas.domain.port

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.visitas.domain.model.ContextoDeVisita
import com.example.msp_app.feature.visitas.domain.model.RecomendacionMostrada
import com.example.msp_app.feature.visitas.domain.model.VisitaRegistrada
import java.time.LocalDate
import java.time.LocalTime

/**
 * La visita que se va a escribir, ya validada. Viaja en tipos de dominio: el
 * monto es [Money] hasta el adaptador, que es el ÚNICO lugar donde cruza a los
 * **centavos enteros** de `PROMESA_MONTO_CENTAVOS` (REGLA DE DINERO).
 *
 * [visitaId] lo acuña la pantalla una sola vez y vive en su `SavedStateHandle`:
 * es el id de la visita y la clave de idempotencia del envío, igual que ya hace
 * el abono (Task 18). Reintentar con el mismo id no puede producir dos visitas.
 *
 * [ventaId] es la cuenta que el cobrador tenía abierta (`IMPTE_DOCTO_CC_ID`);
 * [PromesaEstructurada.ventaId] es sobre cuál prometió, que puede ser otra o
 * ninguna.
 */
data class VisitaARegistrar(
    val visitaId: String,
    val clienteId: Int,
    val ventaId: Int?,
    val tipoVisita: String,
    val nota: String?,
    val promesa: PromesaEstructurada? = null,
    val cita: CitaEstructurada? = null,
    val ubicacion: UbicacionDeLaVisita? = null,
    /** La recomendación que llevó al cobrador a esta puerta, si la hubo. */
    val recomendacionId: String? = null
)

/**
 * La promesa, **estructurada**: venta + fecha + monto. Hoy eso es texto libre
 * con una fecha adentro, y por eso el cumplimiento no se puede medir.
 *
 * [fecha] NO es anulable: una promesa sin fecha no difiere nada y no es una
 * promesa (ver `BloqueoDeLaVisita.PROMESA_SIN_FECHA`). [monto] sí lo es —
 * "dijo cuándo pero no cuánto" es un caso real de campo, y `null` es distinto
 * de cero, que significaría "prometió no pagar".
 */
data class PromesaEstructurada(
    val ventaId: Int?,
    val fecha: LocalDate,
    val monto: Money?
)

/**
 * La cita, con su hora como **campo** y no embebida en el texto de `NOTA`.
 *
 * [fecha] NO es anulable y [hora] sí: el mock contempla los tres casos —hoy con
 * hora, otro día con hora, y otro día **sin** hora—, y sin día una cita no cae
 * en ningún segmento de la lista (Task 17).
 */
data class CitaEstructurada(
    val fecha: LocalDate,
    val hora: LocalTime?
)

/** Dónde se registró la visita. `null` cuando no se pudo saber; nunca bloquea. */
data class UbicacionDeLaVisita(val lat: Double, val lng: Double)

/**
 * Cómo terminó el registro. Un enum y no una excepción: los finales son
 * distintos para el cobrador y para el guard anti-duplicado de la pantalla,
 * que solo se libera cuando NADA se escribió.
 */
enum class ResultadoDelRegistro {
    /** La visita quedó en la base y su envío quedó encolado. */
    REGISTRADA,

    /** El teléfono ya no tiene ninguna venta de ese cliente: no hay a qué ligarla. */
    CLIENTE_NO_ESTA_EN_EL_TELEFONO,

    /**
     * No se pudo saber qué cobrador registra. Nada se escribió: una visita sin
     * `COBRADOR_ID` no es atribuible a nadie, y el diálogo de hoy ya la rechaza.
     */
    SIN_COBRADOR,

    /** La escritura falló. Nada quedó escrito: es una transacción. */
    FALLO_EL_GUARDADO
}

/**
 * Escribe la visita.
 *
 * **El puerto se queda en el módulo, el adaptador se va a `:app`** (precedente
 * `RegistroDeAbonoPort` → `RegistroDeAbonoAdapter`): el camino de escritura ya
 * existe y corre en producción —`VisitsLocalDataSource.saveVisitAndEnqueue`,
 * que inserta la visita, propaga el alcance y **encola el envío en la misma
 * llamada** (Task 5)— y esta tarea lo CONSUME. Reimplementarlo rompería
 * justamente la propiedad que la Task 5 vino a garantizar.
 */
interface RegistroDeVisitaPort {

    /** Registra [visita]. Total: no lanza, contesta con un [ResultadoDelRegistro]. */
    suspend fun registrar(visita: VisitaARegistrar): ResultadoDelRegistro
}

/**
 * Lee de vuelta una visita **ya registrada**, por su id.
 *
 * Existe para el ticket de visita (Task 20): su ruta lleva solo el `visitaId`
 * —así vuelve entera después de una rotación o de que el proceso muera con el
 * picker de Bluetooth encima— y de esa fila salen las dos cosas que el ticket no
 * puede inventar: **cuándo** se registró la visita (la entrada de la regla del
 * día del cobro) y **quién** la registró.
 *
 * Cruza a `:core:database`, que es lo que lo justifica frente a YAGNI. `null`
 * cuando el teléfono ya no tiene esa visita: es un estado normal de la pantalla
 * —la poda de sincronizadas la pudo haber borrado— y no una excepción.
 */
interface VisitaImpresaPort {

    suspend fun visita(visitaId: String): VisitaRegistrada?
}

/** El cliente y sus cuentas, para pintar la pantalla. Solo lectura. */
interface ContextoDeVisitaPort {
    suspend fun contexto(clienteId: Int): ContextoDeVisita?
}

/** Lo que el recomendador sugirió para este cliente, si sugirió algo. */
interface RecomendacionesPort {
    suspend fun vigenteDe(clienteId: Int): RecomendacionMostrada?
}

/**
 * La ubicación del teléfono, si se puede saber.
 *
 * **Nunca bloquea el guardado.** Devuelve `null` cuando el permiso está negado,
 * el proveedor no contesta o el hardware falla — el mismo trato que la foto
 * ("la foto nunca bloquea el guardado"). El adaptador vive en `:app` porque
 * necesita `Context` y Play Services.
 */
interface UbicacionPort {
    suspend fun ubicacionActual(): UbicacionDeLaVisita?
}
