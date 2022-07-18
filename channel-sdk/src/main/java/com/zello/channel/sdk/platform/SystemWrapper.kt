package com.zello.channel.sdk.platform

/**
 * Wraps System for testability.
 * Not for public use.
 * @suppress
 */
interface SystemWrapper {
	val currentTimeMillis: Long
}

/**
 * Not for public use.
 * @suppress
 */
class SystemWrapperImpl : SystemWrapper {
	override val currentTimeMillis: Long
		get() {
			return System.currentTimeMillis()
		}
}
