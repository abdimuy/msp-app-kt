package com.example.msp_app.features.forgiveness.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.data.pagos.RegistroDeCondonacion
import com.example.msp_app.data.pagos.ResultadoDeLaCondonacion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lo que el diálogo de condonación necesita saber para pintarse.
 *
 * @property saldo el `SALDO_REST` **releído de Room**, nunca el de la venta que
 *   la pantalla cargó al abrir. `null` mientras no se ha leído, o si la venta no
 *   está en el teléfono.
 * @property guardando hay una escritura en vuelo: "Confirmar" se deshabilita.
 * @property guardadaId el id de la condonación recién escrita. Mientras no sea
 *   `null`, el diálogo navega al ticket y no acepta otra.
 * @property error el mensaje del último intento que no se escribió.
 * @property terminada esta instancia del formulario ya guardó una condonación y
 *   **no acepta otra, nunca**: ver [CondonacionViewModel.condonar].
 */
data class CondonacionUiState(
    val saldo: Double? = null,
    val guardando: Boolean = false,
    val guardadaId: String? = null,
    val error: String? = null,
    val terminada: Boolean = false
)

/**
 * **La escritura de la condonación, esperada y de una sola vez.**
 *
 * Tres defectos del diálogo viejo, cerrados aquí:
 *
 * - **No esperaba a la escritura** (E-APP-047): `savePayment` abría su propia
 *   corrutina y el diálogo navegaba sin saber si se había escrito. Aquí la
 *   pantalla sólo ve [CondonacionUiState.guardadaId] cuando
 *   [RegistroDeCondonacion.condonar] ya volvió con [ResultadoDeLaCondonacion.GUARDADA].
 * - **Validaba contra una foto** (E-APP-043, #4): el tope y el prellenado salían
 *   de la venta cargada al abrir. Aquí [CondonacionUiState.saldo] se relee de
 *   Room al cargar y después de cada intento — así, tras guardar, el diálogo no
 *   puede volver a ofrecer el saldo viejo.
 * - **Sin guarda contra el doble toque** (E-APP-043, #2): [condonar] se niega
 *   **de forma síncrona** mientras hay una escritura en vuelo o una ya guardada,
 *   antes del `launch`. Dos toques en el mismo cuadro producen una sola fila.
 *
 * El error no navega: se queda visible y el cobrador decide.
 */
class CondonacionViewModel(
    private val registro: RegistroDeCondonacion
) : ViewModel() {

    private val _estado = MutableStateFlow(CondonacionUiState())
    val estado: StateFlow<CondonacionUiState> = _estado.asStateFlow()

    private var ventaId: Int? = null

    /**
     * Relee el saldo vigente de la venta. **Siempre relee**, no sólo la primera
     * vez: en la puerta legada el ViewModel vive tanto como la pantalla, y el
     * diálogo puede volver a abrirse después de que el sync movió el saldo. El
     * diálogo lo llama cada vez que entra en composición.
     */
    fun cargar(ventaId: Int) {
        this.ventaId = ventaId
        viewModelScope.launch { releerSaldo() }
    }

    /**
     * Intenta escribir [condonacion]. **Se niega sin hacer nada** si ya hay una
     * escritura en vuelo o si esta instancia ya guardó una
     * ([CondonacionUiState.terminada]).
     *
     * ## Guardada = terminada, para siempre
     *
     * Tras guardar, el formulario sigue en pantalla un instante mientras navega
     * al ticket (lo vio el dueño en el aparato el 2026-09-27). Antes, el diálogo
     * le pedía a este ViewModel que volviera a aceptar ANTES de navegar, y en una
     * condonación PARCIAL (2300 → 1000 → 1300) el resto quedaba condonable con un
     * toque rápido: válido contra el tope, perdón de deuda de más. Ahora no hay
     * forma de reabrir esta instancia: la navegación saca la pantalla y el
     * ViewModel muere con ella, y la puerta legada abre una instancia nueva cada
     * vez que se abre el diálogo (`NewForgivenessDialog`).
     */
    fun condonar(condonacion: PaymentEntity) {
        val actual = _estado.value
        if (actual.guardando || actual.terminada) return
        _estado.value = actual.copy(guardando = true, error = null)
        viewModelScope.launch {
            val resultado = try {
                registro.condonar(condonacion)
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (
                @Suppress("TooGenericExceptionCaught") fallo: Exception
            ) {
                // Room puede fallar de muchas formas; la transacción no dejó nada.
                null
            }
            val saldo = releerSaldoSinPublicar()
            _estado.value = when (resultado) {
                ResultadoDeLaCondonacion.GUARDADA -> CondonacionUiState(
                    saldo = saldo,
                    guardadaId = condonacion.ID,
                    terminada = true
                )
                else -> CondonacionUiState(saldo = saldo, error = mensajeDe(resultado))
            }
        }
    }

    private suspend fun releerSaldo() {
        val saldo = releerSaldoSinPublicar()
        _estado.update { it.copy(saldo = saldo) }
    }

    @Suppress("TooGenericExceptionCaught") // una lectura fallida no tumba la pantalla
    private suspend fun releerSaldoSinPublicar(): Double? = try {
        ventaId?.let { registro.saldoVigente(it) }
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (_: Exception) {
        null
    }

    private fun mensajeDe(resultado: ResultadoDeLaCondonacion?): String = when (resultado) {
        ResultadoDeLaCondonacion.EXCEDE_EL_SALDO -> "El monto excede el saldo actual"
        ResultadoDeLaCondonacion.MONTO_INVALIDO -> "El monto debe ser mayor a cero"
        ResultadoDeLaCondonacion.VENTA_NO_ESTA_EN_EL_TELEFONO -> "La venta no está en el teléfono"
        ResultadoDeLaCondonacion.NO_ES_CONDONACION,
        ResultadoDeLaCondonacion.GUARDADA,
        null -> "No se pudo guardar la condonación"
    }

    /** Fábrica para `viewModel(factory = ...)` desde el diálogo, sin Hilt. */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CondonacionViewModel(
            RegistroDeCondonacion(AppDatabase.getInstance(context.applicationContext))
        ) as T
    }
}
