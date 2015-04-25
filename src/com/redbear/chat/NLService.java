package com.redbear.chat;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class NLService extends NotificationListenerService {
    public static final String ACTION_SONG_CHANGED = 
	"ACTION_SONG_CHANGED";
    public static final String ACTION_NOTIFICATION_POSTED = 
	"ACTION_NOTIFICATION_POSTED";
    static final String TAG = NLService.class.getSimpleName();

    @Override public void onCreate() {
	super.onCreate();
	Log.i(TAG, "onCreate");
    }

    @Override public void onDestroy() {
	super.onDestroy();
	Log.i(TAG, "onDestroy");
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
	Notification n = sbn.getNotification();
	Intent i;

	Log.i(TAG, "Posted");
	Log.i(TAG, "ID: " + sbn.getId());
	Log.i(TAG, "Ticker: " + n.tickerText);
	Log.i(TAG, "Package: " + sbn.getPackageName());
	Log.i(TAG, "String: " + n.toString());

	if (sbn.getPackageName().equals("com.spotify.music")) {
	    i = new Intent(ACTION_SONG_CHANGED);
	} else {
	    i = new Intent(ACTION_NOTIFICATION_POSTED);
	}
	i.putExtra("tickerText", n.tickerText);
	sendBroadcast(i);
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
	Log.i(TAG, "Removed");
	Log.i(TAG, "ID: " + sbn.getId());
    }
}