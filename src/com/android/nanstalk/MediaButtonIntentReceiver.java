package com.android.nanstalk;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.Log;
import android.view.KeyEvent;

public class MediaButtonIntentReceiver extends BroadcastReceiver {
	private static final String TAG = "MediaButtonIntentReceiver";
	private AudioManager mAudioManager;
	private NansTalkService mContext;
	
	public MediaButtonIntentReceiver() {
	    super();
	    mAudioManager = ((NansTalkService)NansTalkService.mContext).mAudioManager;
	    mContext = (NansTalkService)NansTalkService.mContext;
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		String intentAction = intent.getAction();
		Log.i(TAG, "onReceive(), intentAction=" + intentAction);
		
		if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(intentAction)) {
			mAudioManager.registerMediaButtonEventReceiver(new ComponentName(mContext.getPackageName(), MediaButtonIntentReceiver.class.getName()));
			return;
		}
		
		if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intentAction)) {
			mAudioManager.unregisterMediaButtonEventReceiver(new ComponentName(mContext.getPackageName(), MediaButtonIntentReceiver.class.getName()));
			return;
		}
		
		if (!Intent.ACTION_MEDIA_BUTTON.equals(intentAction))
			return;
		
	    KeyEvent event = (KeyEvent)intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
	    if (event == null)
	    	return;

	    int action = event.getAction();
	    int code = event.getKeyCode();
	    Log.d(TAG, "  MediaButton is clicked!!");
	    if (action == KeyEvent.ACTION_UP) {
	    	if (code == KeyEvent.KEYCODE_MEDIA_PREVIOUS || code == KeyEvent.KEYCODE_MEDIA_NEXT) {
	    		mContext.setCurrentRunningPackage();
	    		mContext.speechToText();
	    		abortBroadcast();
	    	}
	    }
	}
}