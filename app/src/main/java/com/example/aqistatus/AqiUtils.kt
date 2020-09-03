/*
 * Copyright (c) 2020. Andrew Davies <a.w.davies.vio@gmail.com>
 *
 *     This file is part of AQI Status
 *
 *     AQI Status is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     AQI Status is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Foobar.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.aqistatus

import kotlin.math.round

private class AqiTuple constructor(val aqi: Int, val pm25: Double)

private val AQI_TABLE = listOf(
    AqiTuple(0, 0.0),
    AqiTuple(51, 12.1),
    AqiTuple(101, 35.5),
    AqiTuple(151, 55.5),
    AqiTuple(201, 150.5),
    AqiTuple(301, 250.5),
    AqiTuple(501, 500.5)
).toTypedArray()

private fun aqiTupleFloor(pm: Double): AqiTuple {
    var res = AqiTuple(0, 0.0)
    for (tuple in AQI_TABLE) {
        if (tuple.pm25 > pm) {
            return res
        }
        res = tuple
    }
    return res
}

private fun aqiTupleCeil(pm: Double): AqiTuple {
    return try {
        AQI_TABLE.first { tuple -> tuple.pm25 > pm }
    } catch (_: NoSuchElementException) {
        AQI_TABLE.last()
    }
}

private fun pmFloor(pm: Double): Double {
    return aqiTupleFloor(pm).pm25
}

private fun pmCeil(pm: Double): Double {
    return aqiTupleCeil(pm).pm25
}

private fun aqiFloor(pm: Double): Int {
    return aqiTupleFloor(pm).aqi
}

private fun aqiCeil(pm: Double): Int {
    return aqiTupleCeil(pm).aqi
}

fun aqiFromPm25(pm25: Double): Int {
    val slope = ((pm25 - pmFloor(pm25)) * (aqiCeil(pm25) - aqiFloor(pm25))) / (pmCeil(pm25) - pmFloor(pm25))
    val intercept = aqiFloor(pm25)
    return round(slope + intercept).toInt()
}

fun lrapaCorrect(pm25: Double): Double {
    // See: http://lrapa.org/DocumentCenter/View/4147/PurpleAir-Correction-Summary
    return pm25*0.5 - 0.66
}

fun aqiToDescription(aqi: Int): String {
    return when {
        aqi >= 301 -> {
            "Hazardous"
        }
        aqi >= 201 -> {
            "Very Unhealthy"
        }
        aqi >= 151 -> {
            "Unhealthy"
        }
        aqi >= 101 -> {
            "Unhealthy for Sensitive Groups"
        }
        aqi >= 51 -> {
            "Moderate"
        }
        aqi >= 0 -> {
            "Good"
        }
        else -> {
            "N/A"
        }
    }
}