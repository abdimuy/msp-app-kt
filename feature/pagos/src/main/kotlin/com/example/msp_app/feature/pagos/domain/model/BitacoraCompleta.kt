package com.example.msp_app.feature.pagos.domain.model

import java.time.LocalDate

/**
 * Todo lo que ha pasado en UNA VENTA, para la pantalla de bitácora.
 *
 * **Es por venta, no por cliente**, desde que el detalle de cliente perdió su
 * sección "últimos contactos" (decisión del dueño): la única puerta a "ver los N
 * contactos" es ahora el detalle de VENTA, así que esta pantalla contesta
 * *"¿qué ha pasado con ESTA cuenta?"* y no *"con este domicilio"*. [ventaId] es
 * el `DOCTO_CC_ACR_ID` que la ruta lleva, igual que [DetalleVenta.ventaId].
 *
 * [titulo] es el producto de la venta —`descripcion.ifBlank { folio }`, el mismo
 * criterio que [com.example.msp_app.feature.pagos.application.CargarDetalleVenta.titulo]—
 * y es lo que la pantalla pinta en grande: una cuenta se reconoce por su mueble,
 * no por su folio. [nombre] sigue siendo el del cliente, y baja al subtítulo.
 *
 * ## Por qué también trae [direccion], si la pantalla no la pinta
 *
 * Porque el mapa que se abre desde un renglón SÍ la pinta: la hoja al pie del
 * mapa dice de qué puerta se trata, y una coordenada suelta no se lo dice a
 * nadie. La que viaja es la **del cliente** —la misma que ya usa el detalle— y
 * no una "dirección del contacto", que no existe: la app no guarda una calle por
 * abono ni por visita, guarda un punto. Y es la respuesta correcta además de la
 * única disponible: el abono se cobró y la visita se hizo **en esa puerta**;
 * el punto dice dónde cayó el teléfono, la calle dice de quién es la puerta.
 *
 * Sale de la misma fila representante del cliente de la que ya salía [nombre],
 * así que no agrega una sola lectura.
 */
data class BitacoraCompleta(
    val clienteId: Int,
    val ventaId: Int,
    val nombre: String,
    val titulo: String,
    val direccion: String,
    /** Sólo lo de ESTA venta — visitas sin cuenta no entran. Ver `CargarBitacoraDeLaVenta`. */
    val contactos: List<ContactoDeCobranza>,
    /**
     * El día de negocio en que se cargó esta pantalla.
     *
     * Existe para que el toque de un renglón sepa si ese cobro es **de hoy** —la
     * mitad de la condición que decide si el toque pregunta *ubicación o ticket*
     * en vez de abrir el mapa directo, ver
     * [com.example.msp_app.feature.pagos.domain.ToqueDelContacto]— con un "hoy"
     * que viene del [com.example.msp_app.core.common.time.AppClock] del caso de
     * uso y **no de dentro de un `@Composable`**.
     *
     * Mismo criterio que [DetalleCliente.hoy], y **sin default** por la misma
     * razón: un default sería un `LocalDate.now()` escrito en otro lado, que es
     * justo lo que esto evita.
     */
    val hoy: LocalDate
)
