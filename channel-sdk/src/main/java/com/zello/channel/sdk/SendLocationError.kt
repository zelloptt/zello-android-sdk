package com.zello.channel.sdk

/**
 * Used to report errors encountered when sending location messages
 *
 * @property errorMessage a description of the error
 */
data class SendLocationError internal constructor(val errorMessage: String) {
	companion object {
		/**
		 * No location provider was available that matched the specified criteria
		 */
		@JvmStatic val NO_PROVIDER = "No providers for criteria"

		/**
		 * The location provider was unable to determine the current location
		 */
		@JvmStatic val NO_LOCATION = "Location could not be found"
	}
}

