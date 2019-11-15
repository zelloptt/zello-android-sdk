package com.zello.channel.sdk.image

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zello.channel.sdk.ImageInfo
import com.zello.channel.sdk.SentImageCallback
import com.zello.channel.sdk.TestTransport
import com.zello.channel.sdk.platform.hexString
import com.zello.channel.sdk.platform.resizedToMaxDimensions
import junit.framework.Assert.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.Arrays
import java.util.concurrent.Executor

@RunWith(AndroidJUnit4::class)
class ImageMessageManagerTests {
	private lateinit var manager: ImageMessageManagerImpl
	private lateinit var transport: TestTransport

	@Before
	fun setup() {
		transport = TestTransport()
	}

	private var receivedImageMessage: ImageInfo? = null
	fun setup(coroutineScope: CoroutineScope) {
		val immediateExecutor = Executor { p0 -> p0.run() }
		val listener = object : ImageMessageManagerListener {
			override fun onImageMessage(message: ImageInfo) {
				receivedImageMessage = message
			}
		}
		manager = ImageMessageManagerImpl(listener, backgroundScope = coroutineScope, mainThreadDispatcher = immediateExecutor.asCoroutineDispatcher())
	}

	@Test
	fun testSendImage() {
		runBlocking {
			setup(coroutineScope = this)

			val image = TestImageUtils.createRedBitmap(100, 120)
			val expectedThumbnail = image.resizedToMaxDimensions(Dimensions.square(90))
			val imageByteStream = ByteArrayOutputStream()
			image.compress(Bitmap.CompressFormat.JPEG, 90, imageByteStream)
			val thumbnailByteStream = ByteArrayOutputStream()
			expectedThumbnail.compress(Bitmap.CompressFormat.JPEG, 90, thumbnailByteStream)
			var continuationCalled = false
			manager.sendImage(image, transport, continuation = SentImageCallback { imageId, error ->
				continuationCalled = true
				assertNull(error)
				assertEquals(12345, imageId)
			})
			yield() // Required for coroutines called in sendImage to run

			assertEquals("send_image not sent", "send_image", transport.sentCommand)
			val json = transport.sentJson
			assertNotNull(json)
			if (json == null) { return@runBlocking }
			assertEquals("jpeg", json.optString("type"))
			assertEquals(100, json.optInt("width"))
			assertEquals(120, json.optInt("height"))
			assertEquals(imageByteStream.size(), json.optInt("content_length"))
			assertEquals(thumbnailByteStream.size(), json.optInt("thumbnail_content_length"))
			val responseHandler = transport.responseHandler

			// Send response and verify data messages
			assertNotNull(responseHandler)
			if (responseHandler == null) { return@runBlocking }
			val response = JSONObject()
			response.put("success", true)
			response.put("image_id", 12345)
			responseHandler.onResponse(response)
			assertTrue("SentImageCallback wasn't called", continuationCalled)
			assertEquals(12345, transport.sentThumbnailId)
			assertTrue("Expected thumbnail data ${thumbnailByteStream.toByteArray().hexString} but got ${transport.sentThumbnailData!!.hexString}", Arrays.equals(thumbnailByteStream.toByteArray(), transport.sentThumbnailData))
			assertEquals(12345, transport.sentImageId)
			assertTrue("Expected image data ${imageByteStream.toByteArray().hexString} but got ${transport.sentImageData!!.hexString}", Arrays.equals(imageByteStream.toByteArray(), transport.sentImageData))
		}
	}

	@Test
	fun testSendImageToRecipient() {
		runBlocking {
			setup(coroutineScope = this)

			val image = TestImageUtils.createRedBitmap(100, 120)
			val expectedThumbnail = image.resizedToMaxDimensions(Dimensions.square(90))
			val imageByteStream = ByteArrayOutputStream()
			image.compress(Bitmap.CompressFormat.JPEG, 90, imageByteStream)
			val thumbnailByteStream = ByteArrayOutputStream()
			expectedThumbnail.compress(Bitmap.CompressFormat.JPEG, 90, thumbnailByteStream)
			var continuationCalled = false
			manager.sendImage(image, transport, recipient = "bogusRecipient", continuation = SentImageCallback { imageId, error ->
				continuationCalled = true
				assertNull(error)
				assertEquals(12345, imageId)
			})
			yield() // Required for coroutines called in sendImage to run

			assertEquals("send_image not sent", "send_image", transport.sentCommand)
			val json = transport.sentJson
			assertNotNull(json)
			if (json == null) { return@runBlocking }
			assertEquals("jpeg", json.optString("type"))
			assertEquals(100, json.optInt("width"))
			assertEquals(120, json.optInt("height"))
			assertEquals(imageByteStream.size(), json.optInt("content_length"))
			assertEquals(thumbnailByteStream.size(), json.optInt("thumbnail_content_length"))
			assertEquals("Wrong or missing recipient", "bogusRecipient", json.optString("for"))
			val responseHandler = transport.responseHandler

			// Send response and verify data messages
			assertNotNull(responseHandler)
			if (responseHandler == null) { return@runBlocking }
			val response = JSONObject()
			response.put("success", true)
			response.put("image_id", 12345)
			responseHandler.onResponse(response)
			assertTrue("SentImageCallback wasn't called", continuationCalled)
			assertEquals(12345, transport.sentThumbnailId)
			assertTrue("Expected thumbnail data ${thumbnailByteStream.toByteArray().hexString} but got ${transport.sentThumbnailData!!.hexString}", Arrays.equals(thumbnailByteStream.toByteArray(), transport.sentThumbnailData))
			assertEquals(12345, transport.sentImageId)
			assertTrue("Expected image data ${imageByteStream.toByteArray().hexString} but got ${transport.sentImageData!!.hexString}", Arrays.equals(imageByteStream.toByteArray(), transport.sentImageData))
		}
	}

	@Test
	fun testSendImage_Error_CallsContinuation() {
		runBlocking {
			setup(coroutineScope = this)

			val image = TestImageUtils.createRedBitmap(100, 120)
			var continuationCalled = false
			manager.sendImage(image, transport, continuation = SentImageCallback { imageId, error ->
				continuationCalled = true
				assertEquals(0, imageId)
				assertNotNull(error)
				if (error == null) { return@SentImageCallback }
				assertEquals("Test error message", error.errorMessage)
			})
			yield()

			assertEquals("send_image not sent", "send_image", transport.sentCommand)
			val responseHandler = transport.responseHandler

			assertNotNull(responseHandler)
			if (responseHandler == null) { return@runBlocking }
			val response = JSONObject()
			response.put("success", false)
			response.put("error", "Test error message")
			responseHandler.onResponse(response)
			assertTrue("SentImageCallback wasn't called", continuationCalled)
		}
	}

	// Verify that listener receives two events when image comes in after thumbnail
	@Test
	fun testOnImageData_ThumbnailThenImage_CallsListenerTwice() {
		runBlocking {
			setup(coroutineScope = this)

			val thumbnail = TestImageUtils.createRedBitmap(90, 72)
			val thumbnailStream = ByteArrayOutputStream()
			thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, thumbnailStream)
			manager.onImageHeader(12345, "bogusSender", Dimensions(500, 400))
			manager.onImageData(12345, ImageTag.THUMBNAIL.imageType, thumbnailStream.toByteArray())
			yield() // Required to let ImageMessageManager's coroutines run

			val received = receivedImageMessage
			assertNotNull("Listener not called", received)
			if (received == null) { return@runBlocking }
			assertEquals(12345, received.imageId)
			assertEquals("bogusSender", received.sender)
			assertNull(received.image)
			val receivedThumbnail = received.thumbnail
			assertNotNull(receivedThumbnail)
			if (receivedThumbnail == null) { return@runBlocking }
			assertEquals(90, receivedThumbnail.width)
			assertEquals(72, receivedThumbnail.height)

			// Verify behavior when full-sized image is received
			receivedImageMessage = null
			val fullSized = TestImageUtils.createRedBitmap(500, 400)
			val fullSizedStream = ByteArrayOutputStream()
			fullSized.compress(Bitmap.CompressFormat.JPEG, 90, fullSizedStream)
			manager.onImageData(12345, ImageTag.IMAGE.imageType, fullSizedStream.toByteArray())
			yield()

			val second = receivedImageMessage
			assertNotNull("Listener not called for full-sized image", second)
			if (second == null) { return@runBlocking }
			assertEquals(12345, second.imageId)
			assertEquals("bogusSender", second.sender)
			assertNotNull(second.thumbnail)
			val receivedImage = second.image
			assertNotNull(receivedImage)
			if (receivedImage == null) { return@runBlocking }
			assertEquals(500, receivedImage.width)
			assertEquals(400, receivedImage.height)
		}
	}

	// Verify that listener receives one event when thumbnail comes in after image
	@Test
	fun testOnImageData_ImageThenThumbnail_CallsListenerOnce() {
		runBlocking {
			setup(coroutineScope = this)

			manager.onImageHeader(12345, "bogusSender", Dimensions(500, 400))

			val fullSized = TestImageUtils.createRedBitmap(500, 400)
			val fullSizedStream = ByteArrayOutputStream()
			fullSized.compress(Bitmap.CompressFormat.JPEG, 90, fullSizedStream)
			manager.onImageData(12345, ImageTag.IMAGE.imageType, fullSizedStream.toByteArray())
			yield()

			val received = receivedImageMessage
			assertNotNull("Listener not called for full-sized image", received)
			if (received == null) { return@runBlocking }
			assertEquals(12345, received.imageId)
			assertEquals("bogusSender", received.sender)
			assertNull(received.thumbnail)
			val receivedImage = received.image
			assertNotNull(receivedImage)
			if (receivedImage == null) { return@runBlocking }
			assertEquals(500, receivedImage.width)
			assertEquals(400, receivedImage.height)
			receivedImageMessage = null

			val thumbnail = TestImageUtils.createRedBitmap(90, 72)
			val thumbnailStream = ByteArrayOutputStream()
			thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, thumbnailStream)
			manager.onImageData(12345, ImageTag.THUMBNAIL.imageType, thumbnailStream.toByteArray())
			yield() // Required to let ImageMessageManager's coroutines run

			val second = receivedImageMessage
			assertNull("Listener called for thumbnail", second)
		}
	}

}
