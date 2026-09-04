package com.example.msp_app.data.visitas

import androidx.room.withTransaction
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.dao.visit.VisitRecommendationDao
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.core.utils.VisitStatusMapper
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.feature.visitas.application.VisitasTelemetria
import com.example.msp_app.feature.visitas.domain.port.RegistroDeVisitaPort
import com.example.msp_app.feature.visitas.domain.port.ResultadoDelRegistro
import com.example.msp_app.feature.visitas.domain.port.VisitaARegistrar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Implementación real de [RegistroDeVisitaPort], provista desde el composition
 * root de `:app` (precedente `RegistroDeAbonoPort` → `RegistroDeAbonoAdapter`).
 *
 * **No reimplementa la escritura de la visita.** El camino que ya corre en
 * producción es [VisitsLocalDataSource.saveVisitAndEnqueue], que inserta la
 * visita, propaga el alcance a las ventas que toca (Task 13) y **encola el
 * envío en la misma llamada** (Task 5). Reescribirlo rompería justamente la
 * propiedad que la Task 5 vino a garantizar: que el envío no cuelgue de que
 * `UpdateLocationService` haya corrido.
 *
 * ## Por qué el par va dentro de `db.withTransaction`
 *
 * La visita y el enlace con su recomendación son **un solo hecho**: "esto fue lo
 * que sugerimos y esto fue lo que pasó". Escribirlos sueltos podría dejar la
 * visita sin su mitad de sugerencia y volver ilegible la evaluación del
 * recomendador — que es justo lo que §10 dice que es imposible de reconstruir
 * después. Mismo patrón que ya usan `CobranzaReconciler` y `RegistroDeAbonoAdapter`.
 *
 * ## El borde `Money` → centavos
 *
 * `PROMESA_MONTO_CENTAVOS` es `Long` (centavos enteros), nunca `Double`. La
 * conversión ocurre **aquí y solo aquí**, con `movePointRight(2)` sobre el
 * `BigDecimal` de escala 2 de `Money`: exacto, sin pasar jamás por un flotante.
 *
 * [traerUsuario] es inyectable **solo para test** (fakes-only, sin MockK).
 */
class RegistroDeVisitaAdapter(
    private val db: AppDatabase,
    private val saleDao: SaleDao,
    private val visitas: VisitsLocalDataSource,
    private val recomendaciones: VisitRecommendationDao,
    private val telemetry: Telemetry,
    private val clock: AppClock = AppClock.System,
    private val traerUsuario: suspend () -> User? = ::usuarioAutenticado
) : RegistroDeVisitaPort {

    @Suppress(
        "TooGenericExceptionCaught"
    ) // Room/Firestore pueden fallar con cualquier excepción; nada quedó escrito.
    override suspend fun registrar(visita: VisitaARegistrar): ResultadoDelRegistro = try {
        // La venta da la atribución que el contrato de `VisitFactory` fija:
        // COBRADOR y ZONA_CLIENTE_ID salen de la venta, COBRADOR_ID del usuario.
        val venta = saleDao.getByClientId(visita.clienteId).firstOrNull()
        val usuario = traerUsuario()
        when {
            venta == null -> ResultadoDelRegistro.CLIENTE_NO_ESTA_EN_EL_TELEFONO
            usuario == null || usuario.COBRADOR_ID == 0 -> ResultadoDelRegistro.SIN_COBRADOR
            else -> {
                guardar(visita, venta.NOMBRE_COBRADOR, venta.ZONA_CLIENTE_ID, usuario.COBRADOR_ID)
                ResultadoDelRegistro.REGISTRADA
            }
        }
    } catch (cancelada: CancellationException) {
        // Se relanza ANTES del catch general: una cancelación no es un fallo de
        // escritura, y tragársela con una transacción adentro sería peor.
        throw cancelada
    } catch (fallo: Throwable) {
        // Anti-PII: viaja el nombre de la clase de la excepción, nunca su texto
        // (que puede arrastrar la nota o el nombre del cliente) ni el monto.
        telemetry.error(
            code = VisitasTelemetria.CODE_VISITA_NO_SE_GUARDO,
            message = "la escritura de la visita fallo; la transaccion no dejo nada escrito",
            props = mapOf(VisitasTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
        ResultadoDelRegistro.FALLO_EL_GUARDADO
    }

    private suspend fun guardar(
        visita: VisitaARegistrar,
        nombreCobrador: String,
        zonaClienteId: Int,
        cobradorId: Int
    ) {
        val entidad = entidadDe(visita, nombreCobrador, zonaClienteId, cobradorId)
        db.withTransaction {
            visitas.saveVisitAndEnqueue(
                saleId = entidad.IMPTE_DOCTO_CC_ID,
                visit = entidad,
                newState = VisitStatusMapper.map(entidad.TIPO_VISITA)
            )
            reagendarCobranzaLegada(entidad)
            ligarRecomendacion(visita)
        }
    }

    /**
     * Arma la fila. **Nada estructurado se serializa en [VisitEntity.NOTA]**: la
     * fecha va a `PROMESA_FECHA`, el monto a `PROMESA_MONTO_CENTAVOS` y la hora
     * a `CITA_HORA`. Meterlas en la nota —como hace hoy `NewVisitDialog` con
     * "La cita ha sido reagendada para el …"— es el defecto que este plan vino a
     * arreglar.
     *
     * `LAT`/`LNG` en cero cuando no hubo ubicación, exactamente como ya hace
     * `VisitFactory` mientras `UpdateLocationService` parcha las columnas
     * después. La ubicación **nunca** bloquea el guardado.
     */
    private fun entidadDe(
        visita: VisitaARegistrar,
        nombreCobrador: String,
        zonaClienteId: Int,
        cobradorId: Int
    ): VisitEntity = VisitEntity(
        ID = visita.visitaId,
        CLIENTE_ID = visita.clienteId,
        COBRADOR = nombreCobrador,
        COBRADOR_ID = cobradorId,
        FECHA = AppTime.toWireFormat(clock.now()),
        // El diálogo de hoy tampoco tiene selector de forma de cobro para las
        // visitas: el contrato de cable lleva el campo y siempre viaja en 0.
        FORMA_COBRO_ID = 0,
        LAT = visita.ubicacion?.lat ?: 0.0,
        LNG = visita.ubicacion?.lng ?: 0.0,
        NOTA = visita.nota,
        TIPO_VISITA = visita.tipoVisita,
        ZONA_CLIENTE_ID = zonaClienteId,
        IMPTE_DOCTO_CC_ID = visita.ventaId ?: SIN_VENTA,
        GUARDADO_EN_MICROSIP = 0,
        PROMESA_VENTA_ID = visita.promesa?.ventaId,
        PROMESA_FECHA = visita.promesa?.let { AppTime.toWireDate(it.fecha) },
        PROMESA_MONTO_CENTAVOS = visita.promesa?.monto?.let { aCentavos(it.amount) },
        CITA_FECHA = visita.cita?.let { AppTime.toWireDate(it.fecha) },
        CITA_HORA = visita.cita?.hora?.let(HORA_DE_CITA::format)
    )

    /**
     * Mantiene `DIA_TEMPORAL_COBRANZA` como lo deja hoy `NewVisitDialog` cuando
     * el cliente pide reagendar.
     *
     * No es una segunda fuente de verdad: la derivación del periodo **no** lee
     * esa columna (lee `PROMESA_FECHA`/`CITA_FECHA`). Se sigue escribiendo
     * porque la pantalla de ventas legada sí la lee, y mientras las dos convivan
     * una visita capturada aquí no debe verse distinta de una capturada allá.
     * Cuando la lista legada se retire, esta línea se va con ella.
     */
    private suspend fun reagendarCobranzaLegada(entidad: VisitEntity) {
        val dia = entidad.PROMESA_FECHA ?: entidad.CITA_FECHA ?: return
        if (entidad.IMPTE_DOCTO_CC_ID == SIN_VENTA) return
        saleDao.updateTemporaryCollectionDate(saleId = entidad.IMPTE_DOCTO_CC_ID, newDate = dia)
    }

    /**
     * Ata la recomendación mostrada a la visita que la atendió — el par
     * "qué sugirió el sistema" / "qué hizo el cobrador".
     *
     * Si la fila ya no está, el par se pierde para esta visita y **eso no se
     * calla**: se emite [VisitasTelemetria.CODE_RECOMENDACION_NO_LIGADA]. La
     * visita se queda escrita: el trabajo de campo pesa más que el enlace.
     */
    private suspend fun ligarRecomendacion(visita: VisitaARegistrar) {
        val recomendacionId = visita.recomendacionId ?: return
        val ligadas = recomendaciones.ligarConVisita(recomendacionId, visita.visitaId)
        if (ligadas == 0) {
            telemetry.error(
                code = VisitasTelemetria.CODE_RECOMENDACION_NO_LIGADA,
                message = "la recomendacion mostrada ya no estaba al guardar la visita",
                props = emptyMap()
            )
        }
    }

    private companion object {
        /** El centinela de "sin venta ligada" que ya usa el schema. */
        const val SIN_VENTA: Int = 0

        /** De pesos con dos decimales a centavos enteros. Exacto, sin flotantes. */
        fun aCentavos(pesos: BigDecimal): Long =
            pesos.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }
}

/**
 * `HH:mm` — el MISMO patrón con el que `RoomVisitasAdapter` lee `CITA_HORA`.
 * Escritor y lector citan la misma constante de `AppTime.Formats`, así que no
 * pueden despegarse.
 */
private val HORA_DE_CITA: DateTimeFormatter =
    DateTimeFormatter.ofPattern(AppTime.Formats.TIME_24H)

/**
 * El usuario autenticado desde Firestore (`users` where `EMAIL == email`) — la
 * MISMA resolución que usa `RegistroDeAbonoAdapter`. Su `COBRADOR_ID` es a quién
 * se le atribuye la visita: al que está parado frente al cliente, no al cobrador
 * de la venta (contrato de atribución de `VisitFactory`).
 */
private suspend fun usuarioAutenticado(): User? {
    val email = FirebaseAuth.getInstance().currentUser?.email ?: return null
    val snapshot = FirebaseFirestore.getInstance()
        .collection(Constants.USERS_COLLECTION)
        .whereEqualTo("EMAIL", email)
        .get()
        .await()
    val doc = snapshot.documents.firstOrNull() ?: return null
    return doc.toObject(User::class.java)?.copy(ID = doc.id)
}
