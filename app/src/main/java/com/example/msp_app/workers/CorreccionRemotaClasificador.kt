package com.example.msp_app.workers

import com.example.msp_app.data.api.services.ventas.VentaSituacionDTO
import com.example.msp_app.data.api.services.ventas.codigoDeErrorHttp
import com.example.msp_app.feature.ventacorreccion.domain.CorreccionRemotaTerminal
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Qué hacer con UNA de las peticiones de una corrección remota después de
 * intentar entregarla (plan "Corregir una venta DESPUÉS de que subió",
 * nivel 2).
 *
 * Son tres, y sólo tres: la petición se entregó, la corrección **nunca**
 * va a entrar (y hay que dejar la marca terminal para que el dueño lo vea en
 * la UI), o todavía no se sabe y se vuelve a intentar.
 */
sealed interface DesenlaceCorreccionRemota {

    /** El servidor tomó el cuerpo: `cerrarCorreccionRemota` y listo. */
    data object Entregada : DesenlaceCorreccionRemota

    /**
     * El servidor cerró la puerta en definitiva. [estado] es el vocabulario
     * de `CORRECCION_REMOTA_ESTADO` — siempre uno de
     * [CorreccionRemotaTerminal.CONOCIDOS], nunca un literal nuevo.
     */
    data class Terminal(val estado: String) : DesenlaceCorreccionRemota

    /** Nadie sabe todavía: `Result.retry()`, la fila se queda pendiente. */
    data object Reintentar : DesenlaceCorreccionRemota
}

/**
 * Códigos que son señal de espera, nunca de rechazo definitivo, aunque sean
 * 4xx: 401 es el parpadeo del token (el interceptor lo renueva y el siguiente
 * intento pasa), y 408/425/429 son señales explícitas de backoff del servidor
 * o del túnel. Misma lista que `core.upload.classifyUpload` salvo el 409 —
 * ver el porqué en [clasificarCorreccionRemota].
 */
private val SIEMPRE_REINTENTABLES = setOf(401, 408, 425, 429)

/**
 * El código de negocio con el que el API v2 marca que la oficina escribió
 * primero. Viaja como `code=<codigo>` dentro del cuerpo de error — se saca con
 * `codigoDeErrorHttp`, nunca leyendo un campo `code`.
 */
private const val CODIGO_CONFLICTO_DE_VERSION = "venta_version_conflicto"

private const val PRIMER_CODIGO_EXITO = 200
private const val ULTIMO_CODIGO_EXITO = 299
private const val PRIMER_CODIGO_CLIENTE = 400
private const val ULTIMO_CODIGO_CLIENTE = 499
private const val CODIGO_CONFLICTO_DE_PRECONDICION = 412

/**
 * La tabla de decisión de UNA petición de la corrección remota — la misma
 * para las tres, porque el servidor les da a las tres la misma guarda
 * (`estado=active` + `situacion=borrador`), el mismo permiso
 * (`ventas:editar`) y los mismos códigos de error. Pura: no toca red ni base,
 * así que se prueba entera sin fakes de infraestructura
 * (`CorreccionRemotaClasificadorTest`).
 *
 * Lo que esta función NO decide es qué marca terminal se escribe cuando el
 * rechazo llega con peticiones anteriores YA aplicadas: eso lo resuelve
 * [ejecutarSecuenciaCorreccionRemota], que es quien sabe cuántas pasaron.
 *
 * | Situación | Desenlace |
 * |---|---|
 * | 2xx | [DesenlaceCorreccionRemota.Entregada] |
 * | 401, 408, 425, 429 | [DesenlaceCorreccionRemota.Reintentar] |
 * | 412, o código `venta_version_conflicto` | Terminal `CONFLICTO` |
 * | cualquier otro 4xx (403, 404, 409, 422…) | Terminal `RECHAZADA_ESTADO` |
 * | 5xx, o código desconocido | [DesenlaceCorreccionRemota.Reintentar] |
 * | sin respuesta ([codigoHttp] `null`: red, timeout) | [DesenlaceCorreccionRemota.Reintentar] |
 *
 * **Por qué NO se reusa `core.upload.classifyUpload`.** Esa tabla decide si
 * una CAPTURA queda resguardada, y ahí el 409 es reintentable porque el
 * servidor puede estar guardándola para que la oficina la corrija (la prueba
 * es la cabecera `X-Intent-Captured`). Este endpoint no captura nada: lo que
 * se manda no es una venta nueva —esa ya llegó— sino una EDICIÓN de sus
 * líneas, y el servidor sólo la acepta mientras la venta siga en `borrador`.
 * Su 409 `venta_no_editable` significa que salió de borrador: la oficina la
 * aprobó o ya se aplicó en Microsip. Esa corrección **nunca** va a entrar, y
 * reintentarla es quemar batería para siempre contra una puerta cerrada.
 *
 * Por la misma razón el resto de los 4xx es terminal y no reintentable:
 * - **403** (falta `ventas:editar`): el permiso no aparece por reintentar.
 * - **404** (`venta_not_found`): el servidor no tiene esa venta; el cuerpo no
 *   la va a crear, el endpoint sólo reemplaza líneas de una que ya existe.
 * - **422** (`venta_productos_vacios`, `producto_combo_referencia_invalida`,
 *   validaciones de monto): el cuerpo está mal; repetirlo lo repite mal.
 *
 * Y al revés: 5xx y los fallos de red se reintentan siempre. Ahí nadie tiene
 * la corrección y el teléfono es el único que la conserva — la fila queda
 * pendiente y la cola la vuelve a tomar.
 *
 * @param codigoHttp código HTTP de la respuesta, o `null` si NO hubo
 *   respuesta (red caída, timeout, el proceso no llegó a hablar con nadie).
 * @param codigoDeError el código de negocio del cuerpo de error
 *   (`codigoDeErrorHttp`), o `null` si la respuesta no trae ninguno. Sólo
 *   decide en un caso —distinguir el conflicto de versión, que puede llegar
 *   como 409 en vez de 412— porque el resto de los 4xx caen en el mismo
 *   desenlace con código o sin él.
 */
fun clasificarCorreccionRemota(
    codigoHttp: Int?,
    codigoDeError: String? = null
): DesenlaceCorreccionRemota = when {
    codigoHttp == null -> DesenlaceCorreccionRemota.Reintentar
    codigoHttp in PRIMER_CODIGO_EXITO..ULTIMO_CODIGO_EXITO -> DesenlaceCorreccionRemota.Entregada
    codigoHttp in SIEMPRE_REINTENTABLES -> DesenlaceCorreccionRemota.Reintentar
    codigoHttp == CODIGO_CONFLICTO_DE_PRECONDICION || codigoDeError == CODIGO_CONFLICTO_DE_VERSION ->
        DesenlaceCorreccionRemota.Terminal(CorreccionRemotaTerminal.CONFLICTO)
    codigoHttp in PRIMER_CODIGO_CLIENTE..ULTIMO_CODIGO_CLIENTE ->
        DesenlaceCorreccionRemota.Terminal(CorreccionRemotaTerminal.RECHAZADA_ESTADO)
    else -> DesenlaceCorreccionRemota.Reintentar
}

// ─── La secuencia de tres peticiones ────────────────────────────────────────

/**
 * Cuál de las tres peticiones de la corrección remota. Existe para una sola
 * cosa, y no es cosmética: cuando la secuencia se rompe a medias hay que poder
 * decir **cuál** falló. Sin eso, un servidor aplicado a medias es indistinguible
 * de uno intacto en los registros, y el diagnóstico se vuelve adivinanza.
 *
 * [codigo] es lo que viaja al `RemoteLogger` — inglés, snake_case, igual que el
 * resto de los códigos, y **nunca se le enseña a nadie**.
 *
 * Se llama `codigo` y no `etiqueta` a propósito: `CadaTextoDeUsuarioEmpiezaEnMayusculaTest`
 * descubre por reflexión todo `enum class` con un parámetro de constructor
 * llamado `etiqueta`, porque ése es el molde de los rótulos que SÍ se pintan
 * (`MetodoDeCobro` y compañía). Este no se pinta: es diagnóstico. Con el nombre
 * viejo la compuerta lo reclamaba con razón —desde su punto de vista era un
 * rótulo en minúscula— y "arreglarlo" poniéndole mayúscula habría ensuciado el
 * log para callar a una prueba que estaba haciendo bien su trabajo.
 */
enum class PasoCorreccionRemota(val codigo: String) {
    /** `PATCH /v2/ventas/{id}`: dirección, GPS, fecha, plan de crédito, día de cobranza, nota. */
    HEADER("header"),

    /** `PATCH /v2/ventas/{id}/cliente`: nombre, teléfono, aval. */
    CLIENTE("cliente"),

    /** `PUT /v2/ventas/{id}/lineas`: productos y combos. */
    LINEAS("lineas")
}

/**
 * Una petición de la secuencia: qué paso es y cómo se manda. El cuerpo ya viene
 * armado desde fuera —[enviar] sólo toca la red— porque armar los tres cuerpos
 * ANTES de la primera petición es lo que garantiza que las tres describan el
 * MISMO estado de la fila; armarlos sobre la marcha dejaría que una corrección
 * que entra a mitad de la secuencia se cuele partida en dos.
 */
class PeticionDeCorreccion(
    val paso: PasoCorreccionRemota,
    val enviar: suspend () -> VentaSituacionDTO
)

/**
 * Cómo terminó la secuencia completa.
 *
 * La diferencia que justifica este tipo —y que no existía cuando la corrección
 * era una sola petición— es [Terminal.pasosAplicados]: un rechazo definitivo
 * con peticiones ya aplicadas deja al servidor **a medias**, y eso no se le
 * puede contar al cobrador como "la aplicó la oficina" (que significa "no entró
 * nada"). Ver [ejecutarSecuenciaCorreccionRemota].
 */
sealed interface ResultadoDeLaSecuencia {

    /** Las tres pasaron. La corrección se cierra con [ultimaRespuesta] de testigo. */
    data class Entregada(val ultimaRespuesta: VentaSituacionDTO) : ResultadoDeLaSecuencia

    /**
     * El servidor cerró la puerta en definitiva, en [paso].
     *
     * @param estado la marca que va a la columna `CORRECCION_REMOTA_ESTADO`:
     *   [CorreccionRemotaTerminal.APLICADA_PARCIAL] si [pasosAplicados] > 0, y
     *   si no, la que decidió [clasificarCorreccionRemota].
     * @param estadoSinParcial la marca que la clasificación habría escrito
     *   ignorando lo ya aplicado. No se persiste; se registra, porque es la
     *   única forma de saber después SI la venta salió de borrador o SI la
     *   oficina escribió primero cuando [estado] ya no lo dice.
     * @param pasosAplicados cuántas peticiones alcanzaron a entrar antes de
     *   ésta. `0` = el servidor quedó intacto.
     */
    data class Terminal(
        val estado: String,
        val estadoSinParcial: String,
        val paso: PasoCorreccionRemota,
        val pasosAplicados: Int,
        val codigoHttp: Int,
        val codigoDeError: String?,
        val causa: HttpException
    ) : ResultadoDeLaSecuencia

    /**
     * Todavía no se sabe: la corrección se queda pendiente y la corrida
     * siguiente repite **las tres** peticiones desde el principio.
     *
     * [pasosAplicados] es diagnóstico y nada más. No habilita reanudar a media
     * secuencia: las tres son reemplazos totales, así que repetirlas converge al
     * mismo estado, y una reanudación parcial tendría que confiar en un progreso
     * que ningún lado persiste.
     */
    data class Reintentar(
        val paso: PasoCorreccionRemota,
        val pasosAplicados: Int,
        val causa: Exception
    ) : ResultadoDeLaSecuencia
}

/**
 * Corre las [peticiones] EN ORDEN y se detiene en la primera que no entre.
 *
 * El orden que le pasa `RemoteSaleCorrectionWorker` es header → cliente →
 * líneas, y la secuencia entera es **atómica sólo desde el teléfono**: el
 * servidor no tiene una transacción que abarque las tres, así que un rechazo en
 * la segunda o la tercera deja aplicado lo de antes. De ahí las dos reglas que
 * esta función implementa y que son todo su valor:
 *
 * 1. **Nada se cierra hasta que las tres pasan.** Sólo
 *    [ResultadoDeLaSecuencia.Entregada] autoriza `cerrarCorreccionRemota`.
 * 2. **Un terminal con algo ya aplicado se marca
 *    [CorreccionRemotaTerminal.APLICADA_PARCIAL]**, no con la marca que la
 *    clasificación habría dado. Da igual cuál de los terminales sea —409 porque
 *    la oficina sacó la venta de borrador, 403, 404, 422, o el conflicto de
 *    versión—: lo que el cobrador tiene que poder distinguir es "no entró nada"
 *    de "entró parte", porque en el segundo caso su teléfono y el servidor ya
 *    NO coinciden y nadie más se va a dar cuenta. El motivo original no se
 *    pierde, viaja en [ResultadoDeLaSecuencia.Terminal.estadoSinParcial] a los
 *    registros.
 *
 * Reintentar la secuencia completa desde el principio es seguro: las tres
 * peticiones son reemplazos totales (no deltas), así que repetirlas sobre una
 * venta que sigue editable converge al mismo estado.
 *
 * No toca red ni base por sí misma —las peticiones entran como lambdas—, así
 * que se prueba entera con fakes (`CorreccionRemotaSecuenciaTest`).
 *
 * @throws CancellationException si el worker se está deteniendo. No es un
 *   desenlace: se propaga tal cual, igual que en el resto del worker.
 */
suspend fun ejecutarSecuenciaCorreccionRemota(
    peticiones: List<PeticionDeCorreccion>
): ResultadoDeLaSecuencia {
    require(peticiones.isNotEmpty()) { "la secuencia de corrección remota no puede ir vacía" }

    var aplicados = 0
    var ultima: VentaSituacionDTO? = null

    for (peticion in peticiones) {
        ultima = try {
            peticion.enviar()
        } catch (e: HttpException) {
            return desenlaceDelFallo(peticion.paso, aplicados, e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // Red, timeout, DNS: nadie sabe si el servidor llegó a aplicar ésta.
            // Repetir las tres lo resuelve sin ambigüedad.
            return ResultadoDeLaSecuencia.Reintentar(peticion.paso, aplicados, e)
        } catch (e: Exception) {
            return ResultadoDeLaSecuencia.Reintentar(peticion.paso, aplicados, e)
        }
        aplicados++
    }

    // `checkNotNull` y no `!!`: la lista no puede estar vacía (el `require` de
    // arriba) y el bucle asigna en cada vuelta, así que llegar acá con `null`
    // sería un error de programación, no un estado posible.
    return ResultadoDeLaSecuencia.Entregada(checkNotNull(ultima))
}

/**
 * Traduce un fallo HTTP de [paso] a un desenlace de la secuencia, aplicando la
 * regla 2 de [ejecutarSecuenciaCorreccionRemota].
 */
private fun desenlaceDelFallo(
    paso: PasoCorreccionRemota,
    aplicados: Int,
    e: HttpException
): ResultadoDeLaSecuencia {
    val codigoDeError = codigoDeErrorHttp(e)
    return when (val desenlace = clasificarCorreccionRemota(e.code(), codigoDeError)) {
        is DesenlaceCorreccionRemota.Terminal -> ResultadoDeLaSecuencia.Terminal(
            estado = if (aplicados > 0) {
                CorreccionRemotaTerminal.APLICADA_PARCIAL
            } else {
                desenlace.estado
            },
            estadoSinParcial = desenlace.estado,
            paso = paso,
            pasosAplicados = aplicados,
            codigoHttp = e.code(),
            codigoDeError = codigoDeError,
            causa = e
        )

        DesenlaceCorreccionRemota.Reintentar ->
            ResultadoDeLaSecuencia.Reintentar(paso, aplicados, e)

        // Inalcanzable: Retrofit no construye un `HttpException` con un 2xx. Si
        // alguna vez lo hiciera, reintentar es lo conservador — nunca declarar
        // entregada una secuencia por un camino que no pasó por la respuesta real.
        DesenlaceCorreccionRemota.Entregada ->
            ResultadoDeLaSecuencia.Reintentar(paso, aplicados, e)
    }
}

// ─── El latido del arrendamiento ────────────────────────────────────────────

/**
 * Corre [trabajo] con un LATIDO en paralelo que llama a [renovar] cada
 * [periodoMs] para renovar el arrendamiento del candado.
 *
 * Es función de archivo y no método del worker para poder probarla sin una base
 * real: [renovar] entra como lambda, así que una prueba puede contar los latidos
 * con tiempo virtual. Vive en este archivo, con la clasificación y la secuencia,
 * por la misma razón que ellas: es la parte del worker remoto que se puede
 * medir sin Android.
 *
 * **Cubre la secuencia ENTERA, no la primera petición.** Ése es el punto con
 * tres peticiones: el cliente HTTP no fija `callTimeout` ni `writeTimeout`
 * (`RetrofitClientFactory` sólo fija `connect` y `read`, y los dos miden
 * inactividad entre bytes, no duración total), así que tres peticiones lentas
 * pero VIVAS pueden durar más que el arrendamiento entero. El latido se lanza
 * antes de la primera y se cancela después de la tercera.
 *
 * El latido nunca RE-RECLAMA: si [renovar] devuelve 0 el candado ya no es
 * nuestro y el latido se detiene — retomarlo le robaría la fila al editor. Un
 * fallo de renovación tampoco tumba la corrida: se registra por [alFallarElLatido]
 * y se sigue latiendo.
 *
 * @param periodoMs cada cuánto late. Un valor no positivo lo apaga — sólo para
 *   pruebas que quieran reproducir el candado que caduca en vuelo.
 * @param renovar devuelve las filas renovadas: 1 = sigue siendo nuestro, 0 = no.
 * @param alFallarElLatido se llama cuando [renovar] LANZA (SQLite trabado, el
 *   proceso congelado). No detiene nada.
 * @param alPerderElCandado se llama la única vez que [renovar] devuelve 0.
 */
internal suspend fun <T> conLatidoDelArrendamiento(
    periodoMs: Long,
    renovar: suspend () -> Int,
    alFallarElLatido: (Exception) -> Unit = {},
    alPerderElCandado: () -> Unit = {},
    trabajo: suspend () -> T
): T = coroutineScope {
    val latido = if (periodoMs > 0) {
        launch {
            while (true) {
                delay(periodoMs)
                val renovadas = try {
                    renovar()
                } catch (cancelacion: CancellationException) {
                    throw cancelacion
                } catch (fallo: Exception) {
                    alFallarElLatido(fallo)
                    continue
                }
                if (renovadas == 0) {
                    alPerderElCandado()
                    break
                }
            }
        }
    } else {
        null
    }

    try {
        trabajo()
    } finally {
        latido?.cancel()
    }
}
