package com.example.msp_app.core.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * Test unitario para ImageCompressor
 *
 * Estos tests validan la lógica de decisión de compresión sin necesidad
 * de imágenes reales o contexto de Android.
 */
class ImageCompressorTest {

    /**
     * Test: Imagen muy grande debe aplicar compresión agresiva
     */
    @Test
    fun `imagen muy grande aplica compresion agresiva`() {
        // Arrange: 10MB, 4000x3000 (12MP)
        val fileSize = 10L * 1024 * 1024
        val width = 4000
        val height = 3000

        // Act
        val config = ImageCompressor.determineCompressionConfig(fileSize, width, height)

        // Assert
        assertTrue("Debe redimensionar", config.shouldResize)
        assertTrue("Dimensión máxima debe ser 2560 o menos", config.maxDimension <= 2560)
        assertTrue("Calidad debe ser 85 o menos", config.quality <= 85)
        assertTrue("Razón debe mencionar tamaño", config.reason.contains("grande", ignoreCase = true))
    }

    /**
     * Test: Imagen mediana debe aplicar compresión moderada
     */
    @Test
    fun `imagen mediana aplica compresion moderada`() {
        // Arrange: 3MB, 2400x1800 (4.3MP)
        val fileSize = 3L * 1024 * 1024
        val width = 2400
        val height = 1800

        // Act
        val config = ImageCompressor.determineCompressionConfig(fileSize, width, height)

        // Assert
        assertTrue("Debe redimensionar", config.shouldResize)
        assertEquals("Dimensión máxima debe ser 1920", 1920, config.maxDimension)
        assertTrue("Calidad debe ser entre 65 y 85", config.quality in 65..85)
    }

    /**
     * Test: Imagen pequeña optimizada no debe redimensionarse
     */
    @Test
    fun `imagen pequena optimizada no se redimensiona`() {
        // Arrange: 300KB, 1200x800 (0.96MP)
        val fileSize = 300L * 1024
        val width = 1200
        val height = 800

        // Act
        val config = ImageCompressor.determineCompressionConfig(fileSize, width, height)

        // Assert
        assertFalse("No debe redimensionar", config.shouldResize)
        assertEquals("Calidad debe ser alta (85)", 85, config.quality)
        assertTrue("Razón debe mencionar optimizada", config.reason.contains("optimizada", ignoreCase = true))
    }

    /**
     * Test: Alta resolución pero poco peso debe reducir resolución
     */
    @Test
    fun `alta resolucion con poco peso reduce resolucion`() {
        // Arrange: 800KB, 3000x2000 (6MP) - foto bien comprimida
        val fileSize = 800L * 1024
        val width = 3000
        val height = 2000

        // Act
        val config = ImageCompressor.determineCompressionConfig(fileSize, width, height)

        // Assert
        assertTrue("Debe redimensionar por megapixels", config.shouldResize)
        assertTrue("Calidad debe ser alta para preservar detalles", config.quality >= 75)
    }

    /**
     * Test: Factor de muestreo se calcula correctamente
     */
    @Test
    fun `calculo de factor de muestreo es correcto`() {
        // Arrange
        val width = 4000
        val height = 3000
        val maxDimension = 1920

        // Act
        val inSampleSize = ImageCompressor.calculateInSampleSize(width, height, maxDimension)

        // Assert
        assertTrue("inSampleSize debe ser potencia de 2", isPowerOfTwo(inSampleSize))
        assertTrue("inSampleSize debe ser al menos 2", inSampleSize >= 2)

        // Verificar que el resultado redimensionado es cercano al máximo
        val resultWidth = width / inSampleSize
        val resultHeight = height / inSampleSize
        assertTrue("Resultado debe estar cerca del máximo",
            resultWidth <= maxDimension * 2 || resultHeight <= maxDimension * 2)
    }

    /**
     * Test: Dimensiones pequeñas no requieren muestreo
     */
    @Test
    fun `dimensiones pequenas no requieren muestreo`() {
        // Arrange
        val width = 800
        val height = 600
        val maxDimension = 1920

        // Act
        val inSampleSize = ImageCompressor.calculateInSampleSize(width, height, maxDimension)

        // Assert
        assertEquals("No debe aplicar muestreo para imágenes pequeñas", 1, inSampleSize)
    }

    /**
     * Test: Formato de tamaño de archivo es legible
     */
    @Test
    fun `formato de tamaño de archivo es correcto`() {
        // Bytes
        assertEquals("500 B", ImageCompressor.formatFileSize(500))

        // Kilobytes
        assertEquals("100 KB", ImageCompressor.formatFileSize(100 * 1024))

        // Megabytes
        val result = ImageCompressor.formatFileSize(5L * 1024 * 1024)
        assertTrue("Debe contener MB", result.contains("MB"))
        assertTrue("Debe mostrar 5.00 MB", result.startsWith("5."))
    }

    /**
     * Test: Configuración para imagen extremadamente grande
     */
    @Test
    fun `imagen extremadamente grande tiene limites apropiados`() {
        // Arrange: 50MB, 8000x6000 (48MP) - foto de cámara profesional
        val fileSize = 50L * 1024 * 1024
        val width = 8000
        val height = 6000

        // Act
        val config = ImageCompressor.determineCompressionConfig(fileSize, width, height)

        // Assert
        assertTrue("Debe redimensionar agresivamente", config.shouldResize)
        assertTrue("Dimensión máxima debe ser 2560 o menos", config.maxDimension <= 2560)

        // Calcular reducción esperada
        val originalMegapixels = (width * height) / 1_000_000f
        val finalMegapixels = (config.maxDimension * config.maxDimension) / 1_000_000f
        val reduction = (originalMegapixels - finalMegapixels) / originalMegapixels

        assertTrue("Reducción debe ser significativa (>70%)", reduction > 0.7f)
    }

    /**
     * Test: Imagen cuadrada se maneja correctamente
     */
    @Test
    fun `imagen cuadrada se comprime correctamente`() {
        // Arrange: Imagen cuadrada 3000x3000
        val fileSize = 5L * 1024 * 1024
        val width = 3000
        val height = 3000

        // Act
        val config = ImageCompressor.determineCompressionConfig(fileSize, width, height)

        // Assert
        assertTrue("Debe redimensionar", config.shouldResize)
        assertTrue("Dimensión máxima debe preservar aspecto cuadrado",
            config.maxDimension > 0)
    }

    /**
     * Test: Imagen panorámica extrema
     */
    @Test
    fun `imagen panoramica extrema se maneja correctamente`() {
        // Arrange: Imagen muy ancha 4000x500
        val fileSize = 2L * 1024 * 1024
        val width = 4000
        val height = 500

        // Act
        val config = ImageCompressor.determineCompressionConfig(fileSize, width, height)

        // Assert
        assertTrue("Debe redimensionar por dimensión grande", config.shouldResize)
        assertTrue("Dimensión máxima debe ser razonable", config.maxDimension <= 2560)
    }

    // Helper function
    private fun isPowerOfTwo(n: Int): Boolean {
        return n > 0 && (n and (n - 1)) == 0
    }
}
