package com.example.aqistatus

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

private const val TAG = "AqiPoller"
private const val LOCATION_POLL_INTERVAL = 65000L
private const val LOCATION_POLL_FASTEST = 55000L

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel()
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                Log.d(TAG, "Firing location result callback.")
                locationResult ?: return
                val location = locationResult.lastLocation
                Log.d(TAG, "Got location: LAT: ${location.latitude} LON: ${location.longitude}.")
                val req = JsonObjectRequest(
                    Request.Method.GET,
                    PurpleAirUrl(location).squareMileQueryString(4),
                    null,
                    Response.Listener<JSONObject> { response ->
                        Log.d(TAG, "Received HTTP response from PurpleAir")
                        val sensorQueue = SensorQueue.create(location.latitude, location.longitude, response)
                        sensorQueue ?: return@Listener
                        val nearestSensors = sensorQueue.nearestSensors(30)
                        val average = nearestSensors.sumBy { s -> s.aqi } / nearestSensors.size
                        Log.i(TAG, "Need to set foreground here: $average")
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
        fusedLocationClient.requestLocationUpdates(
            LocationRequest.create().setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(LOCATION_POLL_INTERVAL)
                .setFastestInterval(LOCATION_POLL_FASTEST),
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
        return NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(aqiDescription)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
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
