package com.zello.channel.sdk

import com.zello.channel.sdk.transport.Transport
import com.zello.channel.sdk.transport.TransportEvents
import com.zello.channel.sdk.transport.TransportSendAck
import org.json.JSONObject

internal class TestTransport: Transport {
	var connectCalled: Boolean = false
		private set

	var eventListener: TransportEvents? = null
		private set
	var connectedAddress: String? = null
		private set
	var sentCommand: String? = null
		private set
	var sentJson: JSONObject? = null
		private set
	var responseHandler: TransportSendAck? = null
		private set

	@Throws(SessionConnectErrorException::class)
	override fun connect(events: TransportEvents, address: String, requestTimeoutSec: Long) {
		eventListener = events
		connectedAddress = address
		connectCalled = true
	}

	override fun disconnect() { }

	override fun send(command: String, json: JSONObject, ack: TransportSendAck?) {
		sentCommand = command
		sentJson = json
		responseHandler = ack
	}

	override fun sendVoiceStreamData(streamId: Int, data: ByteArray) { }

	var sentImageId: Int? = null
		private set
	var sentThumbnailId: Int? = null
		private set
	var sentImageData: ByteArray? = null
		private set
	var sentThumbnailData: ByteArray? = null
		private set
	override fun sendImageData(imageId: Int, tag: Int, data: ByteArray) {
		if (tag == 1) {
			sentImageId = imageId
			sentImageData = data
		} else if (tag == 2) {
			sentThumbnailId = imageId
			sentThumbnailData = data
		}
	}
}
