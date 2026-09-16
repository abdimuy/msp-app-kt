package com.example.msp_app.feature.visitas.ui

import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.visitas.data.fake.VisitasFixtures
import com.example.msp_app.feature.visitas.domain.ComprobantesDeVisita
import com.example.msp_app.feature.visitas.domain.ReglasDeLaVisita
import com.example.msp_app.feature.visitas.domain.model.CapturaDeVisita
import com.example.msp_app.feature.visitas.domain.model.ComprobanteDeVisita
import com.example.msp_app.feature.visitas.domain.model.Miniatura
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Los **tres estados del mock** `registrar-visita.html`, en tipos de dominio:
 * elegir, prometió y no estaba.
 *
 * "Hoy" es el martes 1-sep-2026, el mismo de los fixtures de `:feature:pagos`,
 * para que las dos pantallas cuenten la misma historia. Los bloqueos NO se
 * escriben a mano: salen de `ReglasDeLaVisita`, así que el golden retrata lo que
 * la pantalla de verdad haría.
 */
object VisitaFixtures {

    /** Martes 1-sep-2026 en zona de negocio. */
    val HOY: LocalDate = LocalDate.of(2026, 9, 1)

    /** **Estado 1 del mock:** los cinco desenlaces, ninguno elegido, CTA apagado. */
    fun elegir(): RegistrarVisitaUiState = estado(CapturaDeVisita())

    /**
     * **Estado 3 del mock (`VisitaC`):** prometió — **una sola cuenta**, fecha y
     * monto. La otra cuenta queda desmarcada: una promesa lleva una fecha y un
     * monto, y repartirlos entre dos cuentas sería inventar dinero.
     */
    fun prometio(): RegistrarVisitaUiState = estado(
        CapturaDeVisita(
            resultado = ResultadoDeVisita.PROMETIO,
            etiqueta = TipoVisitaCatalogo.PIDE_REAGENDAR,
            nota = "el viernes que cobre mi esposo",
            cuentas = setOf(VisitasFixtures.REFRIGERADOR),
            fechaPromesa = HOY.plusDays(3),
            montoPrometido = Money.of(BigDecimal("220"))
        )
    )

    /**
     * **Estado 2 del mock (`VisitaB`):** se negó — **las dos cuentas marcadas**,
     * que es como nacen. Es la captura que antes exigía registrar dos visitas
     * para decir algo que el cliente dijo una sola vez.
     */
    fun seNegoEnTodo(): RegistrarVisitaUiState = estado(
        CapturaDeVisita(
            resultado = ResultadoDeVisita.SE_NEGO,
            etiqueta = TipoVisitaCatalogo.NO_VA_A_DAR_PAGO,
            nota = "dice que hasta que le arreglen el refri",
            cuentas = VisitasFixtures.dosCuentas().map { it.ventaId }.toSet()
        )
    )

    /** El mismo desenlace con **todas desmarcadas**: el CTA se apaga y dice por qué. */
    fun seNegoSinCuentas(): RegistrarVisitaUiState = estado(
        CapturaDeVisita(
            resultado = ResultadoDeVisita.SE_NEGO,
            etiqueta = TipoVisitaCatalogo.NO_VA_A_DAR_PAGO,
            cuentas = emptySet()
        )
    )

    /** **Estado 1 del mock, ya elegido:** no estaba — aplica a toda la puerta. */
    fun noEstaba(): RegistrarVisitaUiState = estado(
        CapturaDeVisita(
            resultado = ResultadoDeVisita.NO_ESTABA,
            etiqueta = TipoVisitaCatalogo.NO_SE_ENCONTRABA,
            nota = "preguntar por la mañana, llega a las 8"
        )
    )

    /** La cita: día y hora como campos, no dentro del texto de la nota. */
    fun cita(): RegistrarVisitaUiState = estado(
        CapturaDeVisita(
            resultado = ResultadoDeVisita.CITA,
            etiqueta = TipoVisitaCatalogo.PIDE_TIEMPO,
            fechaCita = HOY,
            horaCita = java.time.LocalTime.of(16, 0)
        )
    )

    /** Con la recomendación a la vista: lo que el sistema sugirió, en pantalla. */
    fun conRecomendacion(): RegistrarVisitaUiState =
        elegir().copy(recomendacion = VisitasFixtures.recomendacion())

    /**
     * **El estado más apretado de la sección de comprobantes** (Task 23): dos
     * fotos adjuntas, el aviso ámbar encendido y el botón de agregar.
     *
     * Se captura la SECCIÓN sola y no la pantalla entera, y la razón es una
     * medición: a 360×800dp la sección vive debajo de la línea de flotación, así
     * que un golden de pantalla completa saldría idéntico al de sin fotos —
     * verde porque no ve nada. Es exactamente la lección de la Task 21 que la
     * Task 22 tuvo que aprender otra vez.
     */
    fun conComprobantes(): RegistrarVisitaUiState = noEstaba().copy(
        comprobantes = listOf(
            ComprobanteDeVisita("IMG-1", "/files/comprobante_visita_IMG-1.jpg", "image/jpeg"),
            ComprobanteDeVisita("IMG-2", "/files/comprobante_visita_IMG-2.jpg", "image/jpeg"),
            // El tercero es un PDF: no tiene miniatura que enseñar y su cuadro
            // pinta el glifo. Va en el fixture porque es el caso que un golden de
            // puras fotos nunca vería, y es el que el explorador de archivos trae.
            ComprobanteDeVisita("IMG-3", "/files/comprobante_visita_IMG-3.pdf", "application/pdf")
        ),
        miniaturas = mapOf("IMG-1" to miniatura(0), "IMG-2" to miniatura(1)),
        intentos = listOf(IntentoFallido("ARCH-9", FalloDeLaFoto.TIPO_NO_PERMITIDO))
    )

    /** La rejilla llena: sin «+», porque ya no caben más. */
    fun comprobantesLlenos(): RegistrarVisitaUiState = noEstaba().copy(
        comprobantes = (1..ComprobantesDeVisita.MAXIMO).map {
            ComprobanteDeVisita("IMG-$it", "/files/comprobante_visita_IMG-$it.jpg", "image/jpeg")
        },
        miniaturas = (1..ComprobantesDeVisita.MAXIMO).associate { "IMG-$it" to miniatura(it) }
    )

    /** La hoja del «+» arriba, sobre la pantalla con una foto ya puesta. */
    fun eligiendoOrigen(): RegistrarVisitaUiState = noEstaba().copy(
        comprobantes = listOf(
            ComprobanteDeVisita("IMG-1", "/files/comprobante_visita_IMG-1.jpg", "image/jpeg")
        ),
        miniaturas = mapOf("IMG-1" to miniatura(0)),
        eligiendoOrigen = true
    )

    /**
     * Una miniatura **sintética y determinista**, para los goldens.
     *
     * Píxeles calculados, no leídos de un archivo: un golden que dependiera de
     * decodificar un JPEG de disco dependería del decodificador de la máquina que
     * lo grabó. Esta fórmula da el mismo buffer en cualquier parte, y el diagonal
     * con dos tonos hace que un `ContentScale.Crop` mal puesto se note — un
     * relleno liso se vería igual recortado que estirado.
     */
    fun miniatura(semilla: Int): Miniatura {
        val lado = LADO_DE_LA_MINIATURA
        val pixeles = IntArray(lado * lado) { indice ->
            val x = indice % lado
            val y = indice / lado
            if (x + y < lado) TONOS[semilla % TONOS.size] else TONOS[(semilla + 1) % TONOS.size]
        }
        return Miniatura(ancho = lado, alto = lado, pixeles = pixeles)
    }

    /** Chico a propósito: el cuadro la escala, y 32x32 basta para ver el diagonal. */
    private const val LADO_DE_LA_MINIATURA = 32

    /** Tres ARGB opacos, escogidos para que se distingan en claro y en oscuro. */
    private val TONOS = intArrayOf(
        0xFF2563EB.toInt(),
        0xFFB0C2B6.toInt(),
        0xFFEDE8DC.toInt()
    )

    /** El estado que arma la pantalla con [captura], con sus bloqueos derivados. */
    fun estado(captura: CapturaDeVisita): RegistrarVisitaUiState = RegistrarVisitaUiState(
        cargando = false,
        contexto = VisitasFixtures.victoria(),
        captura = captura,
        hoy = HOY,
        bloqueos = ReglasDeLaVisita.bloqueosDe(captura, HOY),
        // El teléfono del cobrador (SM-A256E, Android 13+) SÍ trae el
        // reconocedor en-dispositivo, así que el micrófono se pinta. Un fixture
        // con `false` dejaría los goldens enseñando una pantalla que ningún
        // cobrador de la flota ve.
        sePuedeDictar = true
    )
}
