package com.example.msp_app.feature.visitas.data.fake

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.visitas.domain.model.ContextoDeVisita
import com.example.msp_app.feature.visitas.domain.model.RecomendacionMostrada
import com.example.msp_app.feature.visitas.domain.model.VentaParaVisitar
import com.example.msp_app.feature.visitas.domain.port.ContextoDeVisitaPort
import com.example.msp_app.feature.visitas.domain.port.RecomendacionesPort
import com.example.msp_app.feature.visitas.domain.port.RegistroDeVisitaPort
import com.example.msp_app.feature.visitas.domain.port.ResultadoDelRegistro
import com.example.msp_app.feature.visitas.domain.port.UbicacionDeLaVisita
import com.example.msp_app.feature.visitas.domain.port.UbicacionPort
import com.example.msp_app.feature.visitas.domain.port.VisitaARegistrar
import java.math.BigDecimal
import java.time.Instant

/**
 * Fakes escritos a mano — **estado público + lista pública que graba las
 * llamadas**. Sin MockK, sin Mockito (DISPATCH-CONVENTIONS).
 *
 * Datos mexicanos, como pide el repo: Victoria Flores Olmedo y sus dos cuentas,
 * las mismas del mock.
 */
object VisitasFixtures {

    const val VICTORIA: Int = 5021
    const val SALA: Int = 77021
    const val REFRIGERADOR: Int = 77188

    fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))

    fun victoria(ventas: List<VentaParaVisitar> = dosCuentas()): ContextoDeVisita =
        ContextoDeVisita(
            clienteId = VICTORIA,
            nombre = "Victoria Flores Olmedo",
            direccion = "C. Hidalgo 214, Centro",
            saldoTotal = Money.sum(ventas.map { it.saldo }),
            ventas = ventas
        )

    fun dosCuentas(): List<VentaParaVisitar> = listOf(
        VentaParaVisitar(
            ventaId = SALA,
            folio = "V-5021",
            descripcion = "Sala 3 piezas + base",
            saldo = dinero("2100")
        ),
        VentaParaVisitar(
            ventaId = REFRIGERADOR,
            folio = "V-5188",
            descripcion = "Refrigerador Mabe 14'",
            saldo = dinero("1450")
        )
    )

    fun recomendacion(
        id: String = "rec-victoria-1",
        grupo: String = RecomendacionMostrada.GRUPO_TRATAMIENTO
    ): RecomendacionMostrada = RecomendacionMostrada(
        recomendacionId = id,
        clienteId = VICTORIA,
        ventaId = REFRIGERADOR,
        posicion = 0,
        motivo = "cercania",
        algoritmo = "cercania_v1",
        grupo = grupo,
        generadaEn = Instant.parse("2026-09-01T14:00:00Z")
    )
}

/** Fake de [ContextoDeVisitaPort]: estado público + grabación de llamadas. */
class FakeContextoDeVisitaPort(
    var contexto: ContextoDeVisita? = VisitasFixtures.victoria(),
    private val falla: Throwable? = null
) : ContextoDeVisitaPort {

    val consultados: MutableList<Int> = mutableListOf()

    override suspend fun contexto(clienteId: Int): ContextoDeVisita? {
        consultados += clienteId
        falla?.let { throw it }
        return contexto
    }
}

/** Fake de [RecomendacionesPort]. */
class FakeRecomendacionesPort(
    var recomendacion: RecomendacionMostrada? = null,
    private val falla: Throwable? = null
) : RecomendacionesPort {

    val consultados: MutableList<Int> = mutableListOf()

    override suspend fun vigenteDe(clienteId: Int): RecomendacionMostrada? {
        consultados += clienteId
        falla?.let { throw it }
        return recomendacion
    }
}

/**
 * Fake de [RegistroDeVisitaPort]. [registradas] es la lista que los tests
 * inspeccionan: qué se escribió, con qué promesa y con qué cita.
 */
class FakeRegistroDeVisitaPort(
    var resultado: ResultadoDelRegistro = ResultadoDelRegistro.REGISTRADA
) : RegistroDeVisitaPort {

    val registradas: MutableList<VisitaARegistrar> = mutableListOf()

    override suspend fun registrar(visita: VisitaARegistrar): ResultadoDelRegistro {
        registradas += visita
        return resultado
    }
}

/**
 * Fake de [UbicacionPort]. Puede contestar una ubicación, contestar `null`
 * (permiso negado) o **lanzar** (Play Services caído): los tres caminos importan,
 * y en los tres la visita tiene que quedar registrada igual.
 */
class FakeUbicacionPort(
    private val ubicacion: UbicacionDeLaVisita? = UbicacionDeLaVisita(18.46, -97.39),
    private val falla: Throwable? = null
) : UbicacionPort {

    var vecesConsultada: Int = 0
        private set

    override suspend fun ubicacionActual(): UbicacionDeLaVisita? {
        vecesConsultada++
        falla?.let { throw it }
        return ubicacion
    }
}
