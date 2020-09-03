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

import android.location.Location

private const val DEGREE_TO_MILE_FACTOR: Double = 60 / 1.1515
private const val SURFACE_MILE_IN_DEGREES: Double = 1 / DEGREE_TO_MILE_FACTOR

class PurpleAirUrl(private val origin: Location) {
    fun squareMileQueryString(sqMiles: Int): String {
        val radius = (sqMiles.toDouble() / 4.0) * SURFACE_MILE_IN_DEGREES
        val nwLat = origin.latitude + radius
        val seLat = origin.latitude - radius
        val nwLon = origin.longitude + radius
        val seLon = origin.longitude - radius
        return "https://www.purpleair.com/data.json?opt=1/i/mAQI/a0/cC0&fetch=true&nwlat=$nwLat&selat=$seLat&nwlong=$nwLon&selong=$seLon&fields=pm_0"
    }
}