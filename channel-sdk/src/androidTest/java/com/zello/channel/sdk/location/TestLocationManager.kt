package com.zello.channel.sdk.location

import android.location.Criteria
import android.location.LocationListener
import android.os.Looper

internal class TestLocationManager : AndroidLocationManager {
	var bestProvider: String? = null
	override fun getBestProvider(criteria: Criteria, enabledOnly: Boolean): String? {
		return bestProvider
	}

	var lastKnownLocation: android.location.Location? = null
	override fun getLastKnownLocation(provider: String): android.location.Location? {
		return lastKnownLocation
	}

	var singleLocationRequested: Boolean = false
	var requestedProvider: String? = null
	var locationListener: LocationListener? = null
	override fun requestSingleUpdate(provider: String, listener: LocationListener, looper: Looper?) {
		singleLocationRequested = true
		requestedProvider = provider
		locationListener = listener
	}
}
