package com.example.msp_app.di

import android.content.Context
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.feature.garantias.data.http.V2GarantiasApi
import com.example.msp_app.feature.garantias.domain.port.EncoladoDeGarantiasPort
import com.example.msp_app.workmanager.enqueuePendingGarantiasV2Worker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/**
 * Cablea lo que la captura de garantías necesita de `:app`.
 *
 * ## Qué se cablea acá y qué no
 *
 * Casi todos los adaptadores viven en `:feature:garantias` y se unen a sus
 * puertos en `GarantiasBindingsModule`. Acá quedan sólo las dos piezas que
 * dependen del composition root:
 *
 * 1. **El servicio Retrofit**, construido con [V2ApiProvider] para heredar la
 *    baseURL v2 del flavor y el token de la sesión. La interfaz vive en el
 *    módulo; acá sólo se le da el `Retrofit` ya armado.
 * 2. **El encolado real** de WorkManager, porque el módulo no depende de `:app`
 *    y el puerto [EncoladoDeGarantiasPort] es su única salida.
 *
 * Ninguno lleva scope, como el resto de los servicios de red
 * (`NetworkKillSwitchGuardTest`): la baseURL v2 es estática por flavor, pero el
 * token se resuelve por request y el adaptador no guarda estado.
 */
@Module
@InstallIn(SingletonComponent::class)
object GarantiasPortsModule {

    @Provides
    fun provideV2GarantiasApi(): V2GarantiasApi = V2ApiProvider.create(V2GarantiasApi::class.java)

    @Provides
    fun provideEncoladoDeGarantias(@ApplicationContext context: Context): EncoladoDeGarantiasPort =
        EncoladoDeGarantiasAdapter(context)
}

/**
 * La implementación de [EncoladoDeGarantiasPort] sobre WorkManager.
 *
 * Vive en este archivo porque es una sola línea de traducción y no tiene
 * lógica propia: cualquier prueba de la cola usa un fake del puerto y nunca
 * toca WorkManager.
 */
private class EncoladoDeGarantiasAdapter(
    private val context: Context
) : EncoladoDeGarantiasPort {
    override fun encolar() = enqueuePendingGarantiasV2Worker(context)
}
