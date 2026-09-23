package com.example.msp_app.core.speech.adapters

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.msp_app.core.speech.domain.AvanceDeLaDescarga
import com.example.msp_app.core.speech.domain.EstadoDelModelo
import com.example.msp_app.core.speech.domain.ModeloDeDictado
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * **El "solo con wifi" del mock, cumplido en vez de prometido.**
 *
 * `NetworkType.UNMETERED` es la única forma de que 75 MB no salgan de los datos
 * del cobrador sin que él lo sepa. Comprobarlo a mano antes de empezar no
 * bastaría: la descarga dura minutos y el teléfono puede cambiar de wifi a
 * datos a la mitad — WorkManager la pausa sola y la reanuda cuando vuelve el
 * wifi, que es exactamente la promesa que el mock hace.
 *
 * `ExistingWorkPolicy.KEEP` y no `REPLACE`: tocar "Descargar" dos veces no
 * puede tirar los 48 MB que ya bajaron.
 */
@Singleton
class PlanificadorConWorkManager @Inject constructor(
    @ApplicationContext private val context: Context
) : PlanificadorDeLaDescarga {

    override fun encolarSoloConWifi(modelo: ModeloDeDictado) {
        val trabajo = OneTimeWorkRequestBuilder<DescargaDelModeloWorker>()
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
        /** El nombre único de la cola. Uno solo: no hay dos modelos compitiendo. */
        const val TRABAJO: String = "descarga_del_modelo_de_dictado"
    }
}

/**
 * Baja el modelo en segundo plano.
 *
 * La restricción de red ya la puso [PlanificadorConWorkManager] al encolar;
 * este worker **no vuelve a decidirla**. Lo que sí decide es reintentar o
 * rendirse, con el mismo criterio que `ApkDownloadWorker` de `:core:appgate`:
 *
 * - cortada → `retry`, y lo bajado se conserva;
 * - checksum mal → `retry` también, pero con el parcial ya borrado: volver a
 *   bajar de cero es lo único que puede arreglarlo;
 * - lista → `success`.
 *
 * Nunca `failure`: no hay entrada que pueda faltar —el modelo es una constante
 * del módulo— así que no hay caso irrecuperable que reportar.
 */
@HiltWorker
class DescargaDelModeloWorker @AssistedInject constructor(
    @Assisted contexto: Context,
    @Assisted parametros: WorkerParameters,
    private val descarga: DescargaDelModelo,
    private val almacen: AlmacenDelModelo,
    private val estado: EstadoDelModeloEnMemoria,
    private val modelo: ModeloDeDictado
) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        val desenlace = descarga.bajar(modelo) { avance ->
            estado.poner(EstadoDelModelo.Descargando(avance))
        }
        return when (desenlace) {
            DesenlaceDeLaDescarga.Listo -> {
                estado.poner(EstadoDelModelo.Listo)
                Result.success()
            }

            is DesenlaceDeLaDescarga.Cortada -> {
                estado.poner(EstadoDelModelo.Interrumpido(almacen.avance(modelo)))
                Result.retry()
            }

            DesenlaceDeLaDescarga.Corrupta -> {
                // El parcial ya lo borró el almacén: se vuelve a cero y se dice
                // que se vuelve a cero, en vez de dejar una barra al 100% que
                // no avanza.
                estado.poner(
                    EstadoDelModelo.Interrumpido(AvanceDeLaDescarga(0L, modelo.tamanoBytes))
                )
                Result.retry()
            }
        }
    }
}
