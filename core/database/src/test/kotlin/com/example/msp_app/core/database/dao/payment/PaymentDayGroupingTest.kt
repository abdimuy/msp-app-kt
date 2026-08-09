package com.example.msp_app.core.database.dao.payment

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.testing.RobolectricTestBase
import java.util.TimeZone
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val CASH_FORMA_COBRO_ID = 157
private const val LOOKBACK_START_DATE = "2020-01-01"

/**
 * Characterizes the money-path fix to [PaymentDao.getPaymentsGroupedByDaySince] /
 * [PaymentDao.observePaymentsGroupedByDaySince]: the day-grouping key now uses the
 * BUSINESS zone (`America/Mexico_City`, via `dayKeyOf`/`AppTime.toBusinessDate` in
 * `PaymentDao.kt`) instead of the device's timezone (the bug the deleted
 * `PaymentDateGrouping.kt` copy shared with the legacy date util's `formatIsoDate`,
 * see `date-lib-audit.md` bugs #1/#7).
 *
 * The first test method below is the pinned old-behavior-vs-new-behavior case
 * required by the Task 3 dispatch: it fails if grouping ever reverts to
 * device-timezone.
 */
class PaymentDayGroupingTest : RobolectricTestBase() {

    private lateinit var database: AppDatabase
    private val originalDefaultTimeZone: TimeZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
        TimeZone.setDefault(originalDefaultTimeZone)
    }

    private fun payment(id: String, nombreCliente: String, fechaHoraPago: String) = PaymentEntity(
        ID = id,
        COBRADOR = "Efrain Dominguez Reyes",
        DOCTO_CC_ACR_ID = 48213,
        DOCTO_CC_ID = 91027,
        FECHA_HORA_PAGO = fechaHoraPago,
        GUARDADO_EN_MICROSIP = true,
        IMPORTE = 350.0,
        LAT = 19.043415,
        LNG = -98.198234,
        CLIENTE_ID = 30144,
        COBRADOR_ID = 7,
        FORMA_COBRO_ID = CASH_FORMA_COBRO_ID,
        ZONA_CLIENTE_ID = 21,
        NOMBRE_CLIENTE = nombreCliente
    )

    /**
     * THE characterization test demanded by the Task 3 dispatch: a payment at
     * `2026-03-15T05:30:00Z` is `2026-03-14 23:30` in CDMX (business zone,
     * UTC-6 year-round since Mexico dropped national DST in 2022).
     *
     * NEW (fixed) behavior, asserted below: groups under **2026-03-14** (the
     * business-zone-correct day).
     *
     * OLD (buggy) behavior, documented here and NOT reproduced (the buggy code
     * path no longer exists — that is the point of this test): the deleted
     * legacy day-grouping helper (mirroring the legacy date util's `formatIsoDate`) converted to
     * `ZoneId.systemDefault()` instead of the business zone. On a device set to
     * a UTC-ish zone (e.g. UTC or UTC+1, plausible for a misconfigured phone or
     * one that roamed abroad), `2026-03-15T05:30:00Z` stays on **2026-03-15**
     * local — one day later than the business-correct grouping. This test
     * fails if the grouping key is ever recomputed from `ZoneId.systemDefault()`
     * again: forcing the device default to `UTC` below and still asserting the
     * `2026-03-14` bucket is exactly what pins that regression.
     */
    @Test
    fun `pago a las 05_30 UTC del 15-marzo agrupa al 14-marzo, dia de negocio, no al 15 de un dispositivo UTC`() =
        runTest {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

            database.paymentDao().savePayment(
                payment("pago-005", "Rosa Elena Martinez Vazquez", "2026-03-15T05:30:00Z")
            )

            val grouped = database.paymentDao().getPaymentsGroupedByDaySince(LOOKBACK_START_DATE)

            assertTrue(
                "el pago debe agruparse al dia de NEGOCIO 2026-03-14 (23:30 CDMX), " +
                    "no al 2026-03-15 que produciria la zona del dispositivo (UTC) bajo el bug viejo",
                grouped.containsKey("2026-03-14")
            )
            assertTrue(
                "NO debe existir un grupo 2026-03-15 (eso seria el comportamiento viejo, " +
                    "zona del dispositivo, reintroducido)",
                !grouped.containsKey("2026-03-15")
            )
            assertEquals(1, grouped["2026-03-14"]?.size)
        }

    @Test
    fun `dos pagos cerca de medianoche CDMX caen en dias de negocio distintos, no el mismo dia UTC`() =
        runTest {
            // 2026-04-16T04:30:00Z = 2026-04-15 22:30 CDMX
            database.paymentDao().savePayment(
                payment("pago-001", "Maria Lopez Hernandez", "2026-04-16T04:30:00Z")
            )
            // 2026-04-16T07:00:00Z = 2026-04-16 01:00 CDMX
            database.paymentDao().savePayment(
                payment("pago-002", "Guadalupe Ramirez Torres", "2026-04-16T07:00:00Z")
            )

            val grouped = database.paymentDao().getPaymentsGroupedByDaySince(LOOKBACK_START_DATE)

            assertEquals(setOf("pago-001"), grouped["2026-04-15"]?.map { it.ID }?.toSet())
            assertEquals(setOf("pago-002"), grouped["2026-04-16"]?.map { it.ID }?.toSet())
        }

    @Test
    fun `limite exacto de medianoche CDMX separa los grupos`() = runTest {
        // 23:59:59 CDMX del 15-abril
        database.paymentDao().savePayment(
            payment("pago-003", "Juana Perez Gonzalez", "2026-04-16T05:59:59Z")
        )
        // 00:00:00 CDMX del 16-abril
        database.paymentDao().savePayment(
            payment("pago-004", "Alicia Fernandez Ruiz", "2026-04-16T06:00:00Z")
        )

        val grouped = database.paymentDao().getPaymentsGroupedByDaySince(LOOKBACK_START_DATE)

        assertEquals(setOf("pago-003"), grouped["2026-04-15"]?.map { it.ID }?.toSet())
        assertEquals(setOf("pago-004"), grouped["2026-04-16"]?.map { it.ID }?.toSet())
    }

    @Test
    fun `agrupamiento es independiente de la zona del dispositivo`() = runTest {
        database.paymentDao().savePayment(
            payment("pago-006", "Sofia Castillo Mendoza", "2026-04-16T04:30:00Z")
        )
        database.paymentDao().savePayment(
            payment("pago-007", "Ricardo Ochoa Delgado", "2026-04-16T07:00:00Z")
        )

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val groupedUtc = database.paymentDao().getPaymentsGroupedByDaySince(LOOKBACK_START_DATE)

        TimeZone.setDefault(TimeZone.getTimeZone("America/Tijuana"))
        val groupedTijuana = database.paymentDao().getPaymentsGroupedByDaySince(LOOKBACK_START_DATE)

        val expected = mapOf(
            "2026-04-15" to setOf("pago-006"),
            "2026-04-16" to setOf("pago-007")
        )
        assertEquals(expected, groupedUtc.mapValues { (_, list) -> list.map { it.ID }.toSet() })
        assertEquals(expected, groupedTijuana.mapValues { (_, list) -> list.map { it.ID }.toSet() })
    }

    @Test
    fun `fila legacy sin Z agrupa sin lanzar`() = runTest {
        // Rama legacy de AppTime.parseWireFormat: sin offset, se interpreta
        // directo como hora de negocio (CDMX) — no lanza.
        database.paymentDao().savePayment(
            payment("pago-008", "Teresa Aguilar Solis", "2026-04-16T10:00:00")
        )

        val grouped = database.paymentDao().getPaymentsGroupedByDaySince(LOOKBACK_START_DATE)

        assertNotNull(grouped["2026-04-16"])
        assertEquals(setOf("pago-008"), grouped["2026-04-16"]?.map { it.ID }?.toSet())
    }
}
