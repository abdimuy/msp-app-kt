package com.example.msp_app.core.common.time

import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FechaDelCatalogoTest {
    private var almacen = 0L
    private val reloj = FakeClock(Instant.parse("2026-11-10T15:00:00Z"))
    private val fecha = FechaDelCatalogo({ almacen }, { almacen = it }, reloj)

    @Test fun `sin descarga registrada no hay fecha`() = assertNull(fecha.ultima())

    @Test fun `registrar guarda la hora del reloj`() {
        fecha.registrar()
        assertEquals(Instant.parse("2026-11-10T15:00:00Z"), fecha.ultima())
    }

    @Test fun `un registro nuevo reemplaza al anterior`() {
        fecha.registrar()
        reloj.setNow(Instant.parse("2026-11-12T08:00:00Z"))
        fecha.registrar()
        assertEquals(Instant.parse("2026-11-12T08:00:00Z"), fecha.ultima())
    }
}
