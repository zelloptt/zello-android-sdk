package com.zello.channel.sdk

data class SendLocationError internal constructor(val errorMessage: String) {
	companion object {
		@JvmStatic val NO_PROVIDER = "No providers for criteria"
		@JvmStatic val NO_LOCATION = "Location could not be found"
	}
}

