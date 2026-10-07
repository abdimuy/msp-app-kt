package com.example.msp_app.navigation.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.msp_app.BuildConfig
import com.example.msp_app.components.UpdateBanner
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.navegacion.ui.MenuLateral
import com.example.msp_app.core.navegacion.ui.MenuLateralContenedor
import com.example.msp_app.core.navegacion.ui.MenuLateralViewModel
import com.example.msp_app.core.navegacion.ui.components.PieDelMenu
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.auth.User

/**
 * El menú lateral nuevo para cualquier pantalla de `:app`. Misma forma que
 * `DrawerContainer(navController) { abrir -> … }`: adoptarlo es cambiar el
 * nombre de la llamada.
 *
 * `runCatching` sobre `LocalAuthViewModel` por la misma razón que el cajón
 * viejo: una red de prueba compone destinos sin sesión, y sin sesión el menú se
 * pinta con sólo el pie (Review Focus 4), nunca truena.
 *
 * ## `temaOscuro`/`montosOcultos`/`onAlternarTema`/`onAlternarMontos` (Ruling R2)
 *
 * El fragmento del plan para esta tarea es anterior al Ruling R2 de la Task 4:
 * hoy `MenuLateralContenedor` exige `onAlternarTema` (sin default) y
 * `MenuLateral` exige los cuatro parámetros del encabezado (sin default). Este
 * composable es el único punto que los conecta al ViewModel real
 * (`estado.temaOscuro`, `estado.montosOcultos`, `viewModel::alternarTema`,
 * `viewModel::alternarMontos`) — pasar `{}` aquí dejaría el botón sol/luna o
 * el ojo de privacidad mudos sin ningún error de compilación.
 */
@Composable
fun MenuDeLaApp(
    navController: NavController,
    contenido: @Composable (abrirMenu: () -> Unit) -> Unit
) {
    val authViewModel = runCatching { LocalAuthViewModel.current }.getOrNull()
    val usuario: User? = if (authViewModel != null) {
        val estadoDelUsuario by authViewModel.userData.collectAsState()
        (estadoDelUsuario as? ResultState.Success<User?>)?.data
    } else {
        null
    }
    val viewModel: MenuLateralViewModel = hiltViewModel()
    val estado by viewModel.estado.collectAsState()
    val rutaActual = navController.currentBackStackEntryAsState().value?.destination?.route
    val secciones = CatalogoDelMenu.secciones(usuario?.MODULOS.orEmpty(), estado.contadores)

    MenuLateralContenedor(
        hoja = { cerrar, apertura ->
            MenuLateral(
                cabecera = CatalogoDelMenu.cabecera(usuario, estado.envio, estado.nombreDeZona),
                secciones = secciones,
                activo = CatalogoDelMenu.activoPara(rutaActual),
                seccionesPlegadas = estado.plegadas,
                apertura = apertura,
                onTogglePlegado = viewModel::cambiarPlegado,
                onElegir = { elemento ->
                    cerrar()
                    navegarDesdeElMenu(navController, elemento.id)
                },
                temaOscuro = estado.temaOscuro,
                montosOcultos = estado.montosOcultos,
                onAlternarTema = viewModel::alternarTema,
                onAlternarMontos = viewModel::alternarMontos,
                pie = {
                    PieDelMenu(
                        version = "Versión ${BuildConfig.VERSION_NAME}",
                        onConfiguracion = {
                            cerrar()
                            navegarAConfiguracion(navController)
                        },
                        aviso = { UpdateBanner(compact = true) }
                    )
                }
            )
        },
        onAbierto = {
            viewModel.alAbrir(secciones.flatMap { seccion -> seccion.items.map { it.id } }.toSet())
        },
        onAlternarTema = viewModel::alternarTema,
        contenido = contenido
    )
}
