package com.zello.channel.sdk

/**
 * Used to report errors encountered when sending image messages
 *
 * @property errorMessage a description of the error
 */
class SendImageError internal constructor(errorMessage: String) : InformationalError(errorMessage)
