package com.example.msp_app.feature.ventacorreccion.domain

/**
 * Las cinco cadenas de usuario de la corrección de venta, en un solo lugar
 * (plan "Corregir una venta antes de que suba", corrección 1 del
 * orquestador: MAYÚSCULA INICIAL, no minúscula — el dueño lo pidió
 * explícitamente el 2026-09-20 y la rama `feat/pagos-y-visitas` ya cerró
 * esta discusión el 18-sep, commit `62a25715`: la regla escrita en
 * `CLAUDE.md` §3 estaba invertida para texto de usuario y esa inversión ya
 * causó un defecto en producción).
 *
 * Regla de forma, verificada en `TextosCorreccionTest.kt`: 2 a 4 palabras,
 * arranca con mayúscula, sin punto final, nunca la palabra "ciclo".
 */
object TextosCorreccion {
    /** Botón para entrar a corregir una venta [EstadoCorreccion.Corregible]. */
    const val CORREGIR_VENTA = "Corregir venta"

    /** Aviso cuando el estado es [EstadoCorreccion.SeEstaEnviando]. */
    const val SE_ESTA_ENVIANDO = "Se está enviando"

    /** Aviso cuando el estado es [EstadoCorreccion.YaSeEnvio]. */
    const val YA_SE_ENVIO = "Ya se envió"

    /** Aviso cuando el estado es [EstadoCorreccion.LaRevisaLaOficina]. */
    const val LA_REVISA_LA_OFICINA = "La revisa la oficina"

    /** Confirmación tras guardar una corrección exitosamente. */
    const val CORRECCION_GUARDADA = "Corrección guardada"
}
