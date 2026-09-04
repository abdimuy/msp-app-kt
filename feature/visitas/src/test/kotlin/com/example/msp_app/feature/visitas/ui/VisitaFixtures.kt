package com.example.msp_app.feature.visitas.ui

import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.visitas.data.fake.VisitasFixtures
import com.example.msp_app.feature.visitas.domain.ReglasDeLaVisita
import com.example.msp_app.feature.visitas.domain.model.CapturaDeVisita
import com.example.msp_app.feature.visitas.domain.model.ComprobanteDeVisita
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

    /** **Estado 2 del mock:** prometió — venta, fecha y monto. */
    fun prometio(): RegistrarVisitaUiState = estado(
        CapturaDeVisita(
            resultado = ResultadoDeVisita.PROMETIO,
            etiqueta = TipoVisitaCatalogo.PIDE_REAGENDAR,
            nota = "el viernes que cobre mi esposo",
            ventaDeLaPromesa = VisitasFixtures.REFRIGERADOR,
            fechaPromesa = HOY.plusDays(3),
            montoPrometido = Money.of(BigDecimal("220"))
        )
    )

    /** **Estado 3 del mock:** no estaba — aplica a las dos cuentas del cliente. */
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
            ComprobanteDeVisita("IMG-2", "/files/comprobante_visita_IMG-2.jpg", "image/jpeg")
        ),
        falloDeLaFoto = FalloDeLaFoto.TIPO_NO_PERMITIDO
    )

    /** El estado que arma la pantalla con [captura], con sus bloqueos derivados. */
    fun estado(captura: CapturaDeVisita): RegistrarVisitaUiState = RegistrarVisitaUiState(
        cargando = false,
        contexto = VisitasFixtures.victoria(),
        captura = captura,
        hoy = HOY,
        bloqueos = ReglasDeLaVisita.bloqueosDe(captura, HOY)
    )
}
