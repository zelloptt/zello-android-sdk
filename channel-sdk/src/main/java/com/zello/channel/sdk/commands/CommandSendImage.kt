package com.zello.channel.sdk.commands

import com.zello.channel.sdk.image.Dimensions
import com.zello.channel.sdk.transport.Transport
import org.json.JSONException
import org.json.JSONObject

internal abstract class CommandSendImage(transport: Transport,
										 private val imageDimensions: Dimensions,
										 private val imageDataLength: Int,
										 private val thumbnailDataLength: Int,
										 private val recipient: String? = null) : Command(transport = transport, requiresResponse = true) {

	abstract fun sendImageBody(imageId: Int)
	abstract fun onError(message: String?)

	override fun getCommandName(): String = commandSendImage

	override fun getCommandBody(): JSONObject {
		val json = JSONObject()
		json.put(keyType, "jpeg")
		json.put(keyThumbnailLength, thumbnailDataLength)
		json.put(keyImageLength, imageDataLength)
		json.put(keyImageWidth, imageDimensions.width)
		json.put(keyImageHeight, imageDimensions.height)
		if (recipient != null) {
			json.put(keyRecipient, recipient)
		}
		return json
	}

	override fun read(json: JSONObject) {
		println("(CSI) Received response $json")
		parseSimpleResponse(json)
		if (succeeded) {
			onSuccess(json)
		} else {
			onError(json.optString("error"))
		}
	}

	override fun error() { }

	private fun onSuccess(json: JSONObject) {
		try {
			val imageId = json.getInt(keyImageId)
			sendImageBody(imageId)
		} catch (e: JSONException) {
			error()
		}
	}

}