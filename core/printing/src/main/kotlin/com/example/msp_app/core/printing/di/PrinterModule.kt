package com.example.msp_app.core.printing.di

import com.example.msp_app.core.printing.adapters.DantSuPrinterGateway
import com.example.msp_app.core.printing.adapters.PreferredPrinterRepository
import com.example.msp_app.core.printing.domain.PreferredPrinterStore
import com.example.msp_app.core.printing.domain.PrinterPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the hexagonal [PrinterPort] to its one Android implementation, the
 * [DantSuPrinterGateway]. The application/ViewModel layers depend only on the
 * port; swapping the adapter (or faking it in tests) never touches them.
 * [BluetoothPrinterDiscovery][com.example.msp_app.core.printing.adapters.BluetoothPrinterDiscovery]
 * is a plain `@Inject` constructor type, so no explicit provider is needed for it.
 * [PreferredPrinterRepository] is bound to its [PreferredPrinterStore] port so the
 * T4 ViewModel depends only on the abstraction (and fakes it in unit tests).
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class PrinterModule {
    @Binds
    @Singleton
    abstract fun bindPrinterPort(impl: DantSuPrinterGateway): PrinterPort

    @Binds
    @Singleton
    abstract fun bindPreferredPrinterStore(impl: PreferredPrinterRepository): PreferredPrinterStore
}
