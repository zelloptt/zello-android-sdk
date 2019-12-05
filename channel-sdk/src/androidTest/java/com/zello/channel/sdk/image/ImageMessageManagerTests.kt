package com.zello.channel.sdk.image

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zello.channel.sdk.ImageInfo
import com.zello.channel.sdk.InvalidImageMessageError
import com.zello.channel.sdk.TestTransport
import com.zello.channel.sdk.platform.hexString
import com.zello.channel.sdk.platform.resizedToMaxDimensions
import junit.framework.Assert.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.Arrays
import java.util.concurrent.CancellationException
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
	fun setup(coroutineScope: CoroutineScope, incomingImageTimeout: Long = 2 * 60 * 1000L) {
		val immediateExecutor = Executor { p0 -> p0.run() }
		val listener = object : ImageMessageManagerListener {
			override fun onImageMessage(message: ImageInfo) {
				receivedImageMessage = message
			}

			override fun onInvalidImageMessage(error: InvalidImageMessageError) { }
		}
		manager = ImageMessageManagerImpl(listener, backgroundScope = coroutineScope, mainThreadDispatcher = immediateExecutor.asCoroutineDispatcher(), incomingImageTimeout = incomingImageTimeout)
	}

	@Test
	fun testIsTypeValid() {
		runBlocking {
			setup(this)

			assertTrue(manager.isTypeValid("jpeg"))
			assertFalse(manager.isTypeValid("png"))
		}
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
			manager.sendImage(image, transport, continuation = { imageId, error ->
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
			manager.sendImage(image, transport, recipient = "bogusRecipient", continuation = { imageId, error ->
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
			manager.sendImage(image, transport, continuation = { imageId, error ->
				continuationCalled = true
				assertEquals(0, imageId)
				assertNotNull(error)
				if (error == null) { return@sendImage }
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
		try {
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
				if (received == null) {
					return@runBlocking
				}
				assertEquals(12345, received.imageId)
				assertEquals("bogusSender", received.sender)
				assertNull(received.image)
				val receivedThumbnail = received.thumbnail
				assertNotNull(receivedThumbnail)
				if (receivedThumbnail == null) {
					return@runBlocking
				}
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
				if (second == null) {
					return@runBlocking
				}
				assertEquals(12345, second.imageId)
				assertEquals("bogusSender", second.sender)
				assertNotNull(second.thumbnail)
				val receivedImage = second.image
				assertNotNull(receivedImage)
				if (receivedImage == null) {
					return@runBlocking
				}
				assertEquals(500, receivedImage.width)
				assertEquals(400, receivedImage.height)

				cancel() // Don't wait for incoming image cache cleanup
			}
		} catch (e: CancellationException) {
			// Don't care
		}
	}

	// Verify that listener receives one event when thumbnail comes in after image
	@Test
	fun testOnImageData_ImageThenThumbnail_CallsListenerOnce() {
		try {
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
				if (received == null) {
					return@runBlocking
				}
				assertEquals(12345, received.imageId)
				assertEquals("bogusSender", received.sender)
				assertNull(received.thumbnail)
				val receivedImage = received.image
				assertNotNull(receivedImage)
				if (receivedImage == null) {
					return@runBlocking
				}
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

				cancel() // Don't wait for incoming image cache cleanup
			}
		} catch (e: CancellationException) {
			// Don't care
		}
	}

	// Verify that cache is cleaned up after timeout
	@Test
	fun testIncomingImagesCleanup() {
		runBlocking {
			setup(coroutineScope = this, incomingImageTimeout = 100L)

			manager.onImageHeader(12345, "bogusSender", Dimensions(500, 400))

			// Wait for incoming image cache cleanup to run
			delay(200L)

			val thumbnail = TestImageUtils.createRedBitmap(90, 72)
			val thumbnailStream = ByteArrayOutputStream()
			thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, thumbnailStream)
			manager.onImageData(12345, ImageTag.THUMBNAIL.imageType, thumbnailStream.toByteArray())
			yield() // Required to let ImageMessageManager's coroutines run

			val first = receivedImageMessage
			assertNull("Recieved image message that should have timed out.", first)

			manager.onImageHeader(4567, "bogusSender", Dimensions(500, 400))
			manager.onImageData(4567, ImageTag.THUMBNAIL.imageType, thumbnailStream.toByteArray())
			yield()

			val second = receivedImageMessage
			assertNotNull("Didn't get thumbnail.", second)
			if (second == null) { return@runBlocking }
			assertEquals(4567, second.imageId)
			assertEquals("bogusSender", second.sender)
			assertNull(second.image)
			val receivedThumbnail = second.thumbnail
			assertNotNull(receivedThumbnail)
			if (receivedThumbnail == null) {
				return@runBlocking
			}
			assertEquals(90, receivedThumbnail.width)
			assertEquals(72, receivedThumbnail.height)

			receivedImageMessage = null
			delay(200L)
			val fullSized = TestImageUtils.createRedBitmap(500, 400)
			val fullSizedStream = ByteArrayOutputStream()
			fullSized.compress(Bitmap.CompressFormat.JPEG, 90, fullSizedStream)
			manager.onImageData(4567, ImageTag.IMAGE.imageType, fullSizedStream.toByteArray())
			val third = receivedImageMessage
			assertNull("Received full-sized image should have timed out.", third)
		}
	}

	@Test
	fun testIncomingImagesCleanup_OnlyCleansTimedOut() {
		runBlocking {
			val thumbnail = TestImageUtils.createRedBitmap(90, 90)
			val thumbnailBytes = ByteArrayOutputStream()
			thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, thumbnailBytes)

			setup(coroutineScope = this, incomingImageTimeout = 100L)

			manager.onImageHeader(1234, "bogusSender", Dimensions.square(100))

			delay(75L)

			manager.onImageHeader(2345, "bogusSender", Dimensions.square(100))

			delay(50L)

			manager.onImageData(1234, ImageTag.THUMBNAIL.imageType, thumbnailBytes.toByteArray())
			yield()

			val timedOut = receivedImageMessage
			assertNull("First message should have timed out.", timedOut)

			manager.onImageData(2345, ImageTag.THUMBNAIL.imageType, thumbnailBytes.toByteArray())
			yield()

			val received = receivedImageMessage
			assertNotNull("Second message should not have timed out.", received)
			if (received == null) { return@runBlocking }
			assertEquals(2345, received.imageId)
			assertEquals("bogusSender", received.sender)
			assertNull(received.image)
			val receivedThumbnail = received.thumbnail
			assertNotNull(receivedThumbnail)
			if (receivedThumbnail == null) { return@runBlocking }
			assertEquals(90, receivedThumbnail.width)
			assertEquals(90, receivedThumbnail.height)
		}
	}

}
