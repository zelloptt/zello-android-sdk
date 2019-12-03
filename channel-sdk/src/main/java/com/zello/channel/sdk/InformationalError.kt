package com.zello.channel.sdk

import org.json.JSONObject

/**
 *
 */
open class InformationalError internal constructor(val errorMessage: String) {
	companion object {
		@JvmStatic val INVALID_MESSAGE_RECEIVED = "Invalid Message Format"
	}
}

/**
 * Reported when the session receives a message with an invalid format from the channel. The `key`
 * property specifies the payload key that is missing or invalid, and `errorMessage` contains details
 * about the error.
 *
 * @property command The name of the invalid message
 * @property key The key that is missing or invalid in the payload
 * @property payload The original message payload
 * @property errorMessage A description of the error
 */
class InvalidMessageFormatError internal constructor(val command: String, val key: String, val payload: JSONObject, errorMessage: String) : InformationalError(errorMessage)

