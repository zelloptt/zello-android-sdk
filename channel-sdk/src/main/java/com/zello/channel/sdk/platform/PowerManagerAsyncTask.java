package com.zello.channel.sdk.platform;

import android.annotation.SuppressLint;
import android.os.AsyncTask;

import androidx.annotation.NonNull;

@SuppressWarnings("WeakerAccess")
@SuppressLint("NewApi")
abstract class PowerManagerAsyncTask extends AsyncTask<Object, Void, Void> {

	private final PowerManager _pm;
	private final String _name;

	abstract protected void run();

	abstract protected String getType();


	PowerManagerAsyncTask(PowerManager pm, String name) {
		super();
		_pm = pm;
		_name = name;
		acquire();
	}

	public void acquire() {
		_pm.acquireCpuLock(toString());
	}

	public void release() {
		_pm.releaseCpuLock(toString());
	}

	void executeEx(Object... params) {
		try {
			executeOnExecutor(THREAD_POOL_EXECUTOR, params);
		} catch (Throwable ignore) {
			try {
				super.execute(params);
			} catch (Throwable t) {
				release();
			}
		}
	}

	@Override
	protected Void doInBackground(Object[] cb) {
		try {
			run();
		} catch (Throwable ignore) {
		} finally {
			release();
		}
		return null;
	}

	@Override
	public @NonNull String toString() {
		return getType() + ": " + _name;
	}

}
