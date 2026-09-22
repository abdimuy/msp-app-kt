package com.example.msp_app.feature.ventacorreccion.domain

/**
 * Las nueve cadenas de usuario de la corrección de venta, en un solo lugar
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
 *
 * [CORRECCION_EN_CAMINO] y [LA_APLICO_LA_OFICINA] llegaron con el nivel 2
 * ("Corregir una venta DESPUÉS de que subió, mientras siga en borrador"),
 * los avisos de los dos estados nuevos que NO ofrecen corregir. El tercer
 * estado nuevo, [EstadoCorreccion.CorregibleEnviada], NO trae cadena propia:
 * reusa [CORREGIR_VENTA], ver su KDoc.
 *
 * [GUARDAR_CORRECCION] se agregó en la ronda de arreglo 1 de Task 5: era un
 * literal suelto dentro de `EditSaleScreen.kt` (`:app`), el ÚNICO texto de
 * usuario de esa pantalla sin ninguna red — `:app` no aplica Roborazzi, así
 * que nada impedía que alguien lo recortara, le pusiera punto final o le
 * metiera "ciclo" sin que ninguna prueba se enterara. Aquí sí lo cubre
 * `TextosCorreccionTest`.
 */
object TextosCorreccion {
    /**
     * Botón para entrar a corregir una venta: [EstadoCorreccion.Corregible]
     * (todavía no sube) y [EstadoCorreccion.CorregibleEnviada] (ya subió y
     * sigue en borrador en el servidor). El nivel 2 NO le da cadena propia a
     * [EstadoCorreccion.CorregibleEnviada] a propósito: para el dueño es la
     * misma acción — corregir su venta — y un texto distinto según dónde
     * esté la venta sólo sembraría la duda de si ahí significa otra cosa.
     */
    const val CORREGIR_VENTA = "Corregir venta"

    /** Aviso cuando el estado es [EstadoCorreccion.SeEstaEnviando]. */
    const val SE_ESTA_ENVIANDO = "Se está enviando"

    /** Aviso cuando el estado es [EstadoCorreccion.CorreccionEnCamino]. */
    const val CORRECCION_EN_CAMINO = "Corrección en camino"

    /** Aviso cuando el estado es [EstadoCorreccion.LaOficinaYaLaAplico]. */
    const val LA_APLICO_LA_OFICINA = "La aplicó la oficina"

    /**
     * Aviso cuando el estado es [EstadoCorreccion.SeAplicoAMedias]: parte de la
     * corrección entró y parte no, porque la oficina cerró la venta a media
     * entrega. Dice "revísala" y no sólo lo que pasó, porque es el único aviso
     * del juego que pide una acción: aquí el teléfono y la oficina NO coinciden,
     * y nadie más se va a dar cuenta.
     */
    const val SE_APLICO_A_MEDIAS = "Entró a medias, revísala"

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

    /** Botón que envía el formulario de corrección (`EditSaleScreen`, `:app`). */
    const val GUARDAR_CORRECCION = "Guardar corrección"
}
