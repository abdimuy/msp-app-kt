package com.example.msp_app.data.pagos

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.clientprofile.ClientProfileDao
import com.example.msp_app.core.database.entities.ClientProfileEntity
import com.example.msp_app.core.database.entities.ClientProfileSignalEntity
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.data.auth.usuarioAutenticado
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.domain.model.FichaDelCliente
import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha
import com.example.msp_app.feature.pagos.domain.port.FichaDelClientePort
import com.example.msp_app.feature.pagos.domain.port.ResultadoDeLaFicha
import kotlinx.coroutines.CancellationException

/**
 * La ficha del cliente sobre `cliente_ficha` + `cliente_ficha_senales`.
 *
 * **Vive en `:app` y no en `:feature:pagos` por el `COBRADOR_ID`**: se resuelve
 * contra Firestore y esa resolución solo existe aquí (precedente
 * `LiquidacionPort` → `SettlementLiquidacionAdapter`). El puerto se queda en el
 * feature.
 *
 * La resolución del usuario se toma de `data/auth/UsuarioAutenticado.kt`, un
 * sitio neutral: **la ficha no depende del adaptador del abono ni al revés**.
 * El pago es soberano para que nada pueda volverlo frágil, y una dependencia de
 * compilación en esa dirección alcanza para romper esa promesa.
 *
 * ## No saber quién edita NUNCA impide guardar
 *
 * `cliente_ficha.COBRADOR_ID` es nullable a propósito y este adaptador es quien
 * lo aprovecha: si el usuario no se puede resolver —sin sesión, sin señal—, la
 * ficha se guarda igual con `COBRADOR_ID = null`. Es la diferencia exacta con
 * el abono, que sin cobrador se rechaza porque *"es dinero que nadie entregó"*:
 * aquí lo que está en juego es conocimiento, y perderlo por no saber quién lo
 * escribió sería el peor de los dos finales.
 *
 * ## Guardar NO borra lo que este build no entiende
 *
 * El conjunto de señales se guarda desmarcando **una por una las del catálogo
 * que quedaron fuera**, nunca con un `DELETE WHERE CLIENTE_ID`. La diferencia
 * importa el día que se retire un valor del catálogo —*"nace corto y crece"*—:
 * el esquema conserva el literal como texto y un borrado por cliente lo
 * evaporaría en la primera edición posterior, en silencio. Ver el KDoc de
 * [ClientProfileDao.guardar].
 *
 * [traerUsuario] es inyectable **solo para test** (fakes-only, sin MockK).
 */
class FichaDelClienteAdapter(
    private val fichas: ClientProfileDao,
    private val telemetry: Telemetry,
    private val clock: AppClock = AppClock.System,
    private val traerUsuario: suspend () -> User? = ::usuarioAutenticado
) : FichaDelClientePort {

    @Suppress(
        "TooGenericExceptionCaught"
    ) // Room puede fallar con cualquier excepción; la pantalla degrada, no muere.
    override suspend fun fichaDe(clienteId: Int): FichaDelCliente? = try {
        val nota = fichas.fichaDe(clienteId)
        val filas = fichas.senalesDe(clienteId)
        val senales = filas.mapNotNull { SenalDeFicha.deLiteral(it.SENAL) }
        reportaLasDesconocidas(filas.size - senales.size)
        FichaDelCliente(
            senales = senales.toSet(),
            nota = nota?.NOTA?.takeIf { it.isNotBlank() },
            actualizada = AppTime.parseWireFormatOrNull(nota?.ACTUALIZADA_EN)
        )
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (fallo: Throwable) {
        telemetry.error(
            code = PagosTelemetria.CODE_FICHA_NO_SE_PUDO_LEER,
            message = "no se pudo leer la ficha del cliente",
            props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
        // `null` y NO una ficha vacía: aplanarlos haría que el cobrador
        // escribiera encima de lo que sí estaba guardado. Ver el puerto.
        null
    }

    @Suppress(
        "TooGenericExceptionCaught"
    ) // Room/Firestore pueden fallar con cualquier excepción; nada quedó escrito.
    override suspend fun guardar(clienteId: Int, ficha: FichaDelCliente): ResultadoDeLaFicha = try {
        val instante = clock.now()
        val ahora = AppTime.toWireFormat(instante)
        val marcadas = ficha.senales.map { it.name }
        fichas.guardar(
            ficha = ClientProfileEntity(
                CLIENTE_ID = clienteId,
                NOTA = ficha.nota,
                ACTUALIZADA_EN = ahora,
                COBRADOR_ID = cobradorId()
            ),
            aMarcar = marcadas.map {
                ClientProfileSignalEntity(
                    CLIENTE_ID = clienteId,
                    SENAL = it,
                    ACTUALIZADA_EN = ahora
                )
            },
            aDesmarcar = SenalDeFicha.entries.map { it.name } - marcadas.toSet()
        )
        ResultadoDeLaFicha.Guardada(ficha.copy(actualizada = instante))
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (fallo: Throwable) {
        // Anti-PII: viaja el nombre de la clase, nunca el texto de la nota.
        telemetry.error(
            code = PagosTelemetria.CODE_FICHA_NO_SE_GUARDO,
            message = "la escritura de la ficha fallo; la transaccion no dejo nada escrito",
            props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
        ResultadoDeLaFicha.FalloElGuardado
    }

    /**
     * El cobrador que está editando, o `null` si no se puede saber.
     *
     * `COBRADOR_ID == 0` es el "sin resolver" que ya usan `PaymentFactory` y
     * `VisitFactory`, así que aquí también se traduce a `null` en vez de
     * guardarse como si fuera el cobrador número cero.
     *
     * Su fallo se traga a propósito **y se reporta**: un Firestore inalcanzable
     * no puede impedir que se guarde una nota (el teléfono trabaja sin señal la
     * mitad del día). Lo que se pierde es la atribución, no el conocimiento.
     */
    @Suppress("TooGenericExceptionCaught") // Firebase lanza de todo; la ficha se guarda igual.
    private suspend fun cobradorId(): Int? = try {
        traerUsuario()?.COBRADOR_ID?.takeIf { it != 0 }
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (fallo: Throwable) {
        telemetry.error(
            code = PagosTelemetria.CODE_FICHA_SIN_COBRADOR,
            message = "no se pudo resolver el cobrador; la ficha se guarda sin atribucion",
            props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
        null
    }

    /**
     * Señales guardadas que este build ya no reconoce.
     *
     * Se emite el CONTEO y **no el literal**: la norma anti-PII exige que
     * `props` sea estático del desarrollador, y este literal viene del disco.
     * El conteo alcanza para lo que el evento existe: enterarse de que hay
     * fichas apuntando a un valor que la pantalla ya no sabe pintar.
     */
    private fun reportaLasDesconocidas(cuantas: Int) {
        if (cuantas <= 0) return
        telemetry.error(
            code = PagosTelemetria.CODE_FICHA_SENAL_DESCONOCIDA,
            message = "la ficha trae senales que este build no conoce; se ignoran al pintar",
            props = mapOf(PagosTelemetria.PROP_OCURRENCIAS to cuantas.toString())
        )
    }
}
