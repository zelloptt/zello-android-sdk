package com.zello.channel.sdk

import android.graphics.Bitmap

/**
 * Encapsulates information about a received image
 *
 * @property imageId The server-assigned id for this image. Use the `imageId` to associate a thumbnail
 * and a full-sized image, when they arrive separately.
 * @property sender The username of the user who sent the image message
 * @property thumbnail If the image is larger than 90x90, a thumbnail image will usually be provided
 * to increase responsiveness while the larger image is being transferred
 * @property image Full-sized image
 */
data class ImageInfo internal constructor(val imageId: Int, val sender: String, val thumbnail: Bitmap?, val image: Bitmap?)
