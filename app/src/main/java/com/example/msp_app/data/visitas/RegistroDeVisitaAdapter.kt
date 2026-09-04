package com.example.msp_app.data.visitas

import androidx.room.withTransaction
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.dao.visit.VisitRecommendationDao
import com.example.msp_app.core.database.entities.SaleWithProductsEntity
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

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
        val venta = cuentaDeAtribucion(visita.clienteId)
        val usuario = traerUsuario()
        when {
            venta == null -> ResultadoDelRegistro.CLIENTE_NO_ESTA_EN_EL_TELEFONO
            usuario == null || usuario.COBRADOR_ID == 0 -> ResultadoDelRegistro.SIN_COBRADOR
            else -> {
                guardar(visita, venta, usuario.COBRADOR_ID)
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

    /**
     * La cuenta de la que sale la atribución (`COBRADOR`, `ZONA_CLIENTE_ID`) y,
     * como último escalón, la cuenta de la visita — **elegida de forma
     * determinista**.
     *
     * `SaleDao.getByClientId` agrupa por `DOCTO_CC_ID` y **no lleva `ORDER
     * BY`**. En la práctica SQLite suele emitir en orden ascendente de la llave
     * del grupo, pero nada lo garantiza: ni el plan de consulta, ni una versión
     * distinta del motor, ni la misma base después de un `VACUUM`. Con
     * `firstOrNull()`, la venta a la que aterriza una promesa "del cliente
     * completo" podía cambiar entre dos corridas de la app sin que nada en el
     * dato cambiara. Es la misma forma que la Task 17 tuvo que arreglar con un
     * desempate explícito, y por la misma razón.
     *
     * El desempate es **el `DOCTO_CC_ACR_ID` más bajo**: es la llave primaria de
     * la venta, así que es única y total —no hay empate posible— y estable entre
     * dispositivos y corridas. Además tiene lectura de negocio: es la cuenta más
     * vieja del cliente, que es el default razonable cuando el cobrador no
     * eligió ninguna.
     */
    private suspend fun cuentaDeAtribucion(clienteId: Int): SaleWithProductsEntity? =
        saleDao.getByClientId(clienteId).minByOrNull { it.DOCTO_CC_ACR_ID }

    /**
     * Escribe el hecho completo y encola el envío — **las dos cosas o ninguna**.
     *
     * ## Por qué el encolado quedó FUERA de la transacción
     *
     * Dentro de `withTransaction` tenía un defecto propio: si algo posterior
     * lanzaba (el enlace de la recomendación, por ejemplo), la fila se revertía
     * y el trabajo de WorkManager ya estaba agendado, así que el worker
     * despertaba a buscar una visita que no existe.
     *
     * ## Por qué el par va dentro de `NonCancellable`
     *
     * Sacarlo de la transacción abrió una ventana peor que la que cerró.
     * `registrar` corre bajo el `viewModelScope` de la pantalla, que se cancela
     * en cuanto el cobrador navega hacia atrás. Y una cancelación no se observa
     * solo "entre las dos líneas": `withTransaction` termina en un `withContext`,
     * y **`withContext` lanza al reanudar si el job se canceló mientras el bloque
     * corría, aunque el bloque haya terminado bien** — o sea, con la transacción
     * ya *comiteada*. El resultado sería una visita escrita y jamás encolada:
     * **exactamente el defecto que la Task 5 existió para arreglar**, alcanzado
     * por otra puerta.
     *
     * Por eso el `NonCancellable` envuelve el par completo y no solo el
     * encolado: envolver solo la segunda línea no sirve de nada si la
     * cancelación se observa al salir de la primera. Con esto:
     *
     * - una **falla** de la transacción revierte la fila y no encola nada;
     * - una **cancelación** no puede colarse entre el commit y el encolado;
     * - la **muerte del proceso** entre los dos sigue siendo recuperable —
     *   `VisitsPendingSynchronizer` reencola en el siguiente login todo lo que
     *   quedó en `GUARDADO_EN_MICROSIP = 0`.
     *
     * El bloque no bloquea nada perceptible: son tres escrituras locales a
     * SQLite y un `enqueue` de WorkManager, todo sin red.
     */
    private suspend fun guardar(
        visita: VisitaARegistrar,
        venta: SaleWithProductsEntity,
        cobradorId: Int
    ) {
        val entidad = entidadDe(visita, venta, cobradorId)
        withContext(NonCancellable) {
            db.withTransaction {
                visitas.insertVisitAndUpdateState(
                    saleId = entidad.IMPTE_DOCTO_CC_ID,
                    visit = entidad,
                    newState = VisitStatusMapper.map(entidad.TIPO_VISITA)
                )
                reagendarCobranzaLegada(entidad)
                ligarRecomendacion(visita)
            }
            visitas.enqueueUpload(entidad.ID)
        }
    }

    /**
     * Arma la fila. **Nada estructurado se serializa en [VisitEntity.NOTA]**: la
     * fecha va a `PROMESA_FECHA`, el monto a `PROMESA_MONTO_CENTAVOS` y la hora
     * a `CITA_HORA`. Meterlas en la nota —como hacía el `NewVisitDialog` con
     * "La cita ha sido reagendada para el …", retirado en la Task 21— es el
     * defecto que este plan vino a arreglar.
     *
     * `LAT`/`LNG` en cero cuando no hubo ubicación, exactamente como ya hace
     * `VisitFactory` mientras `UpdateLocationService` parcha las columnas
     * después. La ubicación **nunca** bloquea el guardado.
     */
    private fun entidadDe(
        visita: VisitaARegistrar,
        venta: SaleWithProductsEntity,
        cobradorId: Int
    ): VisitEntity = VisitEntity(
        ID = visita.visitaId,
        CLIENTE_ID = visita.clienteId,
        COBRADOR = venta.NOMBRE_COBRADOR,
        COBRADOR_ID = cobradorId,
        FECHA = AppTime.toWireFormat(clock.now()),
        // El diálogo de hoy tampoco tiene selector de forma de cobro para las
        // visitas: el contrato de cable lleva el campo y siempre viaja en 0.
        FORMA_COBRO_ID = 0,
        LAT = visita.ubicacion?.lat ?: 0.0,
        LNG = visita.ubicacion?.lng ?: 0.0,
        NOTA = visita.nota,
        TIPO_VISITA = visita.tipoVisita,
        ZONA_CLIENTE_ID = venta.ZONA_CLIENTE_ID,
        IMPTE_DOCTO_CC_ID = cuentaDeLaVisita(visita, venta),
        GUARDADO_EN_MICROSIP = 0,
        PROMESA_VENTA_ID = visita.promesa?.ventaId,
        PROMESA_FECHA = visita.promesa?.let { AppTime.toWireDate(it.fecha) },
        PROMESA_MONTO_CENTAVOS = visita.promesa?.monto?.let { aCentavos(it.amount) },
        CITA_FECHA = visita.cita?.let { AppTime.toWireDate(it.fecha) },
        CITA_HORA = visita.cita?.hora?.let(HORA_DE_CITA::format)
    )

    /**
     * La cuenta a la que se ata la visita (`IMPTE_DOCTO_CC_ID`), en tres
     * escalones y **nunca en 0**.
     *
     * ## El defecto que esto cierra
     *
     * Antes se escribía `visita.ventaId ?: 0`. La lectura traduce `0` a `null`
     * (`RoomVisitasAdapter`), y `EstadoCuentaDeriver` manda una visita de
     * alcance VENTA sin venta ligada a `sueltas` —la cuenta como `huerfanas` y
     * **no la indexa**—. `PROMETIO_PROXIMA` es de alcance VENTA, así que una
     * promesa capturada desde el detalle de CLIENTE (un punto de entrada que la
     * ruta sostiene a propósito) se escribía en Room y después desaparecía de la
     * derivación. Eso vacía "la promesa existe para poder consultarse" en uno de
     * los dos caminos de entrada.
     *
     * ## Los tres escalones
     *
     * 1. **La venta que el cliente eligió para la promesa.** Si el cobrador tocó
     *    "de cuál venta", esa es la cuenta de la visita — y con eso el chip deja
     *    de ser un control que promete un efecto que no tenía.
     * 2. **La cuenta por la que se entró**, cuando no hubo promesa (o la promesa
     *    fue del cliente completo).
     * 3. **La cuenta más vieja del cliente** —el `DOCTO_CC_ACR_ID` más bajo—,
     *    la misma fila que ya dio la atribución (`COBRADOR`, `ZONA_CLIENTE_ID`),
     *    cuando se entró por el cliente y no se eligió venta. La elige
     *    [cuentaDeAtribucion] con un desempate explícito: la consulta que las
     *    trae **no tiene `ORDER BY`**.
     *
     * ## Por qué NO choca con la regla de alcance de la Task 13
     *
     * El alcance no sale de esta columna: sale de `VisitScopeMapper`, sobre el
     * literal y el día de la cita. Una visita de alcance CLIENTE ("no estaba",
     * "cita") **ignora** este `saleId` y se propaga por `CLIENTE_ID`, así que
     * darle una cuenta concreta no cambia ni una fila. Una de alcance VENTA solo
     * mejora: antes `updateTotal(0, …)` no tocaba ninguna fila, ahora toca la
     * que corresponde. Ninguna visita alcanza una venta de otro cliente: los
     * tres escalones salen de `getByClientId(visita.clienteId)` o del argumento
     * del propio destino.
     *
     * [VisitEntity.PROMESA_VENTA_ID] se sigue escribiendo aparte a propósito:
     * distingue "la promesa fue sobre esta cuenta" de "la visita se abrió sobre
     * esta cuenta", que coinciden casi siempre pero no son lo mismo.
     */
    private fun cuentaDeLaVisita(visita: VisitaARegistrar, venta: SaleWithProductsEntity): Int =
        visita.promesa?.ventaId
            ?: visita.ventaId
            ?: venta.DOCTO_CC_ACR_ID

    /**
     * Mantiene `DIA_TEMPORAL_COBRANZA` como lo dejaba el `NewVisitDialog` cuando
     * el cliente pedía reagendar.
     *
     * No es una segunda fuente de verdad: la derivación del periodo **no** lee
     * esa columna (lee `PROMESA_FECHA`/`CITA_FECHA`). La Task 21 retiró la lista
     * legada que sí la pintaba, pero la línea **se queda**: la columna sigue
     * viajando en el modelo, `CobranzaSyncManager` la preserva a propósito en
     * cada merge incremental (`DIA_TEMPORAL_COBRANZA = existing.…`) y dejar de
     * escribirla haría que una visita capturada hoy se viera distinta de las que
     * ya están en los teléfonos. Retirarla es una limpieza con su propia
     * verificación, no un efecto colateral de mover un botón.
     */
    private suspend fun reagendarCobranzaLegada(entidad: VisitEntity) {
        val dia = entidad.PROMESA_FECHA ?: entidad.CITA_FECHA ?: return
        // Sin guarda por `IMPTE_DOCTO_CC_ID == 0`: `cuentaDeLaVisita` nunca
        // devuelve 0, y una rama que no puede dispararse es ruido que envejece.
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
