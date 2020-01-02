package com.zello.channel.sdk.commands

import com.zello.channel.sdk.Location
import com.zello.channel.sdk.transport.Transport
import org.json.JSONObject

internal class CommandSendLocation(transport: Transport,
								   private val location: Location,
								   private val recipient: String? = null) : Command(transport, false) {
	override fun getCommandName(): String = "send_location"

	override fun getCommandBody(): JSONObject {
		val json = JSONObject()
		json.put(keyLatitude, location.latitude)
		json.put(keyLongitude, location.longitude)
		json.put(keyAccuracy, location.accuracy)
		val address = location.address
		if (address != null) {
			json.put(keyFormattedAddress, address)
		}
		if (recipient != null) {
			json.put(keyRecipient, recipient)
		}
		return json
	}

	override fun read(json: JSONObject) {
		// No response expected
	}

	override fun error() { }

}
