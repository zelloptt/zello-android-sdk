package com.zello.channel.sdk

/**
 * Encapsulates geographic coordinates and a reverse geocoded user-readable description of the
 * location.
 *
 * @property latitude The latitude in degrees
 * @property longitude The longitude in degrees
 * @property accuracy Sender's reported accuracy in meters
 * @property address Reverse geocoded location from the sender
 */
data class Location internal constructor(val latitude: Double, val longitude: Double, val accuracy: Double, val address: String? = null) {
	internal fun withAddress(address: android.location.Address): Location {
		println("(L) address: $address")
		val pretty = with(address) {
			(0..maxAddressLineIndex).map {
				println("(L) pretty line $it: ${getAddressLine(it)}")
				getAddressLine(it)
			}
		}.joinToString("\n")
		println("(L) Pretty address: $pretty")
		return Location(latitude, longitude, accuracy, pretty)
	}

	internal fun copyingAddressFrom(other: Location): Location {
		return Location(latitude, longitude, accuracy, other.address)
	}

	companion object {
		fun fromLocation(platformLocation: android.location.Location): Location {
			return Location(platformLocation.latitude, platformLocation.longitude, platformLocation.accuracy.toDouble())
		}
	}
}
