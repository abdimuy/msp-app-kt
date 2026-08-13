package com.example.msp_app.core.utils

import com.example.msp_app.core.common.location.SaleDistance
import com.example.msp_app.data.models.payment.PaymentLocationsGroup
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.apache.commons.math3.ml.clustering.DBSCANClusterer
import org.apache.commons.math3.ml.clustering.DoublePoint

data class Coord(val lat: Double, val lng: Double)

/**
 * Qué tan cerca está una venta de la posición actual del cobrador.
 *
 * [distance] es [SaleDistance.Unknown] cuando la venta no tiene centroide: con
 * `minPts = 3`, DBSCAN no produce ninguno hasta que la venta acumula tres pagos
 * georreferenciados y agrupados, así que toda venta nueva —o con pagos
 * dispersos— pasa por acá aunque el servidor mande coordenadas perfectas.
 */
data class SaleProximity(
    val saleId: Int,
    val distance: SaleDistance
)

fun computeCentroids(
    rawCoords: List<Pair<Double, Double>>,
    eps: Double = 0.0005,
    minPts: Int = 3
): List<Coord> {
    val points = rawCoords.map { (lat, lng) ->
        DoublePoint(doubleArrayOf(lat, lng))
    }
    val clusters = DBSCANClusterer<DoublePoint>(eps, minPts).cluster(points)
    return clusters.map { cluster ->
        val (sumLat, sumLng) = cluster.points.fold(0.0 to 0.0) { (accLat, accLng), p ->
            (accLat + p.point[0]) to (accLng + p.point[1])
        }
        Coord(sumLat / cluster.points.size, sumLng / cluster.points.size)
    }
}

/**
 * Ordena las ventas de la más cercana a la más lejana. Las que no tienen
 * centroide quedan al final: es [SaleDistance] quien lo garantiza al ordenar
 * [SaleDistance.Unknown] después de cualquier distancia real, sin necesidad de
 * un número centinela que después alguien pueda imprimir.
 */
fun sortGroupsByClosestCentroid(
    groups: List<PaymentLocationsGroup>,
    currentPosition: Coord
): List<SaleProximity> {
    return groups.map { group ->
        val minDist = group.locations
            .minOfOrNull { loc -> haversineDistance(Coord(loc.LAT, loc.LNG), currentPosition) }
            ?: return@map SaleProximity(group.saleId, SaleDistance.Unknown)

        SaleProximity(group.saleId, SaleDistance.of(minDist))
    }.sortedBy { it.distance }
}

private fun haversineDistance(a: Coord, b: Coord): Double {
    val toRad = { x: Double -> x * PI / 180 }
    val dLat = toRad(b.lat - a.lat)
    val dLng = toRad(b.lng - a.lng)
    val sinDLat = sin(dLat / 2)
    val sinDLng = sin(dLng / 2)
    val aHarv = sinDLat * sinDLat +
        cos(toRad(a.lat)) * cos(toRad(b.lat)) *
        sinDLng * sinDLng
    val c = 2 * atan2(sqrt(aHarv), sqrt(1 - aHarv))
    return 6371e3 * c // metros
}
