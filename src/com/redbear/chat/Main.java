package com.redbear.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;
import android.widget.TextView;

public class Main extends Activity {
	private BluetoothAdapter mBluetoothAdapter;
	private static final int REQUEST_ENABLE_BT = 1;
	private static final long SCAN_PERIOD = 3000;
	private Dialog mDialog;
	public static List<BluetoothDevice> mDevices = new ArrayList<BluetoothDevice>();
	final static String TAG = Main.class.getSimpleName();
	public static final String EXTRAS_DEVICE = "EXTRAS_DEVICE";

	String mDeviceName;
	String mDeviceAddress;
	RBLService mService;
	TextView mTextView;

	final BroadcastReceiver mReceiver = new BroadcastReceiver() {	
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();

			if (action.equals(RBLService.ACTION_READY)) {
				/* We may want to auto-connect if there's a stored device */
			} else if (action.equals(RBLService.ACTION_CONNECTED)) {
				mTextView.setText("Connected");
			} else if (action.equals(RBLService.ACTION_DISCONNECTED)) {
				mTextView.setText("Disconnected");
			} else if (action.equals(RBLService.ACTION_CONNECTING)) {
				mTextView.setText("Connecting");
			}
		}
	};

	IntentFilter mFilter = new IntentFilter();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.main);

		mFilter.addAction(RBLService.ACTION_CONNECTED);
		mFilter.addAction(RBLService.ACTION_DISCONNECTED);
		mFilter.addAction(RBLService.ACTION_READY);
		mFilter.addAction(NLService.ACTION_SONG_CHANGED);
		mFilter.addAction(NLService.ACTION_NOTIFICATION_POSTED);

		startService(new Intent(this, RBLService.class));

		if (!getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
					.show();
			finish();
		}

		BluetoothManager mBluetoothManager =
			(BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

		mBluetoothAdapter = mBluetoothManager.getAdapter();
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
					.show();
			finish();
			return;
		}

		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}

		mTextView = (TextView)findViewById(R.id.tv);

		Button connect = (Button)findViewById(R.id.connectBtn);
		connect.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				scanLeDevice();

				showRoundProcessDialog(Main.this, R.layout.loading_process_dialog_anim);

				Timer mTimer = new Timer();
				mTimer.schedule(new TimerTask() {

					@Override
					public void run() {
						Intent deviceListIntent = new Intent(getApplicationContext(),
								Device.class);
						startActivity(deviceListIntent);
						mDialog.dismiss();
					}
				}, SCAN_PERIOD);
			}
		});

		Button disconnect = (Button)findViewById(R.id.disconnectBtn);
		disconnect.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(RBLService.ACTION_FORGET);
				sendBroadcast(intent);
			}
		});
	}

	public void showRoundProcessDialog(Context mContext, int layout) {
		OnKeyListener keyListener = new OnKeyListener() {
			@Override
			public boolean onKey(DialogInterface dialog, int keyCode,
					KeyEvent event) {
				if (keyCode == KeyEvent.KEYCODE_HOME
						|| keyCode == KeyEvent.KEYCODE_SEARCH) {
					return true;
				}
				return false;
			}
		};

		mDialog = new AlertDialog.Builder(mContext).create();
		mDialog.setOnKeyListener(keyListener);
		mDialog.show();

		mDialog.setContentView(layout);
	}

	private void scanLeDevice() {
		new Thread() {

			@Override
			public void run() {
				mBluetoothAdapter.startLeScan(mLeScanCallback);

				try {
					Thread.sleep(SCAN_PERIOD);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				mBluetoothAdapter.stopLeScan(mLeScanCallback);
			}
		}.start();
	}

	private BluetoothAdapter.LeScanCallback
		mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

		@Override
		public void onLeScan(final BluetoothDevice device, final int rssi,
				byte[] scanRecord) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (device != null) {
						if (mDevices.indexOf(device) == -1)
							mDevices.add(device);
					}
				}
			});
		}
	};

	@Override
	protected void onResume() {
		super.onResume();
		registerReceiver(mReceiver, mFilter);
	}

	@Override
	protected void onStop() {
		super.onStop();
		unregisterReceiver(mReceiver);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// User chose not to enable Bluetooth.
		if (requestCode == REQUEST_ENABLE_BT
				&& resultCode == Activity.RESULT_CANCELED) {
			finish();
			return;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		System.exit(0);
	}
}
