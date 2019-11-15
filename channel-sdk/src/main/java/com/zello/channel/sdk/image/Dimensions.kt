package com.zello.channel.sdk.image

internal data class Dimensions(val width: Int, val height: Int) {
	companion object {
		fun square(dimension: Int) = Dimensions(dimension, dimension)
	}
}
