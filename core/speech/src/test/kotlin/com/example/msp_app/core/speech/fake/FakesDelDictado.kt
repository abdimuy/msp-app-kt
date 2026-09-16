package com.example.msp_app.core.speech.fake

import android.os.ParcelFileDescriptor
import com.example.msp_app.core.speech.adapters.EventoDelReconocedor
import com.example.msp_app.core.speech.adapters.GrabadoraDeAudio
import com.example.msp_app.core.speech.adapters.MotorWhisperNativo
import com.example.msp_app.core.speech.adapters.PermisoDeMicrofono
import com.example.msp_app.core.speech.adapters.PlanificadorDeLaDescarga
import com.example.msp_app.core.speech.adapters.ReconocedorDeAndroid
import com.example.msp_app.core.speech.domain.GrabacionDictada
import com.example.msp_app.core.speech.domain.ModeloDeDictado

/**
 * Los fakes del módulo: **estado público + lista pública que graba las
 * llamadas**, escritos a mano. Sin MockK ni Mockito, que es regla dura del repo
 * (`CLAUDE.md` §2 y global-constraints §Definición de "tests en todo").
 */

/** Un reconocedor que contesta lo que el test le diga, cuando el test quiera. */
class ReconocedorFalso(
    override var disponible: Boolean = true,
    var arranca: Boolean = true
) : ReconocedorDeAndroid {

    /** Las llamadas, en orden. */
    val llamadas: MutableList<String> = mutableListOf()

    /** La fuente que el adaptador le pasó. Prueba que el audio se comparte. */
    var fuenteRecibida: ParcelFileDescriptor? = null

    private var escucha: ((EventoDelReconocedor) -> Unit)? = null

    override fun comenzar(
        fuente: ParcelFileDescriptor?,
        alEvento: (EventoDelReconocedor) -> Unit
    ): Boolean {
        llamadas += "comenzar"
        fuenteRecibida = fuente
        escucha = alEvento
        return arranca
    }

    override fun detener() {
        llamadas += "detener"
    }

    override fun cancelar() {
        llamadas += "cancelar"
    }

    /** El test dispara el evento que quiere probar. */
    fun emite(evento: EventoDelReconocedor) {
        escucha?.invoke(evento)
    }
}

/** Una grabadora que guarda —o que falla— según lo que el test pida. */
class GrabadoraFalsa(
    var abre: Boolean = true,
    var cierra: Boolean = true,
    var grabacion: GrabacionDictada = GrabacionDictada("g-1", "/tmp/dictado.wav", 8_000L)
) : GrabadoraDeAudio {

    val llamadas: MutableList<String> = mutableListOf()

    var nivel: Float = 0.5f

    override suspend fun comenzar(): Result<Unit> {
        llamadas += "comenzar"
        return if (abre) Result.success(Unit) else Result.failure(IllegalStateException("ocupado"))
    }

    override suspend fun terminar(): Result<GrabacionDictada> {
        llamadas += "terminar"
        return desenlace()
    }

    override suspend fun cancelar(): Result<GrabacionDictada> {
        llamadas += "cancelar"
        return desenlace()
    }

    override fun nivel(): Float = nivel

    override fun fuenteCompartida(): ParcelFileDescriptor? = null

    private fun desenlace(): Result<GrabacionDictada> =
        if (cierra) Result.success(grabacion) else Result.failure(java.io.IOException("vacia"))
}

/** El permiso, dicho por el test. */
class PermisoFalso(var concedido: Boolean = true) : PermisoDeMicrofono {
    override fun concedido(): Boolean = concedido
}

/** whisper nativo, sin nada nativo. */
class NativoFalso(
    override var cargada: Boolean = true,
    var respuesta: Result<String> = Result.success("texto de whisper")
) : MotorWhisperNativo {

    val transcritos: MutableList<Pair<String, String>> = mutableListOf()

    override fun transcribir(rutaModelo: String, rutaAudio: String): Result<String> {
        transcritos += rutaModelo to rutaAudio
        return respuesta
    }
}

/** El planificador, que solo graba qué se le pidió. */
class PlanificadorFalso : PlanificadorDeLaDescarga {

    val encolados: MutableList<ModeloDeDictado> = mutableListOf()

    var cancelaciones: Int = 0

    override fun encolarSoloConWifi(modelo: ModeloDeDictado) {
        encolados += modelo
    }

    override fun cancelar() {
        cancelaciones++
    }
}

/** El modelo de prueba. Números chicos: acá no se baja nada de verdad. */
val MODELO_DE_PRUEBA: ModeloDeDictado = ModeloDeDictado(
    url = "https://ejemplo.invalido/modelo.bin",
    tamanoBytes = 1_000L,
    sha256 = "0000000000000000000000000000000000000000000000000000000000000000"
)
