package com.zello.channel.sdk.image

import android.graphics.Bitmap
import com.zello.channel.sdk.SendImageError
import com.zello.channel.sdk.SentImageCallback
import com.zello.channel.sdk.commands.CommandSendImage
import com.zello.channel.sdk.transport.Transport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ImageTag(val imageType: Int) {
	IMAGE(1),
	THUMBNAIL(2)
}

internal interface ImageMessageManager {
	fun sendImage(image: Bitmap, transport: Transport, recipient: String? = null, continuation: SentImageCallback? = null)

}

internal class ImageMessageManagerImpl(private val backgroundScope: CoroutineScope = GlobalScope,
									   private val mainThreadDispatcher: CoroutineDispatcher = Dispatchers.Main) : ImageMessageManager {

	override fun sendImage(image: Bitmap, transport: Transport, recipient: String?, continuation: SentImageCallback?) {
		backgroundScope.launch {
			val resizedImage = ImageUtils.bitmapWithMaxDimensions(image, maxDimension, maxDimension)
			val thumbnail = ImageUtils.bitmapWithMaxDimensions(image, maxThumbnailDimension, maxThumbnailDimension)
			val imageBytes = ImageUtils.compressImage(resizedImage, maxSize = maxImageDataLength)
			val thumbnailBytes = ImageUtils.compressImage(thumbnail, maxSize = Int.MAX_VALUE)
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

	companion object {
		const val maxDimension = 1280
		const val maxThumbnailDimension = 90
		const val maxImageDataLength = 524288
	}
}
