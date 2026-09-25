package com.example.msp_app.core.geo

/**
 * **Junta las mediciones que son el mismo lugar.**
 *
 * ## Los 30 metros, y por qué no hay un número "correcto"
 *
 * [RADIO_DE_AGRUPACION_M] **salió de los datos, no de la intuición.** El 2026-09-24
 * se barrió el radio de 5 a 500 m sobre 1,091 cuentas reales con 8 o más
 * mediciones (26,342 puntos), midiendo cuánto compra cada metro extra en
 * "porcentaje de mediciones que caen en el grupo mayor":
 *
 * | radio | % en el grupo mayor | lo que compra cada metro |
 * |---|---|---|
 * | 5 m | 49.4 % | — |
 * | 10 m | 69.4 % | 4.00 %/m |
 * | 15 m | 76.6 % | 1.44 %/m |
 * | 20 m | 79.5 % | 0.58 %/m |
 * | **30 m** | **82.9 %** | **0.30 %/m** |
 * | 40 m | 84.4 % | 0.14 %/m |
 * | 100 m | 87.5 % | 0.036 %/m |
 * | 500 m | 92.7 % | — |
 *
 * El codo está entre 25 y 40 m y a partir de 30 el retorno cae por debajo de una
 * quinta parte. Arriba de 40 sólo se compra el derecho a fundir dos puertas
 * distintas.
 *
 * ## La parte incómoda, que hay que leer antes de cambiar el número
 *
 * **No hay un valle limpio.** El histograma de distancias entre mediciones del
 * mismo cliente decae de forma continua desde su pico en 5-10 m: **no existe una
 * distancia donde el mundo se parta en dos.** Lo que existe es un codo, y 30 es
 * dónde está.
 *
 * O sea: quien suba esto a 60 no está corrigiendo un error, está moviendo un
 * compromiso — y lo que gana (unos pocos puntos porcentuales de mediciones
 * agrupadas) lo paga **juntando lugares que son distintos**, que es el error
 * caro: manda al cobrador a una puerta equivocada. Si lo cambia, cambie también
 * esta tabla, porque se midió y se puede volver a medir.
 *
 * ## Por qué enlace simple y no k-means ni DBSCAN de biblioteca
 *
 * Enlace simple (dos mediciones son el mismo lugar si hay una cadena de saltos
 * de ≤ 30 m entre ellas) es lo que corresponde al modelo del problema: **no hay
 * un número de lugares conocido de antemano** —un cliente puede tener uno, o dos
 * si se mudó— así que k-means, que exige ese número, contestaría la pregunta
 * equivocada.
 *
 * Y es **aritmética en metros**, que es el punto entero. `maps-compose` trae
 * agrupamiento (`maps-compose-utils`), pero su algoritmo agrupa por **distancia
 * en píxeles al zoom actual**: los grupos se reharían solos al acercarse —la
 * puerta se partiría en tres— y **no hay forma de expresar "30 metros"**. Con ~20
 * mediciones por cliente el costo de hacerlo a mano es irrelevante y el
 * resultado no miente.
 */
object AgrupadorDeLugares {

    /**
     * **Treinta metros.** Ver el KDoc de la clase antes de cambiarlo: el número
     * está medido y la tabla que lo justifica está ahí.
     */
    const val RADIO_DE_AGRUPACION_M: Double = 30.0

    /**
     * Agrupa [mediciones] en lugares, marcando cada uno contra [compartidos].
     *
     * El resultado viene **ordenado de mayor a menor por [LugarAgrupado.conteo]**,
     * y con la fecha más reciente como desempate, para que la pantalla no tenga
     * que reordenar ni dependa del orden en que Room devolvió las filas.
     */
    fun agrupar(
        mediciones: List<MedicionDelCobro>,
        compartidos: IndiceDePuntosCompartidos,
        radioM: Double = RADIO_DE_AGRUPACION_M
    ): List<LugarAgrupado> {
        if (mediciones.isEmpty()) return emptyList()
        return etiquetas(mediciones, radioM)
            .let { etiquetas ->
                mediciones.indices.groupBy { etiquetas[it] }.values
                    .map { indices -> lugarDe(indices.map { mediciones[it] }, compartidos) }
            }
            .sortedWith(
                compareByDescending<LugarAgrupado> { it.conteo }.thenByDescending { it.masReciente }
            )
    }

    /**
     * El enlace simple, en crudo: recorre cada medición sin etiqueta y arrastra
     * hacia ella todo lo que esté a [radioM] o menos, en cadena.
     *
     * Es O(n²) a propósito. Con las ~20 mediciones por cliente que la medición
     * del 2026-09-24 encontró (y 81 en el cliente más cargado de toda la
     * cartera), un índice espacial sería más código para esconder el mismo
     * resultado.
     */
    private fun etiquetas(mediciones: List<MedicionDelCobro>, radioM: Double): IntArray {
        val etiqueta = IntArray(mediciones.size) { SIN_ETIQUETA }
        var actual = 0
        for (semilla in mediciones.indices) {
            if (etiqueta[semilla] != SIN_ETIQUETA) continue
            expandirDesde(semilla, actual, mediciones, radioM, etiqueta)
            actual++
        }
        return etiqueta
    }

    /**
     * Arrastra al grupo [grupo] todo lo alcanzable desde [semilla] por saltos de
     * [radioM] o menos, marcándolo en [etiqueta].
     *
     * Está separado de [etiquetas] porque la cadena es la parte con estado del
     * algoritmo, y leerla sola —sin el bucle que reparte semillas— deja ver la
     * invariante que la hace terminar: **una medición se etiqueta al encolarla,
     * no al desencolarla**, así que no puede entrar dos veces a la pila.
     */
    private fun expandirDesde(
        semilla: Int,
        grupo: Int,
        mediciones: List<MedicionDelCobro>,
        radioM: Double,
        etiqueta: IntArray
    ) {
        etiqueta[semilla] = grupo
        val pendientes = ArrayDeque<Int>()
        pendientes.addLast(semilla)
        while (pendientes.isNotEmpty()) {
            val i = pendientes.removeLast()
            val vecinas = mediciones.indices.filter { j ->
                etiqueta[j] == SIN_ETIQUETA &&
                    mediciones[i].punto.distanciaA(mediciones[j].punto) <= radioM
            }
            vecinas.forEach { j ->
                etiqueta[j] = grupo
                pendientes.addLast(j)
            }
        }
    }

    private fun lugarDe(
        mediciones: List<MedicionDelCobro>,
        compartidos: IndiceDePuntosCompartidos
    ): LugarAgrupado {
        // El centro se promedia sobre las mediciones de domicilio cuando las hay:
        // una transferencia capturada a tres cuadras jalaría el centro —y con él
        // el pin— hacia donde no vive nadie. Si el lugar es sólo de
        // transferencias, se promedia lo que hay: es el único centro posible y
        // dibujarlo es más honesto que no dibujar nada.
        val paraElCentro = mediciones.filter { !it.esTransferencia }.ifEmpty { mediciones }
        val centro = Punto(
            lat = paraElCentro.sumOf { it.punto.lat } / paraElCentro.size,
            lon = paraElCentro.sumOf { it.punto.lon } / paraElCentro.size
        )
        val clientes = compartidos.clientesQueComparten(centro)
        return LugarAgrupado(
            mediciones = mediciones.sortedByDescending { it.fecha },
            centro = centro,
            esCompartido = clientes >= IndiceDePuntosCompartidos.MINIMO_DE_CLIENTES,
            clientesQueLoComparten = clientes,
            cobradoresQueCobranAqui = compartidos.cobradoresQueComparten(centro)
        )
    }

    private const val SIN_ETIQUETA = -1
}
