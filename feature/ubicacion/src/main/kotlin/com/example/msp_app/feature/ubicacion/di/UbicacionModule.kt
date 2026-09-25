package com.example.msp_app.feature.ubicacion.di

import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.feature.ubicacion.data.RoomPuntosAdapter
import com.example.msp_app.feature.ubicacion.domain.port.PuntosPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * El cableado del módulo de ubicación.
 *
 * Sólo un puerto. La pantalla no habla con el API ni con Firestore: todo lo que
 * necesita ya está en Room, y ése es el hallazgo que hizo barata esta pantalla.
 */
@Module
@InstallIn(SingletonComponent::class)
object UbicacionModule {

    @Provides
    @Singleton
    fun providePuntosPort(paymentDao: PaymentDao): PuntosPort = RoomPuntosAdapter(paymentDao)

    // **`AppClock` NO se provee aquí.** `CollectionReportDataModule` ya lo
    // publica en el grafo, y el repo tiene una regla dura de un solo reloj:
    // `AppTime`/`AppClock` existen precisamente para que no convivan dos
    // nociones de "ahora". Un `java.time.Clock` propio en este módulo habría
    // sido el segundo, y habría pasado desapercibido porque Hilt no se queja de
    // tipos distintos.
    //
    // El ViewModel lo recibe inyectado y no llama a `Instant.now()`: "últimos 3
    // meses" se prueba con un reloj falso, o la prueba caduca sola — y una
    // prueba que empieza a fallar en marzo es una que alguien termina borrando.
}
