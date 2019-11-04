package com.zello.channel.sdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zello.channel.sdk.platform.AudioReceiver
import com.zello.channel.sdk.platform.AudioReceiverEvents
import com.zello.channel.sdk.platform.AudioSource
import com.zello.channel.sdk.platform.AudioSourceEvents
import com.zello.channel.sdk.platform.Decoder
import com.zello.channel.sdk.platform.DecoderListener
import com.zello.channel.sdk.platform.Encoder
import com.zello.channel.sdk.platform.EncoderListener
import com.zello.channel.sdk.platform.PlayerListener
import com.zello.channel.sdk.transport.Transport
import com.zello.channel.sdk.transport.TransportEvents
import com.zello.channel.sdk.transport.TransportFactory
import com.zello.channel.sdk.transport.TransportSendAck
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

internal class TestAudioSource: AudioSource {
	override val level: Int = 0

	override fun prepare(sampleRate: Int, bufferSampleCount: Int, levelMeter: Boolean, noiseSuppression: Boolean, useAGC: Boolean): Boolean {
		// TODO: Implement prepare(sampleRate:, bufferSampleCount:, levelMeter:, noiseSuppression:, useAGC:)
		return false
	}

	override fun start() { }

	override fun stop() { }
}

internal class TestEncoder: Encoder {
	var prepareAsyncCalled: Boolean = false
	var _listener: EncoderListener? = null

	override fun init(audioSource: AudioSource) { }

	override fun getHeader(): ByteArray {
		return ByteArray(0)
	}

	override fun getName(): String = "opus"

	override fun getPacketDuration(): Int = 10

	override fun getFrameDuration(): Int = 0

	override fun getSampleRate(): Int = 0

	override fun setListener(listener: EncoderListener) {
		_listener = listener
	}

	override fun prepareAsync(amplifierGain: Int, levelMeter: Boolean): Boolean {
		prepareAsyncCalled = true
		return true
	}

	override fun start() { }

	override fun stop() { }

	override fun getLevel(): Int = 0

	override fun saveConfig(): Any = Object()

	override fun getPacketsPerSecond(): Int = 0

	override fun onAudioSourceHasData(data: ShortArray) { }

	override fun onAudioSourceReady(sampleRate: Int) { }

	override fun onAudioSourceStart() { }

	override fun onAudioSourceEnd() { }

	override fun onAudioSourceError() { }

	override fun onAudioSourceInitError() { }

	override fun onRecordPermissionError() { }
}

internal class TestReceiver: AudioReceiver {
	override fun prepare(channels: Int, sampleRate: Int, bitsPerSample: Int, packetDuration: Int): Boolean = false

	override fun start() { }

	override fun stop() { }

	override fun pause() { }

	override fun resume() { }

	override fun setStreamVolume(percent: Int) { }

	override fun setDeviceVolume(percent: Int) { }

	override fun setMuted(muted: Boolean) { }

	/**
	 * Returns the playback position in milliseconds from the start of the audio stream
	 */
	override fun getPosition(): Int = 0

	override fun setPlayerListener(listener: PlayerListener?, listenerContext: Any?) { }

	override fun reset() { }

	override fun isPlaying(): Boolean = false
}

internal class TestDecoder: Decoder {
	override  fun init(receiver: AudioReceiver) { }

	override fun getName(): String = ""

	override fun setListener(listener: DecoderListener) { }

	override fun setPlayerListener(listener: PlayerListener, `object`: Any) { }

	override fun setPacketDuration(count: Int) { }

	override fun getMissingPacket(): ByteArray = ByteArray(0)

	override fun prepareAsync(header: ByteArray, amplifierGain: Int, levelMeter: Boolean) { }

	override fun start() { }

	override fun stop() { }

	override fun pause() { }

	override fun resume() { }

	override fun setAudioVolume(value: Int) { }

	override fun setGain(gain: Int) { }

	override fun setMuted(muted: Boolean) { }

	override fun getPosition(): Int = 0

	override fun getStarted(): Boolean = false

	override fun onGet8BitData(): ByteArray? = null

	override fun onGet16BitData(): ShortArray? = null

	override fun onPlaybackStart() { }

	override fun onPlaybackEnd() { }

	override fun onPlaybackInitError() { }
}

internal class TestTransport: Transport {
	var connectCalled: Boolean = false
		private set

	var connectedAddress: String? = null
	var sentCommand: String? = null
	var sentJson: JSONObject? = null

	@Throws(SessionConnectErrorException::class)
	override fun connect(events: TransportEvents, address: String, requestTimeoutSec: Long) {
		connectedAddress = address
		connectCalled = true
	}

	override fun disconnect() { }

	override fun send(command: String, json: JSONObject, ack: TransportSendAck?) {
		sentCommand = command
		sentJson = json
	}

	override fun sendVoiceStreamData(streamId: Int, data: ByteArray) { }
}

internal class TestTransportFactory: TransportFactory {
	val transport = TestTransport()

	override fun instantiate(): Transport {
		return transport
	}
}

internal class TestSessionContext: SessionContext {
	override val transportFactory: TestTransportFactory = TestTransportFactory()
	val encoder: TestEncoder = TestEncoder()

	override fun setLogger(logger: SessionLogger?) { }

	override fun getLogger(): SessionLogger = SessionLogger.NONE

	override fun loadNativeLibraries(logger: SessionLogger?): Boolean = true

	override fun createAudioSource(configuration: OutgoingVoiceConfiguration?, audioEventHandler: AudioSourceEvents, stream: OutgoingVoiceStream): AudioSource {
		return TestAudioSource()
	}

	override fun createEncoder(): Encoder {
		return encoder
	}

	override fun createAudioReceiver(configuration: IncomingVoiceConfiguration?, receiverEventHandler: AudioReceiverEvents, stream: IncomingVoiceStream): AudioReceiver {
		return TestReceiver()
	}

	override fun createDecoder(): Decoder {
		return TestDecoder()
	}

	override fun runOnUiThread(run: Runnable) {
		run.run()
	}

	override fun runOnUiThread(run: Runnable, delayMillis: Long) {
		run.run()
	}
}

internal class TestSessionListener: SessionListener {
	var connectedStartedCalled: Boolean = false
	override fun onConnectStarted(session: Session) {
		connectedStartedCalled = true
	}

	override fun onConnectFailed(session: Session, error: SessionConnectError) {

	}

	override fun onConnectSucceeded(session: Session) {

	}

	override fun onDisconnected(session: Session) {

	}

	override fun onOutgoingVoiceError(session: Session, stream: OutgoingVoiceStream, error: OutgoingVoiceStreamError) {

	}

	override fun onOutgoingVoiceStateChanged(session: Session, stream: OutgoingVoiceStream) {

	}

	override fun onOutgoingVoiceProgress(session: Session, stream: OutgoingVoiceStream, positionMs: Int) {

	}

	override fun onIncomingVoiceStarted(session: Session, stream: IncomingVoiceStream) {

	}

	override fun onIncomingVoiceStopped(session: Session, stream: IncomingVoiceStream) {

	}

	override fun onIncomingVoiceProgress(session: Session, stream: IncomingVoiceStream, positionMs: Int) {

	}

}

internal class TestVoiceSource: VoiceSource {
	override fun startProvidingAudio(sink: VoiceSink, sampleRate: Int, stream: OutgoingVoiceStream) { }

	override fun stopProvidingAudio(sink: VoiceSink) { }
}

@RunWith(AndroidJUnit4::class)
class SessionTests {
	private lateinit var sessionContext: TestSessionContext
	private lateinit var session: Session
	private lateinit var sessionListener: TestSessionListener

	@Before
	fun setup() {
		val context = TestSessionContext()
		sessionContext = context
		val session = Session(context, "http://example.com/", "auth", "bogusUser", "password", "testChannel")
		this.session = session
		sessionListener = TestSessionListener()
		session.sessionListener = sessionListener
	}

	@Test
	fun testConnect() {
		val transport = sessionContext.transportFactory.transport
		assertTrue("Session failed to connect", session.connect())

		assertTrue("transport.connect() not called", transport.connectCalled)
		assertEquals("Connected to wrong address", "http://example.com/", transport.connectedAddress)
		assertTrue("Session listener not informed of connect starting", sessionListener.connectedStartedCalled)
	}

	// Verify that the Session returns what the VoiceManager gives it
	@Test
	fun testStartStream_NotReady_ReturnsNull() {
		assertNull("Starting voice message should fail before session is initialized", session.startVoiceMessage())
	}

	@Test
	fun testStartStream_SendsStartMessage() {
		assertTrue(session.connect())

		val stream = session.startVoiceMessage()
		assertNotNull(stream)
		if (stream == null) { return }
		assertTrue(sessionContext.encoder.prepareAsyncCalled)
		sessionContext.encoder._listener?.onEncoderReady()
		assertEquals("start_stream", sessionContext.transportFactory.transport.sentCommand)
		val json = sessionContext.transportFactory.transport.sentJson
		assertNotNull(json)
		if (json == null) { return }
		assertEquals("audio", json.getString("type"))
		assertEquals("opus", json.getString("codec"))
		assertEquals("", json.getString("codec_header"))
		assertEquals(10, json.getInt("packet_duration"))
	}

	@Test
	fun testStartStreamToRecipient_SendsStartMessage() {
		assertTrue(session.connect())

		val stream = session.startVoiceMessage("bogusRecipient")
		assertNotNull("Stream not created", stream)
		if (stream == null) { return }
		assertTrue(sessionContext.encoder.prepareAsyncCalled)
		sessionContext.encoder._listener?.onEncoderReady()
		assertEquals("start_stream", sessionContext.transportFactory.transport.sentCommand)
		val json = sessionContext.transportFactory.transport.sentJson
		assertNotNull(json)
		if (json == null) { return }
		assertEquals("audio", json.optString("type"))
		assertEquals("opus", json.optString("codec"))
		assertEquals("", json.optString("codec_header"))
		assertEquals(10, json.getInt("packet_duration"))
		assertEquals("bogusRecipient", json.optString("for"))
	}

	@Test
	fun testStartStreamWithSourceConfig_SendsStartMessage() {
		assertTrue(session.connect())

		val voiceSource = TestVoiceSource()
		val sourceConfig = OutgoingVoiceConfiguration(voiceSource, 16000)
		val stream = session.startVoiceMessage(sourceConfig)
		assertNotNull("Stream not created", stream)
		if (stream == null) { return }
		assertTrue(sessionContext.encoder.prepareAsyncCalled)
		sessionContext.encoder._listener?.onEncoderReady()
		assertEquals("start_stream", sessionContext.transportFactory.transport.sentCommand)
		val json = sessionContext.transportFactory.transport.sentJson
		assertNotNull(json)
		if (json == null) { return }
		assertEquals("audio", json.optString("type"))
		assertEquals("opus", json.optString("codec"))
		assertEquals("", json.optString("codec_header"))
		assertEquals(10, json.getInt("packet_duration"))
	}

	@Test
	fun testStartStreamToRecipientWithSourceConfig_SendsStartMessage() {
		assertTrue(session.connect())

		val voiceSource = TestVoiceSource()
		val sourceConfig = OutgoingVoiceConfiguration(voiceSource, 16000)
		val stream = session.startVoiceMessage(recipient = "bogusRecipient", sourceConfig = sourceConfig)
		assertNotNull("Stream not created", stream)
		if (stream == null) { return }
		assertTrue(sessionContext.encoder.prepareAsyncCalled)
		sessionContext.encoder._listener?.onEncoderReady()
		assertEquals("start_stream", sessionContext.transportFactory.transport.sentCommand)
		val json = sessionContext.transportFactory.transport.sentJson
		assertNotNull(json)
		if (json == null) { return }
		assertEquals("audio", json.optString("type"))
		assertEquals("opus", json.optString("codec"))
		assertEquals("", json.optString("codec_header"))
		assertEquals(10, json.getInt("packet_duration"))
		assertEquals("bogusRecipient", json.optString("for"))
	}
}