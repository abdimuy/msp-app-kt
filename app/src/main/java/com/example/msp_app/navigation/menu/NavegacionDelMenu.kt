package com.example.msp_app.navigation.menu

import androidx.navigation.NavController
import com.example.msp_app.navigation.Screen

/**
 * Navega desde el menú con las mismas pilas que `DrawerContainer` (Home abajo)
 * más dos reglas nuevas (Review Focus 2): la opción de la pantalla actual no
 * navega, y `launchSingleTop` evita la copia doble de un toque repetido.
 */
internal fun navegarDesdeElMenu(navController: NavController, itemId: String) {
    val entrada = CatalogoDelMenu.entrada(itemId) ?: return
    if (navController.currentDestination?.route == entrada.ruta) return
    navController.navigate(entrada.ruta) {
        if (entrada.ruta == Screen.Home.route) {
            popUpTo(Screen.Home.route) { inclusive = true }
        } else {
            popUpTo(Screen.Home.route)
        }
        launchSingleTop = true
    }
}

internal fun navegarAConfiguracion(navController: NavController) {
    if (navController.currentDestination?.route == Screen.Configuracion.route) return
    navController.navigate(Screen.Configuracion.route) { launchSingleTop = true }
}
