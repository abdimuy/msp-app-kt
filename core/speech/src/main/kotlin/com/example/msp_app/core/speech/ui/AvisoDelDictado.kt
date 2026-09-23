package com.example.msp_app.core.speech.ui

import com.example.msp_app.core.speech.domain.FalloDelDictado

/**
 * **Qué se le dice al cobrador cuando el dictado no pudo.**
 *
 * Vive acá y no en cada pantalla que dicte, por una razón concreta: si cada
 * llamador inventa su frase, dos pantallas dirán cosas distintas del mismo
 * hecho. Y porque el repo pide **2-4 palabras, sin punto final** (`CLAUDE.md`
 * §3) — una regla que se rompe sola en cuanto la frase se escribe en el sitio
 * de la llamada.
 *
 * ## Por qué ninguna frase dice "escribe la nota"
 *
 * Porque eso ya lo dice la forma: sin dictado **no se pinta el micrófono**, y lo
 * único que queda es un campo de texto. Agregarlo por escrito sería repetir el
 * dato que la pantalla ya da (principio 5), y además rompería el límite de
 * palabras para no decir nada nuevo.
 *
 * ## Los dos silencios
 *
 * [FalloDelDictado.SIN_HABLA] y [FalloDelDictado.AUDIO_NO_SE_GUARDO] devuelven
 * `null`, y por motivos opuestos: en el primero no pasó nada (nadie habló) y
 * avisar inventaría un problema; en el segundo lo que se perdió fue el respaldo,
 * no la nota, y ya se reportó por telemetría — molestar al cobrador con eso, en
 * la puerta, no le deja hacer nada distinto.
 */
fun avisoDe(fallo: FalloDelDictado?): String? = when (fallo) {
    FalloDelDictado.SIN_PERMISO -> "Sin permiso del micrófono"
    FalloDelDictado.SIN_MOTOR -> "Este teléfono no dicta"
    FalloDelDictado.MICROFONO_OCUPADO -> "El micrófono está ocupado"
    FalloDelDictado.MOTOR_FALLO -> "No se pudo dictar"
    FalloDelDictado.SIN_HABLA -> null
    FalloDelDictado.AUDIO_NO_SE_GUARDO -> null
    null -> null
}
