package com.zello.channel.sdk.transport

internal interface TransportFactory {
	fun instantiate(): Transport
}

internal class WebSocketsTransportFactory: TransportFactory {
	override fun instantiate(): Transport {
		return TransportWebSockets()
	}
}
