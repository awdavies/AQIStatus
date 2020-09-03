package com.example.aqistatus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat

private const val TAG = "AqiStatus"
private const val PERMISSION_ID = 123

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
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
                startAqiPoller()
            }
            permissions.fold(true) { acc, permission -> acc && this.shouldShowRequestPermissionRationale(permission) } -> {
                // TODO(awdavies): Punt some info to the user.
                Log.i(TAG, "TODO: Need to explain why we don't have permissions.")
            }
            else -> {
                Log.d(TAG, "Running permission request dialogue")
                requestPermissions(permissions, PERMISSION_ID)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_ID -> {
                Log.d(TAG, "Permission callback is in")
                if (!grantResults.fold(true) { acc, element -> acc && element == PackageManager.PERMISSION_GRANTED }) {
                    // TODO(awdavies): Punt some info to the user.
                    Log.w(TAG, "Some permissions missing. Cannot operate normally.")
                } else {
                    Log.d(TAG, "Attempting to start service")
                    startAqiPoller()
                }
            }
            else -> {
                // Ignored.
            }
        }
    }

    private fun startAqiPoller() {
        startService(Intent(this, AqiPollerService::class.java))
    }

    override fun onDestroy() {
        Log.d(TAG, "Shutting down")
        super.onDestroy()
        stopService(Intent(this, AqiPollerService::class.java))
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
        }
    }
}