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