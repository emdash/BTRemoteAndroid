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
	private final static String TAG = Chat.class.getSimpleName();

	public static final String EXTRAS_DEVICE = "EXTRAS_DEVICE";
	private TextView tv = null;
	private EditText et = null;
	private Button btn = null;
	private String mDeviceName;
	private String mDeviceAddress;
	private RBLService mService;

	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName,
				IBinder service) {
			mService = ((RBLService.LocalBinder) service)
					.getService();
			if (!mService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				finish();
			}
			// Automatically connects to the device upon successful start-up
			// initialization.
			mService.connect(mDeviceAddress);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			mService = null;
		}
	};

	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();

			displayData("Got intent: " + action + "\n");

			if (action.equals(RBLService.ACTION_DISCONNECTED)) {
				finish();
			} else if (RBLService.ACTION_RX.equals(action)) {
				String data = new String(intent.getByteArrayExtra(RBLService.EXTRA_DATA));
				displayData("Extra Data: " + data);
			}
			displayData("\n");
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.second);

		tv = (TextView) findViewById(R.id.textView);
		tv.setMovementMethod(ScrollingMovementMethod.getInstance());
		et = (EditText) findViewById(R.id.editText);
		btn = (Button) findViewById(R.id.send);
		btn.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
			  mService.sendString(et.getText().toString());
				et.setText("");
			}
		});

		Intent intent = getIntent();

		mDeviceAddress = intent.getStringExtra(Device.EXTRA_DEVICE_ADDRESS);
		mDeviceName = intent.getStringExtra(Device.EXTRA_DEVICE_NAME);

		getActionBar().setTitle(mDeviceName);
		getActionBar().setDisplayHomeAsUpEnabled(true);

		Intent gattServiceIntent = new Intent(this, RBLService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
	}

	@Override
	protected void onResume() {
		super.onResume();

		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
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

		unregisterReceiver(mGattUpdateReceiver);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		System.exit(0);
	}

	private void displayData(String data) {
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

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();

		intentFilter.addAction(RBLService.ACTION_CONNECTED);
		intentFilter.addAction(RBLService.ACTION_DISCONNECTED);
		intentFilter.addAction(RBLService.ACTION_RX);

		return intentFilter;
	}
}
