package com.android.nanstalk;

import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaSession;
import android.util.Log;
import android.view.KeyEvent;

public class MediaButtonIntentReceiver extends BroadcastReceiver {
	private static final String TAG = "MediaButtonIntentReceiver";
	private NansTalkService mContext;
	
	// hschoi 5.0
	//private AudioManager mAudioManager;	
	private MediaSession mMediaSession;
	private MediaSession.Callback mMediaSessionCallback = new MediaSession.Callback() {
		@Override
	    public boolean onMediaButtonEvent(final Intent mediaButtonIntent) {
	        Log.i(TAG, "MediaButtonEvent!");
			String intentAction = mediaButtonIntent.getAction();
			Log.i(TAG, "mediaButtonIntentAction = " + intentAction);
			
	        KeyEvent event = (KeyEvent)mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
		    if (event == null)
		    	return super.onMediaButtonEvent(mediaButtonIntent);

		    int action = event.getAction();
		    int code = event.getKeyCode();
		    Log.i(TAG, "event.action = " + action + ", event.code = "); // event.action = 0 => ACTION_DOWN, 1 => ACTION_UP, 2 => ACTION_MULTIPLE
		    if (action == KeyEvent.ACTION_UP) {
		    	if (code == KeyEvent.KEYCODE_MEDIA_PREVIOUS || code == KeyEvent.KEYCODE_MEDIA_NEXT) {
		    		mContext.setCurrentRunningPackage();
		    		mContext.speechToText();
		    	}
		    }	
		    
		    return super.onMediaButtonEvent(mediaButtonIntent); 
	    }
	};
	
	public MediaButtonIntentReceiver() {
	    super(); 
	    Log.i(TAG, "MediaButtonIntentReceiver Created");
	    mContext = (NansTalkService)NansTalkService.mContext;	    
	    
	    // hschoi 5.0
	    //mAudioManager = ((NansTalkService)NansTalkService.mContext).mAudioManager;
	
	    mMediaSession = new MediaSession(mContext, TAG);
		mMediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
		Intent intent = new Intent(mContext, NansTalkService.class);
		PendingIntent pendingIntent = PendingIntent.getService(mContext, 0, intent, 0);
		mMediaSession.setMediaButtonReceiver(pendingIntent);
		//mMediaSession.setMediaButtonReceiver(PendingIntent.getBroadcast(mContext, 0, new Intent(mContext, MediaButtonIntentReceiver.class), 0));
		mMediaSession.setCallback(mMediaSessionCallback);
		mMediaSession.setActive(true);
/*
		PlaybackState state = new PlaybackState.Builder()
			    .setActions(PlaybackState.ACTION_PLAY)
			    .setState(PlaybackState.STATE_STOPPED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0)
			    .build();
		mMediaSession.setPlaybackState(state);
		*/
	    
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		String intentAction = intent.getAction();
		Log.i(TAG, "onReceive(), intentAction=" + intentAction);
		
		if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(intentAction)) {
			// hschoi 5.0
			//mAudioManager.registerMediaButtonEventReceiver(PendingIntent.getBroadcast(mContext, 0, new Intent(mContext, MediaButtonIntentReceiver.class), 0));
			/*mMediaSession.setMediaButtonReceiver(PendingIntent.getBroadcast(mContext, 0, new Intent(mContext, MediaButtonIntentReceiver.class), 0));
			mMediaSession.setCallback(new Callback() {
				@Override
			    public boolean onMediaButtonEvent(final Intent mediaButtonIntent) {
			        Log.i(TAG, "GOT EVENT");
			        return super.onMediaButtonEvent(mediaButtonIntent);
			    }
			});
			mMediaSession.setActive(true);
			*/
			mContext = (NansTalkService)NansTalkService.mContext;	    
		    
		    // hschoi 5.0
		    //mAudioManager = ((NansTalkService)NansTalkService.mContext).mAudioManager;
		    /*
			mMediaSession = new MediaSession(mContext, TAG);
			mMediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
			mMediaSession.setMediaButtonReceiver(PendingIntent.getBroadcast(mContext, 0, new Intent(mContext, MediaButtonIntentReceiver.class), 0));
			mMediaSession.setCallback(mMediaSessionCallback);
			mMediaSession.setActive(true);*/
			
			return;
		}
		
		if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intentAction)) {
			// 5.0 depricated		
			//mAudioManager.unregisterMediaButtonEventReceiver(new ComponentName(mContext.getPackageName(), MediaButtonIntentReceiver.class.getName()));
			//mMediaSession.release();
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
	    //if (action == KeyEvent.ACTION_UP) {
	    if (action == KeyEvent.ACTION_DOWN) {
	    	if (code == KeyEvent.KEYCODE_MEDIA_PREVIOUS || code == KeyEvent.KEYCODE_MEDIA_NEXT) {
	    		mContext.setCurrentRunningPackage();
	    		mContext.speechToText();
	    		abortBroadcast();
	    	}
	    }
	}
}