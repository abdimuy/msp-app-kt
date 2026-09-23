package com.example.msp_app.core.utils

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * **El caso real del cobrador que dijo que no.**
 *
 * Sin permiso de ubicación, [CurrentLocationReader.current] tiene que contestar
 * `null` —no lanzar, no colgarse— porque quien lo llama es la pantalla
 * principal: una `SecurityException` ahí tumbaría el arranque del turno, y una
 * espera sin fin dejaría a la pantalla cargando para siempre. La otra mitad del
 * caso (qué hace la pantalla con ese `null`) está en
 * `HomeNearbyClientsSectionTest`.
 *
 * Lo que **no** se prueba acá es el camino con permiso concedido: ahí la
 * respuesta la da Play Services, que en Robolectric no existe. Fingirlo
 * probaría el fake, no el código. Ese camino termina igual —`null`— y su
 * consecuencia está medida en la sección.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CurrentLocationReaderTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `sin permiso contesta null en vez de lanzar`() = runTest {
        Shadows.shadowOf(context).denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        assertNull(CurrentLocationReader(context).current())
    }

    /**
     * Control positivo del test de arriba: el fixture **puede** distinguir los
     * dos estados del permiso. Sin esto, un `denyPermissions` que no hiciera
     * nada —o un Robolectric que concediera todo por defecto— dejaría pasar el
     * `null` por el motivo equivocado.
     */
    @Test
    fun `el fixture del permiso si distingue conceder de negar`() {
        Shadows.shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertEquals(PackageManager.PERMISSION_DENIED, permisoFino())

        Shadows.shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertEquals(PackageManager.PERMISSION_GRANTED, permisoFino())
    }

    private fun permisoFino(): Int =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
}
