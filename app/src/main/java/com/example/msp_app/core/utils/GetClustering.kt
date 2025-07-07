package com.example.msp_app.core.utils

import com.example.msp_app.data.models.payment.PaymentLocationsGroup
import org.apache.commons.math3.ml.clustering.DBSCANClusterer
import org.apache.commons.math3.ml.clustering.DoublePoint
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Coord(val lat: Double, val lng: Double)

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

fun sortGroupsByClosestCentroid(
    groups: List<PaymentLocationsGroup>,
    currentPosition: Coord
): List<Triple<Int, Coord, Long>> {
    return groups.map { group ->
        val (closestCoord, minDist) = group.locations
            .map { loc ->
                val coord = Coord(loc.LAT, loc.LNG)
                coord to haversineDistance(coord, currentPosition)
            }
            .minByOrNull { it.second }
            ?: return@map Triple(group.saleId, Coord(0.0, 0.0), Long.MAX_VALUE)

        Triple(group.saleId, closestCoord, minDist.toLong())
    }.sortedBy { it.third }
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
    return 6371e3 * c  // metros
}
