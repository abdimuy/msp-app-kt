package com.example.msp_app.features.productsInventory.compartir

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.common.ventas.precios.TipoDePrecio
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.utils.preciosClasificadosParaMostrar
import com.example.msp_app.core.utils.preciosParaMostrar
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.data.models.productInventory.PrecioOrdenado
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.feature.compartirproductos.android.AlmacenDeTemporales
import com.example.msp_app.feature.compartirproductos.android.IntentDeCompartir
import com.example.msp_app.feature.compartirproductos.domain.FormatoDePesos
import com.example.msp_app.feature.compartirproductos.domain.PreciosDeTarjeta
import com.example.msp_app.feature.compartirproductos.domain.SeleccionDePrecios
import java.io.File
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cableado de «Compartir» en `:app` (E-APP-180): de qué producto sale qué tarjeta, que los precios
 * sean los MISMOS que pinta el catálogo, y que el `FileProvider` real de la app sirva la carpeta
 * `cacheDir/compartir/`.
 */
class CompartirProductosCableadoTest : RobolectricTestBase() {

    private fun producto(
        nombre: String = "SALA VALENCIA",
        linea: String = "SALAS",
        precios: String? = "Precio 1 Meses:8990,Precio 4 Meses:10450,Precio de lista:13200",
        ordenados: List<PrecioOrdenado>? = null
    ) = ProductInventory(7, nombre, 3, 1, linea, precios, ordenados)

    // region — los precios son los de la app

    @Test fun `por nombre los tipos salen de ClasificadorDePrecios y el orden es contado corto lista`() {
        val p = producto(precios = "Precio de lista:13200,Precio 1 Meses:8990,Precio 4 Meses:10450")
        val r = preciosClasificadosParaMostrar(p)
        assertEquals(
            listOf(
                TipoDePrecio.CONTADO,
                TipoDePrecio.CORTO_PLAZO,
                TipoDePrecio.LARGO_PLAZO
            ),
            r.map {
                it.tipo
            }
        )
        assertEquals(listOf(8990.0, 10450.0, 13200.0), r.map { it.valor })
    }

    @Test fun `con precios_ordenados del servidor manda el tipo del servidor`() {
        val p = producto(
            precios = null,
            ordenados = listOf(
                PrecioOrdenado("largo_plazo", 3, "Precio de lista", 13200.0),
                PrecioOrdenado("contado", 1, "Precio 1 Meses", 8990.0),
                PrecioOrdenado("corto_plazo", 2, "Precio 4 Meses", 10450.0),
                // sin nombre: se descarta como en el catálogo
                PrecioOrdenado("contado", 9, "", 5.0),
                // sin precio: se descarta
                PrecioOrdenado("contado", 9, "Otro", null)
            )
        )
        val r = preciosClasificadosParaMostrar(p)
        assertEquals(
            listOf(
                TipoDePrecio.CONTADO,
                TipoDePrecio.CORTO_PLAZO,
                TipoDePrecio.LARGO_PLAZO
            ),
            r.map {
                it.tipo
            }
        )
    }

    @Test fun `la lista clasificada es identica a la que pinta el catalogo en todos los casos`() {
        val casos = listOf(
            producto(),
            producto(precios = "Precio de lista:100"),
            producto(precios = null),
            producto(precios = ""),
            producto(precios = "Precio de Contado:50,Precio de lista:90"),
            producto(precios = "Desconocido:5,Precio 1 Meses:1"),
            producto(
                precios = null,
                ordenados = listOf(PrecioOrdenado("contado", 1, "Precio 1 Meses", 10.0))
            )
        )
        casos.forEach { p ->
            assertEquals(
                preciosParaMostrar(p),
                preciosClasificadosParaMostrar(p).map { it.nombre to it.valor }
            )
        }
    }

    @Test fun `la tarjeta enseña exactamente los montos del catalogo con su mismo formato de pesos`() {
        val montos =
            listOf(1.0, 99.0, 100.0, 1234.0, 8990.0, 10450.0, 13200.0, 124900.0, 999999.0, 1250000.0, 99999999.0, 8990.49, 8990.5, 8990.51, 0.5)
        montos.forEach { m ->
            assertEquals(
                "$m",
                m.toCurrency(noDecimals = true),
                FormatoDePesos.formatear(BigDecimal.valueOf(m))
            )
        }
        val articulo = producto().paraCompartir(null)
        val enTarjeta = PreciosDeTarjeta.armar(
            articulo.precios,
            SeleccionDePrecios()
        ).map { it.texto }
        val enCatalogo = preciosParaMostrar(
            producto()
        ).map { it.second.toCurrency(noDecimals = true) }
        assertEquals(enCatalogo, enTarjeta)
    }
    // endregion

    // region — mapeo del producto

    @Test fun `mapea id nombre completo categoria y foto`() {
        val a = producto(
            nombre = "SALA VALENCIA 3 PZAS C/CAJ.",
            linea = "SALAS"
        ).paraCompartir("/data/foto.jpg")
        assertEquals(7, a.id)
        assertEquals("SALA VALENCIA 3 PZAS C/CAJ.", a.nombre)
        assertEquals("SALAS", a.categoria)
        assertEquals("/data/foto.jpg", a.fotoPath)
    }

    @Test fun `sin categoria o con la linea en blanco no se inventa una`() {
        assertNull(producto(linea = "").paraCompartir(null).categoria)
        assertNull(producto(linea = "   ").paraCompartir(null).categoria)
    }

    @Test fun `un producto cuyo nombre o linea llegaron null de Gson no truena`() {
        val roto: ProductInventory = com.google.gson.Gson().fromJson(
            """{"ARTICULO_ID":5,"EXISTENCIAS":1,"LINEA_ARTICULO_ID":1}""",
            ProductInventory::class.java
        )
        val a = roto.paraCompartir(null)
        assertEquals("", a.nombre)
        assertNull(a.categoria)
        assertTrue(a.precios.isEmpty())
    }

    @Test fun `un precio en cero o negativo pasa al modulo y alli no se muestra`() {
        // Por la cadena vieja un «-5» llega como 5 (parsePrice quita todo lo que no es cifra o punto);
        // el negativo solo puede venir de precios_ordenados del servidor.
        val a = producto(
            precios = null,
            ordenados = listOf(
                PrecioOrdenado("contado", 1, "Precio 1 Meses", 0.0),
                PrecioOrdenado("corto_plazo", 2, "Precio 4 Meses", -5.0),
                PrecioOrdenado("largo_plazo", 3, "Precio de lista", 13200.0)
            )
        ).paraCompartir(null)
        assertEquals(3, a.precios.size)
        assertEquals(
            listOf(
                "Crédito"
            ),
            PreciosDeTarjeta.armar(a.precios, SeleccionDePrecios()).map {
                it.fila.etiqueta
            }
        )
    }
    // endregion

    // region — FileProvider real de la app

    /**
     * `FileProvider` guarda en una variable ESTÁTICA (`sCache`) la raíz de cada autoridad. En la suite
     * completa otra prueba ya la llenó con SU `cacheDir` temporal (cada prueba de Robolectric tiene el suyo)
     * y ésta fallaba con «Failed to find configured root» aunque sola pasaba. Se vacía antes de pedir el URI.
     */
    private fun reiniciarCacheDelFileProvider() {
        val campo = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
        campo.isAccessible = true
        (campo.get(null) as MutableMap<*, *>).clear()
    }

    @Test fun `el FileProvider de la app sirve cacheDir barra compartir`() {
        reiniciarCacheDelFileProvider()
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        val archivo = AlmacenDeTemporales(contexto.cacheDir).archivoPara(7, 0).also {
            it.writeBytes(byteArrayOf(1))
        }
        val uri = IntentDeCompartir.uriDe(contexto, archivo)
        assertEquals("content", uri.scheme)
        assertEquals("${contexto.packageName}.fileprovider", uri.authority)
        assertEquals("/cache/compartir/${archivo.name}", uri.path)
        archivo.delete()
        File(contexto.cacheDir, "compartir").delete()
    }
    // endregion
}
