package com.zello.channel.sdk.location

import android.content.Context
import android.location.Criteria
import android.location.LocationListener
import android.os.Bundle
import android.os.Looper
import com.zello.channel.sdk.Location
import com.zello.channel.sdk.SendLocationError
import com.zello.channel.sdk.SentLocationCallback
import com.zello.channel.sdk.commands.CommandSendLocation
import com.zello.channel.sdk.platform.SystemWrapper
import com.zello.channel.sdk.platform.SystemWrapperImpl
import com.zello.channel.sdk.transport.Transport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.math.min

internal interface LocationManager {
	fun sendLocation(transport: Transport, criteria: Criteria, callback: SentLocationCallback?) {
		sendLocation(transport, criteria, null, callback)
	}

	fun sendLocation(transport: Transport, criteria: Criteria, recipient: String?, callback: SentLocationCallback?)
}

internal class LocationManagerImpl(context: Context,
								   private val platformLocationManager: AndroidLocationManager,
								   private val system: SystemWrapper = SystemWrapperImpl(),
								   private val geocoderFactory: GeocoderFactory = GeocoderFactoryImpl(context),
								   private val backgroundScope: CoroutineScope = GlobalScope,
								   private val mainThreadDispatcher: CoroutineDispatcher = Dispatchers.Main,
								   private val immediateMainThreadDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate) : LocationManager {

	/// If the last location is older than five seconds, we'll discard it
	private val maxLastLocationLifespan = 5000

	private var lastLocationWithReverseGeocode: Location? = null
	private var lastReverseGeocodedLocation: android.location.Location? = null

	override fun sendLocation(transport: Transport, criteria: Criteria, recipient: String?, callback: SentLocationCallback?) {
		val provider = platformLocationManager.getBestProvider(criteria, true)
		if (provider == null) {
			callback?.onLocationSent(null, SendLocationError(SendLocationError.NO_PROVIDER))
			return
		}

		val lastLocation = platformLocationManager.getLastKnownLocation(provider)
		// If the last fix was less than five seconds ago, use it
		if (lastLocation != null && (system.currentTimeMillis - lastLocation.time <= maxLastLocationLifespan)) {
			performReverseGeocoding(transport, lastLocation, recipient, callback)
			return
		}

		// Request a new location fix from the system
		val listener = object: LocationListener {
			override fun onLocationChanged(gpsLocation: android.location.Location) {
				if (gpsLocation == null) {
					callback?.onLocationSent(null, SendLocationError(SendLocationError.NO_LOCATION))
					return
				}
				performReverseGeocoding(transport, gpsLocation, recipient, callback)
			}

			override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {
				// Don't care
			}

			override fun onProviderEnabled(p0: String) {
				// Don't care
			}

			override fun onProviderDisabled(p0: String) {
				// Retry with a new provider
				sendLocation(transport, criteria, recipient, callback)
			}
		}
		platformLocationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
	}

	private fun performReverseGeocoding(transport: Transport, location: android.location.Location, recipient: String?, callback: SentLocationCallback?) {
		if (!geocoderFactory.isGeocoderPresent) {
			val toSend = Location.fromLocation(location)
			CommandSendLocation(transport, toSend, recipient).send()
			callback?.onLocationSent(toSend, null)
			return
		}

		// Read last geocode properties on main thread?
		var lastGeocoded: android.location.Location? = null
		var lastReversed: Location? = null
		runBlocking(immediateMainThreadDispatcher) {
			lastGeocoded = lastReverseGeocodedLocation
			lastReversed = lastLocationWithReverseGeocode
		}
		val geocoded = lastGeocoded
		val reversed = lastReversed
		if (geocoded != null && location.overlaps(geocoded)) {
			if (reversed != null) {
				// Send the new coordinates with the old geocoding result
				val toSend = Location.fromLocation(location).copyingAddressFrom(reversed)
				CommandSendLocation(transport, toSend, recipient).send()
				callback?.onLocationSent(toSend, null)
				return
			}
		}

		backgroundScope.launch {
			val geocoder = geocoderFactory.geocoder()
			val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

			withContext(mainThreadDispatcher) {
				var toSend = Location.fromLocation(location)
				if (addresses.isNotEmpty()) {
					toSend = toSend.withAddress(addresses[0])
					lastReverseGeocodedLocation = location
					lastLocationWithReverseGeocode = toSend
				}
				CommandSendLocation(transport, toSend, recipient).send()
				callback?.onLocationSent(toSend, null)
			}
		}
	}

}

private fun android.location.Location.overlaps(other: android.location.Location): Boolean {
	val distance = distanceTo(other)
	return (distance <= min(accuracy, other.accuracy))
}
