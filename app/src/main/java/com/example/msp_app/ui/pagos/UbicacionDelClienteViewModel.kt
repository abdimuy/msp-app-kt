package com.example.msp_app.ui.pagos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.domain.port.AccionesExternasPort
import com.example.msp_app.feature.pagos.domain.port.DestinoEnElMapa
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * El único estado con lógica del mapa grande: **salir a navegar**.
 *
 * ## Por qué existe, si la pantalla solo enseña un punto
 *
 * Porque "cómo llegar" no puede lanzarse desde el Composable. El KDoc de
 * [AccionesExternasPort] lo explica: `startActivity` desde la UI rompe el
 * contrato de capas y, peor, `ActivityNotFoundException` es un caso **real** de
 * la flota —un teléfono sin app de mapas— que metido en un `onClick` o tumba la
 * pantalla o se traga en un `catch` mudo. El puerto contesta `Result` y acá se
 * decide qué hacer con el fallo, que es reportarlo.
 *
 * ## Y por qué NO se reusa el `comoLlegar` del detalle de cliente
 *
 * Ese vive en `DetalleClienteViewModel`, que carga un cliente entero desde Room.
 * Esta pantalla ya recibe el punto y la dirección por la ruta y no necesita
 * volver a leer nada: pedirle el ViewModel del detalle la ataría a una carga que
 * no usa. Lo que sí se comparte es el **puerto y su código de telemetría**, así
 * que un fallo al abrir la app de mapas se ve igual desde las dos pantallas.
 */
@HiltViewModel
class UbicacionDelClienteViewModel @Inject constructor(
    private val accionesExternas: AccionesExternasPort,
    private val telemetry: Telemetry
) : ViewModel() {

    /**
     * Abre la app de mapas del teléfono en [lat]/[lng], con [direccion] de
     * etiqueta.
     *
     * Siempre con coordenadas: a esta pantalla no se llega sin un punto medido
     * —el cuadro del detalle solo se puede tocar cuando lo hay—, así que el
     * `geo:` lleva a la puerta y no a donde el buscador crea que está la calle.
     */
    fun comoLlegar(direccion: String, lat: Double, lng: Double) {
        telemetry.tap(PANTALLA, PagosTelemetria.ACCION_COMO_LLEGAR)
        viewModelScope.launch {
            accionesExternas.comoLlegar(
                DestinoEnElMapa(etiqueta = direccion, direccion = direccion, lat = lat, lng = lng)
            ).onFailure { fallo ->
                // Norma de errores: ningún `Throwable` se traga. Anti-PII —
                // viajan la acción (catálogo cerrado) y el nombre de la clase de
                // excepción; la dirección NO.
                telemetry.error(
                    code = PagosTelemetria.CODE_ACCION_EXTERNA_FALLO,
                    message = "no se pudo abrir la app de mapas desde la ubicación del cliente",
                    props = mapOf(
                        PagosTelemetria.PROP_ACCION to PagosTelemetria.ACCION_COMO_LLEGAR,
                        PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName
                    )
                )
            }
        }
    }

    private companion object {
        const val PANTALLA = "pagos_ubicacion_cliente"
    }
}
