package com.example.msp_app.core.utils

import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo

/**
 * Alcance del efecto de una visita sobre las ventas del cliente.
 *
 * El enum se mudó a `:core:common`
 * ([com.example.msp_app.core.common.cobranza.domain.VisitScope]) en la Task 14,
 * porque ahora lo comparten dos consumidores en dos módulos:
 * `VisitsLocalDataSource` (`:app`, Task 13) y el catálogo de ocho estados
 * (`:core:common`, Task 14, que `:feature:pagos` y `:feature:visitas` van a
 * consumir en la Task 15). Un tipo compartido no puede quedarse en `:app`:
 * `:core:common` no puede depender de `:app`.
 *
 * Este `typealias` mantiene válidos todos los
 * `import com.example.msp_app.core.utils.VisitScope` que ya existen — cero call
 * sites tocados.
 */
typealias VisitScope = com.example.msp_app.core.common.cobranza.domain.VisitScope

/**
 * Deriva el [VisitScope] de un `TIPO_VISITA`.
 *
 * **Ya no clasifica: delega.** La Task 13 mantenía aquí la lista de literales
 * de alcance cliente. La Task 14 la absorbió en [TipoVisitaCatalogo], donde el
 * alcance se deriva del estado del catálogo de ocho ("no estaba" y "cita a una
 * hora" son los dos estados de alcance cliente), así que **no hay una segunda
 * clasificación** que se pueda desincronizar de la primera.
 *
 * El comportamiento es idéntico al de la Task 13, literal por literal — lo
 * prueban los 11 casos de `VisitScopeMapperTest`, que no cambiaron: las tres
 * etiquetas de "no estaba" dan [VisitScope.CLIENTE] y todo lo demás, incluidos
 * los literales retirados, los futuros y los desconocidos, da
 * [VisitScope.VENTA]. El alcance más angosto sigue siendo el default seguro:
 * una etiqueta mal clasificada como VENTA simplemente deja de propagar; mal
 * clasificada como CLIENTE toca ventas que el cobrador no visitó — el bug que
 * la Task 13 existe para evitar, no para reintroducir por otro lado.
 */
object VisitScopeMapper {
    fun map(tipoVisita: String): VisitScope = TipoVisitaCatalogo.alcanceDe(tipoVisita)
}
