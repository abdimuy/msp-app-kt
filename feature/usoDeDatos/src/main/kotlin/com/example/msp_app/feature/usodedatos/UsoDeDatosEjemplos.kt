package com.example.msp_app.feature.usodedatos

/**
 * Estados de ejemplo para las pruebas y los goldens (`internal` al módulo, sin
 * relación con datos reales). Los números son redondos a propósito.
 */
object UsoDeDatosEjemplos {
    val conDatos = UsoDeDatosUiState(
        cargando = false,
        hoy = PeriodoDeUso(
            total = 48_300_000,
            movil = 12_100_000,
            wifi = 36_200_000,
            abierta = 30_400_000,
            segundoPlano = 17_900_000,
            servicios = listOf(
                FilaDeServicio("Lista de clientes", 18_600_000),
                FilaDeServicio("Pagos y ventas (sincronización)", 9_100_000),
                FilaDeServicio("Mapa, Firebase y otros", 8_400_000),
                FilaDeServicio("Fotos (subida)", 6_000_000),
                FilaDeServicio("Cuadre con la oficina", 3_700_000),
                FilaDeServicio("Avisos en vivo", 1_800_000),
                FilaDeServicio("Visitas", 700_000)
            ),
            sinDetalle = false
        ),
        semana = PeriodoDeUso(
            total = 301_700_000,
            movil = 80_500_000,
            wifi = 221_200_000,
            abierta = 190_000_000,
            segundoPlano = 111_700_000,
            servicios = listOf(
                FilaDeServicio("Lista de clientes", 120_400_000),
                FilaDeServicio("Pagos y ventas (sincronización)", 62_300_000),
                FilaDeServicio("Mapa, Firebase y otros", 52_000_000),
                FilaDeServicio("Fotos (subida)", 38_900_000),
                FilaDeServicio("Cuadre con la oficina", 14_600_000),
                FilaDeServicio("Avisos en vivo", 9_300_000),
                FilaDeServicio("Visitas", 4_200_000)
            ),
            sinDetalle = false
        )
    )

    val sinDesglose = UsoDeDatosUiState(
        cargando = false,
        hoy = PeriodoDeUso(
            5_000_000,
            1_000_000,
            4_000_000,
            3_000_000,
            2_000_000,
            emptyList(),
            true
        ),
        semana = PeriodoDeUso(
            5_000_000,
            1_000_000,
            4_000_000,
            3_000_000,
            2_000_000,
            emptyList(),
            true
        )
    )

    val vacio = UsoDeDatosUiState(cargando = false)
}
