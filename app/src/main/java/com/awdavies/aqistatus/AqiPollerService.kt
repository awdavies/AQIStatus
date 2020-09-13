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

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Handler
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
import kotlinx.coroutines.Runnable
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "AqiPoller"
private const val WIGGLE_ROOM_MS = 10000L

class AqiPollerService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val purpleAirPoller: VolleyWrapper by lazy {
        VolleyWrapper(Volley.newRequestQueue(applicationContext), this)
    }
    private val notificationManager: NotificationManager by lazy {
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager
    }
    private val locationPollingMinutes: Long = 1
    private var pollFrequencyMinutes: Long = 1
    private lateinit var lastLocation: Location
    private var httpLoopStarted: Boolean = false

    private fun startHttpLoop() {
        if (httpLoopStarted) {
            return
        }
        httpLoopStarted = true
        purpleAirPoller.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Starting AQI Poller")
        super.onStartCommand(intent, flags, startId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel()
        }
        pollFrequencyMinutes = intent?.getIntExtra(getString(R.string.polling_key), 1)?.toLong() ?: 1
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                Log.d(TAG, "Firing location result callback.")
                locationResult ?: return
                lastLocation = locationResult.lastLocation
                Log.d(TAG, "Got location: LAT: ${lastLocation.latitude} LON: ${lastLocation.longitude}.")
                startHttpLoop()
            }
        }
        startForeground(R.string.notification_channel_id, foregroundInfo(-1))
        startLocationLoop()
        return START_STICKY_COMPATIBILITY
    }

    override fun onDestroy() {
        Log.d(TAG, "Shutting down AQI Poller")
        fusedLocationClient.removeLocationUpdates(locationCallback)
        purpleAirPoller.stop()
        super.onDestroy()
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
        val interval = TimeUnit.MINUTES.toMillis(locationPollingMinutes)
        fusedLocationClient.requestLocationUpdates(
            LocationRequest.create().setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setInterval(interval + WIGGLE_ROOM_MS)
                .setFastestInterval(interval - WIGGLE_ROOM_MS),
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun versionExceptionForegroundInfo(e: VersionException): Notification {
        val channelId = applicationContext.getString(R.string.notification_channel_id)
        val description = "PurpleAir version: '${e.got}'. Our version: '${e.want}'"
        val title = applicationContext.getString(R.string.version_error_title)
        return foregroundInfoHelper(title, description, channelId)
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
        return foregroundInfoHelper(title, aqiDescription, channelId)
    }

    private fun foregroundInfoHelper(title: String, description: String, channelId: String): Notification {
        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(description)
            .setSmallIcon(R.drawable.ic_stat_cloud_queue)
            .setOngoing(true)
            .setWhen(System.currentTimeMillis())
        if (this::lastLocation.isInitialized) {
            val nextIntent = Intent(Intent.ACTION_VIEW)
            nextIntent.data = Uri.parse(PurpleAirUrl(lastLocation).intentUrl())
            val pendingIntent = TaskStackBuilder.create(applicationContext).run {
                addNextIntentWithParentStack(nextIntent)
                getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
            }
            builder.addAction(
                R.drawable.common_google_signin_btn_text_dark_normal,
                getString(R.string.purple_air_open_btn),
                pendingIntent
            )
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

    class VolleyWrapper(private val queue: RequestQueue, private val poller: AqiPollerService) {
        private var numberOfErrorAttempts: Long = 0
        private var lastRequestFailed: Boolean = false
        private var errorRetryMs: Long = 30000
        private val errorRetryMax: Long = 3
        private val errorRetryFactor: Float = 1.5f
        private val handler: Handler by lazy { Handler(Looper.getMainLooper()) }

        private fun backoffReset() {
            numberOfErrorAttempts = 0
            lastRequestFailed = false
            errorRetryMs = 30000
        }

        fun start() {
            queue.addRequestFinishedListener(RequestQueue.RequestFinishedListener<JsonObjectRequest> {
                if (lastRequestFailed && numberOfErrorAttempts < errorRetryMax) {
                    Log.d(TAG, "Last request failed, doing backoff reattempt.")
                    handler.postDelayed(Runnable {
                        queue.add(makeRequest())
                    }, errorRetryMs)
                    errorRetryMs = (errorRetryMs * errorRetryFactor).toLong()
                    numberOfErrorAttempts++
                    if (numberOfErrorAttempts >= errorRetryMax) {
                        Log.d(
                            TAG,
                            "Reached max number of retries. Resuming normal behavior after this next attempt fires."
                        )
                    }
                } else {
                    Log.d(TAG, "Posting delayed poller (normal behavior).")
                    handler.postDelayed(Runnable {
                        queue.add(makeRequest())
                    }, TimeUnit.MINUTES.toMillis(poller.pollFrequencyMinutes))
                }
            })
            queue.add(makeRequest())
        }

        fun stop() {
            queue.cancelAll(TAG)
            handler.removeCallbacksAndMessages(null)
        }

        private fun makeRequest(): JsonObjectRequest {
            val req = JsonObjectRequest(
                Request.Method.GET,
                PurpleAirUrl(poller.lastLocation).squareMileQueryString(4),
                null,
                Response.Listener<JSONObject> { response ->
                    Log.d(TAG, "Received HTTP response from PurpleAir.")
                    backoffReset()
                    try {
                        val sensorQueue =
                            SensorQueue.create(poller.lastLocation.latitude, poller.lastLocation.longitude, response)
                        sensorQueue ?: return@Listener
                        val nearestSensors = sensorQueue.nearestSensors(100)
                        val average = nearestSensors.sumBy { s -> s.aqi } / nearestSensors.size
                        poller.notificationManager.notify(
                            R.string.notification_channel_id,
                            poller.foregroundInfo(average)
                        )
                    } catch (e: VersionException) {
                        poller.notificationManager.notify(
                            R.string.notification_channel_id,
                            poller.versionExceptionForegroundInfo(e)
                        )
                    }
                },
                Response.ErrorListener { error ->
                    lastRequestFailed = true
                    Log.d(TAG, "Error from HTTP request.", error)
                })
            req.tag = TAG
            return req
        }
    }
}