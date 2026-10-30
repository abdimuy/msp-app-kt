package com.example.msp_app.data.pagos

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PaymentsWorkEnqueuer
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.BUSINESS_ZONE
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.captura.CapturaEnCursoDao
import com.example.msp_app.core.database.dao.payment.PaymentImageDao
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.entities.CapturaEnCursoEntity
import com.example.msp_app.core.database.entities.PaymentImageEntity
import com.example.msp_app.core.database.entities.UbicacionDeCapturaEntity
import com.example.msp_app.core.database.escribirSinCancelar
import com.example.msp_app.core.database.sinCancelar
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.core.ubicacion.MomentoDeGuardado
import com.example.msp_app.core.ubicacion.UbicacionAlGuardar
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.data.auth.emailDeLaSesion
import com.example.msp_app.data.auth.usuarioAutenticado
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.payment.toEntity
import com.example.msp_app.data.models.sale.toDomain
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.domain.SeguridadDelAbono
import com.example.msp_app.feature.pagos.domain.port.AbonoARegistrar
import com.example.msp_app.feature.pagos.domain.port.AbonoEnCurso
import com.example.msp_app.feature.pagos.domain.port.RegistroDeAbonoPort
import com.example.msp_app.feature.pagos.domain.port.ResultadoDelAbono
import com.example.msp_app.features.payments.newpayment.PaymentFactory
import java.time.Duration
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException

/**
 * Implementación real de [RegistroDeAbonoPort], provista desde el composition
 * root de `:app` (precedente `LiquidacionPort` → [SettlementLiquidacionAdapter]).
 *
 * **No reimplementa la escritura de dinero.** El camino que ya corre en
 * producción es `PaymentFactory.fromSale` + `PaymentsLocalDataSource.
 * saveAndEnqueue`, que inserta el pago y baja el `SALDO_REST` de la venta. Esta
 * tarea lo CONSUME tal cual: reescribirlo sería tocar dinero que no vino a
 * tocar, y tenerlo dos veces garantizaría que las dos versiones se despeguen.
 *
 * ## Por qué el par va dentro de `db.withTransaction`
 *
 * `PaymentsLocalDataSource` es una `class` común, y el `@Transaction` que lleva
 * `insertPaymentAndUpdateSale` **no hace nada fuera de un `@Dao`**: Room solo
 * genera el envoltorio para métodos de un DAO. O sea que hoy el insert del pago
 * y el descuento del saldo son dos escrituras sueltas.
 *
 * Sin transacción, un fallo entre las dos deja **un pago cobrado con el saldo
 * intacto**: dinero que el historial cuenta y que la venta sigue debiendo. Y es
 * peor de lo que suena, porque el guard anti-duplicado de la pantalla se
 * resuelve mirando el historial — vería ese pago huérfano y daría el abono por
 * registrado, con el saldo nunca descontado. Se envuelve con
 * `db.withTransaction`, el mismo patrón que ya usan `CobranzaReconciler` y
 * `CobranzaSyncManager`.
 *
 * ## El tercer cinturón vive aquí, contra el saldo recién leído
 *
 * Este puerto es un ESCRITOR DE DINERO inyectable en todo el grafo. Los dos
 * cinturones de arriba (pantalla y caso de uso) se topan contra el saldo que el
 * llamador traía cargado; si mañana otro consumidor inyecta este puerto, o si el
 * saldo cambió entre la carga y el toque, ninguno de los dos ayuda. Por eso el
 * adaptador vuelve a evaluar **con el `SALDO_REST` que acaba de leer de la base**
 * y con la MISMA función que los otros dos ([SeguridadDelAbono.bloqueosDe]).
 * Ningún camino escribe un sobrepago.
 *
 * ## El borde `Money` -> `Double`
 *
 * `PaymentEntity.IMPORTE` es `Double` en el schema de producción, que es
 * inmutable. La conversión ocurre **aquí y solo aquí**, con
 * `BigDecimal.toPlainString().toDouble()` sobre el `BigDecimal` de escala 2 de
 * `Money`: se pasa por el texto decimal exacto, nunca por una operación que
 * pueda arrastrar el error binario del flotante. Del otro lado de esta línea no
 * hay un solo `Double` de dinero.
 *
 * ## El encolado es del abono, no de la ubicación (Arreglo C)
 *
 * Hasta la revisión final de esta rama, el ÚNICO encolado inmediato del abono
 * vivía dentro de `UpdateLocationHandler`: `pedirUbicacion` (ya retirado, E-APP-146) arrancaba
 * `UpdateLocationService` y el handler llamaba a `enqueuePendingPaymentsWorker`
 * **después** de escribir `LAT`/`LNG`. Eso hacía que la subida del dinero
 * colgara de un adorno — servicio que Android 12+ puede rechazar en segundo
 * plano, GPS sin fix, permiso denegado, escritura de coordenadas que truena.
 *
 * Es exactamente el hallazgo de campo #2 de la Task 5 (*"pagos tiene el mismo
 * bug latente"*), que se cerró **solo para visitas**. Aquí se cierra para el
 * dinero, con la MISMA forma que `VisitsLocalDataSource.saveVisitAndEnqueue`:
 * [encolador] se llama en la misma corrutina de la escritura,
 * **incondicionalmente**, y la ubicación pasa a ser lo que siempre debió ser —
 * un acompañante que no decide nada.
 *
 * ## Un solo encolado (E-APP-146)
 *
 * `UpdateLocationHandler` ya no se arranca desde aquí, así que el abono se encola UNA vez, con
 * `ExistingWorkPolicy.KEEP` y nombre único. Si algo lo encola otra vez (el barrido de sesión, el
 * botón «Enviar pendientes»), `KEEP` salta el encolado mientras el trabajo previo sigue
 * ENQUEUED/RUNNING/BLOCKED —incluida la espera de la ubicación— y, una vez soltada la captura
 * (`GUARDADO_EN_MICROSIP = 1`), `PendingPaymentsWorker.doWork` sale sin tocar la red.
 *
 * ## Por qué el par va dentro de `NonCancellable`
 *
 * Misma razón que `RegistroDeVisitaAdapter` (Ruling AD): `registrar` corre bajo
 * el `viewModelScope` de la pantalla, que se cancela en cuanto el cobrador
 * navega hacia atrás, y `withTransaction` termina en un `withContext` que
 * **lanza al reanudar si el job se canceló mientras el bloque corría, aunque el
 * bloque haya terminado bien** — o sea con el dinero ya commiteado. Sin la
 * guarda quedaría un abono escrito, sin fotos y jamás encolado.
 *
 * ## La ubicación: nunca se espera al GPS, y nunca decide nada (E-APP-145, E-APP-146, E-APP-147)
 *
 * El mapa del día descarta el punto 0,0 (`RouteMapScreen`: `lat != 0.0 || lng != 0.0`), así que un
 * abono sin ubicación es invisible ahí. Pero guardar NUNCA espera al GPS: antes de la transacción se
 * lee, sin suspender, la mejor ubicación que el tomador ya tenía ([UbicacionAlGuardar.alGuardar]).
 * - Con ubicación ANTES, el pago nace con esas coordenadas y la fila de `ubicacion_de_captura` va en
 *   la MISMA transacción.
 * - Sin ella, el pago nace en 0,0 y en la MISMA transacción se anota su plazo de envio
 *   (`plazo_de_envio`); tras el commit queda esperando el primer fix valido, que el tomador le
 *   escribe en cuanto llega. El worker RETIENE el envio hasta que haya ubicacion o pasen 30 s desde
 *   el guardado, lo que ocurra primero (ver `PendingPaymentsWorker`).
 *
 * Lo que toca la ubicación después del commit va *fuera* de la transacción y su fallo se atrapa
 * entero: se emite [PagosTelemetria.CODE_ABONO_SIN_UBICACION] y el resultado sigue siendo
 * `REGISTRADO`. Misma regla que la foto — **el dinero se guarda, y se encola, aunque no haya GPS,
 * señal ni permiso**.
 *
 * ## Los comprobantes: el segundo caso de la misma familia (Task 22)
 *
 * Las fotos se escriben con la MISMA forma que la ubicación —después del
 * commit, fuera de la transacción, dentro de un `catch (Throwable)` con
 * telemetría— porque tienen el mismo contrato: **la foto nunca bloquea el
 * guardado**. Ni la cámara que falla, ni el disco lleno, ni Room negándose a
 * insertar pueden convertir un abono ya escrito en `FALLO_EL_GUARDADO`.
 *
 * Lo único que las distingue es el ORDEN: los comprobantes van **antes** del
 * encolado, porque encolar es lo que despierta al worker que los va a subir.
 * Ver [guardarComprobantes].
 *
 * [traerUsuario] y [ubicacion] son inyectables **solo para test**
 * (fakes-only, sin MockK): ningún test unitario toca el GPS real.
 */
@Suppress("LongParameterList", "TooManyFunctions") // las dependencias reales del camino del dinero.
class RegistroDeAbonoAdapter(
    private val db: AppDatabase,
    private val saleDao: SaleDao,
    private val pagos: PaymentsLocalDataSource,
    private val imagenes: PaymentImageDao,
    private val telemetry: Telemetry,
    private val encolador: PaymentsWorkEnqueuer,
    private val clock: AppClock = AppClock.System,
    private val traerUsuario: suspend () -> User? = ::usuarioAutenticado,
    private val ubicacion: UbicacionAlGuardar = UbicacionAlGuardar.Ninguna,
    private val capturas: CapturaEnCursoDao = db.capturaEnCursoDao(),
    private val emailEnSesion: () -> String? = ::emailDeLaSesion
) : RegistroDeAbonoPort {

    /**
     * ## Captura nunca se pierde (E-APP-084/085/086)
     *
     * 1. **Escritura 1, la primera operación, sin suspensión previa:** el
     *    marcador CAPTURADA con el resumen (importe, método, comprobantes, hora
     *    de captura) dentro de `NonCancellable`. Si la pantalla se cierra o el
     *    proceso muere después, la captura sigue en el disco.
     * 2. **Espera cancelable:** el cobrador (Firestore). Cancelada aquí, no se
     *    escribe dinero y el marcador queda CAPTURADA ("Abono de $X sin
     *    guardar").
     * 3. **Escritura 2, [escribirSinCancelar]:** idempotente por el marcador y
     *    por `Payment.ID`/`PAGO_RECIBIDO_ID`; el saldo se lee DENTRO de la
     *    transacción (tercer cinturón); pago + descuento + ESCRITA en el mismo
     *    commit. Un segundo intento con el mismo UUID no descuenta otra vez.
     * 4. **Post-commit:** comprobantes (fuera de la transacción: una foto nunca
     *    impide un cobro, decisión 2 del dueño), encolado, ubicación.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // Room/Firestore pueden fallar con cualquier excepción; se reporta sin PII.
    override suspend fun registrar(abono: AbonoARegistrar): ResultadoDelAbono = try {
        val usuarioEmail = emailEnSesion()
        if (usuarioEmail.isNullOrBlank()) {
            ResultadoDelAbono.SIN_COBRADOR
        } else {
            sinCancelar { capturar(abono, usuarioEmail) }
            val usuario = traerUsuario()
            if (usuario == null || usuario.COBRADOR_ID == 0) {
                ResultadoDelAbono.SIN_COBRADOR
            } else {
                escribir(abono, usuario)
            }
        }
    } catch (cancelada: CancellationException) {
        // Se relanza ANTES del catch general: una cancelación no es un fallo de
        // escritura. Si llegó en la espera del cobrador, el marcador quedó
        // CAPTURADA y la pantalla lo ofrece como "sin guardar".
        throw cancelada
    } catch (fallo: Throwable) {
        // Anti-PII: viaja el nombre de la clase de la excepción, nunca su texto
        // (que puede arrastrar datos del cliente) ni el monto.
        telemetry.error(
            code = PagosTelemetria.CODE_ABONO_NO_SE_GUARDO,
            message = "la escritura del abono fallo; la transaccion no dejo nada escrito",
            props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
        ResultadoDelAbono.FALLO_EL_GUARDADO
    }

    /**
     * Escritura 1. Un UUID ya escrito no se toca; uno sólo capturado se
     * re-resume (el cobrador corrigió tras un fallo) conservando su hora de
     * captura si el llamador no trae una (E-APP-085).
     */
    private suspend fun capturar(abono: AbonoARegistrar, usuario: String) {
        val ahora = AppTime.toWireFormat(clock.now())
        val previa = capturas.obtener(abono.abonoId)
        val horaPrevia = previa?.let { ResumenDelAbono.leer(it.RESUMEN)?.capturadoEn }
            ?.let { AppTime.parseWireFormatOrNull(it) }
        val capturadoEn = (abono.capturadoEn ?: horaPrevia ?: clock.now()).truncatedTo(
            ChronoUnit.SECONDS
        )
        val json = ResumenDelAbono.de(abono, capturadoEn).json()
        val nueva = capturas.capturar(
            CapturaEnCursoEntity(
                ID = abono.abonoId,
                TIPO = CapturaEnCursoEntity.TIPO_ABONO,
                LLAVE = abono.ventaId.toString(),
                USUARIO = usuario,
                ESTADO = CapturaEnCursoEntity.CAPTURADA,
                RESUMEN = json,
                CREADA_EN = ahora,
                ACTUALIZADA_EN = ahora
            )
        )
        if (nueva == -1L) capturas.actualizarCapturada(abono.abonoId, json, ahora)
    }

    private suspend fun escribir(abono: AbonoARegistrar, usuario: User): ResultadoDelAbono {
        // E-APP-146: lo que el telefono ya sabe de la ubicacion, sin esperar al GPS.
        val momento = momentoDeGuardado()
        val desenlace = db.escribirSinCancelar { escribirEnLaTransaccion(abono, usuario, momento) }
        return when (desenlace) {
            is Desenlace.Bloqueado -> {
                reportarBloqueo(desenlace.bloqueos)
                ResultadoDelAbono.BLOQUEADO_POR_SEGURIDAD
            }

            Desenlace.SinVenta -> ResultadoDelAbono.VENTA_NO_ESTA_EN_EL_TELEFONO
            Desenlace.Escrito, Desenlace.YaEstaba -> {
                // Fuera de la transacción y DESPUÉS del commit: el dinero ya está
                // escrito y nada de lo que pase aquí puede deshacerlo. Los
                // comprobantes van ANTES del encolado: encolar despierta al
                // worker, que manda lo que encuentre en `pago_imagenes`.
                sinCancelar {
                    guardarComprobantes(abono)
                    // E-APP-147: el envio se encola YA, con KEEP, como siempre; la espera de la
                    // ubicacion vive en el worker. Registrar la captura para el fix tardio va antes
                    // para que el worker, al despertar, ya la encuentre esperando.
                    if (desenlace == Desenlace.Escrito) ubicarElAbono(abono.abonoId, momento)
                    encolarSubida(abono.abonoId)
                }
                ResultadoDelAbono.REGISTRADO
            }
        }
    }

    /** Dentro de la transacción: sin esperas, sin telemetría, con el saldo de la base. */
    private suspend fun escribirEnLaTransaccion(
        abono: AbonoARegistrar,
        usuario: User,
        momento: MomentoDeGuardado
    ): Desenlace {
        val marcador = capturas.obtener(abono.abonoId)
        val yaEscrito = marcador != null && marcador.ESTADO != CapturaEnCursoEntity.CAPTURADA
        if (yaEscrito || capturas.pagosConUuid(abono.abonoId) > 0) {
            capturas.marcarEscrita(abono.abonoId, AppTime.toWireFormat(clock.now()))
            return Desenlace.YaEstaba
        }
        val venta = saleDao.getById(abono.ventaId) ?: return Desenlace.SinVenta
        val bloqueos = SeguridadDelAbono.bloqueosDe(
            monto = abono.importe,
            saldo = Money.of(venta.SALDO_REST)
        )
        if (bloqueos.isNotEmpty()) {
            return Desenlace.Bloqueado(
                bloqueos.joinToString(",") { it.name }
            )
        }
        // E-APP-085: la hora del pago es la de CAPTURA, nunca la del guardado.
        val resumen = marcador?.let { ResumenDelAbono.leer(it.RESUMEN) }
        val capturadoEn = AppTime.parseWireFormatOrNull(resumen?.capturadoEn)
            ?: (abono.capturadoEn ?: clock.now())
        val pago = PaymentFactory.fromSale(
            sale = venta.toDomain(),
            currentUser = usuario,
            importe = abono.importe.amount.toPlainString().toDouble(),
            formaCobroId = formaCobroDe(abono),
            id = abono.abonoId,
            fecha = AppTime.toWireFormat(capturadoEn.truncatedTo(ChronoUnit.SECONDS)),
            lat = momento.antes?.lat ?: 0.0,
            lng = momento.antes?.lon ?: 0.0
        )
        // El insert del pago, el descuento del saldo, la anotacion de ubicacion (fila de ANTES o
        // plazo de envio, E-APP-145) y el marcador ESCRITA, o todo o nada. La anotacion nunca
        // lanza: no puede revertir el dinero.
        pagos.saveAndEnqueue(
            payment = pago.toEntity(),
            saleId = pago.DOCTO_CC_ACR_ID,
            newAmount = pago.IMPORTE,
            newEstadoCobranza = EstadoCobranza.PAGADO
        )
        anotarLaUbicacion(abono.abonoId, momento)
        capturas.marcarEscrita(abono.abonoId, AppTime.toWireFormat(clock.now()))
        return Desenlace.Escrito
    }

    override suspend fun enCursoDe(ventaId: Int): AbonoEnCurso? {
        val usuario = emailEnSesion()?.takeIf { it.isNotBlank() } ?: return null
        barrerCerradas()
        val marcador = capturas.abiertaDe(CapturaEnCursoEntity.TIPO_ABONO, ventaId.toString(), usuario)
            ?: return null
        val escrito = marcador.ESTADO == CapturaEnCursoEntity.ESCRITA
        // Una ESCRITA de un día anterior ya no se ofrece: el cobrador ya siguió.
        if (escrito && !esDeHoy(marcador.CREADA_EN)) return null
        val abono = ResumenDelAbono.leer(marcador.RESUMEN)?.aAbono(marcador.ID) ?: return null
        if (escrito) reponerComprobantes(abono)
        return AbonoEnCurso(
            abonoId = marcador.ID,
            escrito = escrito,
            importe = abono.importe,
            metodo = abono.metodo,
            comprobantes = abono.comprobantes,
            capturadoEn = checkNotNull(abono.capturadoEn)
        )
    }

    /**
     * ESCRITA → CERRADA sólo con los comprobantes del resumen en `pago_imagenes`
     * (E-APP-086). Si falta alguno, se repone primero; si no se pudo, el
     * marcador sigue ESCRITA y la siguiente entrada lo vuelve a intentar.
     */
    @Suppress("TooGenericExceptionCaught") // cerrar el marcador nunca toca el dinero.
    override suspend fun cerrar(abonoId: String) {
        try {
            sinCancelar {
                val marcador = capturas.obtener(abonoId) ?: return@sinCancelar
                val abono = ResumenDelAbono.leer(marcador.RESUMEN)?.aAbono(abonoId)
                if (abono != null && !reponerComprobantes(abono)) return@sinCancelar
                capturas.marcarCerrada(abonoId, AppTime.toWireFormat(clock.now()))
            }
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (fallo: Throwable) {
            telemetry.error(
                code = PagosTelemetria.CODE_ABONO_SIN_COMPROBANTES,
                message = "no se pudo cerrar el marcador del abono; el abono si quedo escrito",
                props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
            )
        }
    }

    /** `true` = todos los comprobantes del abono están en `pago_imagenes`. */
    private suspend fun reponerComprobantes(abono: AbonoARegistrar): Boolean {
        if (abono.comprobantes.isEmpty()) return true
        guardarComprobantes(abono)
        val presentes = capturas.comprobantesDePagoPresentes(
            abono.comprobantes.map { it.id }
        ).toSet()
        return abono.comprobantes.all { it.id in presentes }
    }

    @Suppress("TooGenericExceptionCaught") // el barrido es limpieza; nunca toca dinero.
    private suspend fun barrerCerradas() {
        try {
            capturas.barrerCerradas(AppTime.toWireFormat(clock.now().minus(VEJEZ_DE_CERRADAS)))
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (_: Throwable) {
            // Sin barrer: las CERRADA viejas sólo ocupan disco.
        }
    }

    private fun esDeHoy(creadaEn: String): Boolean {
        val instante = AppTime.parseWireFormatOrNull(creadaEn) ?: return false
        return instante.atZone(BUSINESS_ZONE).toLocalDate() == AppTime.todayInBusinessZone(clock)
    }

    private sealed interface Desenlace {
        data object Escrito : Desenlace
        data object YaEstaba : Desenlace
        data object SinVenta : Desenlace
        data class Bloqueado(val bloqueos: String) : Desenlace
    }

    /**
     * Encola la subida del abono recién escrito. **Total: no propaga nada.**
     *
     * `WorkManager.enqueueUniqueWork` puede lanzar (el proceso muriendo, el
     * componente deshabilitado), y un abono ya escrito no puede volverse
     * `FALLO_EL_GUARDADO` por eso: la pantalla ofrecería reintentar un cobro que
     * ya ocurrió. Se reporta con código propio y se sigue —
     * `PaymentsPendingSynchronizer` recoge en el siguiente login todo lo que
     * quedó con `GUARDADO_EN_MICROSIP = 0`.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // encolar puede fallar de varias formas; ninguna toca el dinero ya commiteado.
    private fun encolarSubida(pagoId: String) {
        try {
            encolador.enqueue(pagoId)
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (fallo: Throwable) {
            // Anti-PII: el nombre de la clase de la excepción, nunca su texto.
            telemetry.error(
                code = PagosTelemetria.CODE_ABONO_SIN_ENCOLAR,
                message = "no se pudo encolar la subida del abono; el abono si quedo escrito",
                props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
            )
        }
    }

    /**
     * Guarda los comprobantes del abono recién escrito. **Total: no propaga
     * nada** — es el segundo caso de la misma familia que la ubicación.
     *
     * ## Qué va en cada columna, y por qué importa
     *
     * - `ID` es el UUID de **la imagen**, el que el teléfono acuñó antes de
     *   abrir la cámara y el que viaja como `id_<n>`. Es lo que hace idempotente
     *   al reintento: sin él, el servidor inventa uno nuevo en cada intento
     *   (`parsePositionalImagenID`) y la misma foto sube dos veces.
     * - `PAGO_ID` es el `Payment.ID` — el mismo `abonoId` que ya viajó a
     *   la ubicación. **No son la misma columna ni el mismo valor**, y
     *   cruzarlos es el defecto que este plan ya cazó siete veces.
     * - `ORDEN` es la posición en la lista, que es el orden de captura y el `n`
     *   del multipart.
     * - `SUBIDA_EN` nace `NULL`: nadie ha subido nada todavía. Lo estampa el
     *   worker, y solo por las que el servidor efectivamente recibió.
     *
     * Un fallo aquí **no puede** cambiar el resultado: el abono ya está en la
     * base, y devolver `FALLO_EL_GUARDADO` haría que la pantalla ofreciera
     * reintentar un cobro que ya ocurrió. Se reporta y se sigue.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // Room puede fallar de varias formas; ninguna toca el dinero ya commiteado.
    private suspend fun guardarComprobantes(abono: AbonoARegistrar) {
        if (abono.comprobantes.isEmpty()) return
        try {
            // Sólo los que faltan: `insertAll` es REPLACE y re-escribir uno ya
            // subido le borraría `SUBIDA_EN` y lo volvería a mandar (E-APP-086).
            val presentes = capturas.comprobantesDePagoPresentes(
                abono.comprobantes.map { it.id }
            ).toSet()
            val faltan = abono.comprobantes.withIndex().filterNot { it.value.id in presentes }
            if (faltan.isEmpty()) return
            val creadaEn = AppTime.toWireFormat(clock.now())
            imagenes.insertAll(
                faltan.map { (orden, comprobante) ->
                    PaymentImageEntity(
                        ID = comprobante.id,
                        PAGO_ID = abono.abonoId,
                        URI = comprobante.archivo,
                        MIME = comprobante.mime,
                        ORDEN = orden,
                        CREADA_EN = creadaEn
                    )
                }
            )
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (fallo: Throwable) {
            // Anti-PII: el nombre de la clase de la excepción, nunca su texto
            // (que podría arrastrar la ruta del archivo).
            telemetry.error(
                code = PagosTelemetria.CODE_ABONO_SIN_COMPROBANTES,
                message = "no se pudieron guardar los comprobantes; el abono si quedo escrito",
                props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
            )
        }
    }

    /**
     * Dentro de la transaccion del dinero: la fila de ubicacion de ANTES o el plazo de envio. **Total:
     * no propaga nada** (salvo la cancelacion): escribir el plazo nunca puede revertir ni impedir el
     * guardado (K1). Si falla, el abono queda sin plazo y su envio sale sin retencion.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun anotarLaUbicacion(pagoId: String, momento: MomentoDeGuardado) {
        try {
            ubicacion.anotarEnLaTransaccion(pagoId, UbicacionDeCapturaEntity.TIPO_PAGO, momento)
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (_: Throwable) {
            // El dinero manda.
        }
    }

    /**
     * Lo que el telefono sabe de la ubicacion AL GUARDAR (E-APP-146). **Total: no propaga nada.**
     * No suspende ni espera al GPS: lee el buffer del tomador. Si lanza, el abono se guarda sin
     * ubicacion, igual que si no hubiera ninguna.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun momentoDeGuardado(): MomentoDeGuardado = try {
        ubicacion.alGuardar()
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (e: Throwable) {
        MomentoDeGuardado.SinUbicacion
    }

    /**
     * Despues del commit: si el abono se guardo sin ubicacion, lo deja esperando el primer fix
     * valido (E-APP-146). **Total: no propaga nada.**
     *
     * Un fallo aqui no puede cambiar el resultado del registro: el abono ya esta en la base y
     * devolver `FALLO_EL_GUARDADO` haria que la pantalla ofreciera reintentar un cobro que ya
     * ocurrio. Se reporta y se sigue. Ya no arranca `UpdateLocationService` ni encola por segunda
     * vez el pago.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // la ubicacion puede fallar de varias formas; ninguna toca el dinero.
    private fun ubicarElAbono(pagoId: String, momento: MomentoDeGuardado) {
        try {
            ubicacion.despuesDeGuardar(pagoId, UbicacionDeCapturaEntity.TIPO_PAGO, momento)
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (fallo: Throwable) {
            // Anti-PII: el nombre de la clase de la excepción, nunca su texto.
            telemetry.error(
                code = PagosTelemetria.CODE_ABONO_SIN_UBICACION,
                message = "no se pudo atender la ubicacion del abono; el abono si quedo escrito",
                props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
            )
        }
    }

    /**
     * El tercer cinturón disparó contra el saldo REAL de la venta, leído dentro
     * de la transacción. Anti-PII: viajan los NOMBRES de los bloqueos, nunca el
     * monto ni el saldo.
     */
    private fun reportarBloqueo(bloqueos: String) {
        telemetry.error(
            code = PagosTelemetria.CODE_ABONO_BLOQUEADO_EN_ESCRITURA,
            message = "el saldo real de la venta no admite este abono; nada se escribio",
            props = mapOf(PagosTelemetria.PROP_BLOQUEOS to bloqueos)
        )
    }

    /**
     * La forma de cobro que va al schema. Se toma de `Constants`, que es de
     * donde la tomaba el retirado `NewPaymentDialog`, y se comprueba contra el
     * id del catálogo de
     * dominio: son el mismo número (157 / 52569) y esta línea existe para que
     * sigan siéndolo.
     */
    private fun formaCobroDe(abono: AbonoARegistrar): Int = when (abono.metodo) {
        com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro.TRANSFERENCIA ->
            Constants.PAGO_CON_TRANSFERENCIA_ID

        else -> Constants.PAGO_EN_EFECTIVO_ID
    }

    private companion object {
        val VEJEZ_DE_CERRADAS: Duration = Duration.ofDays(7)
    }
}
