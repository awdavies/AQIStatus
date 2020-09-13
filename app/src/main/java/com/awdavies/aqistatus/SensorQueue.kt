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
 *     along with AQI Status.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.awdavies.aqistatus

import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

private const val API_VERSION = "7.0.19"
private const val TAG = "SensorQueue"

class VersionException(val want: String, val got: String) : Exception()

private fun distanceFromPoint(
    latitude: Double,
    longitude: Double,
    otherLatitude: Double,
    otherLongitude: Double
): Double {
    // Blatantly copied from: https://www.geodatasource.com/developers/go
    val thisLatRads = (Math.PI * latitude) / 180.0
    val otherLatRads = (Math.PI * otherLatitude) / 180.0

    val theta = (longitude - otherLongitude)
    val thetaRads = (Math.PI * theta) / 180.0
    var dist =
        sin(thisLatRads) * sin(otherLatRads) + cos(thisLatRads) * cos(otherLatRads) * cos(thetaRads)
    if (dist > 1) {
        // TODO(awdavies): Show a debug error, or force making a SpherePoint impossible without a valid
        // lat/lon.
        dist = 1.0
    }
    dist = acos(dist)
    dist = dist * 180 / Math.PI
    return dist
}

class Sensor private constructor(
    val aqi: Int,
    private val distanceFromOrigin: Double
) : Comparable<Sensor> {
    companion object {
        fun create(latitude: Double, longitude: Double, json: JSONArray): Sensor? {
            return try {
                val pmZero = json.getDouble(2)
                val relativeHumidity = try {
                    json.getDouble(4)
                } catch (e: JSONException) {
                    0.0
                }
                val thisLatitude = json.getDouble(7)
                val thisLongitude = json.getDouble(8)
                val pmCorrect = epaCorrect(pmZero, relativeHumidity)
                Log.d(TAG, "PM2.5 EPA correction before: $pmZero, after: $pmCorrect, humidity: $relativeHumidity")
                Sensor(aqiFromPm25(pmCorrect), distanceFromPoint(thisLatitude, thisLongitude, latitude, longitude))
            } catch (e: JSONException) {
                null
            }
        }
    }

    override fun compareTo(other: Sensor): Int {
        return this.distanceFromOrigin.compareTo(other.distanceFromOrigin)
    }
}

class SensorQueue private constructor(private val sensors: PriorityQueue<Sensor>) {
    fun nearestSensors(n: Int): ArrayList<Sensor> {
        val res = ArrayList<Sensor>(n)
        for (i in 0 until sensors.size.coerceAtMost(n)) {
            res.add(sensors.elementAt(i))
        }
        return res
    }

    companion object {
        fun create(latitude: Double, longitude: Double, body: JSONObject): SensorQueue? {
            Log.d(TAG, "Parsing JSON")
            return try {
                val version = body.getString("version") ?: run {
                    Log.e(TAG, "No version found in data: '$body'")
                    return null
                }
                if (version != API_VERSION) {
                    Log.e(TAG, "API Version out of date. Received '$version', expected '$API_VERSION'")
                    throw VersionException(API_VERSION, version)
                }
                val data = body.getJSONArray("data") ?: run {
                    Log.e(TAG, "No data found in response: '$body'")
                    return null
                }
                val sensorArray = PriorityQueue<Sensor>(data.length())
                for (i in 0 until data.length()) {
                    val sensorJson = data.getJSONArray(i) ?: run {
                        Log.e(TAG, "Received null element from data array")
                        return null
                    }
                    if (sensorJson.getInt(5) != 0) {
                        Log.d(TAG, "Skipping ostensibly-indoor sensor.")
                        continue
                    }
                    val sensor = Sensor.create(latitude, longitude, sensorJson)
                    if (sensor == null) {
                        Log.d(TAG, "Unable to parse sensor from JSON: '$sensorJson'")
                        continue
                    }
                    sensorArray.add(sensor)
                }
                SensorQueue(sensorArray)
            } catch (e: JSONException) {
                Log.d(TAG, "Error parsing JSON")
                null
            }
        }
    }
}