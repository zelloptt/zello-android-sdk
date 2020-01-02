package com.zello.channel.sdk.commands

import com.zello.channel.sdk.OutgoingVoiceStreamError
import com.zello.channel.sdk.platform.Utils
import com.zello.channel.sdk.transport.Transport
import org.json.JSONObject

internal abstract class CommandStartVoiceStream(
		transport: Transport,
		private val codec: String?,
		private val codecHeader: ByteArray?,
		private val packetDuration: Int,
		private val recipient: String?) : Command(transport, true) {

	abstract fun onSuccess(streamId: Int)

	abstract fun onFailure(error: OutgoingVoiceStreamError)

	override fun read(json: JSONObject) {
		parseSimpleResponse(json)
		if (succeeded) {
			val streamId = json.optInt(keyStreamId, -1)
			if (streamId >= 0) {
				onSuccess(streamId)
				return
			}
		}
		val errorText = json.optString(keyError, "")
		val errorCode: OutgoingVoiceStreamError
		errorCode = when (errorText) {
			valBusy, errorChannelNotReady -> OutgoingVoiceStreamError.BUSY
			errorListenOnlyConnection -> OutgoingVoiceStreamError.LISTEN_ONLY
			errorFailedToStartStream -> OutgoingVoiceStreamError.FAILED_TO_START
			else -> OutgoingVoiceStreamError.BAD_RESPONSE
		}
		onFailure(errorCode)
	}

	override fun error() {
		onFailure(OutgoingVoiceStreamError.NO_RESPONSE)
	}

	override fun getCommandName(): String {
		return commandStartStream
	}

	override fun getCommandBody(): JSONObject {
		val json = JSONObject()
		json.put(keyType, valAudio)
		json.put(keyCodec, codec)
		json.put(keyCodecHeader, Utils.encodeBase64(codecHeader))
		json.put(keyPacketDuration, packetDuration)
		if (recipient != null) {
			json.put(keyRecipient, recipient)
		}
		return json
	}

}
