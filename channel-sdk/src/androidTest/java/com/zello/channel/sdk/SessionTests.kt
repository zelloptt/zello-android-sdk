package com.zello.channel.sdk

import android.graphics.Bitmap
import android.location.Criteria
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zello.channel.sdk.image.Dimensions
import com.zello.channel.sdk.image.ImageMessageManager
import com.zello.channel.sdk.image.ImageMessageManagerListener
import com.zello.channel.sdk.image.ImageTag
import com.zello.channel.sdk.image.TestImageUtils
import com.zello.channel.sdk.location.LocationManager
import com.zello.channel.sdk.platform.AudioReceiver
import com.zello.channel.sdk.platform.AudioReceiverEvents
import com.zello.channel.sdk.platform.AudioSource
import com.zello.channel.sdk.platform.AudioSourceEvents
import com.zello.channel.sdk.platform.Decoder
import com.zello.channel.sdk.platform.DecoderListener
import com.zello.channel.sdk.platform.Encoder
import com.zello.channel.sdk.platform.EncoderListener
import com.zello.channel.sdk.platform.PlayerListener
import com.zello.channel.sdk.platform.hexString
import com.zello.channel.sdk.transport.Transport
import com.zello.channel.sdk.transport.TransportFactory
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.Arrays

//region Mocks

internal class TestAudioSource: AudioSource {
	override val level: Int = 0

	override fun prepare(sampleRate: Int, bufferSampleCount: Int, levelMeter: Boolean, noiseSuppression: Boolean, useAGC: Boolean): Boolean = false

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

internal class TestTransportFactory: TransportFactory {
	val transport = TestTransport()

	override fun instantiate(): Transport {
		return transport
	}
}

internal class TestImageMessageManager: ImageMessageManager {
	var validType: Boolean = true
	override fun isTypeValid(type: String): Boolean {
		return validType
	}

	var listener: ImageMessageManagerListener? = null
	var sentImage: Bitmap? = null
		private set
	var recipient: String? = null
	override fun sendImage(image: Bitmap, transport: Transport, recipient: String?, continuation: (Int, SendImageError?) -> Unit) {
		this.recipient = recipient
		sentImage = image
	}


	var receivedImageId: Int? = null
	var receivedImageSender: String? = null
	var receivedDimensions: Dimensions? = null
	override fun onImageHeader(imageId: Int, sender: String, dimensions: Dimensions) {
		receivedImageId = imageId
		receivedImageSender = sender
		receivedDimensions = dimensions
	}

	var receivedImageType: Int? = null
	var receivedImageData: ByteArray? = null
	override fun onImageData(imageId: Int, type: Int, data: ByteArray) {
		receivedImageId = imageId
		receivedImageType = type
		receivedImageData = data
	}
}

internal class MockZelloLocationManager : LocationManager {
	var sendLocationCalled: Boolean = false
	var criteria: Criteria? = null
	var recipient: String? = null
	override fun sendLocation(transport: Transport, criteria: Criteria, recipient: String?, callback: SentLocationCallback?) {
		sendLocationCalled = true
		this.criteria = criteria
		this.recipient = recipient
	}

}

internal class TestSessionContext: SessionContext {
	override val transportFactory: TestTransportFactory = TestTransportFactory()
	override var hasLocationPermission: Boolean = false

	val imageMessageManager: TestImageMessageManager = TestImageMessageManager()
	override val locationManager: MockZelloLocationManager = MockZelloLocationManager()

	val encoder: TestEncoder = TestEncoder()

	override fun setLogger(logger: SessionLogger?) { }

	override fun getLogger(): SessionLogger = SessionLogger.NONE

	override fun loadNativeLibraries(logger: SessionLogger?): Boolean = true

	override fun createImageMessageManager(listener: ImageMessageManagerListener): ImageMessageManager {
		imageMessageManager.listener = listener
		return imageMessageManager
	}

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

	var channelStatusUpdateCalled: Boolean = false
	override fun onChannelStatusUpdate(session: Session) {
		channelStatusUpdateCalled = true
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

	var receivedTextMessage: String? = null
	var receivedTextMessageSender: String? = null
	override fun onTextMessage(session: Session, sender: String, message: String) {
		receivedTextMessageSender = sender
		receivedTextMessage = message
	}

	var receivedImageMessage: ImageInfo? = null
	override fun onImageMessage(session: Session, imageInfo: ImageInfo) {
		receivedImageMessage = imageInfo
	}

	var receivedLocation: Location? = null
	var receivedLocationSender: String? = null
	override fun onLocationMessage(session: Session, sender: String, location: Location) {
		receivedLocationSender = sender
		receivedLocation = location
	}

	var infoError: InformationalError? = null
	override fun onError(session: Session, error: InformationalError) {
		infoError = error
	}
}

internal class TestVoiceSource: VoiceSource {
	override fun startProvidingAudio(sink: VoiceSink, sampleRate: Int, stream: OutgoingVoiceStream) { }

	override fun stopProvidingAudio(sink: VoiceSink) { }
}

//endregion Mocks

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

	//region Lifecycle

	@Test
	fun testConnect() {
		val transport = sessionContext.transportFactory.transport
		assertTrue("Session failed to connect", session.connect())

		assertTrue("transport.connect() not called", transport.connectCalled)
		assertEquals("Connected to wrong address", "http://example.com/", transport.connectedAddress)
		assertTrue("Session listener not informed of connect starting", sessionListener.connectedStartedCalled)
	}

	//endregion

	//region Voice Messages

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
		assertTrue("Session failed to connect", session.connect())

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

	//endregion

	//region Channel Features

	@Test
	fun testChannelFeatures() {
		assertTrue(session.connect())

		val listener = sessionContext.transportFactory.transport.eventListener
		assertNotNull(listener)
		if (listener == null) { return }

		val json = JSONObject()
		json.put("command", "on_channel_status")
		listener.onIncomingCommand("on_channel_status", json, null)
		assertFalse("images_supported present", session.channelFeatures.contains(ChannelFeature.ImageMessages))
		assertFalse("texting_supported present", session.channelFeatures.contains(ChannelFeature.TextMessages))
		assertFalse("locations_supported present", session.channelFeatures.contains(ChannelFeature.LocationMessages))

		json.put("images_supported", true)
		listener.onIncomingCommand("on_channel_status", json, null)
		assertTrue("images_supported missing", session.channelFeatures.contains(ChannelFeature.ImageMessages))
		assertFalse("texting_supported present", session.channelFeatures.contains(ChannelFeature.TextMessages))
		assertFalse("locations_supported present", session.channelFeatures.contains(ChannelFeature.LocationMessages))

		json.put("images_supported", false)
		json.put("texting_supported", true)
		listener.onIncomingCommand("on_channel_status", json, null)
		assertFalse("images_supported present", session.channelFeatures.contains(ChannelFeature.ImageMessages))
		assertTrue("texting_supported missing", session.channelFeatures.contains(ChannelFeature.TextMessages))
		assertFalse("locations_supported present", session.channelFeatures.contains(ChannelFeature.LocationMessages))

		json.put("texting_supported", false)
		json.put("locations_supported", true)
		listener.onIncomingCommand("on_channel_status", json, null)
		assertFalse("images_supported present", session.channelFeatures.contains(ChannelFeature.ImageMessages))
		assertFalse("texting_supported present", session.channelFeatures.contains(ChannelFeature.TextMessages))
		assertTrue("locations_supported missing", session.channelFeatures.contains(ChannelFeature.LocationMessages))
	}

	@Test
	fun testChannelFeatures_ResetAfterDisconnect() {
		assertTrue(session.connect())

		val listener = sessionContext.transportFactory.transport.eventListener
		assertNotNull(listener)
		if (listener == null) { return }

		val json = JSONObject()
		json.put("command", "on_channel_status")
		json.put("texting_supported", true)
		listener.onIncomingCommand("on_channel_status", json, null)
		assertTrue("texting_supported missing", session.channelFeatures.contains(ChannelFeature.TextMessages))

		listener.onDisconnected()
		assertFalse("Features not reset after disconnect", session.channelFeatures.contains(ChannelFeature.TextMessages))
	}

	@Test
	fun testChannelUsersOnline() {
		assertTrue(session.connect())

		val listener = sessionContext.transportFactory.transport.eventListener
		assertNotNull(listener)
		if (listener == null) { return }

		val json = JSONObject()
		json.put("command", "on_channel_status")
		json.put("users_online", 10)
		listener.onIncomingCommand("on_channel_status", json, null)
		assertEquals(10, session.channelUsersOnline)

		listener.onDisconnected()
		assertEquals(0, session.channelUsersOnline)
	}

	// Verify that the Session's listener is informed when the channel status is updated
	@Test
	fun testChannelStatusUpdate_CallsListener() {
		assertTrue(session.connect())

		val listener = sessionContext.transportFactory.transport.eventListener
		assertNotNull(listener)
		if (listener == null) { return }

		val json = JSONObject()
		json.put("command", "on_channel_status")
		assertFalse(sessionListener.channelStatusUpdateCalled)
		listener.onIncomingCommand("on_channel_status", json, null)
		assertTrue("Listener.onChannelStatusUpdate() not called", sessionListener.channelStatusUpdateCalled)
	}

	//endregion

	//region Text Messages

	// Verify that sending a voice message to the channel sends the right command
	@Test
	fun testSendText_SendsCommand() {
		assertTrue(session.connect())

		session.sendText("test message")

		assertEquals("Didn't send text message", "send_text_message", sessionContext.transportFactory.transport.sentCommand)
		val json = sessionContext.transportFactory.transport.sentJson
		assertNotNull(json)
		if (json == null) { return }
		assertEquals("Wrong text", "test message", json.optString("text"))
		assertFalse("Recipient present", json.has("for"))
	}

	@Test
	fun testSendTextToRecipient_SendsCommand() {
		assertTrue(session.connect())

		session.sendText("test message", recipient = "bogusRecipient")

		assertEquals("Didn't send text message", "send_text_message", sessionContext.transportFactory.transport.sentCommand)
		val json = sessionContext.transportFactory.transport.sentJson
		assertNotNull(json)
		if (json == null) { return }
		assertEquals("Wrong text", "test message", json.optString("text"))
		assertEquals("Wrong recipient", "bogusRecipient", json.optString("for"))
	}

	// Verify that we send received text messages to the session listener
	@Test
	fun testOnTextMessage_CallsListener() {
		assertTrue(session.connect())

		val json = JSONObject()
		json.put("command", "on_text_message")
		json.put("channel", "testChannel")
		json.put("from", "bogusSender")
		json.put("message_id", 234)
		json.put("text", "example message")
		sessionContext.transportFactory.transport.eventListener?.onIncomingCommand("on_text_message", json, null)
		assertEquals("Wrong text message", "example message", sessionListener.receivedTextMessage)
		assertEquals("Wrong text sender", "bogusSender", sessionListener.receivedTextMessageSender)
	}

	//endregion

	//region Image Messages

	@Test
	fun testSendImageMessage_CallsImageMessageManager() {
		assertTrue(session.connect())

		val image = TestImageUtils.createRedBitmap(500, 400)
		session.sendImage(image)
		assertNull(sessionContext.imageMessageManager.recipient)
		assertEquals(image, sessionContext.imageMessageManager.sentImage)
	}

	@Test
	fun testSendImageMessageToRecipient_CallsImageMessageManager() {
		assertTrue(session.connect())

		val image = TestImageUtils.createRedBitmap(500, 400)
		session.sendImage(image, "bogusRecipient")
		assertEquals("Wrong or missing recipient", "bogusRecipient", sessionContext.imageMessageManager.recipient)
		assertEquals("Wrong or missing image", image, sessionContext.imageMessageManager.sentImage)
	}

	val imageHeaderPayload: JSONObject by lazy {
		val json = JSONObject()
		json.put("command", "on_image")
		json.put("channel", "testChannel")
		json.put("from", "bogusSender")
		json.put("message_id", 345)
		json.put("type", "jpeg")
		json.put("height", 300)
		json.put("width", 500)
		json
	}
	@Test
	fun testOnImageMessageHeader_CallsImageMessageManager() {
		assertTrue(session.connect())

		sessionContext.transportFactory.transport.eventListener?.onIncomingCommand("on_image", imageHeaderPayload, null)
		assertEquals("Didn't call image message manager", 345, sessionContext.imageMessageManager.receivedImageId)
		assertEquals("Didn't pass sender to image message manager", "bogusSender", sessionContext.imageMessageManager.receivedImageSender)
		assertEquals("Didn't pass dimensions to image message manager", Dimensions(500, 300), sessionContext.imageMessageManager.receivedDimensions)
	}

	// Verify behavior for invalid image message headers
	@Test
	fun testOnImageMessageHeader_InvalidHeader() {
		val allFields = arrayOf("command", "channel", "from", "message_id", "type", "height", "width")
		var error: InvalidMessageFormatError? = null

		assertTrue(session.connect())

		// Missing message_id
		sessionListener.infoError = null
		val missingId = JSONObject(imageHeaderPayload, arrayOf("command", "channel", "from", "type", "height", "width"))
		sessionContext.transportFactory.transport.eventListener?.onIncomingCommand("on_image", missingId, null)
		assertNull("Shouldn't have gotten message with missing image id.", sessionContext.imageMessageManager.receivedImageId)
		assertNull(sessionContext.imageMessageManager.receivedImageSender)
		assertNull(sessionContext.imageMessageManager.receivedDimensions)
		error = sessionListener.infoError
		assertNotNull(error)
		if (error == null) return
		assertEquals("Missing image id", error.errorMessage)
		assertEquals("on_image", error.command)
		assertEquals("message_id", error.key)
		assertEquals(missingId, error.payload)

		// Missing type
		sessionListener.infoError = null
		val missingType = JSONObject(imageHeaderPayload, arrayOf("command", "channel", "from", "message_id", "height", "width"))
		sessionContext.transportFactory.transport.eventListener?.onIncomingCommand("on_image", missingType, null)
		assertNull(sessionContext.imageMessageManager.receivedImageId)
		assertNull(sessionContext.imageMessageManager.receivedImageSender)
		assertNull(sessionContext.imageMessageManager.receivedDimensions)
		error = sessionListener.infoError as? InvalidMessageFormatError
		assertNotNull(error)
		if (error == null) return
		assertEquals("Missing image type", error.errorMessage)
		assertEquals("on_image", error.command)
		assertEquals("type", error.key)
		assertEquals(missingType, error.payload)

		// Invalid type
		sessionListener.infoError = null
		sessionContext.imageMessageManager.validType = false
		val invalidType = JSONObject(imageHeaderPayload, allFields)
		invalidType.put("type", "badtype")
		sessionContext.transportFactory.transport.eventListener?.onIncomingCommand("on_image", invalidType, null)
		assertNull("Should not have received message with invalid type.", sessionContext.imageMessageManager.receivedImageId)
		assertNull(sessionContext.imageMessageManager.receivedImageSender)
		assertNull(sessionContext.imageMessageManager.receivedDimensions)
		error = sessionListener.infoError as? InvalidMessageFormatError
		assertNotNull(error)
		if (error == null) return
		assertEquals("Invalid image type 'badtype'", error.errorMessage)
		assertEquals("on_image", error.command)
		assertEquals("type", error.key)
		assertEquals(invalidType, error.payload)

		sessionContext.imageMessageManager.validType = true
		// Missing height
		sessionListener.infoError = null
		val missingHeight = JSONObject(imageHeaderPayload, arrayOf("command", "channel", "from", "message_id", "type", "width"))
		sessionContext.transportFactory.transport.eventListener?.onIncomingCommand("on_image", missingHeight, null)
		assertNull("Should not have received message with missing height.", sessionContext.imageMessageManager.receivedImageId)
		assertNull(sessionContext.imageMessageManager.receivedImageSender)
		assertNull(sessionContext.imageMessageManager.receivedDimensions)
		error = sessionListener.infoError as? InvalidMessageFormatError
		assertNotNull(error)
		if (error == null) return
		assertEquals("Missing image height", error.errorMessage)
		assertEquals("on_image", error.command)
		assertEquals("height", error.key)
		assertEquals(missingHeight, error.payload)

		// Invalid height
		sessionListener.infoError = null
		val heightOverflow = JSONObject(imageHeaderPayload, allFields)
		heightOverflow.put("height", 5000)
		sessionContext.transportFactory.transport.eventListener?.onIncomingCommand("on_image", heightOverflow, null)
		assertNull("Should not have received message with invalid height.", sessionContext.imageMessageManager.receivedImageId)
		assertNull(sessionContext.imageMessageManager.receivedImageSender)
		assertNull(sessionContext.imageMessageManager.receivedDimensions)
		error = sessionListener.infoError as? InvalidMessageFormatError
		assertNotNull(error)
		if (error == null) return
		assertEquals("Height 5000 out of allowed range", error.errorMessage)
		assertEquals("on_image", error.command)
		assertEquals("height", error.key)
		assertEquals(heightOverflow, error.payload)

		sessionListener.infoError = null
		val heightUnderflow = JSONObject(imageHeaderPayload, allFields)
		heightUnderflow.put("height", 0)
		sessionContext.transportFactory.transport.eventListener?.onIncomingCommand("on_image", heightUnderflow, null)
		assertNull("Should not have received message with invalid height.", sessionContext.imageMessageManager.receivedImageId)
		assertNull(sessionContext.imageMessageManager.receivedImageSender)
		assertNull(sessionContext.imageMessageManager.receivedDimensions)
		error = sessionListener.infoError as? InvalidMessageFormatError
		assertNotNull(error)
		if (error == null) return
		assertEquals("Height 0 out of allowed range", error.errorMessage)
		assertEquals("on_image", error.command)
		assertEquals("height", error.key)
		assertEquals(heightUnderflow, error.payload)

		// Missing width
		sessionListener.infoError = null
		val missingWidth = JSONObject(imageHeaderPayload, arrayOf("command", "channel", "from", "message_id", "type", "height"))
		sessionContext.transportFactory.transport.eventListener?.onIncomingCommand("on_image", missingWidth, null)
		assertNull("Should not have received message with missing width.", sessionContext.imageMessageManager.receivedImageId)
		assertNull(sessionContext.imageMessageManager.receivedImageSender)
		assertNull(sessionContext.imageMessageManager.receivedDimensions)
		error = sessionListener.infoError as? InvalidMessageFormatError
		assertNotNull(error)
		if (error == null) return
		assertEquals("Missing image width", error.errorMessage)
		assertEquals("on_image", error.command)
		assertEquals("width", error.key)
		assertEquals(missingWidth, error.payload)

		// Invalid width
		sessionListener.infoError = null
		val widthOverflow = JSONObject(imageHeaderPayload, allFields)
		widthOverflow.put("width", 4500)
		sessionContext.transportFactory.transport.eventListener?.onIncomingCommand("on_image", widthOverflow, null)
		assertNull(sessionContext.imageMessageManager.receivedImageId)
		assertNull(sessionContext.imageMessageManager.receivedImageSender)
		assertNull(sessionContext.imageMessageManager.receivedDimensions)
		error = sessionListener.infoError as? InvalidMessageFormatError
		if (error == null) return
		assertEquals("Width 4500 out of allowed range", error.errorMessage)
		assertEquals("on_image", error.command)
		assertEquals("width", error.key)
		assertEquals(widthOverflow, error.payload)

		sessionListener.infoError = null
		val widthUnderflow = JSONObject(imageHeaderPayload, allFields)
		widthUnderflow.put("width", 0)
		sessionContext.transportFactory.transport.eventListener?.onIncomingCommand("on_image", widthUnderflow, null)
		assertNull(sessionContext.imageMessageManager.receivedImageId)
		assertNull(sessionContext.imageMessageManager.receivedImageSender)
		assertNull(sessionContext.imageMessageManager.receivedDimensions)
		error = sessionListener.infoError as? InvalidMessageFormatError
		assertNotNull(error)
		if (error == null) return
		assertEquals("Width 0 out of allowed range", error.errorMessage)
		assertEquals("on_image", error.command)
		assertEquals("width", error.key)
		assertEquals(widthUnderflow, error.payload)
	}

	// Verify image data is forwarded to ImageMessageManager
	@Test
	fun testOnImageMessageData_CallsImageMessageManager() {
		assertTrue(session.connect())

		val thumbnailBytes = ByteArrayOutputStream()
		val thumbnail = TestImageUtils.createRedBitmap(90, 90)
		thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, thumbnailBytes)
		sessionContext.transportFactory.transport.eventListener?.onIncomingImageData(2345, 2, thumbnailBytes.toByteArray())
		assertEquals("Didn't call image message manager.", 2345, sessionContext.imageMessageManager.receivedImageId)
		assertEquals("Wrong image type.", ImageTag.THUMBNAIL.imageType, sessionContext.imageMessageManager.receivedImageType)
		assertTrue("Wrong image data. Expected <${thumbnailBytes.toByteArray().hexString}> but was <${sessionContext.imageMessageManager.receivedImageData?.hexString}>", Arrays.equals(thumbnailBytes.toByteArray(), sessionContext.imageMessageManager.receivedImageData))
	}

	// Verify forwarding image messages from ImageMessageManager to SessionListener
	@Test
	fun testOnImageMessageFromManager_CallsListener() {
		assertTrue("Session failed to connect", session.connect())

		val imageListener = sessionContext.imageMessageManager.listener
		assertNotNull("ImageMessageManagerListener not set", imageListener)
		if (imageListener == null) { return }

		val thumbnail = TestImageUtils.createRedBitmap(90, 90)
		val imageInfo = ImageInfo(1234, "bogusSender", thumbnail, null)
		imageListener.onImageMessage(imageInfo)

		val received = sessionListener.receivedImageMessage
		assertNotNull("Listener not called", received)
		if (received == null) { return }
		assertEquals("Wrong image id.", received.imageId, 1234)
		assertEquals("Wrong sender.", received.sender, "bogusSender")
		assertEquals("Wrong or missing thumbnail.", thumbnail, received.thumbnail)
		assertNull("Unexpected image present.", received.image)

		sessionListener.receivedImageMessage = null
		val fullSized = TestImageUtils.createRedBitmap(500, 500)
		val secondImage = ImageInfo(1234, "bogusSender", thumbnail, fullSized)
		imageListener.onImageMessage(secondImage)

		val second = sessionListener.receivedImageMessage
		assertNotNull("Listener not called", second)
		if (second == null) { return }
		assertEquals(1234, second.imageId)
		assertEquals("bogusSender", second.sender)
		assertEquals(thumbnail, second.thumbnail)
		assertEquals(fullSized, second.image)
	}

	//endregion

	//region Location Messages

	// Verify that we return false if location permission is not granted
	@Test
	fun testSendLocation_NotReadyOrNoPermission_ReturnsFalse() {
		// Verify that sendLocation() returns false if the session hasn't connected yet
		sessionContext.hasLocationPermission = true
		assertFalse(session.sendLocation(null))

		// Verify that sendLocation() returns false if location permission hasn't been granted
		sessionContext.hasLocationPermission = false
		assertTrue(session.connect())
		assertFalse(session.sendLocation(null))
	}

	// Verify that we call the location message manager for location messages
	@Test
	fun testSendLocation_CallsLocationMessageManager() {
		assertTrue(session.connect())
		sessionContext.hasLocationPermission = true
		assertTrue("sendLocation returned failure.", session.sendLocation(null))
		assertTrue("Send location not called.", sessionContext.locationManager.sendLocationCalled)
		assertNull("Recipient should not have been present.", sessionContext.locationManager.recipient)

		sessionContext.locationManager.sendLocationCalled = false
		assertTrue("sendLocation returned failure.", session.sendLocation("bogusRecipient", null))
		assertTrue("Send location not called.", sessionContext.locationManager.sendLocationCalled)
		assertEquals("bogusRecipient", sessionContext.locationManager.recipient)
	}

	val onLocationPayload: JSONObject by lazy {
		val json = JSONObject()
		json.put("command", "on_location")
		json.put("channel", "testChannel")
		json.put("from", "bogusSender")
		json.put("message_id", 1234)
		json.put("latitude", 23.0)
		json.put("longitude", 15.0)
		json.put("accuracy", 100.0)
		json.put("formatted_address", "A Fancy Place, MD")
		json
	}

	// Verify that we report received location messages to the listener
	@Test
	fun testOnLocation_ReportsLocation() {
		assertTrue(session.connect())

		sessionContext.transportFactory.transport.eventListener?.onIncomingCommand("on_location", onLocationPayload, null)

		assertEquals("bogusSender", sessionListener.receivedLocationSender)
		assertEquals(23.0, sessionListener.receivedLocation?.latitude)
		assertEquals(15.0, sessionListener.receivedLocation?.longitude)
		assertEquals(100.0, sessionListener.receivedLocation?.accuracy)
		assertEquals("A Fancy Place, MD", sessionListener.receivedLocation?.address)

		// Verify we still get a location without an address
		sessionListener.receivedLocation = null
		sessionListener.receivedLocationSender = null
		val missingAddress = JSONObject(onLocationPayload, arrayOf("command", "channel", "from", "message_id", "latitude", "longitude", "accuracy"))
		sessionContext.transportFactory.transport.eventListener?.onIncomingCommand("on_location", missingAddress, null)
		assertEquals("bogusSender", sessionListener.receivedLocationSender)
		assertEquals(23.0, sessionListener.receivedLocation?.latitude)
		assertEquals(15.0, sessionListener.receivedLocation?.longitude)
		assertEquals(100.0, sessionListener.receivedLocation?.accuracy)
		assertNull(sessionListener.receivedLocation?.address)
	}

	// Verify that we report format errors in incoming location messages
	@Test
	fun testOnLocation_InvalidFormat_ReportsError() {
		val allLocationKeys = arrayOf("command", "channel", "from", "message_id", "latitude", "longitude", "accuracy", "formatted_address")

		assertTrue(session.connect())

		// Missing latitude
		val missingLatitude = JSONObject(onLocationPayload, arrayOf("command", "channel", "from", "message_id", "longitude", "accuracy", "formatted_address"))
		sessionContext.transportFactory.transport.eventListener?.onIncomingCommand("on_location", missingLatitude, null)
		assertNull(sessionListener.receivedLocationSender)
		assertNull(sessionListener.receivedLocation)
		var error = sessionListener.infoError as? InvalidMessageFormatError
		assertNotNull(error)
		if (error == null) return
		assertEquals("Missing latitude", error.errorMessage)
		assertEquals("on_location", error.command)
		assertEquals("latitude", error.key)
		assertEquals(missingLatitude, error.payload)

		// Latitude out of range
		sessionListener.infoError = null
		val latitudeOverflow = JSONObject(onLocationPayload, allLocationKeys)
		latitudeOverflow.put("latitude", 100.0)
		sessionContext.transportFactory.transport.eventListener?.onIncomingCommand("on_location", latitudeOverflow, null)
		assertNull(sessionListener.receivedLocation)
		assertNull(sessionListener.receivedLocationSender)
		error = sessionListener.infoError as? InvalidMessageFormatError
		assertNotNull(error)
		if (error == null) return
		assertEquals("Latitude 100.0 out of range -90.0..90.0", error.errorMessage)
		assertEquals("on_location", error.command)
		assertEquals("latitude", error.key)
		assertEquals(latitudeOverflow, error.payload)

		sessionListener.infoError = null
		val latitudeUnderflow = JSONObject(onLocationPayload, allLocationKeys)
		latitudeUnderflow.put("latitude", -95.0)
		sessionContext.transportFactory.transport.eventListener?.onIncomingCommand("on_location", latitudeUnderflow, null)
		assertNull(sessionListener.receivedLocation)
		assertNull(sessionListener.receivedLocationSender)
		error = sessionListener.infoError as? InvalidMessageFormatError
		assertNotNull(error)
		if (error == null) return
		assertEquals("Latitude -95.0 out of range -90.0..90.0", error.errorMessage)
		assertEquals("on_location", error.command)
		assertEquals("latitude", error.key)
		assertEquals(latitudeUnderflow, error.payload)

		// Missing longitude
		sessionListener.infoError = null
		val missingLongitude = JSONObject(onLocationPayload, arrayOf("command", "channel", "from", "message_id", "latitude", "accuracy", "formatted_address"))
		sessionContext.transportFactory.transport.eventListener?.onIncomingCommand("on_location", missingLongitude, null)
		assertNull(sessionListener.receivedLocationSender)
		assertNull(sessionListener.receivedLocation)
		error = sessionListener.infoError as? InvalidMessageFormatError
		assertNotNull(error)
		if (error == null) return
		assertEquals("Missing longitude", error.errorMessage)
		assertEquals("on_location", error.command)
		assertEquals("longitude", error.key)
		assertEquals(missingLongitude, error.payload)

		// Longitude out of range
		sessionListener.infoError = null
		val longitudeOverflow = JSONObject(onLocationPayload, allLocationKeys)
		longitudeOverflow.put("longitude", 184.0)
		sessionContext.transportFactory.transport.eventListener?.onIncomingCommand("on_location", longitudeOverflow, null)
		assertNull("Recieved location with invalid longitude.", sessionListener.receivedLocation)
		assertNull(sessionListener.receivedLocationSender)
		error = sessionListener.infoError as? InvalidMessageFormatError
		assertNotNull(error)
		if (error == null) return
		assertEquals("Longitude 184.0 out of range -180.0..180.0", error.errorMessage)
		assertEquals("on_location", error.command)
		assertEquals("longitude", error.key)
		assertEquals(longitudeOverflow, error.payload)

		sessionListener.infoError = null
		val longitudeUnderflow = JSONObject(onLocationPayload, allLocationKeys)
		longitudeUnderflow.put("longitude", -185.0)
		sessionContext.transportFactory.transport.eventListener?.onIncomingCommand("on_location", longitudeUnderflow, null)
		assertNull("Received location with invalid longitude.", sessionListener.receivedLocation)
		assertNull(sessionListener.receivedLocationSender)
		error = sessionListener.infoError as? InvalidMessageFormatError
		assertNotNull(error)
		if (error == null) return
		assertEquals("Longitude -185.0 out of range -180.0..180.0", error.errorMessage)
		assertEquals("on_location", error.command)
		assertEquals("longitude", error.key)
		assertEquals(longitudeUnderflow, error.payload)

		// Missing accuracy
		sessionListener.infoError = null
		val missingAccuracy = JSONObject(onLocationPayload, arrayOf("command", "channel", "from", "message_id", "latitude", "longitude", "formatted_address"))
		sessionContext.transportFactory.transport.eventListener?.onIncomingCommand("on_location", missingAccuracy, null)
		assertNull(sessionListener.receivedLocationSender)
		assertNull(sessionListener.receivedLocation)
		error = sessionListener.infoError as? InvalidMessageFormatError
		assertNotNull(error)
		if (error == null) return
		assertEquals("Missing accuracy", error.errorMessage)
		assertEquals("on_location", error.command)
		assertEquals("accuracy", error.key)
		assertEquals(missingAccuracy, error.payload)

		// Accuracy out of range
		sessionListener.infoError = null
		val accuracyUnderflow = JSONObject(onLocationPayload, allLocationKeys)
		accuracyUnderflow.put("accuracy", -50.0)
		sessionContext.transportFactory.transport.eventListener?.onIncomingCommand("on_location", accuracyUnderflow, null)
		assertNull(sessionListener.receivedLocation)
		assertNull(sessionListener.receivedLocationSender)
		error = sessionListener.infoError as? InvalidMessageFormatError
		assertNotNull(error)
		if (error == null) return
		assertEquals("Accuracy -50.0 may not be negative", error.errorMessage)
		assertEquals("on_location", error.command)
		assertEquals("accuracy", error.key)
		assertEquals(accuracyUnderflow, error.payload)
	}

	//endregion
}
