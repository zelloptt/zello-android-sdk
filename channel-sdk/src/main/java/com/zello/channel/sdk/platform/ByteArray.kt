package com.zello.channel.sdk.platform

internal val ByteArray.hexString: String
	get() {
		return this.joinToString("") { "%02x".format(it) }
	}
