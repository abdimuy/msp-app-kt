package com.example.msp_app.core.geo

import kotlin.math.cos
import kotlin.math.floor

/**
 * Un punto de la ruta y de quién es: lo que el índice come.
 *
 * Es deliberadamente más pobre que [MedicionDelCobro] — aquí no importa la
 * fecha, ni el importe, ni la venta. Sólo **dónde**, **de qué cliente** y **de
 * qué cobrador**.
 */
data class PuntoDeLaRuta(val clienteId: Int, val cobrador: String, val punto: Punto)

/**
 * **Detecta los puntos que no son la puerta de nadie porque son de muchos.**
 *
 * ## El hecho que lo motiva, medido
 *
 * El 2026-09-24, sobre los 198,505 puntos con GPS que el teléfono sincroniza:
 * hay una celda de 12 m donde **381 clientes distintos**, de **30 cobradores
 * distintos**, tienen mediciones. No es la puerta de ninguno de los 381: es la
 * tienda. Y hay otra con **48 clientes de un solo cobrador**, de las cuales el
 * **85.8 % son transferencias** — la casa de ese cobrador, metiendo
 * transferencias desde ahí.
 *
 * Sin esta detección, **el 6.4 % de los clientes** (558 de 8,689 medidos)
 * tendría su grupo mayor rotulado "la puerta" señalando un lugar donde no vive.
 *
 * ## Qué NO hace, y es una decisión del dueño
 *
 * **No esconde nada y no borra nada.** Marca. Un punto compartido se dibuja
 * igual que los demás, porque el dato es cierto —ahí se cobró— y a veces es
 * justo lo que el cobrador necesita saber. Lo único que se le niega es el título
 * de "la puerta".
 *
 * **Y no nombra el lugar.** No sabe qué es la tienda ni dónde vive ningún
 * cobrador, y no lleva ninguna coordenada cableada: es genérico por
 * construcción. Lo que publica son dos cuentas —clientes y cobradores— y con
 * eso la pantalla arma una frase que es cierta. Ponerle nombre sería inventar,
 * y hay un tercer grupo (50 celdas medidas: un cobrador, cero transferencias)
 * que **no se puede distinguir** entre el punto fijo de ese cobrador y una
 * vecindad de verdad. Ahí callar es lo correcto.
 *
 * ## Por qué el umbral de [MINIMO_DE_CLIENTES] es seguro en el teléfono
 *
 * El teléfono sólo tiene **su zona**, no las 42 de la empresa, así que cuenta
 * menos clientes por punto que el servidor. Eso **no lo hace equivocarse**: sus
 * clientes *son* clientes de la empresa, de modo que **un ≥5 local es un ≥5
 * real**. Lo único que un umbral local puede perder es alcance, nunca certeza.
 *
 * Medido el 2026-09-24 contra la vista completa del servidor:
 *
 * | umbral local | puntos compartidos que alcanza a ver |
 * |---|---|
 * | 3 | 100 % |
 * | **5** | **97 %** |
 * | 8 | 92 % |
 * | 10 | 86 % |
 *
 * **Subirlo a 10 pierde el 14 % de los puntos compartidos** y no compra
 * exactitud, porque no había ninguna que comprar. Quien lo cambie debería
 * leer esto primero.
 *
 * ## Por qué una rejilla de [LADO_DE_LA_CELDA_M] metros
 *
 * Es la escala del error del GPS medido (mediana de 8 m al centro del grupo):
 * dos mediciones de la misma puerta caen en la misma celda o en una vecina, y
 * dos casas distintas no. Se consulta la celda **y sus ocho vecinas**, así que
 * un punto junto a la frontera no se pierde el conteo del otro lado.
 *
 * Un primer intento usó vecindarios de 90 m y dio que el **82 %** de los puntos
 * eran "compartidos" — basura: a esa escala una colonia densa parece un punto
 * caliente. El número válido es el de 12 m, con el que los puntos compartidos
 * son el **4.21 %**. Se deja escrito porque el número equivocado era el cómodo.
 */
class IndiceDePuntosCompartidos private constructor(
    private val clientesPorCelda: Map<Celda, Set<Int>>,
    private val cobradoresPorCelda: Map<Celda, Set<String>>
) {

    /** La coordenada entera de una celda de la rejilla. */
    data class Celda(val fila: Int, val columna: Int)

    /**
     * Cuántos **clientes distintos** tienen mediciones en la celda de [punto] o
     * en cualquiera de sus ocho vecinas.
     */
    fun clientesQueComparten(punto: Punto): Int = vecindario(punto)
        .flatMapTo(mutableSetOf()) { clientesPorCelda[it].orEmpty() }
        .size

    /** Lo mismo, para cobradores distintos. */
    fun cobradoresQueComparten(punto: Punto): Int = vecindario(punto)
        .flatMapTo(mutableSetOf()) { cobradoresPorCelda[it].orEmpty() }
        .size

    /**
     * `true` cuando [punto] cae donde [MINIMO_DE_CLIENTES] o más clientes
     * distintos también tienen mediciones.
     */
    fun esCompartido(punto: Punto): Boolean = clientesQueComparten(punto) >= MINIMO_DE_CLIENTES

    private fun vecindario(punto: Punto): List<Celda> {
        val centro = celdaDe(punto)
        return buildList(VECINAS) {
            for (dFila in -1..1) {
                for (dCol in -1..1) {
                    add(Celda(centro.fila + dFila, centro.columna + dCol))
                }
            }
        }
    }

    companion object {
        /**
         * **Cinco clientes distintos.** Ver el KDoc de la clase: con este umbral
         * el teléfono ve el 97 % de los puntos compartidos que ve el servidor, y
         * nunca cuenta de más.
         */
        const val MINIMO_DE_CLIENTES: Int = 5

        /**
         * **Doce metros de lado.** La escala del error del GPS medido, no un
         * número redondo. Ver el KDoc de la clase.
         */
        const val LADO_DE_LA_CELDA_M: Double = 12.0

        private const val VECINAS = 9

        /**
         * Arma el índice con **todos** los puntos que el teléfono tiene de su
         * zona — no sólo los del cliente que se está mirando. Ésa es justamente
         * la información que hace posible la detección: un punto sólo se puede
         * saber compartido mirando a los demás clientes.
         */
        fun de(puntos: Iterable<PuntoDeLaRuta>): IndiceDePuntosCompartidos {
            val clientes = mutableMapOf<Celda, MutableSet<Int>>()
            val cobradores = mutableMapOf<Celda, MutableSet<String>>()
            for (p in puntos) {
                val celda = celdaDe(p.punto)
                clientes.getOrPut(celda) { mutableSetOf() }.add(p.clienteId)
                cobradores.getOrPut(celda) { mutableSetOf() }.add(p.cobrador)
            }
            return IndiceDePuntosCompartidos(clientes, cobradores)
        }

        /**
         * La celda de un punto.
         *
         * El paso en longitud se divide entre `cos(latitud)` porque un grado de
         * longitud **encoge** al alejarse del ecuador: medido a la latitud de la
         * ruta (18.4°), 0.001° de longitud son 105.5 m contra los 111.19 m que
         * miden 0.001° de latitud. Sin esa corrección las celdas serían
         * rectángulos y el mismo umbral significaría distancias distintas en cada
         * eje.
         */
        internal fun celdaDe(punto: Punto): Celda {
            val gradosPorMetro = 1.0 / (Punto.RADIO_DE_LA_TIERRA_M * Math.PI / GRADOS_MEDIA_VUELTA)
            val pasoLat = LADO_DE_LA_CELDA_M * gradosPorMetro
            val cosLat = cos(Math.toRadians(punto.lat)).coerceAtLeast(MINIMO_COSENO)
            val pasoLon = pasoLat / cosLat
            return Celda(
                fila = floor(punto.lat / pasoLat).toInt(),
                columna = floor(punto.lon / pasoLon).toInt()
            )
        }

        private const val GRADOS_MEDIA_VUELTA = 180.0
        private const val MINIMO_COSENO = 1e-9
    }
}
