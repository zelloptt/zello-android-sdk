package com.zello.channel.sdk.platform

import android.graphics.Bitmap
import com.zello.channel.sdk.image.Dimensions
import java.io.ByteArrayOutputStream

/// The dimensions of the bitmap. Packages the width and height into a single object.
internal val Bitmap.dimensions: Dimensions
	get() {
		return Dimensions(width, height)
	}

/// This method should NOT be called on the main thread
internal fun Bitmap.resizedToMaxDimensions(dimensions: Dimensions): Bitmap {
	fun dimensionsFitting(original: Dimensions, max: Dimensions): Dimensions {
		val widthScalingFactor = max.width.toFloat() / original.width.toFloat()
		val heightScalingFactor = max.height.toFloat() / original.height.toFloat()
		val targetHeight: Int
		val targetWidth: Int
		if (widthScalingFactor < heightScalingFactor) {
			targetWidth = max.width
			targetHeight = (original.height * widthScalingFactor).toInt()
		} else {
			targetHeight = max.height
			targetWidth = (original.width * heightScalingFactor).toInt()
		}
		return Dimensions(targetWidth, targetHeight)
	}

	fun scaleBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
		var tempBitmap = bitmap
		while (tempBitmap.width > targetWidth || tempBitmap.height > targetHeight) {
			var passTargetWidth = targetWidth
			var passTargetHeight = targetHeight
			// If we need to shrink the image by more than 2x, make multiple passes to reduce aliasing
			if ((targetWidth < tempBitmap.width / 2) || (targetHeight < tempBitmap.height / 2)) {
				passTargetWidth = tempBitmap.width / 2
				passTargetHeight = tempBitmap.height / 2
			}
			tempBitmap = Bitmap.createScaledBitmap(tempBitmap, passTargetWidth, passTargetHeight, true)
		}

		return tempBitmap
	}

	if (this.width <= dimensions.width && this.height <= dimensions.height) {
		return this
	}
	val targetDimensions = dimensionsFitting(this.dimensions, dimensions)
	return scaleBitmap(this, targetDimensions.width, targetDimensions.height)

}

/**
 * Compresses a bitmap to JPEG with a maximum data length. Makes multiple compression passes at
 * reduced quality until it finds a JPEG quality that produces compressed data less than maxSize
 * bytes. If the image at quality 10 is still larger than maxSize, it uses that data anyway.
 *
 * @param maxBytes the preferred maximum data length in bytes
 * @return a ByteArray containing the compressed image data
 */
internal fun Bitmap.compressedToJpeg(maxBytes: Int): ByteArray {
	var dataLength: Int
	var quality = 90
	val minQuality = 10
	var stream: ByteArrayOutputStream
	do {
		stream = ByteArrayOutputStream()
		this.compress(Bitmap.CompressFormat.JPEG, quality, stream)
		dataLength = stream.size()
		// If image data is too large, reduce quality by 10 and try again
		if (dataLength > maxBytes) {
			quality -= 10
		}
	} while (dataLength > maxBytes && quality >= minQuality)
	return stream.toByteArray() // Stream guaranteed to be non-null
}
