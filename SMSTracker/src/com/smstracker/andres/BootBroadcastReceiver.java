package com.smstracker.andres;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootBroadcastReceiver extends BroadcastReceiver {
	
    @Override
    public void onReceive(Context context, Intent intent) {
    	Log.i("SMSStatus", "Started on boot");
        Intent startServiceIntent = new Intent(context, TrackerService.class);
        context.startService(startServiceIntent);
    }
    
}
