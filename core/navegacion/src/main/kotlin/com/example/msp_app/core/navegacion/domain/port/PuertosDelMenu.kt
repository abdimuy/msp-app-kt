package com.example.msp_app.core.navegacion.domain.port

import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio
import kotlinx.coroutines.flow.Flow

/**
 * Contadores ya formateados por id de opción, sólo para [ids] (las opciones que
 * el usuario ve). Ids sin fuente simplemente no aparecen en el mapa.
 * Ruling BF: cruza módulo — las fuentes viven en `:feature:pagos` y Room, y el
 * adaptador en `:app` (decisión D2 del plan: se llama al abrir, no en cada frame).
 */
interface ContadoresDelMenuPort {
    suspend fun contadores(ids: Set<String>): Map<String, String>
}

/** Pendientes de subir + hace cuánto hubo contacto sano con el servidor (spec §4). */
interface EstadoDeEnvioPort {
    fun estado(): Flow<EstadoDeEnvio>
}

/** Qué secciones dejó plegadas el usuario. Persistido (decisión D3 del plan). */
interface SeccionesPlegadasPort {
    fun plegadas(): Flow<Set<String>>

    suspend fun setPlegada(seccionId: String, plegada: Boolean)
}

/**
 * El nombre de zona del usuario (p. ej. "R/25"), tal cual lo trae la fuente.
 * Ruling R3 (controlador, 2026-10-06): la cabecera lo muestra si existe y NO
 * muestra nada si no — este puerto nunca construye un respaldo "Zona N", eso
 * lo decide quien lo consume. El adaptador real (sobre `SaleEntity.ZONA_NOMBRE`)
 * es de las Tasks 6/8.
 */
interface ZonaDelUsuarioPort {
    fun nombreDeZona(): Flow<String?>
}

/**
 * Puerto hacia el tema **GLOBAL** de la app. Ruling R2 (controlador,
 * 2026-10-06, decisión del dueño): el botón sol/luna SÍ va en el menú lateral.
 *
 * Misma forma que `feature.pagos.domain.port.TemaDeLaAppPort` a propósito —
 * mismo contrato, misma frontera, misma razón (ver su KDoc): el puerto se
 * queda en el módulo y el adaptador real vive en `:app`, que es el único que
 * ve `ThemeController` (Task 8). No se reusa el puerto de `:feature:pagos`
 * porque `:core:navegacion` no depende de `:feature:pagos` ni debería.
 */
interface TemaDelMenuPort {

    /** Tema oscuro vigente — reactivo, refleja cualquier cambio de origen. */
    val oscuro: Flow<Boolean>

    /** Lectura síncrona — siembra el estado inicial del ViewModel sin un frame en claro. */
    fun oscuroAhora(): Boolean

    /** Alterna el tema GLOBAL de la app y lo persiste. Síncrono en la implementación real. */
    fun alternar()
}

/**
 * "Esconder cantidades" visto desde el menú lateral. Ruling R2 (controlador,
 * 2026-10-06, decisión del dueño): el ojo de privacidad SÍ va en el menú.
 *
 * Misma forma que `feature.pagos.domain.port.PrivacidadPort` a propósito —
 * ver su KDoc para el porqué de [ocultosAhora] y de que el adaptador viva en
 * `:app` (Task 8).
 */
interface PrivacidadDelMenuPort {

    /** `true` mientras los montos deban ir enmascarados. */
    val ocultos: Flow<Boolean>

    /** Lectura síncrona del último valor conocido — siembra el estado inicial. */
    fun ocultosAhora(): Boolean

    /** Invierte la preferencia y la persiste. */
    suspend fun alternar()
}
