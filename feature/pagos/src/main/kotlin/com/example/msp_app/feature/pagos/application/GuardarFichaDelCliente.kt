package com.example.msp_app.feature.pagos.application

import com.example.msp_app.feature.pagos.domain.model.FichaDelCliente
import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha
import com.example.msp_app.feature.pagos.domain.port.FichaDelClientePort
import com.example.msp_app.feature.pagos.domain.port.ResultadoDeLaFicha
import javax.inject.Inject

/**
 * Guarda la ficha del cliente: el catálogo cerrado y la nota libre, juntos.
 *
 * ## Qué hace este caso de uso que el puerto no
 *
 * **Normaliza la nota antes de que llegue a la base** con
 * [FichaDelCliente.limpia]: recorta, corta a [FichaDelCliente.NOTA_MAX] y
 * convierte el vacío en ausencia. Vive aquí y no en la pantalla porque la
 * pantalla no es el único camino posible a la escritura, y no en el adaptador
 * porque es una regla del dominio (qué cuenta como "nota"), no de la
 * persistencia.
 *
 * Las señales llegan como `Set`, así que un duplicado ya es imposible en este
 * nivel — pero la unicidad real la garantiza la llave primaria compuesta
 * `(CLIENTE_ID, SENAL)`, no este tipo. Ver `ClientProfileDao`.
 */
class GuardarFichaDelCliente @Inject constructor(
    private val ficha: FichaDelClientePort
) {

    suspend operator fun invoke(
        clienteId: Int,
        senales: Set<SenalDeFicha>,
        nota: String?
    ): ResultadoDeLaFicha = ficha.guardar(
        clienteId = clienteId,
        ficha = FichaDelCliente(senales = senales, nota = FichaDelCliente.limpia(nota))
    )
}
