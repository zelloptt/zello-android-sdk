package com.zello.channel.sdk

import org.json.JSONObject

/**
 * Error object representing a non-fatal error. Sent to the `SessionListener` when something goes
 * wrong that doesn't prevent future operations from proceeding correctly.
 *
 * @property errorMessage A description of the error
 */
open class InformationalError internal constructor(val errorMessage: String)

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

/**
 * Reported when the binary data received for an image message is invalid
 *
 * @property imageId the id of the image message with invalid data
 * @property errorMessage A description of the error
 */
class InvalidImageMessageError internal constructor(val imageId: Int, errorMessage: String) : InformationalError(errorMessage)
