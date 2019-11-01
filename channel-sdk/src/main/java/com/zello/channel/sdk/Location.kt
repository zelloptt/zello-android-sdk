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
data class Location internal constructor(val latitude: Double, val longitude: Double, val accuracy: Double, val address: String? = null)
