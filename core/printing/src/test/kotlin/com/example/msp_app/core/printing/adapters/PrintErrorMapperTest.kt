package com.example.msp_app.core.printing.adapters

import com.dantsu.escposprinter.exceptions.EscPosBarcodeException
import com.dantsu.escposprinter.exceptions.EscPosConnectionException
import com.dantsu.escposprinter.exceptions.EscPosEncodingException
import com.dantsu.escposprinter.exceptions.EscPosParserException
import com.example.msp_app.core.printing.domain.PrintError
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure, hardware-free coverage of the exception→[PrintError] seam. Feeds the
 * representative throwables the Bluetooth/DantSu stack raises (plus the gateway's
 * own precondition [PrintError]s) and asserts the typed classification.
 */
class PrintErrorMapperTest {
    @Test
    fun `precondition PrintErrors pass through unchanged`() {
        listOf(
            PrintError.BluetoothDisabled,
            PrintError.NotPaired,
            PrintError.PermissionDenied
        ).forEach { error ->
            assertSame(error, PrintErrorMapper.map(error))
        }
    }

    @Test
    fun `SecurityException maps to PermissionDenied`() {
        assertEquals(
            PrintError.PermissionDenied,
            PrintErrorMapper.map(SecurityException("no BLUETOOTH_CONNECT"))
        )
    }

    @Test
    fun `EscPos connection failure maps to ConnectionFailed with cause`() {
        val cause = EscPosConnectionException("unable to connect")

        val mapped = PrintErrorMapper.map(cause)

        assertTrue(mapped is PrintError.ConnectionFailed)
        assertSame(cause, (mapped as PrintError.ConnectionFailed).cause)
    }

    @Test
    fun `IOException maps to ConnectionFailed`() {
        assertTrue(
            PrintErrorMapper.map(IOException("socket dropped")) is PrintError.ConnectionFailed
        )
    }

    @Test
    fun `parser, encoding and barcode failures map to WriteFailed`() {
        listOf(
            EscPosParserException("bad markup"),
            EscPosEncodingException("bad charset"),
            EscPosBarcodeException("bad barcode")
        ).forEach { cause ->
            val mapped = PrintErrorMapper.map(cause)
            assertTrue("$cause should map to WriteFailed", mapped is PrintError.WriteFailed)
            assertSame(cause, (mapped as PrintError.WriteFailed).cause)
        }
    }

    @Test
    fun `unrecognised throwable maps to Unknown preserving cause`() {
        val cause = IllegalStateException("boom")

        val mapped = PrintErrorMapper.map(cause)

        assertTrue(mapped is PrintError.Unknown)
        assertSame(cause, (mapped as PrintError.Unknown).cause)
    }
}
