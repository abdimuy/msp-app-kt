package com.example.msp_app.core.navegacion.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.ChartColumn
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Map
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.ShoppingCart
import com.composables.icons.lucide.Signal
import com.composables.icons.lucide.Truck
import com.composables.icons.lucide.UserCog
import com.composables.icons.lucide.Users
import com.example.msp_app.core.navegacion.domain.model.IconoDelMenu

/** Medido 2026-10-06: los once nombres existen en `icons-lucide-1.0.0` (`unzip -l` del jar del caché). */
internal fun IconoDelMenu.vector(): ImageVector = when (this) {
    IconoDelMenu.INICIO -> Lucide.House
    IconoDelMenu.CLIENTES -> Lucide.Users
    IconoDelMenu.REPORTE -> Lucide.ChartColumn
    IconoDelMenu.MAPA -> Lucide.Map
    IconoDelMenu.VENTAS -> Lucide.ShoppingCart
    IconoDelMenu.CAMIONETA -> Lucide.Truck
    IconoDelMenu.CATALOGO -> Lucide.BookOpen
    IconoDelMenu.TRASPASOS -> Lucide.ArrowLeftRight
    IconoDelMenu.ASIGNACION -> Lucide.UserCog
    IconoDelMenu.GARANTIAS -> Lucide.ShieldCheck
    IconoDelMenu.CONFIGURACION -> Lucide.Settings
    IconoDelMenu.DATOS -> Lucide.Signal
}
