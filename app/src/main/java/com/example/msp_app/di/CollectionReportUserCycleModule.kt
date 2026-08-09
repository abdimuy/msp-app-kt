package com.example.msp_app.di

import com.example.msp_app.data.collectionreport.FirebaseUserCycleAdapter
import com.example.msp_app.feature.collectionreport.domain.port.UserCyclePort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Cablea el puerto [UserCyclePort] del reporte de cobranza a su implementación real
 * [FirebaseUserCycleAdapter] (Plan 5, Task 10). Vive en `:app` —no en
 * `:feature:collectionReport`— porque su fuente (`userData` de Firestore) pertenece a la capa
 * de aplicación, igual que [com.example.msp_app.di.NetworkConfigModule] provee la impl Firebase
 * de `AuthTokenProvider` (los otros puertos del reporte —Payments/Visits/HistoricalTotals— sí se
 * cablean dentro del feature, en `CollectionReportDataModule`, porque leen Room).
 *
 * SIN `@Singleton` a propósito (kill-switch de sesión, ver KDoc del puerto y del adapter): la
 * impl consulta Firestore por el usuario autenticado vigente en cada lectura.
 */
@Module
@InstallIn(SingletonComponent::class)
object CollectionReportUserCycleModule {

    @Provides
    fun provideUserCyclePort(): UserCyclePort = FirebaseUserCycleAdapter()
}
