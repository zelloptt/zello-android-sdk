package com.zello.channel.sdk.location

import android.location.Address

/**
 * Wrapper for android.location.Geocoder for testability
 */
internal interface Geocoder {
	fun getFromLocation(latitude: Double, longitude: Double, maxResults: Int): List<Address>
}

internal class AndroidGeocoder(private val wrapped: android.location.Geocoder) : Geocoder {
	override fun getFromLocation(latitude: Double, longitude: Double, maxResults: Int): List<Address> {
		return wrapped.getFromLocation(latitude, longitude, maxResults)
	}
}
