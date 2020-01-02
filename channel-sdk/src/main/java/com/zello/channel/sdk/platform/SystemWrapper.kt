package com.zello.channel.sdk.platform

/**
 * Wraps System for testability
 */
interface SystemWrapper {
	val currentTimeMillis: Long
}

class SystemWrapperImpl : SystemWrapper {
	override val currentTimeMillis: Long
		get() {
			return System.currentTimeMillis()
		}
}
