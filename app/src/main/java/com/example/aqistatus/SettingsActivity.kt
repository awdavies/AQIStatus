package com.example.aqistatus

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat
import androidx.work.*
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter

private const val TAG = "AqiStatus"
private var locationMutex = Mutex()
private var lastLocation: Location? = null

private const val PURPLE_AIR_READ_INTERVAL_MS: Long = 60000L

private suspend fun writeLocation(newLocation: Location) {
    locationMutex.withLock {
        lastLocation = newLocation
    }
}

private suspend fun readLocation(): Location? {
    locationMutex.withLock {
        return lastLocation
    }
}

class SettingsActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                Log.d(TAG, "Firing location result callback.")
                locationResult ?: return
                val location = locationResult.lastLocation
                Log.i(
                    TAG,
                    "Acquired location: LAT: "
                            + location.latitude.toString()
                            + " LON: "
                            + location.longitude.toString()
                            + "."
                )
                runBlocking {
                    writeLocation(location)
                }
            }
        }
        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionMap: Map<String, Boolean> ->
                if (!permissionMap.values.fold(true) { acc, element -> acc && element }) {
                    Log.w(TAG, "Some permissions missing. Cannot operate normally.")
                }
            }
        val permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ).toTypedArray()
        when {
            permissions.fold(true) { acc, permission ->
                acc && ContextCompat.checkSelfPermission(
                    applicationContext,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            } -> {
                Log.d(TAG, "All location permissions granted")
                startLocationLoop()
            }
            permissions.fold(true) { acc, permission -> acc && this.shouldShowRequestPermissionRationale(permission) } -> {
                Log.i(TAG, "TODO: Need to explain why we don't have permissions.")
            }
            else -> {
                Log.d(TAG, "Running permission request dialogue")
                requestPermissionLauncher.launch(permissions)
            }
        }
        val aqiWorkerRequest = OneTimeWorkRequestBuilder<AqiFetcherWorker>().build()
        WorkManager.getInstance(applicationContext).enqueue(aqiWorkerRequest)
    }

    override fun onDestroy() {
        Log.d(TAG, "Shutting down")
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        WorkManager.getInstance(applicationContext).cancelAllWork()
    }

    private fun startLocationLoop() {
        fusedLocationClient.requestLocationUpdates(
            LocationRequest.create().setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(30000)
                .setFastestInterval(25000),
            locationCallback,
            Looper.getMainLooper()
        )
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
        }
    }

    class AqiFetcherWorker(appContext: Context, workerParams: WorkerParameters) :
        CoroutineWorker(appContext, workerParams) {

        private val httpQueue: RequestQueue by lazy {
            Volley.newRequestQueue(applicationContext)
        }

        private val notificationManager: NotificationManager by lazy {
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as
                    NotificationManager
        }

        override suspend fun doWork(): Result {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createChannel()
            }
            setForeground(foregroundInfo("PENDING..."))
            while (true) {
                val location = readLocation()
                if (location != null) {
                    val req = JsonObjectRequest(Request.Method.GET,
                        PurpleAirUrl(location).squareMileQueryString(4),
                        null,
                        Response.Listener<JSONObject> { response ->
                            Log.d(TAG, "Received HTTP response from PurpleAir")
                            val sensorQueue = SensorQueue.create(location.latitude, location.longitude, response)
                            sensorQueue ?: return@Listener
                            val nearestSensors = sensorQueue.nearestSensors(30)
                            val average = nearestSensors.sumBy { s -> s.aqi } / nearestSensors.size
                            GlobalScope.launch {
                                setForeground(foregroundInfo("Average AQI is $average: ${aqiToDescription(average)}"))
                            }
                        },
                        Response.ErrorListener { error ->
                            val sw = StringWriter()
                            val pw = PrintWriter(sw)
                            error.printStackTrace(pw)
                            Log.e(TAG, "Error from HTTP request: $sw")
                        })
                    req.tag = TAG
                    httpQueue.add(req)
                }
                delay(PURPLE_AIR_READ_INTERVAL_MS)
            }
        }

        private fun foregroundInfo(placeholder: String): ForegroundInfo {
            val channelId = applicationContext.getString(R.string.notification_channel_id)
            val title = applicationContext.getString(R.string.notification_title)
            val notification = NotificationCompat.Builder(applicationContext, channelId)
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(placeholder)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .build()
            return ForegroundInfo(R.string.notification_channel_id, notification)
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
    }
}