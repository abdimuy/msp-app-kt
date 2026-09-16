package com.example.msp_app.core.mapas.adapters

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.msp_app.core.mapas.domain.AvanceDeLaDescarga
import com.example.msp_app.core.mapas.domain.EstadoDelExtracto
import com.example.msp_app.core.mapas.domain.ExtractoDeMapa
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * **El "solo con wifi" de la promesa, cumplido en vez de prometido.**
 *
 * `NetworkType.UNMETERED` es la única forma de que 25 MB no salgan de los datos
 * del cobrador sin que él lo sepa. Comprobarlo a mano antes de empezar no
 * bastaría: la descarga dura minutos y el teléfono puede cambiar de wifi a datos
 * a la mitad — WorkManager la pausa sola y la reanuda cuando vuelve el wifi.
 *
 * `ExistingWorkPolicy.KEEP` y no `REPLACE`: tocar "Descargar" dos veces no puede
 * tirar los 12 MB que ya bajaron.
 */
@Singleton
class PlanificadorConWorkManager @Inject constructor(
    @ApplicationContext private val context: Context
) : PlanificadorDeLaDescarga {

    override fun encolarSoloConWifi() {
        val trabajo = OneTimeWorkRequestBuilder<DescargaDelExtractoWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(TRABAJO, ExistingWorkPolicy.KEEP, trabajo)
    }

    override fun cancelar() {
        WorkManager.getInstance(context).cancelUniqueWork(TRABAJO)
    }

    internal companion object {
        /** El nombre único de la cola. Uno solo: no hay dos mapas compitiendo. */
        const val TRABAJO: String = "descarga_del_extracto_de_mapa"
    }
}

/**
 * Baja el extracto en segundo plano.
 *
 * La restricción de red ya la puso [PlanificadorConWorkManager] al encolar; este
 * worker **no vuelve a decidirla**. Lo que sí decide es reintentar o rendirse,
 * con el mismo criterio que `DescargaDelModeloWorker` de `:core:speech`:
 *
 * - cortada → `retry`, y lo bajado se conserva;
 * - archivo inválido → `retry` también, pero con el parcial ya borrado;
 * - lista → `success`.
 *
 * **`failure` sí existe acá, y es la diferencia con el dictado**: sin extracto
 * configurado no hay URL a la que ir, y reintentar para siempre un trabajo que
 * no puede tener éxito gastaría batería sin poder ganar nunca.
 */
@HiltWorker
class DescargaDelExtractoWorker @AssistedInject constructor(
    @Assisted contexto: Context,
    @Assisted parametros: WorkerParameters,
    private val descarga: DescargaDelExtracto,
    private val almacen: AlmacenDelExtracto,
    private val estado: EstadoDelExtractoEnMemoria,
    private val extracto: ExtractoDeMapa?
) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        val paquete = extracto ?: run {
            estado.poner(EstadoDelExtracto.SinOrigen)
            return Result.failure()
        }
        val desenlace = descarga.bajar(paquete) { avance ->
            estado.poner(EstadoDelExtracto.Descargando(avance))
        }
        return when (desenlace) {
            DesenlaceDeLaDescarga.Listo -> {
                val mapa = almacen.mapaListo()
                if (mapa != null) {
                    estado.poner(EstadoDelExtracto.Listo(mapa))
                    Result.success()
                } else {
                    // Promovió y no verifica al releer: el disco cambió debajo.
                    // Se vuelve a cero, que es lo único que puede arreglarlo.
                    estado.poner(EstadoDelExtracto.Ausente)
                    Result.retry()
                }
            }

            is DesenlaceDeLaDescarga.Cortada -> {
                estado.poner(EstadoDelExtracto.Interrumpido(almacen.avance(paquete)))
                Result.retry()
            }

            DesenlaceDeLaDescarga.Invalida -> {
                // El parcial ya lo borró el almacén: se vuelve a cero y se DICE
                // que se vuelve a cero, en vez de dejar una barra al 100% que no
                // avanza.
                estado.poner(
                    EstadoDelExtracto.Interrumpido(AvanceDeLaDescarga(0L, paquete.tamanoBytes))
                )
                Result.retry()
            }
        }
    }
}
