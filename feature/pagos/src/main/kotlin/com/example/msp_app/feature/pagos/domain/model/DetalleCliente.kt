package com.example.msp_app.feature.pagos.domain.model

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.CuotaDeLaVenta
import com.example.msp_app.feature.pagos.domain.OrigenDeLaCuota
import java.time.Instant
import java.time.LocalDate

/**
 * El detalle de UN cliente con sus ventas dentro.
 *
 * **Por qué por cliente y no por venta** (decisión del `task-16-brief.md`, no
 * se relitiga): un cliente puede estar vencido en una venta y al corriente en
 * otra, y agregar todo a nivel cliente pierde exactamente ese caso — que es el
 * que decide si el cobrador vuelve o no. Por eso [ventas] es una lista y cada
 * una carga su propio [VentaDelCliente.estado].
 *
 * Todo importe es [Money]: este tipo vive en `domain/model` y lo consumen
 * `application/` y `ui/`, así que ya cruzó la frontera de la REGLA DE DINERO
 * (`global-constraints.md`). El `BigDecimal` pelado de
 * [com.example.msp_app.core.common.cobranza.domain.CuentaDelPeriodo] se queda
 * del otro lado, en `cobranza/domain`.
 */
data class DetalleCliente(
    val clienteId: Int,
    val nombre: String,
    val telefono: String,
    val direccion: String,
    val zona: String,
    val aval: String,
    /**
     * A quién llama el cobrador cuando el cliente no contesta.
     *
     * **`null` hoy, siempre, y no es un olvido:** no existe la columna. `sales`
     * trae `AVAL_O_RESPONSABLE` (el nombre) y `TELEFONO` (el del CLIENTE), y el
     * DTO de cobranza (`VentaDto.aval_o_responsable`) tampoco trae teléfono —
     * verificado con `grep -rn "AVAL\|aval"` sobre `:core:database` y sobre
     * `data/api/services/cobranza`, que es la misma consulta que SÍ encontró el
     * nombre. La fila no se pinta mientras el dato no exista, en vez de
     * rellenarla con el teléfono del cliente, que ya está en el encabezado y no
     * es a quien se llama.
     */
    val telefonoAval: String?,
    val saldoTotal: Money,
    val ventas: List<VentaDelCliente>,
    val contactos: List<ContactoDeCobranza>,
    val totalContactos: Int,
    /**
     * Lo que trae la VENTA en su campo `NOTAS`, tal como llega del servidor.
     *
     * **No es la ficha del cliente y por eso ya no se llama así.** La Task 16 lo
     * nombró `ficha` porque era lo único parecido que existía; es dato del
     * servidor, se reescribe en cada sincronización de `sales` y el cobrador no
     * lo puede editar. La ficha de verdad —conocimiento local, editable,
     * persistente— es [ficha], y confundirlas era exactamente el riesgo:
     * escribir en una creyendo escribir en la otra.
     */
    val notaDeLaVenta: String?,
    /**
     * La ficha del cliente: el catálogo cerrado y la nota libre.
     *
     * **`null` significa "no se pudo leer", NO "no tiene".** Un cliente sin
     * ficha llega como [FichaDelCliente] vacía. La distinción es la que impide
     * que una lectura fallida se pinte como ficha en blanco y el cobrador
     * escriba encima del conocimiento que sí estaba guardado — ver
     * [com.example.msp_app.feature.pagos.domain.port.FichaDelClientePort.fichaDe].
     */
    val ficha: FichaDelCliente?,
    val liquidacion: Liquidacion?,
    val ultimaVisita: Instant?,
    /**
     * El día de negocio en que se cargó esta pantalla.
     *
     * Existe para que la **edad de la nota** —*"hace 3 días"*— se calcule con un
     * "hoy" que viene del [com.example.msp_app.core.common.time.AppClock] del
     * caso de uso y **no de dentro de un `@Composable`**. Una pantalla que
     * preguntara la hora por su cuenta haría que los goldens cambiaran de texto
     * cada día que pasa, y que el mismo estado se pintara distinto en dos
     * recomposiciones.
     *
     * No tiene default a propósito: un default sería `LocalDate.now()` escrito
     * en otro lado, que es justo lo que esto evita.
     */
    val hoy: LocalDate,
    /**
     * El día de la semana en que le toca la ruta — `DIA_TEMPORAL_COBRANZA` si
     * alguien lo movió esta vuelta, y si no `DIA_COBRANZA`. Ver
     * [DatosDeVenta.diaDeRuta], que es donde vive esa precedencia.
     *
     * Es del **domicilio**, no de una venta: el cobrador pasa una vez por la
     * puerta. Sale del mismo representante del cliente del que ya salen nombre,
     * teléfono, zona y aval. Vacío cuando la fila no lo trae.
     */
    val diaDeRuta: String = "",
    /** `FREC_PAGO` del representante — "semanal", "quincenal", "mensual". */
    val frecuencia: String = "",
    /**
     * Las tres cifras y la tira de ritmo del bloque de saldo. Ver
     * [ResumenDelCliente].
     */
    val resumen: ResumenDelCliente = ResumenDelCliente(),
    /**
     * Los productos de TODAS sus ventas, con su importe real.
     *
     * A nivel cliente es la lista de lo que hay en esa casa, que es como el
     * cobrador la reconoce ("el refri y la sala"). Sale de `products`, no del
     * `GROUP_CONCAT` de la venta — ver
     * [com.example.msp_app.feature.pagos.domain.port.ProductosPort].
     *
     * **Ya no se pinta** (decisión del dueño): esos mismos nombres encabezan
     * cada renglón de "sus ventas" —la cuenta se nombra por su producto—, así
     * que la hoja repetía la lista un dedo más abajo. Se queda en el modelo
     * porque la lectura que lo llena sigue siendo obligatoria: de ella sale
     * también el nombre de la cuenta de cada contacto de la bitácora, así que
     * exponerla no cuesta una consulta. Quitarla es alcance aparte.
     */
    val productos: List<ProductoDeVenta> = emptyList(),
    /**
     * Dónde se le cobró la última vez, para el pin del mapa.
     *
     * **Es un punto medido, no una dirección geocodificada.** Sale de
     * `Payment.LAT`/`LNG` del abono más reciente: la puerta donde el cobrador de
     * verdad estuvo parado. `null` cuando ningún abono suyo trae coordenadas —y
     * entonces no se pinta un pin en un lugar inventado.
     */
    val ultimoCobroAqui: UbicacionDelCobro? = null
) {
    /** Cuántas cuentas tiene — el badge "N cuentas" del encabezado. */
    val cuentas: Int get() = ventas.size
}

/**
 * Lo que el bloque de saldo del detalle dice además del saldo: **suele dar**,
 * **pídele hoy**, **último pago**, los atrasos y la tira de ritmo.
 *
 * Va junto y no suelto en [DetalleCliente] porque es un bloque de la pantalla,
 * y porque son las cifras DERIVADAS: todas salen de
 * [com.example.msp_app.feature.pagos.domain.MontosSugeridosDelCliente] y
 * [com.example.msp_app.feature.pagos.domain.RitmoDePagos], calculadas en
 * `application/` una vez por carga. La UI las **consume**; derivarlas en un
 * Composable sería re-derivarlas en cada recomposición.
 */
data class ResumenDelCliente(
    /**
     * Lo que suele dar por visita. `null` = ninguna cuenta trae el dato.
     *
     * **Ya no se pinta en el bloque de saldo** — el dueño la marcó como que no
     * sirve para nada. Se queda calculada en el modelo, sin consumidor en
     * `ui/` a la fecha de este cambio, porque borrarla es alcance aparte de
     * quitarla de la pantalla.
     */
    val promedioDeMicrosip: Money? = null,
    /**
     * Lo que hay que pedirle hoy para dejarlo sin atraso.
     *
     * **Ya no se pinta en el bloque de saldo** por la misma razón que
     * [promedioDeMicrosip]: se queda calculada, sin consumidor en `ui/` a la
     * fecha de este cambio, en vez de borrarla de un modelo que otro agente
     * puede estar tocando en paralelo.
     */
    val pideleHoy: Money = Money.ZERO,
    /** El día del último abono, de la cuenta que sea. `null` si nunca pagó. */
    val ultimoPago: LocalDate? = null,
    /** Cuántas parcialidades debe, sumando sus cuentas. */
    val atrasos: Int = 0,
    /** Las doce semanas de la tira, del cliente completo. */
    val ritmo: List<SemanaDeRitmo> = emptyList(),
    /** Cuántas de esas semanas cumplió. */
    val semanasCumplidas: Int = 0,
    /**
     * La parcialidad esperada, SUMADA entre sus cuentas — el mismo importe
     * con el que [ritmo] se arma (ver
     * [com.example.msp_app.feature.pagos.application.CargarDetalleCliente.resumenDe]).
     *
     * Reemplaza a [promedioDeMicrosip] y [pideleHoy] en el bloque de saldo: el
     * dueño pidió la parcialidad en su lugar porque las otras dos "no sirven
     * para nada". No es una cifra nueva — ya se calculaba para el ritmo y
     * simplemente se expone aquí en vez de re-derivarla en la UI.
     */
    val parcialidad: Money = Money.ZERO
) {
    /** El "N de 12" del encabezado del ritmo. */
    val semanasTotales: Int get() = ritmo.size
}

/**
 * Una venta del cliente, tal como se pinta en la lista "sus ventas".
 *
 * [estado] es el resultado de
 * [com.example.msp_app.core.common.cobranza.domain.EstadoCuentaDeriver] — el
 * catálogo de ocho de la Task 14, envuelto en [EstadoDelPeriodo]. La UI lo
 * **consume**; no lo vuelve a derivar, ni siquiera parcialmente.
 */
data class VentaDelCliente(
    val ventaId: Int,
    val folio: String,
    val descripcion: String,
    override val saldo: Money,
    override val parcialidad: Money,
    val abonosPagados: Int,
    val abonosTotales: Int,
    val avance: Float,
    override val estado: EstadoDelPeriodo,
    /**
     * Cuántos pagos lleva atrasados — leído de `NUM_PAGOS_ATRASADOS`, no
     * derivado. Ver el KDoc de [DatosDeVenta.atrasos] para el porqué.
     */
    val atrasos: Int = 0,
    /**
     * ## Los seis campos de abajo: por qué la fila de una venta los carga
     *
     * No son adorno de la lista. Son **exactamente** lo que
     * [com.example.msp_app.feature.pagos.domain.MontosSugeridos] necesita para
     * calcular un sugerido, y sin ellos el "pídele hoy" del detalle del cliente
     * no se puede derivar sin volver a leer las ventas crudas desde la UI — que
     * es la capa que tiene prohibido hacerlo.
     *
     * El cálculo por cliente es la SUMA de los de sus ventas (ver
     * `MontosSugeridosDelCliente`), así que cada fila tiene que traer su parte.
     * Aplanarlos a nivel cliente perdería el caso que este modelo existe para no
     * perder: una venta al corriente y otra vencida no se suman a "medio
     * vencido".
     */
    override val fechaVenta: LocalDate? = null,
    override val frecuencia: String = "",
    val totalVenta: Money = Money.ZERO,
    override val enganche: Money = Money.ZERO,
    override val abonado: Money = Money.ZERO,
    /** "Hoy liquida con" de ESTA venta — por venta, nunca por cliente. */
    override val liquidacion: Liquidacion? = null,
    /** Lo que suele dar en esta cuenta. Ver [DatosDeVenta.pagoPromedio]. */
    val pagoPromedio: Money? = null,
    /**
     * La cuota afirmable de esta cuenta.
     *
     * **El detalle de CLIENTE todavía no la deriva**: por default es la
     * parcialidad capturada, o sea el comportamiento de siempre. La regla de
     * los tres escalones ([CuotaDeLaVenta]) entró por la pantalla de abono, que
     * es donde el dueño vio el defecto y la única que ya cargaba los pagos de
     * cada venta.
     *
     * Traerla acá es un paso aparte y NO gratis: `CargarDetalleCliente` tendría
     * que leer los pagos de TODAS las ventas del cliente para derivar la moda
     * de cada una. Se dice aquí, con su costo, en vez de dejar creer que el
     * "pídele hoy" del detalle de cliente ya está cubierto — no lo está.
     */
    override val cuota: CuotaDeLaVenta = CuotaDeLaVenta(parcialidad, OrigenDeLaCuota.PARCIALIDAD)
) : CuentaCobrable

/**
 * Qué clase de hecho es una línea de la bitácora.
 *
 * Existe porque los **filtros** lo necesitan y porque derivarlo de otra cosa
 * era frágil: hasta ahora "es un cobro" se podía adivinar por
 * `importe != null` o por la etiqueta `"cobré"`, y las dos adivinanzas se
 * rompen solas — una visita con promesa podría traer monto algún día, y la
 * etiqueta es texto de usuario que este mismo plan ya cambió una vez.
 */
enum class TipoDeContacto {
    /** Entró dinero. Trae [ContactoDeCobranza.importe] y método. */
    COBRO,

    /** Se tocó la puerta. Nunca trae método — ver [ContactoDeCobranza.metodo]. */
    VISITA
}

/**
 * Una línea de la bitácora "últimos contactos". [estado] viene del catálogo de
 * ocho aplicado al literal de `TIPO_VISITA` — otra vez, consumido, no derivado
 * en la pantalla.
 */
data class ContactoDeCobranza(
    /**
     * El identificador del hecho: `PagoDelHistorial.pagoId` de un abono,
     * `VisitaDelCliente.visitaId` de una visita — nunca vacío en un contacto
     * que produce [com.example.msp_app.feature.pagos.domain.BitacoraDelCliente.de].
     *
     * Existe para la llave de `LazyColumn` de la bitácora: dos contactos con
     * el MISMO instante y el MISMO importe existen de verdad —dos ventas del
     * mismo cliente con un abono igual al mismo minuto, el caso que
     * `BitacoraDelClienteTest` prueba con `VENTA_RECAMARA`/`VENTA_BOCINA`—, y
     * `fecha + etiqueta + importe` no basta para distinguirlos: con esa llave
     * Compose recicla mal la fila y uno de los dos abonos desaparece de la
     * lista. `pagoId`/`visitaId` no puede chocar entre sí (son UUID de
     * fuentes distintas) ni consigo mismo (son la clave primaria de su propia
     * fila), así que es la única llave que no puede repetirse por accidente.
     */
    val id: String,
    val fecha: Instant,
    val etiqueta: String,
    val nota: String?,
    val estado: EstadoCuenta,
    val importe: Money?,
    /** Cobro o visita. Lo que los filtros preguntan. */
    val tipo: TipoDeContacto = TipoDeContacto.VISITA,
    /**
     * Con qué se pagó, o `null` cuando no hubo pago.
     *
     * **Siempre `null` en una visita, y no por falta de ganas.** La columna
     * `FORMA_COBRO_ID` existe en `VisitEntity`, pero el escritor de producción
     * la deja fija: *"el diálogo de hoy tampoco tiene selector de forma de cobro
     * para las visitas: el contrato de cable lleva el campo y siempre viaja en
     * 0"* (`RegistroDeVisitaAdapter`). Y `MetodoDeCobro.de(0)` cae a EFECTIVO,
     * así que leerla pintaría **"efectivo" sobre un "no estaba"** — un cobro que
     * nunca ocurrió, en una pantalla de dinero.
     *
     * Su ausencia en la visita dice algo cierto: ahí no se cobró.
     */
    val metodo: MetodoDeCobro? = null,
    /** Quién lo registró. Vacío cuando la fila no lo trae. */
    val cobrador: String = "",
    /**
     * De qué cuenta fue, o `null` cuando el hecho es del domicilio y no de una
     * venta —una visita que el cobrador registró sin elegir cuenta—.
     *
     * Lo usa el detalle de VENTA para destacar lo suyo dentro de la línea de
     * tiempo del cliente sin mentir sobre lo que no le pertenece.
     */
    val ventaId: Int? = null,
    /**
     * Cómo se llama la cuenta: el nombre del PRIMER producto de la venta, por
     * `POSICION` (`task-2-brief.md`). Decisión del dueño: la cuenta se
     * identifica SIEMPRE con el nombre del producto, nunca con el folio — el
     * folio es un dato de sistema, no algo que el cobrador reconozca parado en
     * la puerta.
     *
     * **Por qué el primero por `POSICION` y no el de mayor precio**, cuando la
     * cuenta trae varios artículos. `POSICION` es el orden de captura y es
     * exactamente el que
     * [com.example.msp_app.feature.pagos.domain.port.ProductosPort] ya
     * promete devolver — no hace falta un segundo criterio, ni comparar
     * [Money] (que traería su propio desempate cuando dos renglones cuestan lo
     * mismo). En el caso real que originó la tarea (folio `Y00001786`) el
     * primer renglón capturado es la recámara, el mueble principal de la
     * venta; "base de cama" es el complemento que la sigue.
     *
     * **`null`, y nunca un texto de relleno ni el folio de repuesto.** Dos
     * caminos aterrizan aquí, y los dos son el hecho, no un error:
     * - Este contacto es una VISITA. [tipo] == [TipoDeContacto.VISITA] nunca
     *   lleva cuenta, igual que nunca lleva [metodo] — decisión cerrada del
     *   dueño, aunque la visita sí traiga [ventaId] (una visita se puede
     *   registrar apuntando a una cuenta sin que por eso "sea" esa cuenta).
     * - Este contacto es un COBRO de una cuenta cuyos renglones de `products`
     *   todavía no sincronizaron — posible en un teléfono recién instalado,
     *   ver el KDoc de
     *   [com.example.msp_app.feature.pagos.domain.port.ProductosPort].
     *
     * **El nombre ya llega normalizado.** Microsip guarda `ARTICULO` en
     * MAYÚSCULAS; la regla que lo vuelve texto de usuario vive en un solo
     * lugar, [NombreDeProducto.normaliza], y ya corrió sobre este valor antes
     * de que llegara aquí.
     */
    val cuenta: String? = null,
    /**
     * Dónde pasó **este** contacto, cuando el teléfono lo pudo medir.
     *
     * Es lo que hace que tocar un renglón de la bitácora abra el mapa en el
     * punto de ese abono o de esa visita, y no en el del último cobro del
     * cliente: el cobrador pregunta *"¿dónde fue ESA vez?"*, y contestarle
     * siempre con la misma puerta sería contestarle otra cosa.
     *
     * `null` en todo contacto que se registró sin señal o sin permiso, y en todo
     * el histórico anterior a que se guardara. Un renglón sin punto **no se
     * puede tocar**: no hay nada que abrir.
     */
    val ubicacion: UbicacionDelCobro? = null
)

/**
 * "Hoy liquida con": cuánto cierra la deuda hoy y hasta cuándo vale esa cifra.
 *
 * Existe a nivel cliente **y** a nivel venta y **no es redundancia** (decisión
 * del brief): en el cliente cierra la conversación completa, en la venta cierra
 * ese mueble.
 *
 * El cálculo NO se reimplementa aquí: vive en `:app`
 * (`features/sales/domain/models/SettlementCalculator.kt`) y llega por
 * [com.example.msp_app.feature.pagos.domain.port.LiquidacionPort]. Reescribirlo
 * sería tocar lógica de dinero que esta tarea no vino a tocar.
 */
data class Liquidacion(
    val monto: Money,
    val vigenteHasta: LocalDate?,
    val categoria: String
)
