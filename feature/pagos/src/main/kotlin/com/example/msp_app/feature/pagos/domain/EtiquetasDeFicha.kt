package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha

/**
 * La etiqueta en español de cada señal del catálogo cerrado.
 *
 * **`when` exhaustivo, sin `else`, a propósito** — es la compuerta visible del
 * guardrail que el plan pidió copiar de kollect (`SignalLabels`): agregar un
 * valor a [SenalDeFicha] **no compila** hasta que pasa por aquí, así que una
 * señal no puede llegar a la pantalla sin nombre. Un `else -> senal.name`
 * habría sido más corto y habría convertido el error de compilación en un chip
 * que dice `ESTA_EN_LA_MANANA` en la calle.
 *
 * Vocabulario del cobrador y no del catálogo: la app ya dice *"no responde
 * aunque está"*, así que la ficha dice **está**, no *"disponible"* ni
 * *"horario matutino"*.
 *
 * ## Mayúscula inicial, y este KDoc decía lo contrario
 *
 * Aquí se leía *"Minúsculas y sin punto final, como todo el texto de usuario de
 * este plan"*. **Era falso**, y no por descuido de redacción: el principio 10
 * del brief pide mayúscula inicial desde el primer día, y `CLAUDE.md` también.
 * La regla se pudo invertir dentro del código porque el brief vivía sólo en
 * `.superpowers/`, que `.gitignore` no guarda — así que la única versión escrita
 * de la regla que había DENTRO del repo era ésta, la invertida, y
 * `CatalogoDeLaFichaTest` la fijó con un test que cobraba la minúscula.
 *
 * El resultado llegó al teléfono del dueño: *"registrar abono"*, *"visita"*,
 * *"continuar"*, *"otra hora"*. La fuente de verdad es el brief, rescatado en
 * `docs/superpowers/plans/2026-09-17-principios-cobranza-2026.md`, nunca un
 * KDoc; y ahora lo mide una compuerta que barre las fuentes, en
 * `app/src/test/java/com/example/msp_app/navigation/`.
 *
 * **Las constantes del `enum` no se tocaron, y no deben tocarse**: el literal
 * que se persiste en `cliente_ficha_senales.SENAL` es [Enum.name], así que
 * renombrar una desconecta las fichas ya guardadas. Lo que cambia aquí es
 * **cómo se escribe en pantalla**, que es lo único que esta función decide.
 */
fun etiquetaDe(senal: SenalDeFicha): String = when (senal) {
    SenalDeFicha.NO_IR_SOLO -> "No ir solo"
    SenalDeFicha.HAY_PERRO -> "Hay perro"
    SenalDeFicha.ESTA_EN_LA_MANANA -> "Está en la mañana"
    SenalDeFicha.ESTA_EN_LA_TARDE -> "Está en la tarde"
    SenalDeFicha.ESTA_EN_LA_NOCHE -> "Está en la noche"
    SenalDeFicha.ATIENDE_OTRA_PERSONA -> "Atiende otra persona"
}
