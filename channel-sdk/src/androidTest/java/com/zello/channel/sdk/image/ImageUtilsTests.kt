package com.zello.channel.sdk.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color.argb
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zello.channel.sdk.platform.compressedToJpeg
import com.zello.channel.sdk.platform.resizedToMaxDimensions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

internal object TestImageUtils {
	fun createRedBitmap(width: Int, height: Int): Bitmap {
		val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
		val red = argb(1.0f, 1.0f, 0.0f, 0.0f)
		val canvas = Canvas(bitmap)
		val paint = Paint()
		paint.color = red
		canvas.drawRect(0.0f, 0.0f, width.toFloat(), height.toFloat(), paint)
		return bitmap
	}
}

@RunWith(AndroidJUnit4::class)
class ImageUtilsTests {
	@Test
	fun testBitmapWithMaxDimensions() {
		val original = TestImageUtils.createRedBitmap(100, 100)

		// Verify that we don't resize images that fit the max dimensions
		val resized = original.resizedToMaxDimensions(Dimensions.square(100))
		assertEquals(100, resized.width)
		assertEquals(100, resized.height)

		// Verify that we resize an image whose height is too large
		val resizedTooTall = original.resizedToMaxDimensions(Dimensions(100, 60))
		assertEquals("wrong width", 60, resizedTooTall.width)
		assertEquals("wrong height",60, resizedTooTall.height)

		// Verify that we resize an image whose width is too large
		val resizedTooWide = original.resizedToMaxDimensions(Dimensions(40, 100))
		assertEquals("wrong width", 40, resizedTooWide.width)
		assertEquals("wrong height", 40, resizedTooWide.height)
	}

	@Test
	fun testCompressImage() {
		val original = TestImageUtils.createRedBitmap(100, 100)
		val compressed = original.compressedToJpeg(Int.MAX_VALUE)
		val decoded = BitmapFactory.decodeByteArray(compressed, 0, compressed.size)
		assertNotNull("Failed to decode image", decoded)
		assertEquals("Decoded to wrong width", 100, decoded.width)
		assertEquals("Decoded to wrong height", 100, decoded.height)

		// Test with complex image that will require multiple passes
		val fractalStream = javaClass.classLoader?.getResourceAsStream("fractal_Wallpaper_011.jpg")
		val fractal = BitmapFactory.decodeStream(fractalStream)
		assertNotNull("Failed to load test image", fractal)
		val compressedFractal = fractal.compressedToJpeg(400000)
		assertTrue("Compressed test image too large", compressedFractal.size <= 400000)
		val decodedFractal = BitmapFactory.decodeByteArray(compressedFractal, 0, compressedFractal.size)
		assertNotNull("Failed to decode test image", decodedFractal)
		assertEquals(fractal.width, decodedFractal.width)
		assertEquals(fractal.height, decodedFractal.height)

		// Test with size that's too small to fit the image
		val compressedGaveUp = fractal.compressedToJpeg(10000)
		assertTrue(compressedGaveUp.size > 10000)
		val decodedGaveUp = BitmapFactory.decodeByteArray(compressedGaveUp, 0, compressedGaveUp.size)
		assertNotNull("Failed to decode too large test image", decodedGaveUp)
		assertEquals(fractal.width, decodedGaveUp.width)
		assertEquals(fractal.height, decodedGaveUp.height)
	}
}
