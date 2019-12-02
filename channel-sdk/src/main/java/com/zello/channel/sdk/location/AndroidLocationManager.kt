package com.zello.channel.sdk.location

import android.annotation.SuppressLint
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.os.Looper

/**
 * Wrapper interface for Android's location manager
 */
internal interface AndroidLocationManager {
	fun getBestProvider(criteria: Criteria, enabledOnly: Boolean): String?

	fun getLastKnownLocation(provider: String): Location?

	fun requestSingleUpdate(provider: String, listener: LocationListener, looper: Looper?)
}

internal class AndroidLocationManagerImpl(private val locationManager: android.location.LocationManager) : AndroidLocationManager {
	override fun getBestProvider(criteria: Criteria, enabledOnly: Boolean): String? {
		return locationManager.getBestProvider(criteria, enabledOnly)
	}

	// We only call this method if the permission has been granted. Check is upstream.
	@SuppressLint("MissingPermission")
	override fun getLastKnownLocation(provider: String): Location? {
		return locationManager.getLastKnownLocation(provider)
	}

	// We only call this method if the permission has been granted. Check is upstream.
	@SuppressLint("MissingPermission")
	override fun requestSingleUpdate(provider: String, listener: LocationListener, looper: Looper?) {
		locationManager.requestSingleUpdate(provider, listener, looper)
	}
}
