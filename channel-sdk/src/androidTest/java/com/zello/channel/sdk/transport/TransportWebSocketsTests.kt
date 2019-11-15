package com.zello.channel.sdk.transport

import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zello.channel.sdk.image.ImageTag
import com.zello.channel.sdk.platform.HandlerFactory
import com.zello.channel.sdk.platform.hexString
import junit.framework.Assert.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Arrays

internal class TestHandlerFactory : HandlerFactory {
	override fun handler(callback: Handler.Callback): Handler = TestHandler(callback)
}

internal class TestHandler(private val callback: Callback): Handler(callback) {
	override fun sendMessageAtTime(msg: Message?, uptimeMillis: Long): Boolean {
		return callback.handleMessage(msg)
	}
}

internal class TestWebSocket(private val request: Request, val listener: WebSocketListener) : WebSocket {

	override fun queueSize(): Long = 0

	override fun send(text: String): Boolean = false

	var sentBytes: ByteString? = null
	override fun send(bytes: ByteString): Boolean {
		sentBytes = bytes
		return true
	}

	override fun close(code: Int, reason: String?): Boolean = true

	override fun cancel() {

	}

	override fun request(): Request = request

}

internal class MockClient: OkHttpClient() {
	var socket: TestWebSocket? = null

	override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
		socket = TestWebSocket(request, listener)
		return socket!!
	}
}

internal class TestHttpClientFactory : HttpClientFactory {
	val client = MockClient()

	override fun client(timeout: Long, pingInterval: Long): OkHttpClient {
		return client
	}
}

internal class TestTransportEvents : TransportEvents {

	override fun onConnectSucceeded() {

	}

	override fun onDisconnected() {

	}

	override fun onConnectFailed() {

	}

	override fun onIncomingCommand(command: String, json: JSONObject, ack: TransportReadAck?) {

	}

	override fun onIncomingVoiceStreamData(streamId: Int, packetId: Int, data: ByteArray) {

	}

	var receivedImageId: Int? = null
	var receivedImageType: Int? = null
	var receivedImageData: ByteArray? = null
	override fun onIncomingImageData(imageId: Int, imageType: Int, data: ByteArray) {
		receivedImageId = imageId
		receivedImageType = imageType
		receivedImageData = data
	}

}

@RunWith(AndroidJUnit4::class)
class TransportWebSocketsTests {
	companion object {
		@BeforeClass
		@JvmStatic fun setupClass() {
			Looper.prepare()
		}
	}

	private lateinit var transport: Transport

	private lateinit var clientFactory: TestHttpClientFactory
	private lateinit var handlerFactory: TestHandlerFactory

	@Before
	fun setup() {
		clientFactory = TestHttpClientFactory()
		handlerFactory = TestHandlerFactory()
		transport = TransportWebSockets(httpClientFactory = clientFactory, handlerFactory = handlerFactory)
	}

	@Test
	fun testSendImageData() {
		// image message, id 12345, image payload, bogus data
		val expectedBytes = byteArrayOf(2, 0, 0, 0x30, 0x39, 0, 0, 0, 1, 0x8b.toByte(), 0xad.toByte(), 0xf0.toByte(), 0x0d, 0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())

		transport.connect(TestTransportEvents(), "http://example.com/", 30)

		val imageData = byteArrayOf(0x8b.toByte(), 0xad.toByte(), 0xf0.toByte(), 0x0d, 0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())
		transport.sendImageData(12345, ImageTag.IMAGE.imageType, imageData)
		val socket = clientFactory.client.socket
		assertNotNull("Socket not initialized", socket)
		if (socket == null) { return }
		val sentBytes = socket.sentBytes
		assertNotNull("No data sent", sentBytes)
		if (sentBytes == null) { return }

		assertTrue("Expected ($expectedBytes) and actual data sent ($sentBytes) did not match", Arrays.equals(expectedBytes, sentBytes.toByteArray()))
	}

	@Test
	fun testSendImageData_Thumbnail() {
		// image message, id 12345, image payload, bogus data
		val expectedBytes = byteArrayOf(2, 0, 0, 0x30, 0x39, 0, 0, 0, 2, 0x8b.toByte(), 0xad.toByte(), 0xf0.toByte(), 0x0d, 0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())

		transport.connect(TestTransportEvents(), "http://example.com/", 30)

		val imageData = byteArrayOf(0x8b.toByte(), 0xad.toByte(), 0xf0.toByte(), 0x0d, 0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())
		transport.sendImageData(12345, ImageTag.THUMBNAIL.imageType, imageData)
		val socket = clientFactory.client.socket
		assertNotNull("Socket not initialized", socket)
		if (socket == null) { return }
		val sentBytes = socket.sentBytes
		assertNotNull("No data sent", sentBytes)
		if (sentBytes == null) { return }

		assertTrue("Expected ($expectedBytes) and actual data sent ($sentBytes) did not match", Arrays.equals(expectedBytes, sentBytes.toByteArray()))
	}

	// Verify correct behavior when we receive image data
	@Test
	fun testReceiveThumbnailData_SendsToListener() {
		val transportListener = TestTransportEvents()
		transport.connect(transportListener, "http://example.com/", 30)

		val socket = clientFactory.client.socket
		assertNotNull("No socket", socket)
		if (socket == null) { return }
		val listener = socket.listener

		val expectedImageData = byteArrayOf(0x8b.toByte(), 0xad.toByte(), 0xf0.toByte(), 0x0d, 0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())

		val thumbnailBytes = byteArrayOf(2, 0, 0, 0x30, 0x39, 0, 0, 0, 2, 0x8b.toByte(), 0xad.toByte(), 0xf0.toByte(), 0x0d, 0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())
		listener.onMessage(socket, ByteString.of(thumbnailBytes, 0, thumbnailBytes.size))
		assertEquals("Didn't pass on image id", 12345, transportListener.receivedImageId)
		assertEquals("Didn't pass on image type", 2, transportListener.receivedImageType)
		assertTrue("Actual data ${transportListener.receivedImageData?.hexString} didn't match expected ${expectedImageData.hexString}", Arrays.equals(expectedImageData, transportListener.receivedImageData))
	}

	@Test
	fun testReceiveImageData_SendsToListener() {
		val transportListener = TestTransportEvents()
		transport.connect(transportListener, "http://example.com/", 30)

		val socket = clientFactory.client.socket
		assertNotNull("No socket", socket)
		if (socket == null) { return }
		val listener = socket.listener

		val expectedImageData = byteArrayOf(0x8b.toByte(), 0xad.toByte(), 0xf0.toByte(), 0x0d, 0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())

		val thumbnailBytes = byteArrayOf(2, 0, 0, 0x30, 0x39, 0, 0, 0, 1, 0x8b.toByte(), 0xad.toByte(), 0xf0.toByte(), 0x0d, 0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())
		listener.onMessage(socket, ByteString.of(thumbnailBytes, 0, thumbnailBytes.size))
		assertEquals("Didn't pass on image id", 12345, transportListener.receivedImageId)
		assertEquals("Didn't pass on image type", 1, transportListener.receivedImageType)
		assertTrue("Actual data ${transportListener.receivedImageData?.hexString} didn't match expected ${expectedImageData.hexString}", Arrays.equals(expectedImageData, transportListener.receivedImageData))
	}
}
