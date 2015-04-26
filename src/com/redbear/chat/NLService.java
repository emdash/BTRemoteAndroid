package com.redbear.chat;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class NLService extends NotificationListenerService {
    public static final String ACTION_SONG_CHANGED = 
		"ACTION_SONG_CHANGED";
    public static final String ACTION_NOTIFICATION_POSTED = 
		"ACTION_NOTIFICATION_POSTED";
	public static final String ACTION_GET_NOTIFICATIONS =
		"ACTION_GET_NOTIFICATIONS";

    static final String TAG = NLService.class.getSimpleName();

	BroadcastReceiver mReceiver = new BroadcastReceiver () {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.i(TAG, "Got intent: " + intent.getAction());
			if (intent.getAction().equals(ACTION_GET_NOTIFICATIONS)) {
				sendNotifications();
			}
		}
	};

    @Override
	public void onCreate() {
		super.onCreate();
		Log.i(TAG, "onCreate");

		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_GET_NOTIFICATIONS);
		registerReceiver(mReceiver, filter);
    }

    @Override
	public void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "onDestroy");
    }

    @Override
	public void onNotificationPosted(StatusBarNotification sbn) {
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

    @Override
	public void onNotificationRemoved(StatusBarNotification sbn) {
		Log.i(TAG, "Removed");
		Log.i(TAG, "ID: " + sbn.getId());
    }

	void sendNotifications() {
		Log.i(TAG, "sendNotifications");
		for (StatusBarNotification s : getActiveNotifications()) {
			onNotificationPosted(s);
		}
	}
}
