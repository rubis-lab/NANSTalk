package com.android.nanstalk;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootCompleteReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
			Log.i("BootCompleteReceiver", "Start Service");    
			ComponentName cn = new ComponentName(context.getPackageName(), NansTalkService.class.getName());
			ComponentName svcName = context.startService(new Intent().setComponent(cn));
			if (svcName == null) 
				Log.e("BootCompleteReceiver", "Could not start service " + cn.toString());
		}
	}
}

