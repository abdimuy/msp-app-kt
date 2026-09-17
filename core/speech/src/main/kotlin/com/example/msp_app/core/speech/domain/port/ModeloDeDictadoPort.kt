package com.example.msp_app.core.speech.domain.port

import com.example.msp_app.core.speech.domain.EstadoDelModelo
import kotlinx.coroutines.flow.Flow

/**
 * **El modelo de alta fidelidad: dónde está y cómo se pide.**
 *
 * Puerto justificado por el contrato de capas (Ruling BF, caso 3): quien lo
 * consume es la pantalla de descarga, que vive en `ui/` y tiene **prohibido**
 * importar el adaptador donde viven WorkManager, OkHttp y el `File`.
 *
 * Separado de [DictadoPort] a propósito, aunque el modelo sea del dictado: son
 * dos ciclos de vida distintos. El dictado dura ocho segundos con el micrófono
 * abierto; la descarga dura media hora, sobrevive a que la app se cierre y la
 * gobierna una restricción de red. Un solo puerto con las dos cosas obligaría a
 * la pantalla de la visita a conocer la descarga, que es justo lo que no debe
 * pasar: **el cobrador sin modelo dicta igual y no se entera de que existe.**
 */
interface ModeloDeDictadoPort {

    /**
     * **¿Existe el motor que usaría este modelo?**
     *
     * `false` mientras la librería nativa de whisper no viaje en el APK — que es
     * el caso **hoy y en toda la flota**. Ver `MotorWhisperNativo`: whisper.cpp no
     * se vendoró, y la decisión está razonada en su KDoc y en el commit que la
     * tomó.
     *
     * ## Para qué sirve: esconder la descarga, no apagarla
     *
     * Configuración pinta el renglón del dictado **solo si esto es `true`**.
     * Ofrecer bajar 43.5 MB para un motor que no puede cargar nada no es una
     * función a medias: es **ofrecer una mentira**, y peor que no tenerla, porque
     * el cobrador gasta sus datos y no gana nada.
     *
     * ## Por qué es un dato del puerto y no una bandera
     *
     * Podría ser un `const val MOSTRAR_DESCARGA = false` y sería una línea menos.
     * Pero entonces alguien tendría que **acordarse** de voltearla el día que el
     * `.so` exista, y ese día no hay nada que se lo recuerde. Preguntándole al
     * puerto, el renglón **aparece solo** cuando el motor aparece, sin tocar
     * código y sin que nadie tenga que recordar nada.
     *
     * Es el mismo criterio con el que el dictado elige motor: no se pregunta "¿qué
     * dijo una constante?", se pregunta "¿qué hay en este teléfono?".
     */
    val motorDisponible: Boolean

    /** En qué punto está. Una sola fuente, y la pantalla no infiere nada aparte. */
    fun estado(): Flow<EstadoDelModelo>

    /**
     * Encola la descarga **restringida a wifi**. No baja nada por sí misma: deja
     * el trabajo encolado y el sistema lo corre cuando la red deje de ser
     * medida. Por eso no devuelve `Result` — encolar no falla, y lo que puede
     * fallar (la descarga) se ve en [estado].
     */
    suspend fun pedirLaDescarga()

    /**
     * Cancela y **borra lo bajado**. Es la única forma de recuperar los megas, y
     * quien la llama es el cobrador, nunca la app sola.
     */
    suspend fun cancelarYBorrar()
}
