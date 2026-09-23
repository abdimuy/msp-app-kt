package com.example.msp_app.feature.pagos.domain.port

/**
 * Las tres acciones del detalle del cliente que **salen de la app**: marcar el
 * teléfono, escribir por WhatsApp y abrir la dirección en un mapa.
 *
 * ## Por qué un puerto y no un `Intent` dentro del Composable
 *
 * Lanzar el `Intent` desde la UI es lo que hace todo el mundo y aquí no sirve por
 * dos razones concretas:
 *
 *  1. **El contrato de capas** (Ruling BF, caso 3): la UI no puede ver
 *     `data/adapter`, y un `startActivity` con un `Context` es exactamente eso.
 *     Con el puerto, `DetalleClienteContent` se queda puro y los goldens lo
 *     pueden montar sin un `Context` de verdad.
 *  2. **La norma de errores.** `startActivity` lanza
 *     `ActivityNotFoundException` en un teléfono sin app de teléfono, sin
 *     WhatsApp o sin mapas — y los tres casos son reales en la flota. Metido en
 *     un `onClick`, ese fallo o tumba la app o se traga en un `catch` mudo. Como
 *     [Result], el ViewModel tiene que decidir qué hacer con él, y lo que hace es
 *     reportarlo.
 *
 * Devuelve `Result<Unit>` —como `PrinterPort`— y **nunca lanza**: un fallo al
 * abrir WhatsApp no puede tumbar la pantalla donde está el saldo.
 */
interface AccionesExternasPort {

    /**
     * Abre el marcador con el número puesto.
     *
     * **Marca, no llama.** `ACTION_DIAL` deja el número en el teclado y espera a
     * que la persona toque llamar; `ACTION_CALL` marcaría sola. Con el teléfono
     * en una mano y el cuaderno en la otra, un toque accidental que YA llamó al
     * cliente no se puede deshacer — y además `ACTION_DIAL` no necesita el
     * permiso `CALL_PHONE`, así que no hay diálogo que aceptar parado en una
     * puerta.
     */
    suspend fun marcar(telefono: String): Result<Unit>

    /** Abre la conversación de WhatsApp con ese número. */
    suspend fun escribirPorWhatsApp(telefono: String): Result<Unit>

    /**
     * Abre [destino] en la app de mapas que el teléfono tenga.
     *
     * No se asume Google Maps: el `Intent` es `geo:`, así que lo atiende
     * cualquiera de las instaladas.
     */
    suspend fun comoLlegar(destino: DestinoEnElMapa): Result<Unit>
}

/**
 * A dónde hay que llegar.
 *
 * [lat]/[lng] cuando se conoce el punto donde se cobró —un lugar MEDIDO— y solo
 * [direccion] cuando no. Los dos caminos existen porque son distintos de verdad:
 * con coordenadas el mapa lleva a la puerta, con la dirección lleva a donde el
 * buscador crea que está esa calle, y esa diferencia importa en una colonia sin
 * numeración.
 *
 * [etiqueta] es lo que el mapa escribe bajo el pin. **Es el nombre del cliente,
 * así que no viaja a telemetría** en ningún camino de error de este módulo.
 */
data class DestinoEnElMapa(
    val etiqueta: String,
    val direccion: String,
    val lat: Double? = null,
    val lng: Double? = null
)
