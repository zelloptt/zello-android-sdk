package com.zello.channel.sdk.commands

import com.zello.channel.sdk.SessionConnectError
import com.zello.channel.sdk.platform.Utils
import com.zello.channel.sdk.transport.Transport
import org.json.JSONObject

internal abstract class CommandLogon(
		transport: Transport,
		private val authToken: String?,
		private val refreshToken: String?,
		private val username: String?,
		private val password: String?,
		private val channel: String?) : Command(transport, true) {

	override fun read(json: JSONObject) {
		parseSimpleResponse(json)
		if (succeeded) {
			onSuccess(json.optString(keyRefreshToken))
		} else {
			val error = when (this.error) {
				errorNotAuthorized, valInvalidPassword, valInvalidUsername -> {
					SessionConnectError.BAD_CREDENTIALS
				}
				else -> {
					SessionConnectError.BAD_RESPONSE(json)
				}
			}
			onFailure(error)
		}
	}

	override fun error() {
		onFailure(SessionConnectError.NO_RESPONSE)
	}

	abstract fun onSuccess(refreshToken: String?)

	abstract fun onFailure(error: SessionConnectError)

	override fun getCommandName(): String {
		return commandLogon
	}

	override fun getCommandBody(): JSONObject {
		val json = JSONObject()
		// We need an auth token and/or a refresh token
		if (authToken != null) {
			json.put(keyAuthToken, authToken)
		}
		if (refreshToken != null) {
			json.put(keyRefreshToken, refreshToken)
		}
		if (username != null) {
			json.put(keyUsername, username)
		}
		if (password != null) {
			json.put(keyPassword, password)
		}
		json.put(keyChannel, Utils.emptyIfNull(channel))
		return json
	}

}
