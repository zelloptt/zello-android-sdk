package com.zello.channel.sdk.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import com.zello.channel.sdk.ImageInfo
import com.zello.channel.sdk.SendImageError
import com.zello.channel.sdk.SentImageCallback
import com.zello.channel.sdk.commands.CommandSendImage
import com.zello.channel.sdk.platform.compressedToJpeg
import com.zello.channel.sdk.platform.dimensions
import com.zello.channel.sdk.platform.resizedToMaxDimensions
import com.zello.channel.sdk.transport.Transport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class ImageTag(val imageType: Int) {
	IMAGE(1),
	THUMBNAIL(2);

	companion object {
		private val map = ImageTag.values().associateBy(ImageTag::imageType)
		fun fromInt(tag: Int): ImageTag? = map[tag]
	}
}

internal interface ImageMessageManager {
	fun sendImage(image: Bitmap, transport: Transport, recipient: String? = null, continuation: SentImageCallback? = null)
	fun onImageHeader(imageId: Int, sender: String, dimensions: Dimensions)
	fun onImageData(imageId: Int, type: Int, data: ByteArray)
}

internal interface ImageMessageManagerListener {
	fun onImageMessage(message: ImageInfo)
}

internal data class IncomingImageInfo(val imageId: Int, val sender: String, val dimensions: Dimensions) {
	var thumbnail: Bitmap? = null
		set(value) {
			field = value
			lastTouched = SystemClock.elapsedRealtime()
		}

	var image: Bitmap? = null
		set(value) {
			field = value
			lastTouched = SystemClock.elapsedRealtime()
		}

	// ms since device boot
	var lastTouched: Long = SystemClock.elapsedRealtime()
		private set
}

// TODO: Replace GlobalScope with a class-specific coroutine scope (Global scope is recommended against?)
internal class ImageMessageManagerImpl(private val listener: ImageMessageManagerListener,
									   private val backgroundScope: CoroutineScope = GlobalScope,
									   private val mainThreadDispatcher: CoroutineDispatcher = Dispatchers.Main,
									   private val incomingImageTimeout: Long = 2 * 60 * 1000L) : ImageMessageManager {

	// TODO: Remove unreceived images if there's a low-memory situation
	private var incomingImages = hashMapOf<Int, IncomingImageInfo>()

	override fun sendImage(image: Bitmap, transport: Transport, recipient: String?, continuation: SentImageCallback?) {
		backgroundScope.launch {
			val resizedImage = image.resizedToMaxDimensions(Dimensions.square(maxDimension))
			val thumbnail = image.resizedToMaxDimensions(Dimensions.square(maxThumbnailDimension))
			val imageBytes = resizedImage.compressedToJpeg(maxImageDataLength)
			val thumbnailBytes = thumbnail.compressedToJpeg(Int.MAX_VALUE)
			val sendImageCommand = object: CommandSendImage(transport, resizedImage.dimensions, imageBytes.size, thumbnailBytes.size, recipient = recipient) {
				override fun sendImageBody(imageId: Int) {
					transport.sendImageData(imageId, ImageTag.THUMBNAIL.imageType, thumbnailBytes)
					transport.sendImageData(imageId, ImageTag.IMAGE.imageType, imageBytes)
					continuation?.onSentImage(imageId, null)
				}

				override fun onError(message: String?) {
					continuation?.onSentImage(0, SendImageError(message ?: "unknown error"))
				}
			}

			withContext(mainThreadDispatcher) {
				sendImageCommand.send()
			}
		}
	}

	override fun onImageHeader(imageId: Int, sender: String, dimensions: Dimensions) {
		backgroundScope.launch(mainThreadDispatcher) {
			val info = IncomingImageInfo(imageId, sender, dimensions)
			incomingImages[imageId] = info

			setNeedsCleanup()
		}
	}

	override fun onImageData(imageId: Int, type: Int, data: ByteArray) {
		val tag = ImageTag.fromInt(type)
		if (tag == null) {
			// TODO: Report error
			return
		}
		backgroundScope.launch {
			val decoded = BitmapFactory.decodeByteArray(data, 0, data.size)
			withContext(mainThreadDispatcher) {
				val imageInfo = incomingImages[imageId] ?: return@withContext

				val message: ImageInfo
				when(tag) {
					ImageTag.IMAGE -> {
						message = ImageInfo(imageId, imageInfo.sender, imageInfo.thumbnail, decoded)
						incomingImages.remove(imageId)
					}
					ImageTag.THUMBNAIL -> {
						imageInfo.thumbnail = decoded
						incomingImages[imageId] = imageInfo
						message = ImageInfo(imageId, imageInfo.sender, decoded, imageInfo.image)
						setNeedsCleanup()
					}
				}
				listener.onImageMessage(message)
			}
		}
	}

	/// last time we performed a cleanup (ms since system boot)
	private var lastCleanup = 0L
	private var needsCleanup = false

	/// Only call `setNeedsCleanup` on main thread
	private fun setNeedsCleanup() {
		val now = SystemClock.elapsedRealtime()
		if (now - lastCleanup < incomingImageTimeout) {
			return
		}

		needsCleanup = true

		// Schedule cleanup after incomingImageTimeout
		backgroundScope.launch {
			delay(incomingImageTimeout)
			withContext(mainThreadDispatcher) {
				cleanupIncomingImagesIfNeeded()
			}
		}
	}

	/// Only call cleanupIncomingImagesIfNeeded on main thread
	private fun cleanupIncomingImagesIfNeeded() {
		if (!needsCleanup) {
			return
		}

		val now = SystemClock.elapsedRealtime()
		val toRemove = mutableListOf<Int>()
		for ((imageId, info) in incomingImages) {
			if (now - incomingImageTimeout > info.lastTouched) {
				toRemove.add(imageId)
			}
		}
		for (imageId in toRemove) {
			incomingImages.remove(imageId)
		}
	}

	companion object {
		const val maxDimension = 1280
		const val maxThumbnailDimension = 90
		const val maxImageDataLength = 524288
	}
}
