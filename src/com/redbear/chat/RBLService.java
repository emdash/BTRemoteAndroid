/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redbear.chat;

import java.util.UUID;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

/**
 * Service for managing connection and data communication with a GATT server
 * hosted on a given Bluetooth LE device.
 */
public class RBLService extends Service {
	public final static String ACTION_CONNECTED = "ACTION_CONNECTED";
	public final static String ACTION_DISCONNECTED = "ACTION_DISCONNECTED";
	public final static String ACTION_RSSI = "ACTION_RSSI";
	public final static String ACTION_RX = "ACTION_RX";
	public final static String EXTRA_DATA = "EXTRA_DATA";

	public final static UUID UUID_BLE_SHIELD_TX = UUID
			.fromString(RBLGattAttributes.BLE_SHIELD_TX);
	public final static UUID UUID_BLE_SHIELD_RX = UUID
			.fromString(RBLGattAttributes.BLE_SHIELD_RX);
	public final static UUID UUID_BLE_SHIELD_SERVICE = UUID
			.fromString(RBLGattAttributes.BLE_SHIELD_SERVICE);

	final static String TAG = RBLService.class.getSimpleName();

	BluetoothManager mBluetoothManager;
	BluetoothAdapter mBluetoothAdapter;
	BluetoothGatt mBluetoothGatt;
	BluetoothGattCharacteristic mTX;
	BluetoothGattCharacteristic mRX;
	String mBluetoothDeviceAddress;

	AudioManager mAudioManager;

	boolean mPlaying = false;
	boolean mOnline = true;
	byte mVolume = 127;
	String mArtist = "Artist";
	String mTrack = "Track";
	String mSource = "Source";

	TimerTask mPostConnectTask = 
		new TimerTask() {
			public void run () {
				sendState();
			}
		};

	Timer mTimer = new Timer();

	final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt,
											int status,
											int newState) {
			String intentAction;
			
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				Log.i(TAG, "Connected to GATT server.");
				Log.i(TAG, "Discovering services.");
				if (!mBluetoothGatt.discoverServices()) {
					Log.e(TAG, "Service discovery failed to start.");
				}
			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				intentAction = ACTION_DISCONNECTED;
				Log.i(TAG, "Disconnected from GATT server.");
				broadcastUpdate(intentAction);
			}
		}

		public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				broadcastUpdate(ACTION_RSSI, rssi);
			} else {
				Log.w(TAG, "onReadRemoteRssi received: " + status);
			}
		};

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			BluetoothGattService service = getSupportedGattService();

			if (status == BluetoothGatt.GATT_SUCCESS) {
				broadcastUpdate(ACTION_CONNECTED);
				mTX = service.getCharacteristic(UUID_BLE_SHIELD_TX);
				mRX = service.getCharacteristic(UUID_BLE_SHIELD_RX);
				setCharacteristicNotification(mRX, true);
				mTimer.schedule(mPostConnectTask, 1000);
				Log.i(TAG, "Registering broadcast receiver");
				registerReceiver(mReceiver, nlIntentFilter());
			} else {
				Log.w(TAG, "onServicesDiscovered received: " + status);
			}
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				broadcastUpdate(ACTION_RX, characteristic);
			}
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic) {
			broadcastUpdate(ACTION_RX, characteristic);
		}
	};
    
    BroadcastReceiver mReceiver = new BroadcastReceiver() {		   
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Log.i(TAG, "Got intent: " + action);
			String tickerText = intent.getStringExtra("tickerText");
			// Spotify uses an emdash (U+2014) in order to split
			// between artist and track. This is pretty robust, as
			// most song titles contain dashes instead. If they change
			// their format, this will break.
			String [] split = tickerText.split(" \\u2014 ", 2);
			int i;
			Log.i(TAG, "String contains \u2014: " + tickerText.contains("\u2014"));
			Log.i(TAG, "TickerText: " + tickerText + "(" + split.length + ")");
			if (split.length == 2) {
				mTrack = split[0].substring(0, Math.min(split[0].length(), 24));
				mArtist = split[1].substring(0, Math.min(split[1].length(), 24));
					
				Log.i(TAG, "Track: " + mTrack);
				Log.i(TAG, "Artist: "  + mArtist);
				sendArtist();
				sendTrack();
			} else {
				for (String s : split) {
					Log.i(TAG, "Field: " + ((s == null) ? "null" : s));
				}
			}
		}
	};

	private static IntentFilter nlIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(NLService.ACTION_NOTIFICATION_POSTED);
		intentFilter.addAction(NLService.ACTION_SONG_CHANGED);
		return intentFilter;
	}


	private void broadcastUpdate(final String action) {
		final Intent intent = new Intent(action);
		sendBroadcast(intent);
	}

	private void broadcastUpdate(final String action, int rssi) {
		final Intent intent = new Intent(action);
		intent.putExtra(EXTRA_DATA, String.valueOf(rssi));
		sendBroadcast(intent);
	}

	private void broadcastUpdate(final String action,
			final BluetoothGattCharacteristic characteristic) {
		final Intent intent = new Intent(action);

		// This is special handling for the Heart Rate Measurement profile. Data
		// parsing is
		// carried out as per profile specifications:
		// http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
		if (UUID_BLE_SHIELD_RX.equals(characteristic.getUuid())) {
			final byte[] rx = characteristic.getValue();
			for (byte b : rx) {
				handleBtByte(b);
			}
			intent.putExtra(EXTRA_DATA, rx);
		}

		sendBroadcast(intent);
	}

	private char toHex(int b) {
		switch (b) {	
		case  0: return '0';
		case  1: return '1';
		case  2: return '2';
		case  3: return '3';
		case  4: return '4';
		case  5: return '5';
		case  6: return '6';
		case  7: return '7';
		case  8: return '8';
		case  9: return '9';
		case 10: return 'a';
		case 11: return 'b';
		case 12: return 'c';
		case 13: return 'd';
		case 14: return 'e';
		case 15: return 'f';
		}
		return '*';
	}

	private void sendVolume() {
		int volume_index = mAudioManager
			.getStreamVolume(mAudioManager.STREAM_MUSIC);

		int max_volume = mAudioManager
			.getStreamMaxVolume(mAudioManager.STREAM_MUSIC);

		byte volume = (byte) (255 * ((float) (volume_index) / ((float) max_volume)));

		sendString("v" +
				   String.valueOf(toHex((volume >> 4) & 0xF)) +
				   String.valueOf(toHex(volume & 0xF)));
	}

	private void sendPlaying() {
		sendString(mPlaying ? "X" : "x");
	}

	private void sendNetwork() {
		sendString(mOnline ? "O" : "o");
	}

	private void sendArtist() {
		sendString("a" + mArtist + "\n");
	}

	private void sendTrack() {
		sendString("t" + mTrack + "\n");
	}

	private void sendState() {
	    Log.i(TAG, "Send state");
		sendVolume();
		sendPlaying();
		sendArtist();
		sendTrack();
	}

	private void handleBtByte(byte b) {
		char c = (char) b;
		int volume;

		switch (c) {
		  case 'o':
				mOnline = !mOnline;
				sendNetwork();
				break;
			case 'x':
				mPlaying = !mPlaying;
				sendPlaying();
				break;
			case 'P':
				mTrack = "Prev";
				sendTrack();
				break;
			case 'N':
				mTrack = "Next";
				sendTrack();
				break;
		    case 'v':
			    /* this is wrong, but we're in prototype mode */
				mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
												 AudioManager.ADJUST_LOWER,
												 AudioManager.FLAG_VIBRATE | 
												 AudioManager.FLAG_PLAY_SOUND);
				sendVolume();
				break;
		     case 'V':
				 /* this is wrong, but we're in prototype mode */
				 mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
												  AudioManager.ADJUST_RAISE,
												  AudioManager.FLAG_VIBRATE | 
												  AudioManager.FLAG_PLAY_SOUND);
				 sendVolume();
				 break;
				 
		};
	}

	public void sendBytes(byte[] bytes) {
		/* Byte buffer must be prepended with a null byte for some reason */
		byte[] tx = new byte[bytes.length + 1];
		tx[0] = 0x00;

		for (int i = 0; i < bytes.length; i++) {
			tx[i + 1] = bytes[i];
		}

		mTX.setValue(tx);
		writeCharacteristic(mTX);
	}

	public void sendString(String str) {
		sendBytes(str.getBytes());
	}

	public class LocalBinder extends Binder {
		RBLService getService() {
			return RBLService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		close();
		return super.onUnbind(intent);
	}

	private final IBinder mBinder = new LocalBinder();

	/**
	 * Initializes a reference to the local Bluetooth adapter.
	 * 
	 * @return Return true if the initialization is successful.
	 */
	public boolean initialize() {
		// For API level 18 and above, get a reference to BluetoothAdapter
		// through
		// BluetoothManager.
		Log.i(TAG, "Initializing");

		if (mBluetoothManager == null) {
			mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
			if (mBluetoothManager == null) {
				Log.e(TAG, "Unable to initialize BluetoothManager.");
				return false;
			}
		}

		mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

		mBluetoothAdapter = mBluetoothManager.getAdapter();
		if (mBluetoothAdapter == null) {
			Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
			return false;
		}

		return true;
	}

	/**
	 * Connects to the GATT server hosted on the Bluetooth LE device.
	 * 
	 * @param address
	 *            The device address of the destination device.
	 * 
	 * @return Return true if the connection is initiated successfully. The
	 *         connection result is reported asynchronously through the
	 *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 *         callback.
	 */
	public boolean connect(final String address) {
		if (mBluetoothAdapter == null || address == null) {
			Log.w(TAG,
					"BluetoothAdapter not initialized or unspecified address.");
			return false;
		}

		// Previously connected device. Try to reconnect.
		if (mBluetoothDeviceAddress != null
				&& address.equals(mBluetoothDeviceAddress)
				&& mBluetoothGatt != null) {
			Log.d(TAG,
					"Trying to use an existing mBluetoothGatt for connection.");
			if (mBluetoothGatt.connect()) {
				return true;
			} else {
				return false;
			}
		}

		final BluetoothDevice device = mBluetoothAdapter
				.getRemoteDevice(address);
		if (device == null) {
			Log.w(TAG, "Device not found.  Unable to connect.");
			return false;
		}
		// We want to directly connect to the device, so we are setting the
		// autoConnect
		// parameter to false.
		mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
		Log.d(TAG, "Trying to create a new connection.");
		mBluetoothDeviceAddress = address;

		return true;
	}

	/**
	 * Disconnects an existing connection or cancel a pending connection. The
	 * disconnection result is reported asynchronously through the
	 * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 * callback.
	 */
	public void disconnect() {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		mBluetoothGatt.disconnect();
	}

	/**
	 * After using a given BLE device, the app must call this method to ensure
	 * resources are released properly.
	 */
	public void close() {
		if (mBluetoothGatt == null) {
			return;
		}
		mBluetoothGatt.close();
		mBluetoothGatt = null;
	}

	/**
	 * Request a read on a given {@code BluetoothGattCharacteristic}. The read
	 * result is reported asynchronously through the
	 * {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
	 * callback.
	 * 
	 * @param characteristic
	 *            The characteristic to read from.
	 */
	public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}

		mBluetoothGatt.readCharacteristic(characteristic);
	}

	public void readRssi() {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}

		mBluetoothGatt.readRemoteRssi();
	}

	public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}

		mBluetoothGatt.writeCharacteristic(characteristic);
	}

	/**
	 * Enables or disables notification on a give characteristic.
	 * 
	 * @param characteristic
	 *            Characteristic to act on.
	 * @param enabled
	 *            If true, enable notification. False otherwise.
	 */
	public void setCharacteristicNotification(
			BluetoothGattCharacteristic characteristic, boolean enabled) {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

		if (UUID_BLE_SHIELD_RX.equals(characteristic.getUuid())) {
			BluetoothGattDescriptor descriptor = characteristic
					.getDescriptor(UUID
							.fromString(RBLGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
			descriptor
					.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			mBluetoothGatt.writeDescriptor(descriptor);
		}
	}

	/**
	 * Retrieves a list of supported GATT services on the connected device. This
	 * should be invoked only after {@code BluetoothGatt#discoverServices()}
	 * completes successfully.
	 * 
	 * @return A {@code List} of supported services.
	 */
	public BluetoothGattService getSupportedGattService() {
		if (mBluetoothGatt == null)
			return null;

		return mBluetoothGatt.getService(UUID_BLE_SHIELD_SERVICE);
	}
}
