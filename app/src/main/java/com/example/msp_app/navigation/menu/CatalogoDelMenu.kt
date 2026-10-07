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
     * ## Corrección R9-bis (revisión de la Task 10; corrige R9)
     *
     * El ruling R9 original (ver `git log` de este archivo) sólo verificó que
     * las 20 rutas candidatas del brief estuvieran REGISTRADAS en el grafo —
     * confundió "existe como destino" con "compone el menú". El pre-flight de
     * esta tarea (`.superpowers/sdd/2026-10-06-menu-lateral/preflight.md`,
     * hallazgo #10, §4.5) ya había señalado esto: sólo las pantallas que
     * ADOPTARÁN `MenuDeLaApp` (Tasks 11–16) pertenecen aquí. Las otras 10
     * (verificadas una por una: ninguna de las Tasks 11–16 las adopta, y tres
     * —`REGISTRAR_ABONO`, `VisitasRutas.REGISTRAR` y, por la misma regla,
     * cualquier pantalla sin hamburguesa— quedan fuera por decisión explícita
     * del dueño de no ponerles menú) se QUITARON:
     *
     * - `PagosRutas.BITACORA` — bitácora de una venta, sin hamburguesa.
     * - `Screen.SaleMap.route` — mapa de una venta, sin hamburguesa.
     * - `Screen.Forgiveness.route` — condonación (diálogo legado), sin hamburguesa.
     * - `Screen.Guarantee.route` — garantía de una venta, sin hamburguesa.
     * - `PagosRutas.REGISTRAR_ABONO` — decisión explícita del dueño: sin menú.
     * - `VisitasRutas.REGISTRAR` — decisión explícita del dueño: sin menú.
     * - `Screen.EditSale.route` — edición de venta, sin hamburguesa.
     * - `Screen.TransferDetail.route` — detalle de traspaso, sin hamburguesa.
     * - `Screen.NewTransfer.route` — alta de traspaso, sin hamburguesa.
     * - `Screen.GuaranteeDetail.route` — detalle de garantía, sin hamburguesa.
     *
     * Quedan las 10 que el pre-flight señala como las que SÍ adoptan el menú:
     * `DETALLE_CLIENTE`/`DETALLE_VENTA` (Task 16) y `SaleDetails`,
     * `PaymentTicket`, `VisitTicket`, `NewSale`, `SaleDetailsList`,
     * `SaleDescripction`, `ProductDetails`, `CreateGuarantee` (Tasks 13–15).
     *
     * Costo si una pantalla futura adopta el menú en una ruta quitada: su
     * sección no se abre sola hasta que se agregue la ruta aquí — defecto
     * visible y barato (mismo análisis del pre-flight).
     */
    private val RUTAS_HIJAS: Map<String, String> = mapOf(
        PagosRutas.DETALLE_CLIENTE to COBRO,
        PagosRutas.DETALLE_VENTA to COBRO,
        Screen.SaleDetails.route to COBRO,
        Screen.PaymentTicket.route to COBRO,
        Screen.VisitTicket.route to COBRO,
        Screen.NewSale.route to VENTAS,
        Screen.SaleDetailsList.route to VENTAS,
        Screen.SaleDescripction.route to VENTAS,
        Screen.ProductDetails.route to VENTAS,
        Screen.CreateGuarantee.route to GARANTIAS
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
