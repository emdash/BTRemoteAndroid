
package com.redbear.chat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class Chat extends Activity {
	final static String TAG = Chat.class.getSimpleName();
	public static final String EXTRAS_DEVICE = "EXTRAS_DEVICE";

	TextView tv = null;
	EditText et = null;
	Button btn = null;
	String mDeviceName;
	String mDeviceAddress;
	RBLService mService;

	final BroadcastReceiver mReceiver = new BroadcastReceiver() {	
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			displayData("Got intent: " + action + "\n");
			if (action.equals(RBLService.ACTION_READY)) {
				connect();
			} else if (action.equals(RBLService.ACTION_DISCONNECTED)) {
				finish();
			} else if (action.equals(RBLService.ACTION_RX)) {
				handleRx(intent);
			}
		}
	};

	IntentFilter mFilter = new IntentFilter();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.second);

		mFilter.addAction(RBLService.ACTION_CONNECTED);
		mFilter.addAction(RBLService.ACTION_DISCONNECTED);
		mFilter.addAction(RBLService.ACTION_READY);
		mFilter.addAction(RBLService.ACTION_RX);
		mFilter.addAction(NLService.ACTION_SONG_CHANGED);
		mFilter.addAction(NLService.ACTION_NOTIFICATION_POSTED);

		tv = (TextView) findViewById(R.id.textView);
		tv.setMovementMethod(ScrollingMovementMethod.getInstance());
		et = (EditText) findViewById(R.id.editText);
		btn = (Button) findViewById(R.id.send);
		btn.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				sendString(et.getText().toString());
				et.setText("");
			}
		});

		Intent intent = getIntent();
		mDeviceAddress = intent.getStringExtra(Device.EXTRA_DEVICE_ADDRESS);
		mDeviceName = intent.getStringExtra(Device.EXTRA_DEVICE_NAME);

		getActionBar().setTitle(mDeviceName);
		getActionBar().setDisplayHomeAsUpEnabled(true);

		startService(new Intent(this, RBLService.class));
		/*
		 * TODO: this is where call to start service goes.
		 */
		intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
		startActivity(intent);

		
	}

	@Override
	protected void onResume() {
		super.onResume();

		registerReceiver(mReceiver, mFilter);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			mService.disconnect();
			mService.close();

			System.exit(0);
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onStop() {
		super.onStop();

		unregisterReceiver(mReceiver);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		System.exit(0);
	}

	void connect() {
		Intent i = new Intent(RBLService.ACTION_CONNECT);
		i.putExtra(RBLService.EXTRA_DEVICE_ADDRESS, mDeviceAddress);
		sendBroadcast(i);
	}

	void sendString(String s) {
		Intent i = new Intent(RBLService.ACTION_TX);
		i.putExtra(RBLService.EXTRA_TX, s);
		sendBroadcast(i);
	};

	void handleRx(Intent i) {
		String s = new String(i.getByteArrayExtra(RBLService.EXTRA_RX));
		displayData("Extra Data: " + s);
	}


	void displayData(String data) {
		tv.append(data);
		// find the amount we need to scroll. This works by
		// asking the TextView's internal layout for the position
		// of the final line and then subtracting the TextView's height
		final int scrollAmount = tv.getLayout().getLineTop(
					tv.getLineCount())
					- tv.getHeight();
		// if there is no need to scroll, scrollAmount will be <=0
		if (scrollAmount > 0)
			tv.scrollTo(0, scrollAmount);
		else
			tv.scrollTo(0, 0);
	}
}
