package com.zello.channel.sdk.transport

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

interface HttpClientFactory {
	fun client(timeout: Long, pingInterval: Long): OkHttpClient
}

class HttpClientFactoryImpl : HttpClientFactory {
	override fun client(timeout: Long, pingInterval: Long): OkHttpClient {
		return OkHttpClient.Builder().connectTimeout(timeout, TimeUnit.SECONDS).writeTimeout(timeout, TimeUnit.SECONDS).readTimeout(timeout, TimeUnit.SECONDS).pingInterval(pingInterval, TimeUnit.SECONDS).build()
	}
}
