package com.zello.channel.sdk.commands

import com.zello.channel.sdk.transport.Transport
import org.json.JSONObject

internal class CommandSendText(transport: Transport, val message: String, val recipient: String? = null): Command(transport, false) {
	override fun getCommandName(): String = commandSendTextMessage

	override fun getCommandBody(): JSONObject {
		val json = JSONObject()
		json.put(keyTextMessageBody, message)
		if (recipient != null) {
			json.put(keyRecipient, recipient)
		}
		return json
	}

	override fun read(json: JSONObject) {
		// No response expected
	}

	override fun error() {
		// No response expected
	}
}
