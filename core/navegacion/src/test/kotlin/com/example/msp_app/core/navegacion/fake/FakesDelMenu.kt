package com.example.msp_app.core.navegacion.fake

import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio
import com.example.msp_app.core.navegacion.domain.port.ContadoresDelMenuPort
import com.example.msp_app.core.navegacion.domain.port.EstadoDeEnvioPort
import com.example.msp_app.core.navegacion.domain.port.PrivacidadDelMenuPort
import com.example.msp_app.core.navegacion.domain.port.SeccionesPlegadasPort
import com.example.msp_app.core.navegacion.domain.port.TemaDelMenuPort
import com.example.msp_app.core.navegacion.domain.port.ZonaDelUsuarioPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

class FakeEstadoDeEnvioPort : EstadoDeEnvioPort {
    val flujo = MutableSharedFlow<EstadoDeEnvio>(replay = 1)
    var falla: Throwable? = null

    override fun estado(): Flow<EstadoDeEnvio> {
        val error = falla ?: return flujo
        return flow { throw error }
    }
}

class FakeContadoresDelMenuPort : ContadoresDelMenuPort {
    var respuesta: Map<String, String> = emptyMap()
    var falla: Exception? = null
    val pedidos = mutableListOf<Set<String>>()

    override suspend fun contadores(ids: Set<String>): Map<String, String> {
        pedidos += ids
        falla?.let { throw it }
        return respuesta.filterKeys { it in ids }
    }
}

class FakeSeccionesPlegadasPort : SeccionesPlegadasPort {
    val valores = MutableStateFlow<Set<String>>(emptySet())
    val escrituras = mutableListOf<Pair<String, Boolean>>()

    override fun plegadas(): Flow<Set<String>> = valores

    override suspend fun setPlegada(seccionId: String, plegada: Boolean) {
        escrituras += seccionId to plegada
        valores.value = if (plegada) valores.value + seccionId else valores.value - seccionId
    }
}

/**
 * El nombre de zona, fingido. `MutableSharedFlow` sin `replay` inicial —igual
 * que [FakeEstadoDeEnvioPort]— porque la fuente real (Task 6/8, sobre
 * `SaleEntity.ZONA_NOMBRE`) tampoco tiene un valor listo antes de su primera
 * lectura; el `onStart` del ViewModel es lo que cubre ese hueco.
 */
class FakeZonaDelUsuarioPort : ZonaDelUsuarioPort {
    val flujo = MutableSharedFlow<String?>(replay = 1)
    var falla: Throwable? = null

    override fun nombreDeZona(): Flow<String?> {
        val error = falla ?: return flujo
        return flow { throw error }
    }
}

/**
 * El tema GLOBAL, fingido — mismo criterio que `FakeTemaDeLaAppPort` de
 * `:feature:pagos`: **flipea de verdad** y lo emite por [oscuro], para que un
 * cableado roto (el botón llama al puerto pero la pantalla nunca se entera)
 * no pase una prueba por accidente. `MutableStateFlow` con valor desde el
 * arranque —a diferencia del envío o la zona— porque [TemaDelMenuPort.oscuroAhora]
 * siempre tiene una respuesta síncrona ya resuelta.
 */
class FakeTemaDelMenuPort(oscuroInicial: Boolean = false) : TemaDelMenuPort {
    private val estado = MutableStateFlow(oscuroInicial)
    var falla: Throwable? = null
    var alternaciones: Int = 0
        private set

    override val oscuro: Flow<Boolean>
        get() {
            val error = falla ?: return estado.asStateFlow()
            return flow { throw error }
        }

    override fun oscuroAhora(): Boolean = estado.value

    override fun alternar() {
        alternaciones++
        estado.value = !estado.value
    }
}

/** "Esconder cantidades", fingido. Misma forma que [FakeTemaDelMenuPort]. */
class FakePrivacidadDelMenuPort(ocultosIniciales: Boolean = false) : PrivacidadDelMenuPort {
    private val estado = MutableStateFlow(ocultosIniciales)
    var falla: Throwable? = null
    var alternaciones: Int = 0
        private set

    override val ocultos: Flow<Boolean>
        get() {
            val error = falla ?: return estado.asStateFlow()
            return flow { throw error }
        }

    override fun ocultosAhora(): Boolean = estado.value

    override suspend fun alternar() {
        alternaciones++
        estado.value = !estado.value
    }
}
