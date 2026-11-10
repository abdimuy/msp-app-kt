package com.example.msp_app.core.navegacion.domain.model

/**
 * Qué dibujo lleva una opción. El vector concreto (Lucide) lo decide `ui/`:
 * `domain/` no importa Compose (principio 15).
 */
enum class IconoDelMenu {
    INICIO,
    CLIENTES,
    REPORTE,
    MAPA,
    VENTAS,
    CAMIONETA,
    CATALOGO,
    TRASPASOS,
    ASIGNACION,
    GARANTIAS,
    CONFIGURACION,
    DATOS
}

/**
 * Una opción del menú. [contador] llega YA formateado ("8 por visitar") y sólo
 * cuando su fuente existe (spec §4): `null` es "sin contador", nunca un número
 * inventado.
 */
data class ElementoDelMenu(
    val id: String,
    val etiqueta: String,
    val icono: IconoDelMenu,
    val contador: String? = null
)

/** Una sección por módulo: `COBRO`, `VENTAS`, `ALMACEN`, `GARANTIAS`. */
data class SeccionDelMenu(
    val id: String,
    val titulo: String,
    val items: List<ElementoDelMenu>
)

/**
 * Lo que la tarjeta de la cabecera cuenta. Pagos y ventas van separados para
 * que el texto no diga "pagos" cuando lo que espera es una venta (decisión D6
 * del plan). [ultimoEnvioHaceMinutos] es `null` cuando no hay un dato honesto
 * que dar (nunca se sincronizó, falló el último intento o el reloj está raro).
 */
data class EstadoDeEnvio(
    val pagosPendientes: Int,
    val ventasPendientes: Int,
    val ultimoEnvioHaceMinutos: Long?
) {
    enum class Estado { TODO_ENVIADO, ESPERANDO_SENAL }

    val estado: Estado
        get() = if (pagosPendientes + ventasPendientes == 0) Estado.TODO_ENVIADO else Estado.ESPERANDO_SENAL
}

/**
 * La cabecera fija. No hay campo de rol: el dueño decidió no mostrarlo en el
 * menú (ruling R4 del plan). [nombreDeZona] es el texto que ya trae la zona
 * del usuario (p. ej. "R/25") y es opcional porque no toda zona tiene nombre
 * — este módulo nunca construye un respaldo "Zona N" (ruling R3 del plan;
 * quién arma ese texto final de verdad es la Task 3). [envio] es `null`
 * mientras la primera lectura no llega — pintar "Todo enviado" antes de
 * saberlo sería mentir.
 */
data class CabeceraDelMenu(
    val iniciales: String,
    val nombre: String,
    val nombreDeZona: String?,
    val envio: EstadoDeEnvio?
)

/** La sección que se abre sola y la opción que se resalta, según la pantalla actual. */
data class ActivoEnElMenu(
    val seccionId: String?,
    val itemId: String?
) {
    companion object {
        val NINGUNO = ActivoEnElMenu(seccionId = null, itemId = null)
    }
}
