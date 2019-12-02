package com.zello.channel.sdk.location

import android.content.Context

internal interface GeocoderFactory {
	val isGeocoderPresent: Boolean

	fun geocoder(): Geocoder
}

internal class GeocoderFactoryImpl(val context: Context) : GeocoderFactory {
	override val isGeocoderPresent: Boolean
		get() {
			return android.location.Geocoder.isPresent()
		}

	override fun geocoder(): Geocoder {
		return AndroidGeocoder(android.location.Geocoder(context))
	}
}
