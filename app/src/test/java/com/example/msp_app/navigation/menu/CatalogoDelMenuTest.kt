package com.example.msp_app.navigation.menu

import com.example.msp_app.core.navegacion.domain.model.ActivoEnElMenu
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.feature.pagos.ui.PagosRutas
import com.example.msp_app.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Adaptación del brief (Ruling R3/R4 del controlador, 2026-10-06): el brief
 * literal esperaba `cabecera.ruta` y `cabecera.rol`, pero
 * `CabeceraDelMenu` (Task 1) no tiene esos campos — tiene `nombreDeZona`
 * (String?, sin respaldo "Zona N") y ningún campo de rol. Las pruebas de
 * cabecera comparan contra el modelo real.
 */
class CatalogoDelMenuTest {

    @Test
    fun `los cuatro modulos dan las diez opciones en el orden de hoy`() {
        val secciones = CatalogoDelMenu.secciones(
            listOf("COBRO", "VENTAS", "ALMACEN", "GARANTIAS"),
            emptyMap()
        )
        assertEquals(listOf("COBRO", "VENTAS", "ALMACEN", "GARANTIAS"), secciones.map { it.id })
        assertEquals(
            listOf(
                "Inicio", "Clientes", "Reporte de cobranza", "Mapa de rutas",
                "Ventas", "Mi Camioneta", "Catálogo de Productos",
                "Traspasos", "Asignación de Camionetas",
                "Garantías"
            ),
            secciones.flatMap { s -> s.items.map { it.etiqueta } }
        )
    }

    @Test
    fun `solo aparecen las secciones de los modulos del usuario`() {
        assertEquals(
            listOf("VENTAS"),
            CatalogoDelMenu.secciones(listOf("VENTAS"), emptyMap()).map {
                it.id
            }
        )
    }

    @Test
    fun `sin modulos no hay secciones`() {
        assertEquals(emptyList<Any>(), CatalogoDelMenu.secciones(emptyList(), emptyMap()))
    }

    @Test
    fun `los contadores se pegan a su opcion`() {
        val secciones = CatalogoDelMenu.secciones(
            listOf("COBRO"),
            mapOf(IdsDelMenu.CLIENTES to "8 por visitar")
        )
        assertEquals(
            "8 por visitar",
            secciones.single().items.single {
                it.id == IdsDelMenu.CLIENTES
            }.contador
        )
    }

    @Test
    fun `una ruta de nivel superior activa su opcion y su seccion`() {
        assertEquals(
            ActivoEnElMenu("COBRO", IdsDelMenu.CLIENTES),
            CatalogoDelMenu.activoPara(PagosRutas.LISTA_CLIENTES)
        )
        assertEquals(
            ActivoEnElMenu("ALMACEN", IdsDelMenu.TRASPASOS),
            CatalogoDelMenu.activoPara(Screen.TransfersList.route)
        )
    }

    @Test
    fun `una ruta hija abre su seccion sin resaltar opcion`() {
        assertEquals(
            ActivoEnElMenu("COBRO", null),
            CatalogoDelMenu.activoPara(PagosRutas.DETALLE_CLIENTE)
        )
        assertEquals(
            ActivoEnElMenu("VENTAS", null),
            CatalogoDelMenu.activoPara(Screen.NewSale.route)
        )
    }

    @Test
    fun `una ruta que no pertenece a ninguna seccion no activa nada`() {
        assertEquals(ActivoEnElMenu.NINGUNO, CatalogoDelMenu.activoPara(Screen.Configuracion.route))
        assertEquals(ActivoEnElMenu.NINGUNO, CatalogoDelMenu.activoPara(null))
    }

    @Test
    fun `sin usuario la cabecera queda con signo de interrogacion y sin zona`() {
        val cabecera = CatalogoDelMenu.cabecera(usuario = null, envio = null, nombreDeZona = null)
        assertEquals("?", cabecera.iniciales)
        assertEquals("", cabecera.nombre)
        assertNull(cabecera.nombreDeZona)
        assertNull(cabecera.envio)
    }

    @Test
    fun `con usuario la cabecera lleva iniciales, nombre y zona`() {
        // "R/25" y no "Zona 21": ese es justo el respaldo que R3 prohíbe que este
        // catálogo invente. El dato de prueba no debe parecerlo.
        val usuario = User(NOMBRE = "Efraín Domínguez Reyes", MODULOS = listOf("COBRO"))
        val cabecera = CatalogoDelMenu.cabecera(usuario, envio = null, nombreDeZona = "R/25")
        assertEquals("ED", cabecera.iniciales)
        assertEquals("Efraín Domínguez Reyes", cabecera.nombre)
        assertEquals("R/25", cabecera.nombreDeZona)
    }

    @Test
    fun `sin nombre de zona la cabecera no inventa un respaldo`() {
        val usuario = User(NOMBRE = "Efraín Domínguez Reyes", MODULOS = listOf("COBRO"))
        val cabecera = CatalogoDelMenu.cabecera(usuario, envio = null, nombreDeZona = null)
        assertNull(cabecera.nombreDeZona)
    }
}
