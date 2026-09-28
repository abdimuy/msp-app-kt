package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.feature.pagos.domain.model.CondonacionDelHistorial
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.model.TipoDeContacto
import com.example.msp_app.feature.pagos.domain.model.VisitaDelCliente

/**
 * La bitácora del domicilio: **visitas, abonos y condonaciones en una sola
 * línea de tiempo**.
 *
 * Se mezclan a propósito. El cobrador no recuerda dos listas, recuerda una
 * historia: *"la vez pasada me dijo que el viernes, y antes sí me pagó"*.
 * Separarlas obligaría a leer dos columnas y cruzarlas con la vista.
 *
 * ## Por qué es dominio puro y no un método privado del detalle
 *
 * Vivía dentro de `CargarDetalleCliente` como función privada. Ahora la
 * consumen **dos** pantallas —el detalle, que enseña los tres últimos, y la
 * bitácora completa— y dos copias de esta mezcla se despegarían: bastaría con
 * que una clasificara una cita distinto para que el mismo hecho contara dos
 * historias en dos pantallas de la misma app.
 *
 * El estado de cada visita sale de [TipoVisitaCatalogo.estadoDe], que es
 * **consumirlo, no re-derivarlo**: la clasificación literal→estado tiene un solo
 * dueño en el repo y es ese objeto.
 *
 * ## El punto viaja con el contacto, y no se inventa el que falta
 *
 * Cada contacto se lleva la ubicación de SU hecho —la del abono si fue abono, la
 * de la visita si fue visita— y `null` cuando ese hecho no trajo punto. No se
 * rellena con el punto del contacto anterior ni con el del último cobro: eso
 * pondría un pin sobre una puerta que ese día nadie midió, que es la clase de
 * dato falso que [ContactoDeCobranza.ubicacion] existe para no afirmar. Aquí
 * tampoco se decide qué par es válido: eso ya lo decidió
 * [com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro], del lado
 * del adaptador, y la mezcla solo copia.
 *
 * ## La cuenta viaja igual que el punto, salvo que la visita nunca la lleva
 *
 * [cuentas] llega YA resuelto —`ventaId → nombre de cuenta`— porque este
 * mezclador es dominio puro y no puede llamar a
 * [com.example.msp_app.feature.pagos.domain.port.ProductosPort] (un puerto de
 * `application/`); quien lo arma es
 * [com.example.msp_app.feature.pagos.application.cuentasDeLasVentas]. Un pago
 * SÍ busca su cuenta en el mapa por [PagoDelHistorial.ventaId]; una visita
 * NUNCA la busca, ni siquiera cuando el mapa trae la cuenta de su propia
 * [VisitaDelCliente.ventaId] — es la misma decisión cerrada que ya vale para
 * [com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro], y por la
 * misma razón: el diálogo de visita no elige cuenta, así que afirmar una ahí
 * sería inventar un dato que el cobrador nunca dio.
 */
object BitacoraDelCliente {

    /**
     * Todo lo que pasó en ese domicilio, de lo más reciente a lo más viejo.
     *
     * @param cuentas `ventaId → nombre de cuenta`, ya normalizado. Vacío por
     *   defecto: los llamadores que no resuelven cuentas (o los tests que no
     *   la necesitan) siguen compilando y cada [ContactoDeCobranza.cuenta]
     *   sale en `null`.
     */
    fun de(
        visitas: List<VisitaDelCliente>,
        pagos: List<PagoDelHistorial>,
        cuentas: Map<Int, String> = emptyMap(),
        condonaciones: List<CondonacionDelHistorial> = emptyList()
    ): List<ContactoDeCobranza> {
        val deVisitas = visitas.map { visita ->
            ContactoDeCobranza(
                id = visita.visitaId,
                fecha = visita.fecha,
                // Sin `.lowercase()`: el literal ya viene con mayúscula inicial
                // de TipoVisitaCatalogo, que es quien defiende esa garantía
                // (TipoVisitaCatalogoTest) — normalizarlo aquí sería repartir la
                // regla en la mezcla en vez de dejarla en el catálogo (Task 1,
                // principio 10).
                etiqueta = visita.tipoVisita,
                nota = visita.nota?.takeIf { it.isNotBlank() },
                tipo = TipoDeContacto.VISITA,
                // `metodo` se queda en null A PROPÓSITO: ver su KDoc. La columna
                // de la visita siempre trae 0 y leerla diría "efectivo" sobre
                // una puerta donde no se cobró.
                cobrador = visita.cobrador,
                ventaId = visita.ventaId,
                // El MISMO par (literal, ¿trae día de cita?) que usa el deriver:
                // una cita en la bitácora tiene que verse como cita, no como el
                // "vuelvo" de su literal de cable.
                estado = TipoVisitaCatalogo.estadoDe(visita.tipoVisita, visita.fechaCita != null),
                importe = null,
                ubicacion = visita.ubicacion
            )
        }
        val dePagos = pagos.map { pago ->
            ContactoDeCobranza(
                id = pago.pagoId,
                fecha = pago.fecha,
                etiqueta = ETIQUETA_DEL_ABONO,
                nota = pago.nota,
                estado = EstadoCuenta.PAGO,
                importe = pago.importe,
                tipo = TipoDeContacto.COBRO,
                metodo = pago.metodo,
                cobrador = pago.cobrador,
                ventaId = pago.ventaId,
                // `null` cuando la cuenta no está en el mapa: una venta sin
                // renglones de `products` sincronizados todavía. Nunca un
                // texto de relleno ni el folio de repuesto — ver el KDoc de
                // ContactoDeCobranza.cuenta.
                cuenta = cuentas[pago.ventaId],
                ubicacion = pago.ubicacion
            )
        }
        return (deVisitas + dePagos + deCondonaciones(condonaciones, cuentas))
            .sortedByDescending { it.fecha }
    }

    /**
     * Las condonaciones como contactos de tipo [TipoDeContacto.CONDONACION].
     *
     * - **Etiqueta propia** —"Condonación", o "Condonación no aplicada" cuando el
     *   servidor la rechazó—: nunca "Abono", porque no entró dinero.
     * - **Sin [ContactoDeCobranza.metodo]**: condonar no es una forma de pago, y
     *   `MetodoDeCobro.de(137026)` pintaría un método que nadie usó.
     * - **[ContactoDeCobranza.estado] = [EstadoCuenta.SIN_TOCAR]**, que es el
     *   único de los ocho que no afirma nada del periodo — ni pago, ni visita, ni
     *   promesa. Ningún consumidor lo lee para pintar texto; el color del punto
     *   de una condonación lo decide su tipo en la fila, no este estado.
     */
    private fun deCondonaciones(
        condonaciones: List<CondonacionDelHistorial>,
        cuentas: Map<Int, String>
    ): List<ContactoDeCobranza> = condonaciones.map { condonacion ->
        ContactoDeCobranza(
            id = condonacion.condonacionId,
            fecha = condonacion.fecha,
            etiqueta = if (condonacion.aplicada) {
                ETIQUETA_DE_LA_CONDONACION
            } else {
                ETIQUETA_DE_LA_CONDONACION_NO_APLICADA
            },
            nota = null,
            estado = EstadoCuenta.SIN_TOCAR,
            importe = condonacion.importe,
            tipo = TipoDeContacto.CONDONACION,
            metodo = null,
            cobrador = condonacion.cobrador,
            ventaId = condonacion.ventaId,
            cuenta = cuentas[condonacion.ventaId],
            ubicacion = condonacion.ubicacion,
            aplicado = condonacion.aplicada
        )
    }

    /**
     * Cuántos contactos pinta el detalle de venta antes de "ver los N".
     *
     * Cinco y no los tres que pintaba el detalle de cliente: decisión del
     * dueño al ver el mock (*"deja unos 5"*). La lista de ahí abajo se
     * agrupa por mes, y con tres filas el mes casi siempre daba un solo
     * encabezado.
     */
    const val VISIBLES_EN_LA_VENTA: Int = 5

    /**
     * Etiqueta estática del abono en la bitácora.
     *
     * "Abono" y no "Cobré": es la palabra del botón que lo registra y no está
     * conjugada — la fila dice qué pasó, no quién habla (decisión del dueño,
     * mock `fila-de-contactos.html`, sección 05).
     */
    private const val ETIQUETA_DEL_ABONO = "Abono"

    /** Etiqueta de una condonación que bajó la deuda (o está por subir). */
    const val ETIQUETA_DE_LA_CONDONACION = "Condonación"

    /**
     * Etiqueta de una condonación que el servidor rechazó. Se enseña —se intentó
     * y la oficina la tiene— pero sin afirmar que bajó la deuda.
     */
    const val ETIQUETA_DE_LA_CONDONACION_NO_APLICADA = "Condonación no aplicada"
}
