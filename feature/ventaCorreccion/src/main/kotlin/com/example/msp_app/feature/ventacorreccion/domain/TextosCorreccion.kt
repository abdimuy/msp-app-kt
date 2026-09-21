package com.example.msp_app.feature.ventacorreccion.domain

/**
 * Las seis cadenas de usuario de la corrección de venta, en un solo lugar
 * (plan "Corregir una venta antes de que suba", corrección 1 del
 * orquestador: MAYÚSCULA INICIAL, no minúscula — el dueño lo pidió
 * explícitamente el 2026-09-20 y la rama `feat/pagos-y-visitas` ya cerró
 * esta discusión el 18-sep, commit `62a25715`: la regla escrita en
 * `CLAUDE.md` §3 estaba invertida para texto de usuario y esa inversión ya
 * causó un defecto en producción).
 *
 * Regla de forma, verificada en `TextosCorreccionTest.kt`: 2 a 4 palabras,
 * arranca con mayúscula, sin punto final, nunca la palabra "ciclo".
 *
 * [NO_SE_PUDO_GUARDAR] se agregó en la ronda 1 de arreglo de Task 3: antes,
 * un guardado rechazado con [EstadoCorreccion.Corregible] (el caso "candado
 * ajeno pero reentrante" — otra sesión de EDICIÓN ganó la fila entre el
 * guardia y la relectura) mostraba [CORREGIR_VENTA], mintiendo justo en el
 * único caso alcanzable de esa rama. Ver
 * `CorreccionVentaViewModel.aTextoDeRechazoDeGuardado`.
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

    /**
     * Un guardado se rechazó y la razón no es ninguno de los tres estados
     * terminales (ya se envió / se está enviando / lo revisa la oficina) —
     * el candado del llamador ya no era el vigente. Nunca se usa para
     * decidir si se PUEDE corregir (eso es [CORREGIR_VENTA]); sólo explica
     * por qué un guardado en curso no se pudo completar.
     */
    const val NO_SE_PUDO_GUARDAR = "No se pudo guardar"
}
