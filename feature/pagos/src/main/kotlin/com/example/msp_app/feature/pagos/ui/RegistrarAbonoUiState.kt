package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Immutable
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.AvisoDelMonto
import com.example.msp_app.feature.pagos.domain.Comprobantes
import com.example.msp_app.feature.pagos.domain.MontosSugeridos
import com.example.msp_app.feature.pagos.domain.VeredictoDelAbono
import com.example.msp_app.feature.pagos.domain.model.ComprobanteDelAbono
import com.example.msp_app.feature.pagos.domain.model.DestinoDeFoto
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.Miniatura

/**
 * Por qué el abono no quedó. Cuatro ramas y no un texto: al cobrador se le dice
 * una cosa distinta en cada caso, y aplanarlas fue el defecto D5.
 */
enum class FalloDelAbono {
    /** El teléfono ya no tiene la venta. */
    VENTA_NO_ESTA,

    /** No se supo qué cobrador está cobrando. */
    SIN_COBRADOR,

    /** El guardado falló. Se puede reintentar. */
    NO_SE_PUDO_GUARDAR,

    /**
     * El cinturón de `application/` rechazó el monto. Es un defecto de la
     * pantalla, no del cobrador; se dice igual para que no quede un botón que
     * no hace nada.
     */
    BLOQUEADO,

    /**
     * No se pudo comprobar si el abono quedó. **No es reintentable desde aquí**:
     * el guard sigue puesto a propósito, y la duda se resuelve al volver a
     * abrir la pantalla, mirando el historial.
     */
    NO_SE_PUDO_VERIFICAR
}

/**
 * Por qué la foto no se adjuntó. **Ninguno de los tres para el abono**: el
 * dinero se registra igual, y esto solo decide qué dice el aviso.
 *
 * Son tres y no un texto por la misma razón que [FalloDelAbono]: al cobrador se
 * le dice algo distinto en cada caso — volver a intentar, cambiar de archivo, o
 * que ya no caben más.
 */
enum class FalloDeLaFoto {
    /** La cámara, la compresión o el almacenamiento fallaron. Se puede reintentar. */
    NO_SE_PUDO_TOMAR,

    /** El archivo no es de un tipo que el servidor acepte. Reintentar no lo arregla. */
    TIPO_NO_PERMITIDO,

    /** Ya hay [com.example.msp_app.feature.pagos.domain.Comprobantes.MAXIMO] comprobantes. */
    YA_NO_CABEN
}

/**
 * El **paso dos** de la confirmación, congelado.
 *
 * Que sea un objeto aparte y no un `Boolean` es lo que hace que la
 * confirmación enseñe exactamente lo que se va a registrar: el monto, el método
 * y el veredicto (saldo anterior → saldo nuevo, y las rarezas) quedan fijos en
 * el instante del primer toque, aunque el estado de abajo siga moviéndose.
 *
 * Un [ConfirmacionPendiente] no nulo **es** la petición de enseñar la hoja, y
 * nunca escribe nada por sí mismo: el dinero se mueve solo en
 * [RegistrarAbonoViewModel.confirmar].
 */
@Immutable
data class ConfirmacionPendiente(
    val importe: Money,
    val metodo: MetodoDeCobro,
    val veredicto: VeredictoDelAbono,
    /**
     * Cuánta fricción pide este monto. Se congela con el resto: lo que cambia
     * en los casos raros es **qué pide el paso dos** —tocar o teclear— y eso no
     * puede moverse debajo del dedo mientras la hoja está arriba.
     */
    val aviso: AvisoDelMonto = AvisoDelMonto.NINGUNO,
    /**
     * El monto **tecleado otra vez** en el paso dos de nivel 3. Vacío mientras
     * nadie ha escrito nada.
     *
     * Vive aquí y no en un `remember` de la hoja a propósito: el `enabled` de un
     * botón que mueve dinero no puede depender de un estado que sólo existe
     * dentro de la composición, porque entonces cualquier otro llamador de
     * `confirmar()` se lo saltaría. Aquí lo mira también el ViewModel, ver
     * [sePuedeConfirmar].
     */
    val eco: String = ""
) {
    /**
     * ¿El paso dos pide teclear el monto? Ver
     * [com.example.msp_app.feature.pagos.domain.NivelDeAviso.TECLEAR].
     */
    val pideTeclearElMonto: Boolean get() = aviso.pideTeclearElMonto

    /**
     * ¿Lo tecleado de nuevo **es** el monto? Se compara el dinero y no el
     * texto: "250", "250.00" y "0250" son el mismo monto, y exigir el mismo
     * string convertiría la red de seguridad en una prueba de mecanografía.
     */
    val ecoCoincide: Boolean get() = MontoCapturado(crudo = eco).importe == importe

    /**
     * ¿Se puede disparar el paso dos? Todo lo que no sea nivel 3 pasa directo
     * —avisar no es bloquear—, y el nivel 3 pasa **cuando el monto se tecleó
     * otra vez**.
     */
    val sePuedeConfirmar: Boolean get() = !pideTeclearElMonto || ecoCoincide
}

/**
 * Un intento que **no llegó a ser comprobante**: ocupa su propio cuadro de la
 * rejilla, en ámbar, al lado de las fotos que sí entraron.
 *
 * ## Por qué un cuadro y no un aviso suelto
 *
 * El aviso de antes decía "ese archivo no se acepta" **sin decir cuál**, y con
 * una galería que deja escoger varias a la vez eso pasó de incómodo a inútil: el
 * cobrador elige tres, una se cae, y la pantalla no dice cuál de las tres. El
 * cuadro sí lo dice, porque **es** la que se cayó, en el lugar donde habría
 * quedado.
 *
 * [id] es el que acuñó el puerto para ese intento: el de la cámara o el de la
 * importación. Sirve para quitarlo con su tache, igual que a una foto puesta.
 */
data class IntentoFallido(val id: String, val motivo: FalloDeLaFoto)

/**
 * De dónde sale un comprobante. Las tres que ofrece la hoja del «+».
 *
 * Son tres y no dos porque las tres son distintas de verdad: la cámara escribe
 * un archivo nuevo, la galería deja escoger **varias** de un tirón, y el
 * explorador es el único que alcanza un PDF —el selector de fotos del sistema
 * solo enseña imágenes y video, así que sin esta tercera un recibo en PDF sería
 * inalcanzable, y cobranza acepta PDF a propósito porque los recibos SAT llegan
 * así.
 */
enum class OrigenDeLaFoto {
    CAMARA,
    GALERIA,
    ARCHIVO
}

/**
 * Estado observable de la pantalla de abono.
 *
 * [registrado] no nulo es el final del camino: el abono ya está escrito y la
 * pantalla no vuelve a ofrecer registrar. Convive con el guard persistido del
 * ViewModel — el estado se pierde al morir el proceso, el guard no.
 */
@Immutable
data class RegistrarAbonoUiState(
    val cargando: Boolean = true,
    val venta: DetalleVenta? = null,
    val error: ErrorDeDetalle? = null,
    val monto: MontoCapturado = MontoCapturado(),
    val metodo: MetodoDeCobro = MetodoDeCobro.EFECTIVO,
    val sugeridos: List<MontosSugeridos.Sugerido> = emptyList(),
    val veredicto: VeredictoDelAbono = VeredictoDelAbono.SIN_VENTA,
    /**
     * Cuánta fricción merece el monto que se lleva tecleado, **en vivo**.
     *
     * Es un campo del estado y no un cálculo de la pantalla por la misma razón
     * que [veredicto]: la captura y la hoja tienen que estar mirando la misma
     * clasificación, y con dos cálculos se pueden despegar. Lo pone el único
     * lugar que recalcula, `RegistrarAbonoViewModel.conVeredicto`.
     */
    val aviso: AvisoDelMonto = AvisoDelMonto.NINGUNO,
    val confirmacion: ConfirmacionPendiente? = null,
    val guardando: Boolean = false,
    val registrado: String? = null,
    val fallo: FalloDelAbono? = null,
    /**
     * Los comprobantes adjuntos, **en orden de captura**. El índice de esta
     * lista es el `ORDEN` con el que se persisten y el `n` de `id_<n>` con el
     * que viajan, así que su orden es contrato, no presentación.
     */
    val comprobantes: List<ComprobanteDelAbono> = emptyList(),
    /**
     * Los píxeles ya reducidos de cada comprobante, por su id. Lo que falta —un
     * PDF, o una decodificación que falló— pinta el cuadro con su glifo en vez de
     * con la foto, y **no** es un fallo: el comprobante está adjunto y va a
     * viajar con el abono igual.
     */
    val miniaturas: Map<String, Miniatura> = emptyMap(),
    /** Los intentos que no entraron, cada uno con su cuadro ámbar. */
    val intentos: List<IntentoFallido> = emptyList(),
    /**
     * El destino ya preparado que espera a la cámara. No nulo **es** la petición
     * de abrir la cámara: la pantalla lo mira y dispara el intent. Vive también
     * en el `SavedStateHandle`, porque el proceso puede morir con la cámara
     * encima y la foto tiene que volver con el id que ya se le acuñó.
     */
    val destinoDeFoto: DestinoDeFoto? = null,
    /** La hoja del «+» está arriba, preguntando de dónde sale el comprobante. */
    val eligiendoOrigen: Boolean = false,
    /**
     * Hay que abrir un selector del sistema. Nunca vale [OrigenDeLaFoto.CAMARA]
     * — ésa viaja por [destinoDeFoto], que sí se persiste porque su id tiene que
     * sobrevivir a la muerte del proceso.
     */
    val selectorPedido: OrigenDeLaFoto? = null,
    /**
     * El **cerrojo** de la verificación pendiente: la escritura no se pudo
     * comprobar y el guard anti-duplicado sigue puesto.
     *
     * Es un campo propio y NO se deduce de [fallo] a propósito. Cuando el
     * cerrojo vivía en `fallo`, ese campo pasó de "un mensaje que se pinta" a
     * "una entrada de si el CTA está vivo", y sus escritores no se enteraron:
     * teclear un dígito limpiaba `fallo` y devolvía el botón a la vida, con el
     * guard todavía puesto — el mismo botón mudo que esa gate vino a cerrar,
     * por una puerta lateral. Separarlos devuelve a `fallo` su único trabajo
     * (decir qué pasó) y deja el cerrojo donde nadie lo abre de pasada: solo lo
     * pone la rama que no pudo comprobar, y solo lo quita una recarga.
     */
    val verificacionPendiente: Boolean = false
) {
    /**
     * ¿El CTA puede dispararse? Solo con venta cargada, veredicto limpio, nada
     * en vuelo, nada ya registrado y **sin una verificación pendiente**. Es la
     * ÚNICA fuente del `enabled` del botón.
     *
     * La última condición existe porque el guard sobrevive a
     * [FalloDelAbono.NO_SE_PUDO_VERIFICAR] a propósito: sin ella el botón queda
     * vivo, abre la hoja y `confirmar()` no hace nada — un botón que calla es
     * como un cobrador decide que la app está rota. Aquí se apaga, y la salida
     * es el reintento REAL de la banda, que vuelve a cargar y resuelve la duda
     * mirando el historial.
     */
    val sePuedeRegistrar: Boolean
        get() = venta != null &&
            veredicto.sePuedeRegistrar &&
            !guardando &&
            registrado == null &&
            !verificacionPendiente

    /** ¿La banda de fallo ofrece volver a revisar? Solo la duda se resuelve así. */
    val sePuedeRevisar: Boolean get() = verificacionPendiente

    /**
     * ¿Se puede seguir capturando? Con el cerrojo puesto, no: nada de lo que se
     * teclee puede terminar en un registro hasta que la duda se resuelva, y un
     * teclado que acepta lo que no lleva a ningún lado es la misma mentira que
     * un botón que no hace nada.
     */
    val sePuedeCapturar: Boolean
        get() = registrado == null && !guardando && confirmacion == null && !verificacionPendiente

    /**
     * ¿Se puede adjuntar otra foto? La MISMA guarda que el teclado, más el
     * techo del teléfono y "la cámara ya está abierta".
     *
     * Comparte [sePuedeCapturar] a propósito: adjuntar un comprobante a un
     * abono que ya se registró no lo alcanzaría —los comprobantes se escriben
     * en el mismo paso que el dinero—, así que ofrecer el botón sería ofrecer
     * un botón mudo, que es exactamente lo que esta pantalla no hace.
     */
    val sePuedeAgregarFoto: Boolean
        get() = sePuedeCapturar && destinoDeFoto == null && espaciosLibres > 0

    /**
     * Cuántos comprobantes más caben. Cuenta **solo los puestos**: un cuadro
     * ámbar no ocupa lugar porque no hay ninguna foto detrás de él, y descontarlo
     * dejaría al cobrador con un espacio menos por cada archivo que el servidor
     * rechazó.
     */
    val espaciosLibres: Int
        get() = (Comprobantes.MAXIMO - comprobantes.size).coerceAtLeast(0)
}
