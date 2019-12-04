package com.zello.channel.sdk.transport

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal interface HttpClientFactory {
	fun client(timeout: Long, pingInterval: Long): OkHttpClient
}

internal class HttpClientFactoryImpl : HttpClientFactory {
	override fun client(timeout: Long, pingInterval: Long): OkHttpClient {
		return OkHttpClient.Builder().connectTimeout(timeout, TimeUnit.SECONDS).writeTimeout(timeout, TimeUnit.SECONDS).readTimeout(timeout, TimeUnit.SECONDS).pingInterval(pingInterval, TimeUnit.SECONDS).build()
	}
}
