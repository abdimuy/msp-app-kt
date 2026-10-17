package com.example.msp_app.core.navegacion

import com.example.msp_app.core.navegacion.domain.model.CabeceraDelMenu
import com.example.msp_app.core.navegacion.domain.model.ElementoDelMenu
import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio
import com.example.msp_app.core.navegacion.domain.model.IconoDelMenu
import com.example.msp_app.core.navegacion.domain.model.SeccionDelMenu

/** El menú de un usuario con los cuatro módulos: 4 + 3 + 2 + 1 = 10 opciones. */
object MenuFixtures {

    val cobro = SeccionDelMenu(
        id = "COBRO",
        titulo = "Cobro",
        items = listOf(
            ElementoDelMenu("inicio", "Inicio", IconoDelMenu.INICIO),
            ElementoDelMenu(
                "clientes",
                "Clientes",
                IconoDelMenu.CLIENTES,
                contador = "8 por visitar"
            ),
            ElementoDelMenu("reporte_cobranza", "Reporte de cobranza", IconoDelMenu.REPORTE),
            ElementoDelMenu("mapa_rutas", "Mapa de rutas", IconoDelMenu.MAPA)
        )
    )

    val ventas = SeccionDelMenu(
        id = "VENTAS",
        titulo = "Ventas",
        items = listOf(
            ElementoDelMenu("ventas", "Ventas", IconoDelMenu.VENTAS, contador = "1 sin subir"),
            ElementoDelMenu("mi_camioneta", "Mi Camioneta", IconoDelMenu.CAMIONETA),
            ElementoDelMenu("catalogo", "Catálogo de Productos", IconoDelMenu.CATALOGO)
        )
    )

    val almacen = SeccionDelMenu(
        id = "ALMACEN",
        titulo = "Almacén",
        items = listOf(
            ElementoDelMenu("traspasos", "Traspasos", IconoDelMenu.TRASPASOS),
            ElementoDelMenu("asignacion", "Asignación de Camionetas", IconoDelMenu.ASIGNACION)
        )
    )

    val garantias = SeccionDelMenu(
        id = "GARANTIAS",
        titulo = "Garantías",
        items = listOf(ElementoDelMenu("garantias", "Garantías", IconoDelMenu.GARANTIAS))
    )

    val todas: List<SeccionDelMenu> = listOf(cobro, ventas, almacen, garantias)

    val TODO_ENVIADO_HACE_3 =
        EstadoDeEnvio(pagosPendientes = 0, ventasPendientes = 0, ultimoEnvioHaceMinutos = 3)

    val DOS_PAGOS_ESPERANDO =
        EstadoDeEnvio(pagosPendientes = 2, ventasPendientes = 0, ultimoEnvioHaceMinutos = null)

    // R4 (controlador, 2026-10-06): sin campo `rol` en la cabecera, no se muestra.
    // R3 (controlador, adelanto): el nombre de zona llega ya formateado
    // ("R/25"); nunca se construye un respaldo "Zona N" aquí.
    fun cabecera(envio: EstadoDeEnvio? = TODO_ENVIADO_HACE_3) = CabeceraDelMenu(
        iniciales = "ED",
        nombre = "Efraín Domínguez Reyes",
        nombreDeZona = "R/25",
        envio = envio
    )
}
