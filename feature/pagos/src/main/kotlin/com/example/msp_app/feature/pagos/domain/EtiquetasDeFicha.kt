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
 * *"horario matutino"*. Minúsculas y sin punto final, como todo el texto de
 * usuario de este plan.
 */
fun etiquetaDe(senal: SenalDeFicha): String = when (senal) {
    SenalDeFicha.NO_IR_SOLO -> "no ir solo"
    SenalDeFicha.HAY_PERRO -> "hay perro"
    SenalDeFicha.ESTA_EN_LA_MANANA -> "está en la mañana"
    SenalDeFicha.ESTA_EN_LA_TARDE -> "está en la tarde"
    SenalDeFicha.ESTA_EN_LA_NOCHE -> "está en la noche"
    SenalDeFicha.ATIENDE_OTRA_PERSONA -> "atiende otra persona"
}
