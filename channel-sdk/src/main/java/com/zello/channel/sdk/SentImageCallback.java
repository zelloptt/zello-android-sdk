package com.zello.channel.sdk;

import android.support.annotation.Nullable;

public interface SentImageCallback {

	/**
	 *
	 * @param imageId
	 * @param error
	 */
	void onSentImage(int imageId, @Nullable SendImageError error);

}
