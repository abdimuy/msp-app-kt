package com.example.msp_app.navigation.menu

import com.example.msp_app.core.navegacion.application.ReglasDelMenu
import com.example.msp_app.core.navegacion.domain.model.ActivoEnElMenu
import com.example.msp_app.core.navegacion.domain.model.CabeceraDelMenu
import com.example.msp_app.core.navegacion.domain.model.ElementoDelMenu
import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio
import com.example.msp_app.core.navegacion.domain.model.IconoDelMenu
import com.example.msp_app.core.navegacion.domain.model.SeccionDelMenu
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.feature.pagos.ui.PagosRutas
import com.example.msp_app.feature.visitas.ui.VisitasRutas
import com.example.msp_app.navigation.Screen

internal data class EntradaDelCatalogo(
    val elemento: ElementoDelMenu,
    val ruta: String,
    val seccionId: String
)

/**
 * Las opciones del menú por módulo, con las MISMAS etiquetas, rutas y orden que
 * `DrawerContainer.kt:136-334` (un cobrador no debe buscar nada de nuevo).
 *
 * ## Divergencia con el brief literal ([cabecera], Ruling R3/R4 del controlador)
 *
 * El brief pedía `cabecera(usuario, envio): CabeceraDelMenu` con campos
 * `.ruta`/`.rol`. `CabeceraDelMenu` (Task 1) no tiene esos campos: tiene
 * `nombreDeZona` (String?, SIN respaldo "Zona N" — ruling R3) y ningún campo
 * de rol (ruling R4, decisión del dueño). Este catálogo no deriva la zona de
 * `User.ZONA_CLIENTE_ID`: la recibe ya resuelta por parámetro — quien la
 * resuelve de verdad es `ZonaDelUsuarioPort` (Task 8), vía
 * `MenuLateralViewModel.estado.nombreDeZona`, que es lo que `MenuDeLaApp` le
 * pasa.
 */
internal object CatalogoDelMenu {

    private const val COBRO = "COBRO"
    private const val VENTAS = "VENTAS"
    private const val ALMACEN = "ALMACEN"
    private const val GARANTIAS = "GARANTIAS"

    private val TITULOS = linkedMapOf(
        COBRO to "Cobro",
        VENTAS to "Ventas",
        ALMACEN to "Almacén",
        GARANTIAS to "Garantías"
    )

    private val ENTRADAS: List<EntradaDelCatalogo> = listOf(
        entrada(IdsDelMenu.INICIO, "Inicio", IconoDelMenu.INICIO, Screen.Home.route, COBRO),
        entrada(
            IdsDelMenu.CLIENTES,
            "Clientes",
            IconoDelMenu.CLIENTES,
            PagosRutas.LISTA_CLIENTES,
            COBRO
        ),
        entrada(
            IdsDelMenu.REPORTE,
            "Reporte de cobranza",
            IconoDelMenu.REPORTE,
            Screen.DailyReport.route,
            COBRO
        ),
        entrada(IdsDelMenu.MAPA, "Mapa de rutas", IconoDelMenu.MAPA, Screen.RouteMap.route, COBRO),
        entrada(IdsDelMenu.VENTAS, "Ventas", IconoDelMenu.VENTAS, Screen.SaleHome.route, VENTAS),
        entrada(
            IdsDelMenu.MI_CAMIONETA,
            "Mi Camioneta",
            IconoDelMenu.CAMIONETA,
            Screen.Cart.route,
            VENTAS
        ),
        entrada(
            IdsDelMenu.CATALOGO,
            "Catálogo de Productos",
            IconoDelMenu.CATALOGO,
            Screen.ProductsCatalog.route,
            VENTAS
        ),
        entrada(
            IdsDelMenu.TRASPASOS,
            "Traspasos",
            IconoDelMenu.TRASPASOS,
            Screen.TransfersList.route,
            ALMACEN
        ),
        entrada(
            IdsDelMenu.ASIGNACION,
            "Asignación de Camionetas",
            IconoDelMenu.ASIGNACION,
            Screen.CamionetaAssignment.route,
            ALMACEN
        ),
        entrada(
            IdsDelMenu.GARANTIAS,
            "Garantías",
            IconoDelMenu.GARANTIAS,
            Screen.GuaranteeList.route,
            GARANTIAS
        )
    )

    /**
     * Rutas que no son una opción pero pertenecen a una sección: la abren sin
     * resaltar nada.
     *
     * Las 20 entradas, verificadas con `grep` contra `AppNavigation.kt` y los
     * grafos de `destinosDeCobranza`/`PagosRutas`/`VisitasRutas` (Ruling R9):
     * las 20 rutas candidatas del brief están registradas con `composable(...)`
     * en el grafo real — ninguna se quitó. Evidencia: cada constante aparece en
     * un `composable(<constante>)` (directo o por el mismo literal) en
     * `AppNavigation.kt`, `DestinosDeCobranzaGraph.kt`, `PagosRutas.kt` o
     * `VisitasRutas.kt`.
     */
    private val RUTAS_HIJAS: Map<String, String> = mapOf(
        PagosRutas.DETALLE_CLIENTE to COBRO,
        PagosRutas.DETALLE_VENTA to COBRO,
        PagosRutas.BITACORA to COBRO,
        Screen.SaleDetails.route to COBRO,
        Screen.SaleMap.route to COBRO,
        Screen.PaymentTicket.route to COBRO,
        Screen.VisitTicket.route to COBRO,
        Screen.Forgiveness.route to COBRO,
        Screen.Guarantee.route to COBRO,
        PagosRutas.REGISTRAR_ABONO to COBRO,
        VisitasRutas.REGISTRAR to COBRO,
        Screen.NewSale.route to VENTAS,
        Screen.SaleDetailsList.route to VENTAS,
        Screen.SaleDescripction.route to VENTAS,
        Screen.EditSale.route to VENTAS,
        Screen.ProductDetails.route to VENTAS,
        Screen.TransferDetail.route to ALMACEN,
        Screen.NewTransfer.route to ALMACEN,
        Screen.CreateGuarantee.route to GARANTIAS,
        Screen.GuaranteeDetail.route to GARANTIAS
    )

    private fun entrada(
        id: String,
        etiqueta: String,
        icono: IconoDelMenu,
        ruta: String,
        seccion: String
    ) = EntradaDelCatalogo(ElementoDelMenu(id, etiqueta, icono), ruta, seccion)

    fun secciones(modulos: List<String>, contadores: Map<String, String>): List<SeccionDelMenu> =
        TITULOS.filterKeys { it in modulos }.map { (id, titulo) ->
            SeccionDelMenu(
                id = id,
                titulo = titulo,
                items = ENTRADAS.filter {
                    it.seccionId == id
                }.map { it.elemento.copy(contador = contadores[it.elemento.id]) }
            )
        }

    fun activoPara(ruta: String?): ActivoEnElMenu {
        if (ruta == null) return ActivoEnElMenu.NINGUNO
        ENTRADAS.firstOrNull {
            it.ruta == ruta
        }?.let { return ActivoEnElMenu(it.seccionId, it.elemento.id) }
        return RUTAS_HIJAS[ruta]?.let { ActivoEnElMenu(it, null) } ?: ActivoEnElMenu.NINGUNO
    }

    fun entrada(itemId: String): EntradaDelCatalogo? =
        ENTRADAS.firstOrNull { it.elemento.id == itemId }

    /**
     * [nombreDeZona] llega YA resuelto (ruling R3): esta función no deriva
     * ningún respaldo "Zona N" a partir de `usuario`. Sin rol (ruling R4): el
     * modelo [CabeceraDelMenu] no tiene ese campo.
     */
    fun cabecera(usuario: User?, envio: EstadoDeEnvio?, nombreDeZona: String?): CabeceraDelMenu {
        val nombre = usuario?.NOMBRE.orEmpty()
        return CabeceraDelMenu(
            iniciales = ReglasDelMenu.iniciales(nombre),
            nombre = nombre,
            nombreDeZona = nombreDeZona,
            envio = envio
        )
    }
}
