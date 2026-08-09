package com.example.msp_app.features.transfers.presentation.components

import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 12 (fechas/AppTime migration, bug #9) — covers the `FECHA:` line of
 * [generateWarehouseInventoryTicket] (thermal-printer inventory ticket), which previously used
 * `SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())` inline. Made
 * `internal` (from `private`) specifically so this pure function is reachable from `:app`'s
 * test source set.
 */
class WarehouseInventoryTicketTest {

    @Test
    fun `FECHA line uses the injected clock formatted in business zone`() {
        val fixed = Instant.parse("2026-08-08T14:30:00Z") // 08:30 CDMX
        val clock = FakeClock(fixed)

        val ticket = generateWarehouseInventoryTicket(
            warehouseName = "Camioneta 3",
            totalStock = 12,
            assignedUsers = emptyList(),
            products = emptyList(),
            clock = clock
        )

        assertTrue(ticket.contains("FECHA: 08/08/2026 08:30"))
    }

    @Test
    fun `FECHA line is independent of the device default Locale`() {
        val original = Locale.getDefault()
        try {
            val clock = FakeClock(Instant.parse("2026-08-08T14:30:00Z"))

            Locale.setDefault(Locale.US)
            val underUs = generateWarehouseInventoryTicket(
                warehouseName = "Camioneta 3",
                totalStock = 12,
                assignedUsers = emptyList(),
                products = emptyList(),
                clock = clock
            )

            Locale.setDefault(Locale("ar"))
            val underAr = generateWarehouseInventoryTicket(
                warehouseName = "Camioneta 3",
                totalStock = 12,
                assignedUsers = emptyList(),
                products = emptyList(),
                clock = clock
            )

            assertTrue(underUs.contains("FECHA: 08/08/2026 08:30"))
            assertTrue(underAr.contains("FECHA: 08/08/2026 08:30"))
        } finally {
            Locale.setDefault(original)
        }
    }
}
