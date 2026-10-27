package com.example.msp_app.data.visitas

import androidx.room.withTransaction
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.captura.CapturaEnCursoDao
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.dao.visit.VisitImageDao
import com.example.msp_app.core.database.dao.visit.VisitRecommendationDao
import com.example.msp_app.core.database.entities.CapturaEnCursoEntity
import com.example.msp_app.core.database.entities.SaleWithProductsEntity
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.database.entities.VisitImageEntity
import com.example.msp_app.core.database.escribirSinCancelar
import com.example.msp_app.core.database.sinCancelar
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.core.utils.VisitStatusMapper
import com.example.msp_app.data.auth.emailDeLaSesion
import com.example.msp_app.data.captura.CapturasEnProceso
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
import java.time.Instant
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
 * ## Las fotos van entre el commit y el encolado (Task 23)
 *
 * Ver [guardarComprobantes]: fuera de la transacción para que una foto no pueda
 * revertir la visita, y antes de `enqueueUpload` para que el worker no suba la
 * visita sin ellas. Las dos mitades tienen su test.
 *
 * ## El borde `Money` → centavos
 *
 * `PROMESA_MONTO_CENTAVOS` es `Long` (centavos enteros), nunca `Double`. La
 * conversión ocurre **aquí y solo aquí**, con `movePointRight(2)` sobre el
 * `BigDecimal` de escala 2 de `Money`: exacto, sin pasar jamás por un flotante.
 *
 * [traerUsuario] es inyectable **solo para test** (fakes-only, sin MockK).
 */
@Suppress(
    "LongParameterList"
) // las dependencias reales del adaptador; el DAO de fotos es la septima.
class RegistroDeVisitaAdapter(
    private val db: AppDatabase,
    private val saleDao: SaleDao,
    private val visitas: VisitsLocalDataSource,
    private val recomendaciones: VisitRecommendationDao,
    private val imagenes: VisitImageDao,
    private val telemetry: Telemetry,
    private val clock: AppClock = AppClock.System,
    private val traerUsuario: suspend () -> User? = ::usuarioAutenticado,
    private val capturas: CapturaEnCursoDao = db.capturaEnCursoDao(),
    private val emailEnSesion: () -> String? = ::emailDeLaSesion
) : RegistroDeVisitaPort {

    /**
     * Las N cuentas de una captura, **todo o nada** (E-APP-087, decisión 3 del
     * dueño 2026-10-20), con su marcador VISITA (E-APP-084):
     *
     * 1. Marcador CAPTURADA, la primera operación, en `NonCancellable`.
     * 2. Espera cancelable: el cobrador.
     * 3. UNA [escribirSinCancelar] con las N visitas, sus estados de cobranza y
     *    el marcador ESCRITA. Si el marcador ya estaba ESCRITA (reintento
     *    tardío) no se reescribe nada: no se vuelve a fijar `ESTADO_COBRANZA`.
     * 4. Post-commit: fotos del ancla y encolado de cada visita; marcador
     *    CERRADA.
     *
     * E-APP-119 (D15): el marcador lleva en su `RESUMEN` las N cuentas y la
     * hora de captura, así que una visita CAPTURADA con 0 escrituras (pantalla
     * cerrada mientras esperaba al cobrador, proceso muerto) se reanuda con
     * [reanudarPendientes] y la MISMA semilla.
     */
    override suspend fun registrarTodas(
        semilla: String,
        visitas: List<VisitaARegistrar>
    ): ResultadoDelRegistro {
        // Síncrono: no hay suspensión antes del marcador (Q11).
        CapturasEnProceso.entrar(semilla)
        try {
            return registrarTotal(semilla, visitas)
        } finally {
            CapturasEnProceso.salir(semilla)
        }
    }

    /**
     * E-APP-119 (D15, regla del dueño "nada se pierde"): reanuda las visitas
     * CAPTURADA del usuario en sesión que todavía no se escribieron. Corre en
     * primer plano —al volver a la app y al tocar el aviso "N capturas sin
     * terminar"—, nunca desde un worker: la escritura toca `sales`
     * (`ESTADO_COBRANZA`). Idempotente por el marcador. Se salta lo que la
     * pantalla de este mismo proceso está escribiendo ([CapturasEnProceso]) y
     * lo que no se puede reconstruir (un resumen incompleto queda visible en el
     * aviso, nunca se inventa una visita).
     *
     * @return cuántas quedaron escritas ahora.
     */
    @Suppress("TooGenericExceptionCaught") // leer los marcadores puede fallar; la app sigue.
    override suspend fun reanudarPendientes(): Int {
        val email = emailEnSesion()?.takeIf { it.isNotBlank() } ?: return 0
        val pendientes = try {
            capturas.abiertasDe(CapturaEnCursoEntity.TIPO_VISITA, email)
                .filter { it.ESTADO == CapturaEnCursoEntity.CAPTURADA }
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (_: Throwable) {
            return 0
        }
        var reanudadas = 0
        for (marcador in pendientes) {
            val lote = ResumenDeLaVisita.leer(marcador.RESUMEN)?.aLote() ?: continue
            if (!CapturasEnProceso.entrarSiLibre(marcador.ID)) continue
            try {
                if (registrarTotal(marcador.ID, lote) == ResultadoDelRegistro.REGISTRADA) reanudadas++
            } finally {
                CapturasEnProceso.salir(marcador.ID)
            }
        }
        return reanudadas
    }

    @Suppress("TooGenericExceptionCaught") // Room/Firestore pueden fallar; nada queda a medias.
    private suspend fun registrarTotal(
        semilla: String,
        visitas: List<VisitaARegistrar>
    ): ResultadoDelRegistro = try {
        registrarLote(semilla, visitas)
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (fallo: Throwable) {
        reportarSinLanzar(
            code = VisitasTelemetria.CODE_VISITA_NO_SE_GUARDO,
            message = "la escritura de la visita fallo; la transaccion no dejo nada escrito",
            props = mapOf(VisitasTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
        ResultadoDelRegistro.FALLO_EL_GUARDADO
    }

    private suspend fun registrarLote(
        semilla: String,
        lote: List<VisitaARegistrar>
    ): ResultadoDelRegistro {
        val primera = lote.firstOrNull() ?: return ResultadoDelRegistro.REGISTRADA
        val email = emailEnSesion()
        val capturadaEn = if (!email.isNullOrBlank()) {
            sinCancelar { anotarCaptura(semilla, lote, primera.clienteId, email) }
        } else {
            clock.now()
        }
        val venta = cuentaDeAtribucion(primera.clienteId)
            ?: return ResultadoDelRegistro.CLIENTE_NO_ESTA_EN_EL_TELEFONO
        val usuario = traerUsuario()
        if (usuario == null || usuario.COBRADOR_ID == 0) return ResultadoDelRegistro.SIN_COBRADOR
        val entidades = lote.map { entidadDe(it, venta, usuario.COBRADOR_ID, capturadaEn) }
        val escritaAhora = db.escribirSinCancelar {
            val marcador = capturas.obtener(semilla)
            if (marcador != null && marcador.ESTADO != CapturaEnCursoEntity.CAPTURADA) {
                false
            } else {
                entidades.forEachIndexed { indice, entidad ->
                    visitas.insertVisitAndUpdateState(
                        saleId = entidad.IMPTE_DOCTO_CC_ID,
                        visit = entidad,
                        newState = VisitStatusMapper.map(entidad.TIPO_VISITA)
                    )
                    reagendarCobranzaLegada(entidad)
                    ligarRecomendacion(lote[indice])
                }
                val ahora = AppTime.toWireFormat(clock.now())
                if (marcador == null) {
                    capturas.capturar(
                        marcadorDe(
                            semilla,
                            primera.clienteId,
                            email.orEmpty(),
                            ResumenDeLaVisita.de(lote, capturadaEn).json()
                        ).copy(ESTADO = CapturaEnCursoEntity.ESCRITA)
                    )
                } else {
                    capturas.marcarEscrita(semilla, ahora)
                }
                true
            }
        }
        sinCancelar {
            if (escritaAhora) lote.forEach { guardarComprobantes(it) }
            entidades.forEach { encolarEnvio(it.ID) }
            capturas.marcarCerrada(semilla, AppTime.toWireFormat(clock.now()))
        }
        return ResultadoDelRegistro.REGISTRADA
    }

    /**
     * Escritura 1 (E-APP-084) con el lote completo en el `RESUMEN` (E-APP-119).
     * Un reintento desde la pantalla sobre una captura que sigue CAPTURADA
     * conserva la hora de captura original y refresca el lote; una ESCRITA o
     * CERRADA no cambia. Devuelve la hora de captura: la `FECHA` de las visitas.
     */
    private suspend fun anotarCaptura(
        semilla: String,
        lote: List<VisitaARegistrar>,
        clienteId: Int,
        email: String
    ): Instant {
        val previo = capturas.obtener(semilla)
        val hora = previo?.let { ResumenDeLaVisita.leer(it.RESUMEN)?.hora() } ?: clock.now()
        val resumen = ResumenDeLaVisita.de(lote, hora).json()
        if (previo == null) {
            capturas.capturar(marcadorDe(semilla, clienteId, email, resumen))
        } else {
            capturas.actualizarCapturada(semilla, resumen, AppTime.toWireFormat(clock.now()))
        }
        return hora
    }

    private fun marcadorDe(
        semilla: String,
        clienteId: Int,
        usuario: String,
        resumen: String
    ): CapturaEnCursoEntity {
        val ahora = AppTime.toWireFormat(clock.now())
        return CapturaEnCursoEntity(
            ID = semilla,
            TIPO = CapturaEnCursoEntity.TIPO_VISITA,
            LLAVE = clienteId.toString(),
            USUARIO = usuario,
            ESTADO = CapturaEnCursoEntity.CAPTURADA,
            RESUMEN = resumen,
            CREADA_EN = ahora,
            ACTUALIZADA_EN = ahora
        )
    }

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
        // E-VIS-074: sin lanzar. Con varias cuentas el ancla ya pudo quedar
        // escrita en otra llamada; si este reporte lanzara, el puerto "total"
        // lanzaría y la pantalla se quedaría en "guardando".
        reportarSinLanzar(
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
        var bloqueTerminado = false
        try {
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
                guardarComprobantes(visita)
                encolarEnvio(entidad.ID)
                bloqueTerminado = true
            }
        } catch (tardia: CancellationException) {
            // E-APP-078: `withTransaction` (Room 2.6.1) corre en un `runBlocking`
            // HIJO de este bloque, que puede terminar despues que el bloque.
            // Entonces el resultado vuelve por `resumeCancellableWith` y, con el
            // job ya cancelado, `withContext` lanza aunque todo quedo escrito y
            // encolado. Solo esa cancelacion tardia se ignora, para que el
            // llamador cuente la visita escrita; el job sigue cancelado y la
            // cancelacion se observa en la siguiente suspension, como sin carrera.
            if (!bloqueTerminado) throw tardia
        }
    }

    /**
     * Encola el envío de la visita recién escrita. **Total: no propaga nada.**
     *
     * Era el ÚNICO paso post-commit sin guarda, y el daño no era el que parece.
     * `enqueueUpload` termina en `WorkManager.enqueueUniqueWork`, que puede
     * lanzar; la excepción subía al `catch` general de [registrar], que emite
     * [VisitasTelemetria.CODE_VISITA_NO_SE_GUARDO] con el mensaje *"la
     * transaccion no dejo nada escrito"* y devuelve
     * [ResultadoDelRegistro.FALLO_EL_GUARDADO] — cuya documentación en el puerto
     * dice literal *"Nada quedó escrito: es una transacción"*.
     *
     * **La visita SÍ quedaba escrita.** Lo que se rompía era la señal: se le
     * decía al cobrador que su visita no se guardó cuando sí, y se contaminaba
     * `visita_no_se_guardo` — el evento al que el GATE 1 de campo (`DEPLOY.md
     * §0.2`) manda mirar — con visitas que están en la base.
     *
     * Con la guarda, el resultado sigue siendo `REGISTRADA` y el fallo lleva su
     * propio código: `VisitsPendingSynchronizer` reencola en el siguiente login
     * todo lo que quedó en `GUARDADO_EN_MICROSIP = 0`, así que la visita no se
     * pierde, se retrasa.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // encolar puede fallar de varias formas; ninguna deshace la visita ya escrita.
    private suspend fun encolarEnvio(visitaId: String) {
        try {
            visitas.enqueueUpload(visitaId)
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (fallo: Throwable) {
            // Anti-PII: el nombre de la clase de la excepción, nunca su texto.
            reportarSinLanzar(
                code = VisitasTelemetria.CODE_VISITA_SIN_ENCOLAR,
                message = "la visita quedo escrita pero no se pudo encolar su envio",
                props = mapOf(VisitasTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
            )
        }
    }

    /**
     * Escribe las fotos de la visita. **Entre el commit y el encolado**, y las
     * dos posiciones son funcionales:
     *
     * - **Después del commit y fuera de la transacción**, porque la foto no
     *   puede revertir la visita. Si escribir una fila fallara dentro de
     *   `withTransaction`, el trabajo de campo se perdería por una foto — que es
     *   exactamente lo que "la foto nunca bloquea el guardado" prohíbe.
     * - **Antes de `enqueueUpload`**, porque encolar es lo que despierta al
     *   worker, y el worker sube lo que encuentre en `visita_imagenes` **una
     *   sola vez**: al recibir un 2xx marca `GUARDADO_EN_MICROSIP` y ya nadie
     *   reintenta. Escribir las fotos después habría subido la visita sin ellas
     *   cada vez que el teléfono tuviera señal en ese instante, y sin segundo
     *   intento. Es el mismo orden que la Task 22 tuvo que fijar del lado del
     *   dinero, por la misma razón.
     *
     * Un fallo aquí **no cambia el resultado**: la visita quedó escrita y
     * encolada. Se reporta con su propio código —el desenlace es distinto de
     * "no se guardó nada"— y se sigue.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // escribir en Room puede fallar con cualquier excepcion; la visita ya esta escrita.
    private suspend fun guardarComprobantes(visita: VisitaARegistrar) {
        if (visita.comprobantes.isEmpty()) return
        try {
            val creadaEn = AppTime.toWireFormat(clock.now())
            imagenes.insertAll(
                visita.comprobantes.mapIndexed { indice, comprobante ->
                    VisitImageEntity(
                        // El id de LA IMAGEN, nunca el de la visita: el servidor
                        // lo exige como `id_<n>` y con él arma la clave de
                        // storage. Cruzarlos haría que un reintento subiera dos
                        // filas con la misma clave.
                        ID = comprobante.id,
                        VISITA_ID = visita.visitaId,
                        URI = comprobante.archivo,
                        MIME = comprobante.mime,
                        DESCRIPCION = null,
                        // La POSICIÓN de captura: es la que decide el `n` de
                        // `id_<n>` cuando el worker arma el multipart.
                        ORDEN = indice,
                        CREADA_EN = creadaEn,
                        SUBIDA_EN = null
                    )
                }
            )
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (fallo: Throwable) {
            // Anti-PII: el nombre de la clase de la excepción, nunca su texto ni
            // la ruta del archivo.
            reportarSinLanzar(
                code = VisitasTelemetria.CODE_VISITA_COMPROBANTES_NO_SE_GUARDARON,
                message = "la visita quedo escrita y encolada pero sus fotos no se guardaron",
                props = mapOf(VisitasTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
            )
        }
    }

    /**
     * Reporta un fallo. **Total: no lanza.** Es la ÚNICA salida a
     * `telemetry.error` de este adaptador.
     *
     * E-VIS-071: la telemetría también puede fallar (`DurableTelemetry.error`
     * inserta en Room de forma síncrona). Tras el commit, si su excepción
     * saliera de aquí, llegaría al `catch` externo de [registrar], que contesta
     * [ResultadoDelRegistro.FALLO_EL_GUARDADO] con la visita y la foto ya
     * escritas. La pantalla leería "nada se escribió", soltaría la selección
     * congelada y un cambio de cuentas mudaría la foto a otra visita. Tras el
     * commit la respuesta es REGISTRADA, pase lo que pase con el reporte.
     *
     * E-VIS-074: lo mismo vale para los otros dos reportes. El del `catch`
     * externo, porque si lanzara el puerto dejaría de ser total (con varias
     * cuentas el ancla ya está escrita). El de la recomendación no ligada,
     * porque corre DENTRO de la transacción: si lanzara, la revertiría y la
     * visita se perdería por un reporte.
     */
    @Suppress("TooGenericExceptionCaught") // la telemetria caida no cambia lo que se escribio.
    private fun reportarSinLanzar(code: String, message: String, props: Map<String, String>) {
        try {
            telemetry.error(code = code, message = message, props = props)
        } catch (_: Throwable) {
            // Sin reporte: el desenlace de la escritura es lo que se contesta.
        }
    }

    /**
     * Arma la fila. **Nada estructurado se serializa en [VisitEntity.NOTA]**: la
     * fecha va a `PROMESA_FECHA`, el monto a `PROMESA_MONTO_CENTAVOS` y la hora
     * a `CITA_HORA`. Meterlas en la nota —como hacía el `NewVisitDialog` con
     * "La cita ha sido reagendada para el …", retirado en la Task 21— es el
     * defecto que este plan vino a arreglar.
     *
     * `LAT`/`LNG` en cero cuando no hubo ubicación: la ubicación **nunca**
     * bloquea el guardado, y `UbicacionDeVisitaPort` la pide una sola vez, antes
     * de llegar acá. Este KDoc decía además que `UpdateLocationService` parchaba
     * las columnas después; era falso desde que la Task 21 borró
     * `NewVisitDialog`, el único productor que arrancaba ese servicio con un
     * `visit_id`. Lo que el código hace lo fijan
     * `la visita queda encolada aunque no haya ubicacion` y
     * `con ubicacion se escriben las coordenadas`.
     */
    private fun entidadDe(
        visita: VisitaARegistrar,
        venta: SaleWithProductsEntity,
        cobradorId: Int,
        // E-APP-119: la hora de CAPTURA, no la de escritura: una visita
        // reanudada horas después conserva la hora en que se hizo.
        fecha: Instant = clock.now()
    ): VisitEntity = VisitEntity(
        ID = visita.visitaId,
        CLIENTE_ID = visita.clienteId,
        COBRADOR = venta.NOMBRE_COBRADOR,
        COBRADOR_ID = cobradorId,
        FECHA = AppTime.toWireFormat(fecha),
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
            // E-VIS-074: sin lanzar; estamos dentro de la transacción.
            reportarSinLanzar(
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
