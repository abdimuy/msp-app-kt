package com.example.msp_app.feature.visitas.data.fake

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.visitas.domain.model.ComprobanteDeVisita
import com.example.msp_app.feature.visitas.domain.model.ContextoDeVisita
import com.example.msp_app.feature.visitas.domain.model.DestinoDeFoto
import com.example.msp_app.feature.visitas.domain.model.Miniatura
import com.example.msp_app.feature.visitas.domain.model.RecomendacionMostrada
import com.example.msp_app.feature.visitas.domain.model.VentaParaVisitar
import com.example.msp_app.feature.visitas.domain.port.ComprobantesDeVisitaPort
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
            saldo = dinero("2100"),
            parcialidad = dinero("350")
        ),
        VentaParaVisitar(
            ventaId = REFRIGERADOR,
            folio = "V-5188",
            descripcion = "Refrigerador Mabe 14 pies",
            saldo = dinero("1450"),
            parcialidad = dinero("220")
        )
    )

    /** Una sola cuenta: el caso en que la pantalla no pregunta nada. */
    fun unaCuenta(): List<VentaParaVisitar> = listOf(dosCuentas().first())

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

    /**
     * El índice (base 0) de la llamada que debe fallar, o `null` si ninguna.
     * Existe para el caso de varias cuentas: una que falla no puede tirar a las
     * demás, y eso solo se puede probar haciendo fallar exactamente a una.
     */
    var fallaEn: Int? = null

    override suspend fun registrar(visita: VisitaARegistrar): ResultadoDelRegistro {
        val indice = registradas.size
        registradas += visita
        return if (indice == fallaEn) ResultadoDelRegistro.FALLO_EL_GUARDADO else resultado
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

/**
 * Fake de [com.example.msp_app.feature.visitas.domain.port.VisitaImpresaPort]:
 * la visita ya registrada, leída de vuelta por su id.
 */
class FakeVisitaImpresaPort(
    var visitas: List<com.example.msp_app.feature.visitas.domain.model.VisitaRegistrada> =
        emptyList()
) : com.example.msp_app.feature.visitas.domain.port.VisitaImpresaPort {

    /** Cada id consultado, en orden. */
    val consultadas: MutableList<String> = mutableListOf()

    override suspend fun visita(
        visitaId: String
    ): com.example.msp_app.feature.visitas.domain.model.VisitaRegistrada? {
        consultadas += visitaId
        return visitas.firstOrNull { it.visitaId == visitaId }
    }
}

/**
 * Fake de [ComprobantesDeVisitaPort] — la cámara.
 *
 * Graba TODO lo que se le pide: los destinos que acuñó, los que aceptó y los
 * archivos que se le mandó borrar. Cada una de las tres funciones puede fallar
 * por separado, porque los tres caminos tienen que dejar la visita registrada
 * igual y ninguno puede tapar al otro.
 *
 * [mimeAceptado] es lo que devuelve [aceptar]: cambiarlo a algo fuera de la
 * whitelist es cómo se prueba el rechazo por tipo sin escribir bytes reales.
 */
class FakeComprobantesDeVisitaPort : ComprobantesDeVisitaPort {

    // Las perillas van como propiedades y no en el constructor: ya son ocho, y
    // un constructor de ocho parámetros opcionales se lee peor en la llamada
    // (`FakeComprobantesDeVisitaPort(null, null, null, X)`) que un `.also { }`
    // que nombra la única que el test cambia.

    var fallaAlPreparar: Throwable? = null

    var fallaAlAceptar: Throwable? = null

    var fallaAlDescartar: Throwable? = null

    var fallaAlImportar: Throwable? = null

    var fallaAlPedirMiniatura: Throwable? = null

    var mimeAceptado: String = "image/jpeg"

    var mimeImportado: String = "image/jpeg"

    /** Lo que devuelve [miniatura]. `null` = el cuadro se pinta sin vista previa. */
    var miniaturaDeCadaArchivo: Miniatura? = null

    /** Los destinos acuñados, en orden. */
    val destinos: MutableList<DestinoDeFoto> = mutableListOf()

    /** Los `content://` que se mandó importar, en orden. */
    val importados: MutableList<String> = mutableListOf()

    /** Las rutas de las que se pidió miniatura, en orden. */
    val miniaturasPedidas: MutableList<String> = mutableListOf()

    /** Los destinos que llegaron a [aceptar], en orden. */
    val aceptados: MutableList<DestinoDeFoto> = mutableListOf()

    /** Las rutas que se mandó borrar, en orden. */
    val descartados: MutableList<String> = mutableListOf()

    private var siguiente = 0

    override suspend fun nuevoDestino(): DestinoDeFoto {
        fallaAlPreparar?.let { throw it }
        siguiente++
        val destino = DestinoDeFoto(
            id = "IMG-$siguiente",
            uriParaLaCamara = "content://fake/camara/$siguiente",
            archivoCrudo = "/tmp/fake/crudo-$siguiente.jpg"
        )
        destinos += destino
        return destino
    }

    override suspend fun aceptar(destino: DestinoDeFoto): ComprobanteDeVisita {
        fallaAlAceptar?.let { throw it }
        aceptados += destino
        // CONSERVA el id del destino, que es el contrato del puerto: acuñar otro
        // aquí dejaría el `id_<n>` sin clave estable entre reintentos.
        return ComprobanteDeVisita(
            id = destino.id,
            archivo = "/tmp/fake/comprobante-${destino.id}.jpg",
            mime = mimeAceptado
        )
    }

    override suspend fun importar(uri: String): ComprobanteDeVisita {
        fallaAlImportar?.let { throw it }
        importados += uri
        siguiente++
        // Acuña su propio id, igual que el adaptador real: la importación no
        // pasa por un destino, así que nadie le acuñó uno antes.
        return ComprobanteDeVisita(
            id = "ARCH-$siguiente",
            archivo = "/tmp/fake/importado-$siguiente.jpg",
            mime = mimeImportado
        )
    }

    override suspend fun miniatura(archivo: String): Miniatura? {
        miniaturasPedidas += archivo
        fallaAlPedirMiniatura?.let { throw it }
        return miniaturaDeCadaArchivo
    }

    override suspend fun descartar(archivo: String) {
        descartados += archivo
        fallaAlDescartar?.let { throw it }
    }
}
