package com.zello.channel.sdk

/**
 * Describes why the session disconnected and is attempting to reconnect
 */
enum class ReconnectReason {
	/**
	 * The network has changed
	 */
	@Suppress("unused")
	NETWORK_CHANGED,

	/**
	 * Session was disconnected for another reason
	 */
	UNKNOWN
}
