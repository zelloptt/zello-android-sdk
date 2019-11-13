package com.zello.channel.sdk

import android.content.Context
import android.os.Handler
import com.zello.channel.sdk.image.ImageMessageManager
import com.zello.channel.sdk.image.ImageMessageManagerImpl
import com.zello.channel.sdk.platform.AudioReceiver
import com.zello.channel.sdk.platform.AudioReceiverEvents
import com.zello.channel.sdk.platform.AudioSource
import com.zello.channel.sdk.platform.AudioSourceEvents
import com.zello.channel.sdk.platform.CustomAudioReceiver
import com.zello.channel.sdk.platform.CustomAudioSource
import com.zello.channel.sdk.platform.Decoder
import com.zello.channel.sdk.platform.DecoderOpus
import com.zello.channel.sdk.platform.Encoder
import com.zello.channel.sdk.platform.EncoderOpus
import com.zello.channel.sdk.platform.PlayerSpeaker
import com.zello.channel.sdk.platform.RecorderMicrophone
import com.zello.channel.sdk.transport.TransportFactory
import com.zello.channel.sdk.transport.WebSocketsTransportFactory

/**
 * Android-specific session context implementation.
 */
internal class SessionContextAndroid(context: Context) : SessionContext {

	private val context: Context = context.applicationContext
	private var logger: SessionLogger = SessionLoggerNull()
	private var handler: Handler = Handler()

	override val transportFactory: TransportFactory = WebSocketsTransportFactory()
	override val imageMessageManager: ImageMessageManager = ImageMessageManagerImpl()

	override fun loadNativeLibraries(logger: SessionLogger?): Boolean {
		return loadLib("opus", logger) && loadLib("util", logger)
	}

	private fun loadLib(name: String, logger: SessionLogger?): Boolean {
		try {
			System.loadLibrary("embeddable.zello.sdk.$name")
			return true
		} catch (t: Throwable) {
			logger?.logError("Failed to load $name module", t)
		}

		return false
	}

	override fun createAudioSource(configuration: OutgoingVoiceConfiguration?, audioEventHandler: AudioSourceEvents, stream: OutgoingVoiceStream): AudioSource {
		if (configuration == null) {
			return RecorderMicrophone(context, logger, audioEventHandler)
		}

		return CustomAudioSource(configuration, stream, audioEventHandler)
	}

	override fun createEncoder(): Encoder {
		return EncoderOpus(context, logger)
	}

	override fun createAudioReceiver(configuration: IncomingVoiceConfiguration?, receiverEventHandler: AudioReceiverEvents, stream: IncomingVoiceStream): AudioReceiver {
		if (configuration == null) {
			return PlayerSpeaker(context, logger, receiverEventHandler)
		}

		return CustomAudioReceiver(configuration, this, receiverEventHandler, stream)
	}

	override fun createDecoder(): Decoder {
		return DecoderOpus(context, logger)
	}

	override fun setLogger(logger: SessionLogger?) {
		this.logger = logger ?: SessionLoggerNull()
	}

	override fun getLogger(): SessionLogger = logger

	override fun runOnUiThread(run: Runnable) {
		handler.post(run)
	}

	override fun runOnUiThread(run: Runnable, delayMillis: Long) {
		handler.postDelayed(run, delayMillis)
	}

}
