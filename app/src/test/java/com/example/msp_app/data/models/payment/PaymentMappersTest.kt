package com.example.msp_app.data.models.payment

import com.example.msp_app.data.local.entities.PaymentEntity
import org.junit.Test
import org.junit.Assert.*

class PaymentMappersTest {

    private val samplePaymentApi = PaymentApi(
        ID = "TEST_ID_001",
        COBRADOR = "Juan Cobrador",
        DOCTO_CC_ACR_ID = 12345,
        DOCTO_CC_ID = 67890,
        FECHA_HORA_PAGO = "2024-01-15 10:30:00",
        GUARDADO_EN_MICROSIP = true,
        IMPORTE = 1500.50,
        LAT = "19.432608",
        LNG = "-99.133209",
        CLIENTE_ID = 101,
        COBRADOR_ID = 201,
        FORMA_COBRO_ID = 1,
        ZONA_CLIENTE_ID = 301,
        NOMBRE_CLIENTE = "Maria González"
    )

    private val samplePayment = Payment(
        ID = "TEST_ID_002",
        COBRADOR = "Pedro Cobrador",  
        DOCTO_CC_ACR_ID = 54321,
        DOCTO_CC_ID = 98760,
        FECHA_HORA_PAGO = "2024-02-15 14:45:00",
        GUARDADO_EN_MICROSIP = false,
        IMPORTE = 2500.75,
        LAT = 20.659699,
        LNG = -103.349609,
        CLIENTE_ID = 102,
        COBRADOR_ID = 202,
        FORMA_COBRO_ID = 2,
        ZONA_CLIENTE_ID = 302,
        NOMBRE_CLIENTE = "Carlos López"
    )

    private val samplePaymentEntity = PaymentEntity(
        ID = "TEST_ID_003",
        COBRADOR = "Ana Cobradora",
        DOCTO_CC_ACR_ID = 11111,
        DOCTO_CC_ID = 22222,
        FECHA_HORA_PAGO = "2024-03-15 16:20:00",
        GUARDADO_EN_MICROSIP = true,
        IMPORTE = 3000.0,
        LAT = 25.686614,
        LNG = -100.316113,
        CLIENTE_ID = 103,
        COBRADOR_ID = 203,
        FORMA_COBRO_ID = 3, 
        ZONA_CLIENTE_ID = 303,
        NOMBRE_CLIENTE = "Luis Martínez"
    )

    @Test
    fun `PaymentApi toEntity should map correctly with valid coordinates`() {
        val entity = samplePaymentApi.toEntity()
        
        assertEquals(samplePaymentApi.ID, entity.ID)
        assertEquals(samplePaymentApi.COBRADOR, entity.COBRADOR)
        assertEquals(samplePaymentApi.DOCTO_CC_ACR_ID, entity.DOCTO_CC_ACR_ID)
        assertEquals(samplePaymentApi.DOCTO_CC_ID, entity.DOCTO_CC_ID)
        assertEquals(samplePaymentApi.FECHA_HORA_PAGO, entity.FECHA_HORA_PAGO)
        assertEquals(samplePaymentApi.GUARDADO_EN_MICROSIP, entity.GUARDADO_EN_MICROSIP)
        assertEquals(samplePaymentApi.IMPORTE, entity.IMPORTE, 0.0)
        assertEquals(19.432608, entity.LAT!!, 0.000001)
        assertEquals(-99.133209, entity.LNG!!, 0.000001)
        assertEquals(samplePaymentApi.CLIENTE_ID, entity.CLIENTE_ID)
        assertEquals(samplePaymentApi.COBRADOR_ID, entity.COBRADOR_ID)
        assertEquals(samplePaymentApi.FORMA_COBRO_ID, entity.FORMA_COBRO_ID)
        assertEquals(samplePaymentApi.ZONA_CLIENTE_ID, entity.ZONA_CLIENTE_ID)
        assertEquals(samplePaymentApi.NOMBRE_CLIENTE, entity.NOMBRE_CLIENTE)
    }

    @Test
    fun `PaymentApi toEntity should handle invalid coordinates gracefully`() {
        val paymentApiWithInvalidCoords = samplePaymentApi.copy(
            LAT = "invalid_lat",
            LNG = "invalid_lng"
        )
        
        val entity = paymentApiWithInvalidCoords.toEntity()
        
        assertNull(entity.LAT)
        assertNull(entity.LNG)
        assertEquals(paymentApiWithInvalidCoords.ID, entity.ID)
    }

    @Test
    fun `Payment toEntity should map correctly`() {
        val entity = samplePayment.toEntity()
        
        assertEquals(samplePayment.ID, entity.ID)
        assertEquals(samplePayment.COBRADOR, entity.COBRADOR)
        assertEquals(samplePayment.DOCTO_CC_ACR_ID, entity.DOCTO_CC_ACR_ID)
        assertEquals(samplePayment.DOCTO_CC_ID, entity.DOCTO_CC_ID)
        assertEquals(samplePayment.FECHA_HORA_PAGO, entity.FECHA_HORA_PAGO)
        assertEquals(samplePayment.GUARDADO_EN_MICROSIP, entity.GUARDADO_EN_MICROSIP)
        assertEquals(samplePayment.IMPORTE, entity.IMPORTE, 0.0)
        assertEquals(samplePayment.LAT, entity.LAT)
        assertEquals(samplePayment.LNG, entity.LNG)
        assertEquals(samplePayment.CLIENTE_ID, entity.CLIENTE_ID)
        assertEquals(samplePayment.COBRADOR_ID, entity.COBRADOR_ID)
        assertEquals(samplePayment.FORMA_COBRO_ID, entity.FORMA_COBRO_ID)
        assertEquals(samplePayment.ZONA_CLIENTE_ID, entity.ZONA_CLIENTE_ID)
        assertEquals(samplePayment.NOMBRE_CLIENTE, entity.NOMBRE_CLIENTE)
    }

    @Test
    fun `PaymentEntity toDomainApi should map correctly`() {
        val domainApi = samplePaymentEntity.toDomainApi()
        
        assertEquals(samplePaymentEntity.ID, domainApi.ID)
        assertEquals(samplePaymentEntity.COBRADOR, domainApi.COBRADOR)
        assertEquals(samplePaymentEntity.DOCTO_CC_ACR_ID, domainApi.DOCTO_CC_ACR_ID)
        assertEquals(samplePaymentEntity.DOCTO_CC_ID, domainApi.DOCTO_CC_ID)
        assertEquals(samplePaymentEntity.FECHA_HORA_PAGO, domainApi.FECHA_HORA_PAGO)
        assertEquals(samplePaymentEntity.GUARDADO_EN_MICROSIP, domainApi.GUARDADO_EN_MICROSIP)
        assertEquals(samplePaymentEntity.IMPORTE, domainApi.IMPORTE, 0.0)
        assertEquals(samplePaymentEntity.LAT.toString(), domainApi.LAT)
        assertEquals(samplePaymentEntity.LNG.toString(), domainApi.LNG)
        assertEquals(samplePaymentEntity.CLIENTE_ID, domainApi.CLIENTE_ID)
        assertEquals(samplePaymentEntity.COBRADOR_ID, domainApi.COBRADOR_ID)
        assertEquals(samplePaymentEntity.FORMA_COBRO_ID, domainApi.FORMA_COBRO_ID)
        assertEquals(samplePaymentEntity.ZONA_CLIENTE_ID, domainApi.ZONA_CLIENTE_ID)
        assertEquals(samplePaymentEntity.NOMBRE_CLIENTE, domainApi.NOMBRE_CLIENTE)
    }

    @Test
    fun `PaymentEntity toDomain should map correctly`() {
        val domain = samplePaymentEntity.toDomain()
        
        assertEquals(samplePaymentEntity.ID, domain.ID)
        assertEquals(samplePaymentEntity.COBRADOR, domain.COBRADOR)
        assertEquals(samplePaymentEntity.DOCTO_CC_ACR_ID, domain.DOCTO_CC_ACR_ID)
        assertEquals(samplePaymentEntity.DOCTO_CC_ID, domain.DOCTO_CC_ID)
        assertEquals(samplePaymentEntity.FECHA_HORA_PAGO, domain.FECHA_HORA_PAGO)
        assertEquals(samplePaymentEntity.GUARDADO_EN_MICROSIP, domain.GUARDADO_EN_MICROSIP)
        assertEquals(samplePaymentEntity.IMPORTE, domain.IMPORTE, 0.0)
        assertEquals(samplePaymentEntity.LAT, domain.LAT)
        assertEquals(samplePaymentEntity.LNG, domain.LNG)
        assertEquals(samplePaymentEntity.CLIENTE_ID, domain.CLIENTE_ID)
        assertEquals(samplePaymentEntity.COBRADOR_ID, domain.COBRADOR_ID)
        assertEquals(samplePaymentEntity.FORMA_COBRO_ID, domain.FORMA_COBRO_ID)
        assertEquals(samplePaymentEntity.ZONA_CLIENTE_ID, domain.ZONA_CLIENTE_ID)
        assertEquals(samplePaymentEntity.NOMBRE_CLIENTE, domain.NOMBRE_CLIENTE)
    }

    @Test
    fun `PaymentEntity with null coordinates should handle toString conversion`() {
        val entityWithNullCoords = samplePaymentEntity.copy(LAT = null, LNG = null)
        val domainApi = entityWithNullCoords.toDomainApi()
        
        assertEquals("null", domainApi.LAT)
        assertEquals("null", domainApi.LNG)
    }
}