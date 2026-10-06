package com.example.msp_app.core.navegacion.application

import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio

/**
 * Lo que la hoja necesita de los puertos, en un solo valor.
 *
 * [nombreDeZona], [temaOscuro] y [montosOcultos] se suman al trío original
 * del brief (Ruling R2/R3 del controlador, 2026-10-06): el sol/luna y el ojo
 * de privacidad SÍ van en el menú, y la cabecera muestra el nombre de zona
 * cuando existe. `false`/`null` son los valores neutros antes de la primera
 * lectura o tras una fuente que falló — igual que [contadores] vacío o
 * [envio] nulo, nunca se inventa un dato.
 */
data class EstadoDelMenu(
    val envio: EstadoDeEnvio? = null,
    val plegadas: Set<String> = emptySet(),
    val contadores: Map<String, String> = emptyMap(),
    val nombreDeZona: String? = null,
    val temaOscuro: Boolean = false,
    val montosOcultos: Boolean = false
)
