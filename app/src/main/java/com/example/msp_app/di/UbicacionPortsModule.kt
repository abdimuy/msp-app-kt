package com.example.msp_app.di

import com.example.msp_app.feature.pagos.domain.port.AccionesExternasPort
import com.example.msp_app.feature.pagos.domain.port.DestinoEnElMapa
import com.example.msp_app.feature.ubicacion.domain.port.AbrirEnMapasPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Ata el puerto de mapas de `:feature:ubicacion` al **mismo** adaptador que ya
 * usa `:feature:pagos`.
 *
 * ## Por qué esto vive en `:app` y no en ninguno de los dos features
 *
 * Porque es exactamente el trabajo de la raíz de composición: **un feature no
 * puede depender de otro**, y `:app` es el único que tiene los dos en su
 * classpath.
 *
 * Lo que este puente compra es que la decisión de fondo siga en pie: *el `geo:`
 * lo arma un solo lugar*, `IntentAccionesExternasAdapter`. Dos puertos —cada
 * feature con el suyo, sin conocerse— y **un solo constructor de intents**. Si
 * mañana cambia cómo se abre la app de mapas, cambia en un archivo y las dos
 * pantallas lo heredan.
 */
@Module
@InstallIn(SingletonComponent::class)
object UbicacionPortsModule {

    @Provides
    @Singleton
    fun provideAbrirEnMapasPort(acciones: AccionesExternasPort): AbrirEnMapasPort =
        object : AbrirEnMapasPort {
            override suspend fun comoLlegar(
                lat: Double,
                lon: Double,
                etiqueta: String
            ): Result<Unit> = acciones.comoLlegar(
                DestinoEnElMapa(
                    etiqueta = etiqueta,
                    direccion = etiqueta,
                    lat = lat,
                    lng = lon
                )
            )
        }
}
