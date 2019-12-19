package com.zello.channel.sdk;

import androidx.annotation.Nullable;

/**
 * Used for callbacks from send location methods to report the location back to the caller.
 */
public interface SentLocationCallback {
	/**
	 * Called after the current location is found and reverse geocoding is performed
	 *
	 * @param location GPS coordinates and reverse geocoded address that was sent to the channel, or
	 * `null` if an error occurred determining the user's location
	 * @param error If location is `null`, this contains information about the error that occurred.
 	 */
	void onLocationSent(@Nullable Location location, @Nullable SendLocationError error);
}
