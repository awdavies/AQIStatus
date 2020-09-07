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

package com.example.aqistatus

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "AqiPoller"
private const val WIGGLE_ROOM_MS = 10000L

class AqiPollerService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val httpQueue: RequestQueue by lazy {
        Volley.newRequestQueue(applicationContext)
    }
    private val notificationManager: NotificationManager by lazy {
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager
    }
    private var pollFrequencyMinutes: Int = 1
    private lateinit var lastLocation: Location

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel()
        }
        pollFrequencyMinutes = intent?.getIntExtra(getString(R.string.polling_key), 1) ?: 1
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                Log.d(TAG, "Firing location result callback.")
                locationResult ?: return
                lastLocation = locationResult.lastLocation
                Log.d(TAG, "Got location: LAT: ${lastLocation.latitude} LON: ${lastLocation.longitude}.")
                val req = JsonObjectRequest(
                    Request.Method.GET,
                    PurpleAirUrl(lastLocation).squareMileQueryString(4),
                    null,
                    Response.Listener<JSONObject> { response ->
                        Log.d(TAG, "Received HTTP response from PurpleAir")
                        val sensorQueue = SensorQueue.create(lastLocation.latitude, lastLocation.longitude, response)
                        sensorQueue ?: return@Listener
                        val nearestSensors = sensorQueue.nearestSensors(30)
                        val average = nearestSensors.sumBy { s -> s.aqi } / nearestSensors.size
                        notificationManager.notify(
                            R.string.notification_channel_id,
                            foregroundInfo(average)
                        )
                    },
                    Response.ErrorListener { error ->
                        Log.d(TAG, "Error from HTTP request", error)
                    })
                req.tag = TAG
                httpQueue.add(req)
            }
        }
        startForeground(R.string.notification_channel_id, foregroundInfo(-1))
        startLocationLoop()
        return START_STICKY_COMPATIBILITY
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        httpQueue.cancelAll(TAG)
    }

    private fun startLocationLoop() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Don't have location permissions. Giving up.")
            return
        }
        val interval = TimeUnit.MINUTES.toMillis(pollFrequencyMinutes.toLong())
        fusedLocationClient.requestLocationUpdates(
            LocationRequest.create().setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(interval + WIGGLE_ROOM_MS)
                .setFastestInterval(interval - WIGGLE_ROOM_MS),
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun foregroundInfo(aqi: Int): Notification {
        val channelId = applicationContext.getString(R.string.notification_channel_id)
        val aqiDescription = if (aqi < 0) {
            "Waiting for PurpleAir. . ."
        } else {
            aqiToDescription(aqi)
        }
        val aqiString = if (aqi < 0) {
            "N/A"
        } else {
            aqi.toString()
        }
        val title = "${applicationContext.getString(R.string.notification_title)}: $aqiString"
        var builder = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(aqiDescription)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
        if (aqi > 0) {
            val nextIntent = Intent(Intent.ACTION_VIEW)
            nextIntent.data = Uri.parse(PurpleAirUrl(lastLocation).intentUrl())
            val pendingIntent = TaskStackBuilder.create(applicationContext).run {
                addNextIntentWithParentStack(nextIntent)
                getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
            }
            builder.setContentIntent(pendingIntent)
        }
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel() {
        val name = applicationContext.getString(R.string.notification_channel_id)
        val descriptionText = applicationContext.getString(R.string.channel_description)
        val importance =
            NotificationManager.IMPORTANCE_LOW // TODO(awdavies): Figure out if this needs to be higher.
        val channel = NotificationChannel(name, descriptionText, importance)
        notificationManager.createNotificationChannel(channel)
    }

    @Nullable
    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
