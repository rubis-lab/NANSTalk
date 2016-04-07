package com.android.nanstalk;

import java.util.List;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;

import net.daum.mf.speech.api.SpeechRecognizeListener;
import net.daum.mf.speech.api.SpeechRecognizerClient;
import net.daum.mf.speech.api.SpeechRecognizerManager;
import net.daum.mf.speech.api.TextToSpeechClient;
import net.daum.mf.speech.api.TextToSpeechListener;
import net.daum.mf.speech.api.TextToSpeechManager;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
//import android.hardware.display.WifiDisplay;
//import android.hardware.display.WifiDisplayStatus;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.session.MediaSession;
import android.media.session.MediaSession.Callback;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.android.nanstalk.CommandManager.Cid;
import com.android.nanstalk.CommandManager.CommandContent;

public class NansTalkService extends Service implements SpeechRecognizeListener, TextToSpeechListener {
	private final static String TAG = "NansTalkService";
	public static Context mContext;
	
	 public static final String MEDIA = "action_play";
	
	// System Services
	private WindowManager mWindowManager;
	private ActivityManager mActivityManager;
	private InputManager mInputManager;
	private CommandManager mCommandManager;
	private NotificationManager mNotificationManager;
	public AudioManager mAudioManager;
	public DisplayManager mDisplayManager;
	
	// hschoi
	public boolean socketNetwork = false;
	public int audioUse;
	private MediaSession mMediaSession;
	private MediaSession.Callback mMediaSessionCallback; 
	
	
	
	// Variables for Small Window
	private TextView mPopupView;
	private WindowManager.LayoutParams mParams;
	private Handler mHandler;
	private boolean mLongClicked = false;
	private int PREV_X, PREV_Y;
	private int MAX_X = -1, MAX_Y = -1;
	private float START_X, START_Y;
	private static final int SHORT_CLICK = 1;
	private static final int LONG_CLICK = 2;
	private static final int LONG_LONG_CLICK = 3;
	
	// Speech Recognizer & Text To Speech Clients
	private final String apikey = "cdbd8f93a638f8af0d9f4f058117805c";
	private SpeechRecognizerClient sttClient;
	private TextToSpeechClient ttsClient;
	
	// SoundPool for Beep
	private SoundPool mSoundPool;
	private int mSoundBeepId;
	
	//private String lastControlPackage = "";
	private String currentRunningPackage = "";	
	
    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "NansTalkService Created -------------------------- ye");
        
        // Add Small Window
        mPopupView = new TextView(this);
		mPopupView.setText("R");
		mPopupView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
		mPopupView.setTextColor(Color.BLACK);
		mPopupView.setBackgroundColor(Color.argb(30, 255, 255, 255));
		mPopupView.setOnTouchListener(mViewTouchListener);
		mPopupView.setPadding(50, 20, 50, 20);
		mParams = new WindowManager.LayoutParams(
			WindowManager.LayoutParams.WRAP_CONTENT,
			WindowManager.LayoutParams.WRAP_CONTENT,
			WindowManager.LayoutParams.TYPE_PHONE,
			WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
			PixelFormat.TRANSLUCENT);
		
		// hschoi 5.0
		//mParams.gravity = Gravity.TOP | Gravity.RIGHT;
		mParams.gravity = Gravity.TOP | Gravity.END;
		
		mHandler = new keyHandler();
		mContext = this;
		
		// Add System Managers
		mWindowManager = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
		mWindowManager.addView(mPopupView, mParams); 
		mActivityManager = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
		mInputManager = (InputManager)getSystemService(Context.INPUT_SERVICE);
		mDisplayManager = (DisplayManager)getSystemService(Context.DISPLAY_SERVICE);
		mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		mCommandManager = new CommandManager(this.getContentResolver());
		// hschoi 5.0
		//mAudioManager.registerMediaButtonEventReceiver(new ComponentName(getPackageName(), MediaButtonIntentReceiver.class.getName()));

		// Register MediaButtonEventReceiver
		MediaButtonIntentReceiver mReceiver = new MediaButtonIntentReceiver();
		
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
		filter.addAction(Intent.ACTION_MEDIA_BUTTON);
	    filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);	    
		//filter.setPriority(2147483647);
	    registerReceiver(mReceiver, filter);
	    	    
		// Initialize Speech Recognizer & Text To Speech Clients
		SpeechRecognizerManager.getInstance().initializeLibrary(this);
		TextToSpeechManager.getInstance().initializeLibrary(getApplicationContext());
		
		// Add Top Notification
		NotificationCompat.Builder builder = 
	            new NotificationCompat.Builder(this)
	            .setSmallIcon(R.drawable.ic_launcher)
	            .setContentTitle("NANS 서비스 실행중")
	            .setContentText("클릭하면 미라캐스트 설정으로 이동합니다.");
	    Intent intent = new Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS);
	    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent,   
	            PendingIntent.FLAG_UPDATE_CURRENT);
	    builder.setContentIntent(contentIntent);
	    mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);  
	    mNotificationManager.notify(4542, builder.build());
	    
	    
	    // Initialize Sound Pool
	    mSoundPool = new SoundPool(5, AudioManager.STREAM_MUSIC, 0);
		mSoundBeepId = mSoundPool.load(getApplicationContext(), R.raw.beep, 1);
		
		// hschoi 5.0
		//mAudioManager.setForceUse(1, 4); //mAudioManager.setForceUse(AudioSystem.FOR_MEDIA, AudioSystem.FORCE_BT_A2DP);
		//mAudioManager.setForceUse(1, 3); //audioManager.setForceUse(AudioSystem.FOR_MEDIA, AudioSystem.FORCE_BT_SCO);
		try {
			Class c;
			c = Class.forName("android.media.AudioManager");
			Method m = c.getMethod("getForceUse", int.class);
			audioUse = (Integer) m.invoke(mAudioManager, 1);
			Log.d(TAG, "getForceUse : audioUse = " + audioUse);
			if(audioUse != 4)
				mAudioManager.setForceUse(1, 4);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try {
			Class c;
			c = Class.forName("android.media.AudioManager");
			Method m = c.getMethod("getForceUse", int.class);
			audioUse = (Integer) m.invoke(mAudioManager, 1);
			Log.d(TAG, "getForceUse : audioUse = " + audioUse);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// hschoi. Network Thread start. Socket Communication with C# server
		// I made a thread inside the main class in order to use the method speechToText()
		if(socketNetwork) {
			new Thread(){
				public String message = new String();
				public void run() {
			        try{
						Socket socket = new Socket("192.168.0.51", 8011);
						Log.d(TAG, "Socket Connection..");
				        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				        //PrintWriter writer = new PrintWriter(socket.getOutputStream());
				        //writer.write(message, 0, 3);
				        //writer.close();
				        Log.d(TAG, "server port: " + socket.getPort() + " client address: " + socket.getInetAddress() + " Connected!" );
				        
				        // When you call main thread's method in a thread's loop, You should include android.os.Looper.prepare()
				        // it makes it possible to call speechToText()
				        Looper.prepare();
				        while(true)
				        {
				        	if( (message = in.readLine().toString() ) != null) {
				        		//message = in.readLine().toString();
				        		Log.d(TAG, message + " ButtonPressed!!!!" );
				        		
				        		if(message.equals("0") || message.equals("1") ||message.equals("2") || message.equals("3")) {
				        			setCurrentRunningPackage();
				        			speechToText();
				        		}
				        	}
				        }
			        }
				    catch(Exception e){
			        	Log.d(TAG, "Socket Connection fail : " + e.toString());
			        }
				}
			}.start();
		}
		
    }
    
    @Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		handleIntent(intent);
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onDestroy() {
    	Log.d(TAG, "onDestory()");
    	SpeechRecognizerManager.getInstance().finalizeLibrary();
		TextToSpeechManager.getInstance().finalizeLibrary();
		//mAudioManager.unregisterMediaButtonEventReceiver(new ComponentName(getPackageName(), MediaButtonIntentReceiver.class.getName()));
		mNotificationManager.cancel(4542);
		mMediaSession.release();
		if(mWindowManager != null)
			if(mPopupView != null) mWindowManager.removeView(mPopupView);
		super.onDestroy();	
	}
	
    @Override
    public IBinder onBind(Intent arg0) {
    	return null; 
    }
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		setMaxPosition();
		optimizePosition();
	}
	
	// hschoi 5.0
    private void handleIntent( Intent intent ) {
        if( intent == null || intent.getAction() == null )
            return;

        String intentAction = intent.getAction();
		Log.i(TAG, "mediaButtonIntentAction = " + intentAction);
		
        KeyEvent event = (KeyEvent)intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
	    if (event == null)
	    	return;

	    int action = event.getAction();
	    int code = event.getKeyCode();
	    Log.i(TAG, "event.action = " + action + ", event.code = "); // event.action = 0 => ACTION_DOWN, 1 => ACTION_UP, 2 => ACTION_MULTIPLE
	    if (action == KeyEvent.ACTION_UP) {
	    	if (code == KeyEvent.KEYCODE_MEDIA_PREVIOUS || code == KeyEvent.KEYCODE_MEDIA_NEXT) {
	    		setCurrentRunningPackage();
	    		speechToText();
	    	}
	    }
    }	
	
	private void setMaxPosition() {
		DisplayMetrics matrix = new DisplayMetrics();
		mWindowManager.getDefaultDisplay().getMetrics(matrix);
		MAX_X = matrix.widthPixels - mPopupView.getWidth();
		MAX_Y = matrix.heightPixels - mPopupView.getHeight();
	}
	
	private void optimizePosition() {
		if(mParams.x > MAX_X) mParams.x = MAX_X;
		if(mParams.y > MAX_Y) mParams.y = MAX_Y;
		if(mParams.x < 0) mParams.x = 0;
		if(mParams.y < 0) mParams.y = 0;
	}
	
	private OnTouchListener mViewTouchListener = new OnTouchListener() {
		@Override public boolean onTouch(View v, MotionEvent event) {
			switch(event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					if(MAX_X == -1)
						setMaxPosition();
					START_X = event.getRawX();
					START_Y = event.getRawY();
					PREV_X = mParams.x;
					PREV_Y = mParams.y;
					mPopupView.setBackgroundColor(Color.argb(180, 255, 255, 255));
					mHandler.removeMessages(LONG_CLICK);
					mHandler.removeMessages(LONG_LONG_CLICK);
					mHandler.sendEmptyMessageDelayed(LONG_CLICK, ViewConfiguration.getLongPressTimeout() + ViewConfiguration.getTapTimeout());
					mHandler.sendEmptyMessageDelayed(LONG_LONG_CLICK, (ViewConfiguration.getLongPressTimeout() + ViewConfiguration.getTapTimeout()) * 2);
					break;
				case MotionEvent.ACTION_MOVE:
					int x = (int)(event.getRawX() - START_X);
					int y = (int)(event.getRawY() - START_Y);
					boolean move = false;
					if (Math.abs(x) > 20) {
						mParams.x = PREV_X - x;
						move = true;
					}
					if (Math.abs(y) > 20) {
						mParams.y = PREV_Y + y;
						move = true;
					}
					if (move) {
						mPopupView.setBackgroundColor(Color.argb(180, 255, 255, 255));
						mHandler.removeMessages(LONG_CLICK);
						mHandler.removeMessages(LONG_LONG_CLICK);
					}
					optimizePosition();
					mWindowManager.updateViewLayout(mPopupView, mParams);
					break;
				case MotionEvent.ACTION_UP:
					mPopupView.setBackgroundColor(Color.argb(30, 255, 255, 255));
					mHandler.removeMessages(LONG_CLICK);
					mHandler.removeMessages(LONG_LONG_CLICK);
					if (mLongClicked) {
						mLongClicked = false;
						break;
					}
					if (PREV_X == mParams.x && PREV_Y == mParams.y ) {
						// Click Small Window
						setCurrentRunningPackage();
						speechToText();
					}
					break;
			}
			return true;
		}
	};

	private class keyHandler extends Handler {
		public void handleMessage(Message msg) {
			switch(msg.what) {
			case SHORT_CLICK:
				break;
			case LONG_CLICK:
				((Vibrator)getSystemService(Context.VIBRATOR_SERVICE)).vibrate(50);
				mLongClicked = true;
				break;
			case LONG_LONG_CLICK:
				((Vibrator)getSystemService(Context.VIBRATOR_SERVICE)).vibrate(50);
				stopSelf();
				//mNotificationManager.cancel(4542);
				break;
			default:
				break;
			}
		}
	}
	
	public void playSound() { 
		mSoundPool.play(mSoundBeepId, 1f, 1f, 0, 0, 1f);
	}

	public void speechToText() {
		// hschoi
		try {
			Class c;
			c = Class.forName("android.media.AudioManager");
			Method m = c.getMethod("getForceUse", int.class);
			audioUse = (Integer) m.invoke(mAudioManager, 1);
			Log.d(TAG, "getForceUse : audioUse = " + audioUse);
			if(audioUse != 4)
				mAudioManager.setForceUse(1, 4);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if(sttClient != null) {
			return;
		}
		
		Toast.makeText(mContext, "무엇을 도와드릴까요?", Toast.LENGTH_SHORT).show();
		playSound();
		SystemClock.sleep(700);
		
		String serviceType = SpeechRecognizerClient.SERVICE_TYPE_WEB;
		SpeechRecognizerClient.Builder builder = new SpeechRecognizerClient.Builder().
				setApiKey(apikey).
				setServiceType(serviceType);
        sttClient = builder.build();
        sttClient.setSpeechRecognizeListener(this);
        sttClient.startRecording(true);
	}
	
	public void textToSpeech(String text) {
		if (ttsClient != null) {
            ttsClient.stop();
            ttsClient = null;
        }
		
		if(text.equals(""))
			return;
		
		Log.d(TAG, "textToSpeech(), text = " + text);
		
		// Volume Control
		int currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
		if(currentVolume == 0.0f) {
			SpeechRecognizerManager.getInstance().finalizeLibrary();
			TextToSpeechManager.getInstance().finalizeLibrary();
			mAudioManager.unregisterMediaButtonEventReceiver(new ComponentName(getPackageName(), MediaButtonIntentReceiver.class.getName()));
			SpeechRecognizerManager.getInstance().initializeLibrary(this);
			TextToSpeechManager.getInstance().initializeLibrary(getApplicationContext());
			mAudioManager.registerMediaButtonEventReceiver(new ComponentName(mContext.getPackageName(), MediaButtonIntentReceiver.class.getName()));
		}
		
		int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		float percent = 0.7f;
		int seventyVolume = (int)(maxVolume*percent);
		mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, seventyVolume, 0);
		
		// Start TTS
		String voiceType = TextToSpeechClient.VOICE_MAN_READ_CALM;
        double speechSpeed = 1.0;
        ttsClient = new TextToSpeechClient.Builder()
        	.setApiKey(apikey)
        	.setSpeechSpeed(speechSpeed)
        	.setSpeechVoice(voiceType)
        	.setListener(this)
        	.build();
        ttsClient.play(text);
	}
	
	@Override
    public void onResults(Bundle results) {
		Log.i(TAG, "onResults()");
        handleMessage(mCommandManager.getResults(results));
        if(sttClient != null) {
    		sttClient.stopRecording();
			sttClient = null;
    	}
    }
	
	private void handleMessage(CommandContent cmdContent) {
		String packageName = cmdContent.packageName;	// associated package name. can be empty string, i.e., ""
		Cid cid = cmdContent.cmd.cid;					// Command ID
		
		// System Commands
	    if(!packageName.equals("")) {
	    	switch(cid) {
	    	case SYS_CTRL:
	    		moveAppToFront(cmdContent);
	    		break;
	    	case SYS_FRONT:
	    	case SYS_REAR:
	    	case SYS_PHONE:
	    		moveAppDisplay(cmdContent);
	    		break;
	    	/*
	    	case SYS_EXE:
	    		executeApplication(cmdContent);
	    		break;
	    	*/
	    	default:
	    		textToSpeech("다시 말씀해주십시오.");
	    		break;
	    	}
	    	return;
	    }
	     
	    // Reset Display Mapping
	    if(cid == Cid.SYS_RESET) {
	    	resetDisplayMapping();
	    	return;
	    }
	    
	    // Connect WiFi Display
	    if(cid == Cid.SYS_MIRACAST) {
	    	// connectMiracast();
	    	return;
	    }
	    
	    try {
	    	// Facebook Messenger Commands
		    if (currentRunningPackage.equals("com.facebook.orca")) {
		    	switch(cid) {
		    	case MESSENGER_FRIEND_LIST:
			    	sendTap(300, 100);
			    	textToSpeech("친구 목록을 표시합니다");
		    		break;
		    	case MESSENGER_CHAT_LIST:
			    	sendTap(55, 100);
			    	textToSpeech("채팅방을 표시합니다");
		    		break;
		    	case MESSENGER_SEARCH:
	    			textToSpeech("Search " + cmdContent.contact);
	    			sendTap(300, 100);
		    		SystemClock.sleep(1000);
	    			sendTap(1024,100);
		    		SystemClock.sleep(2000);
		    		for(int i=0; i<10; i++)
		    			sendKey(KeyEvent.KEYCODE_DEL);
		    		sendText(cmdContent.content);
		    		sendTap(1115, 200);
		    		SystemClock.sleep(3000);
		    		sendTap(200, 185);
		    		SystemClock.sleep(500);
		    		sendTap(900, 730);
		    		SystemClock.sleep(500);
		    		sendTap(1115, 200);
		    		break;
		    	case MESSENGER_SEND:
		    		sendText(cmdContent.content);
		    		SystemClock.sleep(2000);
		    		sendTap(1115, 200);
		    		SystemClock.sleep(1000);
		    		sendTap(1150, 725);
		    		break;
		    	case SYS_FINISH:
		    		sendKey(KeyEvent.KEYCODE_HOME);
	    			textToSpeech("메신저를 종료합니다");
		    		break;
		    	case SYS_BACK:
		    		sendKey(KeyEvent.KEYCODE_BACK);
		    		break;
		    	default:
		    		textToSpeech("다시 말씀해주십시오");
		    		break;
		    	}
		    	return;
		    }
		    
		    // (KimGiSa) Navigation Commands
		    if (currentRunningPackage.equals("com.locnall.KimGiSa")) {
		    	switch(cid) {
		    	case NAVIGATION_YES:
		    		sendTap(390, 475);
		    		break;
		    	case NAVIGATION_NO:
		    		sendTap(805, 475);
		    		break;
		    	case NAVIGATION_CONTINUE:
		    		sendTap(380, 530);
		    		break;
		    	case NAVIGATION_NAVIGATE:
	    			sendTap(200, 150);
	    			SystemClock.sleep(2000);
	    			//sendTap(200, 300);
	    			//SystemClock.sleep(2000);
		    		sendText(cmdContent.content);
		    		SystemClock.sleep(2000);
		    		sendTap(700, 330);
		    		SystemClock.sleep(2000);
		    		sendTap(940, 600);
		    		SystemClock.sleep(3000);
		    		sendTap(1350, 920);
		    		SystemClock.sleep(2000);
		    		//sendTap(970, 450);
		    		break;
		    	case NAVIGATION_LOCATION:
		    		sendTap(1130, 250);
		    		//sendTap(1230, 220);
		    		textToSpeech("저장된 장소를 표시합니다.");
		    		break;
		    	case NAVIGATION_MAP:
		    		sendTap(1130, 400);
		    		//sendTap(1230, 370);
		    		textToSpeech("지도를 표시합니다.");
		    		break;
		    	case NAVIGATION_NAVEND:
					sendTap(400, 300);
					SystemClock.sleep(1500);
					sendTap(113, 105);
					textToSpeech("길안내를 종료합니다.");
					SystemClock.sleep(1000);
					sendTap(60, 110);
					SystemClock.sleep(1000);
					sendTap(60, 110);
		    		break;
		    	case SYS_FINISH:
		    		sendKey(KeyEvent.KEYCODE_HOME);
	    			textToSpeech("내비게이션을 닫습니다.");
		    	case SYS_BACK:
		    		sendKey(KeyEvent.KEYCODE_BACK);
		    		break;
		    	default:
		    		textToSpeech("다시 말씀해주십시오.");
		    		break;
		    	}
		    	return;
		    }
	    
		    // (MX Player) Video Commands
		    if (currentRunningPackage.equals("com.mxtech.videoplayer.ad")) {
		    	switch(cid) {
		    	case VIDEO_START:
		    		sendTap(500, 300);
		    		SystemClock.sleep(2000);
		    		sendTap(500, 300);
		    		break;
		    	case VIDEO_PLAY:
			    	sendTap(900, 1024);
		    		break;
		    	case VIDEO_STOP:
			    	sendTap(900, 1024);
		    		SystemClock.sleep(500);
			    	sendTap(900, 1024);
		    		break;
			    case SYS_FINISH:
			    	sendKey(KeyEvent.KEYCODE_HOME);
	    			textToSpeech("비디오를 닫습니다.");
	    			break;
			    case SYS_BACK:
		    		sendKey(KeyEvent.KEYCODE_BACK);
		    		break;
			    default:
		    		textToSpeech("다시 말씀해주십시오.");
		    		break;
		    	}
		    	return;
		    }
		    
		    // Browser Commands
		    if (currentRunningPackage.equals("com.android.browser")) {
		    	switch(cid) {
		    	case BROWSER_UPSIDE:
			    	sendSwipe(600, 100, 600, 700);
		    		break;
		    	case BROWSER_DOWNSIDE:
		    		sendSwipe(600, 700, 600, 100);
		    		break;
		    	case BROWSER_RIGHTSIDE:
		    		sendSwipe(750, 400, 100, 400);
		    		break;
		    	case BROWSER_LEFTSIDE:
		    		sendSwipe(100, 400, 750, 400);
		    		break;
			    case SYS_FINISH:
	    			sendKey(KeyEvent.KEYCODE_HOME);
	    			textToSpeech("브라우저를 닫습니다.");
		    		break;
			    case SYS_BACK:
		    		sendKey(KeyEvent.KEYCODE_BACK);
		    		break;
			    default:
		    		textToSpeech("다시 말씀해주십시오.");
		    		break;
		    	}
		    	return;
		    }
    	} catch(Exception e) {
	    	Log.d(TAG, e.toString());
	    }
	    textToSpeech("다시 말씀해주십시오.");
	}
	
	/*
	private void executeApplication(CommandContent cmdContent) {
		String appName = cmdContent.appName;
		String packageName = cmdContent.packageName;
		boolean isBottomLetter = isBottomLetter(appName);
		if (currentRunningPackage.equals(packageName)) {
			textToSpeech(appName + (isBottomLetter?"이":"가") + " 이미 실행중 입니다.");
			return;
		}
		
		if (mActivityManager.isAppBackground(packageName)) {
			textToSpeech(appName + (isBottomLetter?"을":"를") + " 실행합니다.");
			moveAppToFront(cmdContent);
			return;
		}
		
    	textToSpeech(appName + (isBottomLetter?"을":"를") + " 실행합니다.");
    	Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
    	intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
	}
	*/

	public void setCurrentRunningPackage() {
		ActivityManager am = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
		List<ActivityManager.RunningTaskInfo> Info = am.getRunningTasks(1);
		ComponentName topActivity = Info.get(0).topActivity;
		currentRunningPackage = topActivity.getPackageName();
		
		// hschoi
		Log.d(TAG, "setCurrentRunningPackage() : topActivity = " + topActivity);
		Log.d(TAG, "setCurrentRunningPackage() : currentRunningPackage = " + currentRunningPackage);
		
	}
	
	private void moveAppToFront(CommandContent cmd) {
		String appName = cmd.appName;
		String packageName = cmd.packageName;
		boolean isBottomLetter = isBottomLetter(appName);
		int taskId = getTaskIdByPackageName(packageName);
		Display[] displays = mDisplayManager.getDisplays();
		
		// hschoi
		Log.d(TAG, "moveAppToFront() : taskId = " + taskId);
		
		if(currentRunningPackage.equals(packageName)) {
			textToSpeech(appName + (isBottomLetter?"이":"가") + " 이미 수행중입니다.");
			return;
		}
		
		textToSpeech(appName + (isBottomLetter?"을":"를") + " 제어합니다.");

		
		if(taskId != -1) {	// 처음 실행하는 App이 아니라면, taskId가 부여되어 있음
			// hschoi
			/*mActivityManager.setExternalDisplay(taskId, 0);
			if(mActivityManager.isAppBackground(packageName))
				mActivityManager.setLayerStackByPackageName(packageName, 0);
			*/
			setAppDisplay(1, packageName);
			setAppDisplay(1, packageName);
			mActivityManager.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_WITH_HOME);
			
			//startActivity(getPackageManager().getLaunchIntentForPackage(packageName));
			//mActivityManager.setExternalDisplay(taskId, getDisplay(1));			
		} else {
			
			Log.d(TAG, "Call Launcher");
			Intent startMain = new Intent(Intent.ACTION_MAIN);
			startMain.addCategory(Intent.CATEGORY_HOME);
			startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(startMain);
			
			startActivity(getPackageManager().getLaunchIntentForPackage(packageName));
			
		}
	}
		
	private void moveAppDisplay(CommandContent cmdContent) {
		String appName = cmdContent.appName;
		String packageName = cmdContent.packageName;
		Cid cid = cmdContent.cmd.cid;
		boolean isBottomLetter = isBottomLetter(appName);
		if(currentRunningPackage.equals(packageName)) {
			Log.d(TAG, "-------currentPackage="+currentRunningPackage+", requestedPackageName="+packageName);
			if(cid == Cid.SYS_FRONT) {
				if(getDisplay(3)==null) { //(Display.TYPE_WIFI)) {
					textToSpeech("앞좌석에 연결된 디스플레이 장치가 없습니다.");
					return;
				}
				if(setAppDisplay(3, packageName)) //(Display.TYPE_WIFI, packageName))
					textToSpeech(appName + (isBottomLetter?"을":"를") + " 앞좌석에 표시합니다.");
				else
					textToSpeech(appName + (isBottomLetter?"을":"를") + " 앞좌석에 표시할 수 없습니다. 다시 시도해주십시오.");
	    	} else if (cid == Cid.SYS_REAR) {
	    		if(getDisplay(2)==null) { // (Display.TYPE_HDMI)) {
					textToSpeech("뒷좌석에 연결된 디스플레이 장치가 없습니다.");
					return;
				}
				if(setAppDisplay(2, packageName)) // (Display.TYPE_HDMI, packageName))
					textToSpeech(appName + (isBottomLetter?"을":"를") + " 뒷좌석에 표시합니다.");
				else
					textToSpeech(appName + (isBottomLetter?"을":"를") + " 뒷좌석에 표시할 수 없습니다. 다시 시도해주십시오.");
	    	} else if (cid == Cid.SYS_PHONE) {
				if(setAppDisplay(1, packageName)) // (Display.TYPE_BUILT_IN, packageName))
					textToSpeech(appName + (isBottomLetter?"을":"를") + " 폰에 표시합니다.");
				else
					textToSpeech(appName + (isBottomLetter?"을":"를") + " 폰에 표시할 수 없습니다. 다시 시도해주십시오.");
	    	}
    	} else {
    		textToSpeech(appName + (isBottomLetter?"을":"를") + " 먼저 실행해주십시오.");
    	}
	}
	
	private Display getDisplay(int type) {
		Display[] displays = mDisplayManager.getDisplays();
		int displayType = 0;

		for(int i=0; i<displays.length; i++) {
			Display display = displays[i];
			try {
				Class c;
				c = Class.forName("android.view.Display");
				Method m = c.getMethod("getType", null);
				displayType = (Integer) m.invoke(display, null);
				Log.d(TAG, "getDisplay : displayType = " + displayType);
			} catch (Exception e) {
				e.printStackTrace();
			}
			if(displayType == type)
				return display;
		}
		
		return null;
	}
	
	/*private Display hasAvailableDisplay(int type) {
		Display[] displays = mDisplayManager.getDisplays();
		int displayType = 0;
		if(displays.length > 1) {
			for(int i=0; i<displays.length; i++) {
				Display display = displays[i];
				try {
					Class c;
					c = Class.forName("android.view.Display");
					Method m = c.getMethod("getType", null);
					displayType = (Integer) m.invoke(display, null);
					Log.d(TAG, "hasAvailableDisplay : displayType = " + displayType);
				} catch (Exception e) {
					e.printStackTrace();
				}
				if(displayType == type)
					return display;
			}
		}
		
		return null;
	}*/
	
	private boolean setAppDisplay(int type, String packageName) {
		Display[] displays = mDisplayManager.getDisplays();
		int taskId = getTaskIdByPackageName(packageName);
		int displayType = -1;
		Display display;
		
		for(int i=0; i<displays.length; i++) {
			display = displays[i];
			try {
				Class c;
				c = Class.forName("android.view.Display");
				Method m = c.getMethod("getType", null);
				displayType = (Integer) m.invoke(display, null);
				Log.d(TAG, "setAppDisplay() : displayType = " + displayType);
			} catch (Exception e) {
				e.printStackTrace();
			}
			if(displayType == type) {
				//startActivity(getPackageManager().getLaunchIntentForPackage(packageName));
				Log.d(TAG, "setAppDisplay(), taskId="+taskId+" displayType="+displayType);
				mActivityManager.setExternalDisplay(taskId, display);
				return true;
			}
		}
			
		/*if(displays.length >= 1) {
			for(int i=0; i<displays.length; i++) {
				int layerStack = displays[i].getLayerStack();
				if(displays[i].getType() == type) {
					String current = mActivityManager.getPackageNameByLayerStack(layerStack);
					if(current.equals("") && !mActivityManager.isAppBackground(current)) {
    					mActivityManager.setELayerStackByPackageName(packageName, layerStack);
    					mActivityManager.setLayerStackByPackageName(packageName, 0);
    					return true;
					}
				}
			}
		}*/
		return false;
	}

	private void resetDisplayMapping() {
		textToSpeech("모든 앱을 폰으로 가져옵니다.");
		final int currentTaskId = getTaskIdByPackageName(currentRunningPackage);
		Display[] displays = mDisplayManager.getDisplays();
		
		/*for(Display d : displays) {
			int layerStack = d.getLayerStack();
			if(layerStack == 0)
				continue;
			String packageName = mActivityManager.getPackageNameByLayerStack(layerStack);
			if(!packageName.equals("")) {
				try {
					mActivityManager.setELayerStackByPackageName(packageName, 0);
					mActivityManager.setLayerStackByPackageName(packageName, 0);
					mActivityManager.moveTaskToFront(getTaskIdByPackageName(packageName), ActivityManager.MOVE_TASK_WITH_HOME);
				} catch (Exception e) {
					Log.d(TAG, e.toString());
				}
			}
		}
		mActivityManager.moveTaskToFront(currentTaskId, ActivityManager.MOVE_TASK_WITH_HOME);*/
	}
	
	private int getTaskIdByPackageName(String packageName) {
	    final List<RunningTaskInfo> rt = mActivityManager.getRunningTasks(Integer.MAX_VALUE);
	    for(int i=0; i<rt.size(); i++) {
	        RunningTaskInfo info = rt.get(i);
	        String pkgName = info.baseActivity.getPackageName();
	        if(pkgName.equals(packageName))
	            return info.id;
	    }
	    return -1;
	}
/*
	private void connectMiracast() {
		DisplayManager mDisplayManager = ((NansTalkService)NansTalkService.mContext).mDisplayManager;
        WifiDisplayStatus mWifiDisplayStatus = mDisplayManager.getWifiDisplayStatus();
        if(mWifiDisplayStatus.getActiveDisplay() != null) {
        	textToSpeech("이미 연결된 디스플레이가 있습니다.");
        	return;
        }
        
        //textToSpeech("연결가능한 디스플레이를 찾고 있습니다. 잠시만 기다려주십시오.");
        mDisplayManager.scanWifiDisplays();
        SystemClock.sleep(2000);
        WifiDisplay[] displays = mWifiDisplayStatus.getAvailableDisplays();
    	for(WifiDisplay d : displays) {
    		Log.d("NansTalkService", " d="+d.getDeviceName());
    		//if(d.getDeviceName().equals("RTSD5000-115319"))
    		mDisplayManager.connectWifiDisplay(d.getDeviceAddress());	
    		textToSpeech("연결중입니다.");
    		return;
    		
    	}
    	textToSpeech("연결 준비 중입니다. 잠시후 다시 시도해주십시오.");
	}
*/
	private void sendKey(int keyCode) {
		long now = SystemClock.uptimeMillis();
		injectKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
			KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD));
		injectKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, 0,
			KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD));
	}
	
	private void sendTap(float x, float y) {
		long now = SystemClock.uptimeMillis();
		injectMotionEvent(InputDevice.SOURCE_TOUCHSCREEN, MotionEvent.ACTION_DOWN, now, x, y, 1.0f);
		injectMotionEvent(InputDevice.SOURCE_TOUCHSCREEN, MotionEvent.ACTION_UP, now, x, y, 0.0f);
	}
	
    private void sendText(String text) {
    	text = text.trim();
        StringBuffer buff = new StringBuffer(text);
        boolean escapeFlag = false;
        for (int i=0; i<buff.length(); i++) {
            if (escapeFlag) {
                escapeFlag = false;
                if (buff.charAt(i) == 's') {
                    buff.setCharAt(i, ' ');
                    buff.deleteCharAt(--i);
                }
            }
            if (buff.charAt(i) == '%') {
                escapeFlag = true;
            }
        }
        
        char[] chars = buff.toString().toCharArray();
        KeyCharacterMap kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        KeyEvent[] events = kcm.getEvents(chars);
        for(int i = 0; i < events.length; i++) {
            injectKeyEvent(events[i]);
        }
    }
    
    private void sendSwipe(float x1, float y1, float x2, float y2) {
        final int NUM_STEPS = 11;
        long now = SystemClock.uptimeMillis();
        injectMotionEvent(InputDevice.SOURCE_TOUCHSCREEN, MotionEvent.ACTION_DOWN, now, x1, y1, 1.0f);
        for (int i = 1; i < NUM_STEPS; i++) {
            float alpha = (float)i / NUM_STEPS;
            injectMotionEvent(InputDevice.SOURCE_TOUCHSCREEN, MotionEvent.ACTION_MOVE, now, lerp(x1, x2, alpha), lerp(y1, y2, alpha), 1.0f);
            SystemClock.sleep(50);
        }
        injectMotionEvent(InputDevice.SOURCE_TOUCHSCREEN, MotionEvent.ACTION_UP, now, x2, y2, 0.0f);
    }
    
    private static final float lerp(float a, float b, float alpha) {
        return (b - a) * alpha + a;
    }
    
   	private void injectKeyEvent(KeyEvent event) {
		Log.i(TAG, "injectKeyEvent: " + event);
		
		/* hschoi
		Class c;
		try {
			c = Class.forName("android.hardware.input.InputManager");
			Method m = c.getMethod("injectInputEvent", KeyEvent.class, int.class);
			m.invoke(mInputManager, event, 1);
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		mInputManager.injectInputEventToDisplay(event, 0, 1);
		//mInputManager.injectInputEvent(event, InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT);
	}
	
	private void injectMotionEvent(int inputSource, int action, long when, float x, float y, float pressure) {
		final float DEFAULT_SIZE = 1.0f;
		final int DEFAULT_META_STATE = 0;
		final float DEFAULT_PRECISION_X = 1.0f;
		final float DEFAULT_PRECISION_Y = 1.0f;			
		final int DEFAULT_EDGE_FLAGS = 0;
		final int DEFAUT_INPUT_DEVICE = 0;
		MotionEvent event = MotionEvent.obtain(when, when, action, x, y, pressure, DEFAULT_SIZE,
			DEFAULT_META_STATE, DEFAULT_PRECISION_X, DEFAULT_PRECISION_Y, DEFAUT_INPUT_DEVICE, DEFAULT_EDGE_FLAGS);
		event.setSource(inputSource);
		Log.i(TAG, "injectMotionEvent: " + event);

		/* hschoi
		Log.i(TAG, "injectMotionEvent: !!!!!!!!!!!!!!!!!!!" + event);
		Class c;
		try {
			c = Class.forName("android.hardware.input.InputManager");
			Method m = c.getMethod("injectInputEvent", KeyEvent.class, int.class);
			m.invoke(mInputManager, event, (Integer) 1);
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		mInputManager.injectInputEventToDisplay(event, 0, 1);
		//mInputManager.injectInputEvent(event, InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT);
	}

	private boolean isBottomLetter(String msg) {
	    int word = (int)msg.charAt(msg.length()-1);	    
	    int result= (word-44032)%28;	
	    // hschoi
	    return (result != 0) ? true : false;
	}
	
	@Override
    public void onReady() {
		//Log.i(TAG, "onReady()");
	}

    @Override
    public void onBeginningOfSpeech() {
    	//Log.i(TAG, "onBeginningOfSpeech()");
    }

    @Override
    public void onEndOfSpeech() {
    	//Log.i(TAG, "onEndOfSpeech()");
    }

    @Override
    public void onError(int errorCode, String errorMsg) {
		Log.e(TAG, "onError");
		if(ttsClient != null) {
			handleError(errorCode);
			ttsClient.stop();
			ttsClient = null;
		}
		if(sttClient != null) {
    		sttClient.stopRecording();
			sttClient = null;
			textToSpeech("다시 말씀해주십시오.");
    	}
    }

    private void handleError(int errorCode) {
        String errorText;
        switch (errorCode) {
            case TextToSpeechClient.ERROR_NETWORK:
                errorText = "네트워크 오류";
                break;
            case TextToSpeechClient.ERROR_NETWORK_TIMEOUT:
                errorText = "네트워크 지연";
                break;
            case TextToSpeechClient.ERROR_CLIENT_INETRNAL:
                errorText = "음성합성 클라이언트 내부 오류";
                break;
            case TextToSpeechClient.ERROR_SERVER_INTERNAL:
                errorText = "음성합성 서버 내부 오류";
                break;
            case TextToSpeechClient.ERROR_SERVER_TIMEOUT:
                errorText = "음성합성 서버 최대 접속시간 초과";
                break;
            case TextToSpeechClient.ERROR_SERVER_AUTHENTICATION:
                errorText = "음성 합성 인증 실패";
                break;
            case TextToSpeechClient.ERROR_SERVER_SPEECH_TEXT_BAD:
                errorText = "음성 합성 텍스트 오류";
                break;
            case TextToSpeechClient.ERROR_SERVER_SPEECH_TEXT_EXCESS:
                errorText = "음성 합성 텍스트 허용 길이 초과";
                break;
            case TextToSpeechClient.ERROR_SERVER_ALLOWED_REQUESTS_EXCESS:
                errorText = "허용 횟수 초과";
                break;
            default:
                errorText = "정의하지 않은 오류";
                break;
        }
        final String statusMessage = errorText + " (" + errorCode + ")";
        Log.i(TAG, statusMessage);
    }

    @Override
    public void onPartialResult(String text) {}

    @Override
    public void onAudioLevel(float v) {}

	@Override
	public void onFinished() {
		if(ttsClient != null) {
			int intSentSize = ttsClient.getSentDataSize();
			int intRecvSize = ttsClient.getReceivedDataSize();
			Log.i(TAG, "onFinished(), SentSize=" + intSentSize + ", RecvSize=" + intRecvSize);
			ttsClient = null;
		}
	}
}