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
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

private const val TAG = "AqiStatus"
private const val PERMISSION_ID = 123

private fun startAqiPoller(ctx: Context?) {
    val pollFrequencyMinutes =
        PreferenceManager.getDefaultSharedPreferences(ctx).getString(ctx?.getString(R.string.polling_key), "1") ?: "1"
    Log.d(TAG, "Starting AQI poller with frequency of $pollFrequencyMinutes minutes.")
    var i = Intent(ctx, AqiPollerService::class.java)
    i.putExtra(ctx?.getString(R.string.polling_key), Integer.parseInt(pollFrequencyMinutes))
    ctx?.startService(i)
}

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        actionBar?.setDisplayHomeAsUpEnabled(true)
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
                startAqiPoller(this)
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        moveTaskToBack(true)
        return super.onOptionsItemSelected(item)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                moveTaskToBack(true)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
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
                    Log.d(TAG, "Should attempt to start service")
                    startAqiPoller(this)
                }
            }
            else -> {
                // Ignored.
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Shutting down")
        stopService(Intent(this, AqiPollerService::class.java))
        super.onDestroy()
    }

    class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onDestroy() {
            preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
            super.onDestroy()
        }

        override fun onSharedPreferenceChanged(preferences: SharedPreferences?, key: String?) {
            when (key) {
                getString(R.string.polling_key) -> {
                    // If changing the polling frequency, restart the poller (if this happens too frequently,
                    // there might need to be some code that checks when the rate limiter says to query again).
                    Log.d(TAG, "Settings changed. Attempting to restart AQI Poller service")
                    val pollFrequencyMinutes = Integer.parseInt(preferences?.getString(key, "1") ?: "1")
                    Log.d(TAG, "Poll frequency updated to $pollFrequencyMinutes minutes")
                    val ctx = activity?.applicationContext
                    ctx?.stopService(Intent(ctx, AqiPollerService::class.java))
                    startAqiPoller(ctx)
                }
                else -> {
                    // Do nothin.
                }
            }
        }
    }
}