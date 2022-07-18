package com.zello.channel.sdk.platform

import android.os.Handler
import android.os.Looper

internal interface HandlerFactory {
	fun handler(callback: Handler.Callback): Handler
}

internal class HandlerFactoryImpl : HandlerFactory {
	override fun handler(callback: Handler.Callback): Handler = Handler(Looper.myLooper() ?: Looper.getMainLooper(), callback)
}
