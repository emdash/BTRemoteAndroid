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

import java.util.Arrays;
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
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

/**
 * Service for managing connection and data communication with a GATT server
 * hosted on a given Bluetooth LE device.
 */
public class RBLService extends Service {
    public final static String ACTION_CONNECT = "ACTION_CONNECT";
    public final static String ACTION_CONNECTED = "ACTION_CONNECTED";
    public final static String ACTION_DISCONNECT = "ACTION_DISCONNECT";
    public final static String ACTION_DISCONNECTED = "ACTION_DISCONNECTED";
    public final static String ACTION_READY = "ACTION_READY";
    public final static String ACTION_RSSI = "ACTION_RSSI";
    public final static String ACTION_RX = "ACTION_RX";
    public final static String ACTION_TX = "ACTION_TX";
    public final static String ACTION_UNSUPPORTED = "ACTION_UNSUPPORTED";

    public final static String EXTRA_RX = "EXTRA_RX";
    public final static String EXTRA_TX = "EXTRA_TX";
    public final static String EXTRA_DEVICE_ADDRESS = "EXTRA_DEVICE_ADDRESS";

    public final static UUID UUID_BLE_SHIELD_TX = UUID
            .fromString(RBLGattAttributes.BLE_SHIELD_TX);
    public final static UUID UUID_BLE_SHIELD_RX = UUID
            .fromString(RBLGattAttributes.BLE_SHIELD_RX);
    public final static UUID UUID_BLE_SHIELD_SERVICE = UUID
            .fromString(RBLGattAttributes.BLE_SHIELD_SERVICE);
    public final static byte VOLUME_DELTA = 1;

    final static String TAG = RBLService.class.getSimpleName();

    final static String SERVICECMD = "com.spotify.mobile.android.ui.widget.";
    final static String CMDNEXT = "NEXT";
    final static String CMDPREV = "PREVIOUS";
    final static String CMDTOGGLE = "PLAY";
    final static String META_CHANGED =
        "com.android.music.metadatachanged";
    final static String PLAYSTATE_CHANGED =
        "com.spotify.music.playbackstatechanged";
    final static String QUEUE_CHANGED =
        "com.spotify.music.queuechanged";

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
	double reconnectInterval = 1.0d;

    TimerTask mPostConnectTask = new TimerTask() {
        public void run () {
            sendState();
        }
    };

	// TimerTask mReconnectTask = new TimerTask() {
	// 	public void run () {
	// 		connectToDevice();
	// 	}
	// };

    Timer mTimer = new Timer();

    /* Turns out we need to keep the device awake while we're
     * connected, or it'll go to sleep and we lose our connection. */
    PowerManager mPowerManager;
    WakeLock mWakeLock;

    final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt,
                                            int status,
                                            int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                Log.i(TAG, "Discovering services.");
                if (!mBluetoothGatt.discoverServices()) {
                    Log.e(TAG, "Service discovery failed to start.");
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(ACTION_DISCONNECTED);

				/* We respond to different set of intents when
				 * disconnected from the device. */
                unregisterReceiver(mReceiver);
                registerReceiver(mReceiver, mDisconnectedFilter);

				/* We don't want the GATT characteristics from the
				 * last connection to hang around. */ 
                mTX = null;
                mRX = null;

				/* We don't want the device to remain awake
				 * indefinitely with the remote disconnected. */
				mWakeLock.release();
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
                mTX = service.getCharacteristic(UUID_BLE_SHIELD_TX);
                mRX = service.getCharacteristic(UUID_BLE_SHIELD_RX);
                setCharacteristicNotification(mRX, true);
                mTimer.schedule(mPostConnectTask, 1000);

                // Update the set of actions we can handle now that
                // we're connected.
                Log.i(TAG, "Unregistering receiver");
                unregisterReceiver(mReceiver);
                registerReceiver(mReceiver, mConnectedFilter);

                // Announce to the system that we're connected now.
                broadcastUpdate(ACTION_CONNECTED);

                // stash current volume level so it doesn't jump
                mVolume = fromVolumeIndex(mAudioManager
                                          .getStreamVolume(AudioManager
                                                           .STREAM_MUSIC));

                // Acquire wake lock so that we remain powered on as
                // long as we have a bluetooth connection.
                mWakeLock.acquire();
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

    void handleNotificationAction(Intent intent) {
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
    
    BroadcastReceiver mReceiver = new BroadcastReceiver() {        
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i(TAG, "Got intent: " + action);
            if (action.equals(NLService.ACTION_SONG_CHANGED)) {
                handleNotificationAction(intent);
            } else if (action.equals(ACTION_TX)) {
                sendString(intent.getStringExtra(EXTRA_TX));
            } else if (action.equals(ACTION_CONNECT)) {
                connectToDevice(intent);
            } else if (action.equals(ACTION_DISCONNECT)) {
                disconnect();
            } else if (action.equals(PLAYSTATE_CHANGED)) {
                mPlaying = intent.getBooleanExtra("playing", false);
                sendPlaying();
            }
        }
    };

    IntentFilter mDisconnectedFilter = new IntentFilter();
    IntentFilter mConnectedFilter = new IntentFilter();

    void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    void broadcastUpdate(final String action, int rssi) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_RX, String.valueOf(rssi));
        sendBroadcast(intent);
    }

    void broadcastUpdate(final String action,
                         final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        if (UUID_BLE_SHIELD_RX.equals(characteristic.getUuid())) {
            final byte[] rx = characteristic.getValue();
            for (byte b : rx) {
                handleBtByte(b);
            }
            intent.putExtra(EXTRA_RX, rx);
        }

        sendBroadcast(intent);
    }

    char toHex(int b) {
        // TODO: replace this crap with String.format()
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

    void sendVolume() {
        byte volume = (byte) Math.min(255, mVolume * 2);
        String s = ("v" +
                    String.valueOf(toHex((volume >> 4) & 0xF)) +
                    String.valueOf(toHex(volume & 0xF)));
        Log.i(TAG, s);
        sendString(s);
    }

    void sendPlaying() {
        sendString(mPlaying ? "X" : "x");
    }

    void sendNetwork() {
        sendString(mOnline ? "O" : "o");
    }

    void sendArtist() {
        sendString("a" + mArtist + "\n");
    }

    void sendTrack() {
        sendString("t" + mTrack + "\n");
    }

    void sendState() {
        Log.i(TAG, "Send state");
        sendVolume();
        sendPlaying();
        sendArtist();
        sendTrack();
    }

    void handleBtByte(byte b) {
        char c = (char) b;
        int volume;

        switch (c) {
          case 'o':
                mOnline = !mOnline;
                sendNetwork();
                break;
            case 'x':
                sendBroadcast(new Intent(SERVICECMD + CMDTOGGLE));
                break;
            case 'P':
                sendBroadcast(new Intent(SERVICECMD + CMDPREV));
                break;
            case 'N':
                sendBroadcast(new Intent(SERVICECMD + CMDNEXT));
                break;
            case 'v':
                adjustVolume(false);
                sendVolume();
                break;
            case 'V':
                adjustVolume(true);
                sendVolume();
                break;
                 
        };
    }

    byte fromVolumeIndex(int index) {
        int maxVolume = mAudioManager
            .getStreamMaxVolume(mAudioManager.STREAM_MUSIC);
        return ((byte) (((double)index) / ((double)(maxVolume)) * 127));
    }

    int toVolumeIndex(byte volume) {
        int maxVolume = mAudioManager
            .getStreamMaxVolume(mAudioManager.STREAM_MUSIC);
        return ((int) ((((float) volume) / 127.0d) * ((double) maxVolume)));
    }

    void adjustVolume(boolean up)
    {
        int volume = Math.min(
            127,
            Math.max(
                0,
                ((int) mVolume) + (up ? VOLUME_DELTA : -VOLUME_DELTA)));
        mVolume = (byte) volume;
        int target_volume = toVolumeIndex(mVolume);
        Log.i(TAG, "Internal Volume: " + mVolume);
        Log.i(TAG, "New Volume: " + volume);
        Log.i(TAG, "Target Volume: " + target_volume);
		mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
									  target_volume,
									  AudioManager.FLAG_VIBRATE |
									  AudioManager.FLAG_PLAY_SOUND);
	}

	void sendBytes(byte[] bytes) {
		Log.i(TAG, "sendBytes: " + bytes.length);
		if (mTX == null) {
			return;
		}

		int bytesRead = 0;

		while (bytesRead < (bytes.length - 20)) {
			Log.i(TAG, "Sending 20 bytes: " + bytesRead + ", " + (bytesRead + 20));
			byte[] tx = Arrays.copyOfRange(bytes, bytesRead, bytesRead + 20);
			mTX.setValue(tx);
			writeCharacteristic(mTX);
			bytesRead += 20;
		}

		if (bytesRead < bytes.length) {
			Log.i(TAG, "Sending " + (bytes.length - bytesRead) + "bytes.");
			byte[] tx = Arrays.copyOfRange(bytes, bytesRead, bytes.length);					
			mTX.setValue(tx);
			writeCharacteristic(mTX);
		}

		Log.i(TAG, "No more bytes.");
	}

	void sendString(String str) {
		sendBytes(str.getBytes());
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		Log.i(TAG, "Initializing");

		if (mBluetoothManager == null) {
			mBluetoothManager = (BluetoothManager)
				getSystemService(Context.BLUETOOTH_SERVICE);
			if (mBluetoothManager == null) {
				Log.e(TAG, "Unable to initialize BluetoothManager.");
				sendBroadcast(new Intent(ACTION_UNSUPPORTED));
				return;
			}
		}

		mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

		mBluetoothAdapter = mBluetoothManager.getAdapter();
		if (mBluetoothAdapter == null) {
			Log.e(TAG, "Unable to get BluetoothAdapter.");
			sendBroadcast(new Intent(ACTION_UNSUPPORTED));
			return;
		}

		// These actions should only be handled once we're connected.
		mConnectedFilter.addAction(NLService.ACTION_NOTIFICATION_POSTED);
		mConnectedFilter.addAction(NLService.ACTION_SONG_CHANGED);
		mConnectedFilter.addAction(RBLService.ACTION_TX);
		mConnectedFilter.addAction(RBLService.ACTION_DISCONNECT);
		mConnectedFilter.addAction(PLAYSTATE_CHANGED);

		// This is the only action we can safely handle when we're
		// disconnected.
		mDisconnectedFilter.addAction(RBLService.ACTION_CONNECT);
		mDisconnectedFilter.addAction(NLService.ACTION_NOTIFICATION_POSTED);
		mDisconnectedFilter.addAction(NLService.ACTION_SONG_CHANGED);
		mConnectedFilter.addAction(PLAYSTATE_CHANGED);

		// We start disconnected.
		registerReceiver(mReceiver, mDisconnectedFilter);

		sendBroadcast(new Intent(ACTION_READY));
		sendBroadcast(new Intent(NLService.ACTION_GET_NOTIFICATIONS));

		// Initialize PM and Wake Lock
		mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
	}

	public void onDestroy() {
		super.onDestroy();
		close();
	}

	/**
	 * Connects to the GATT server hosted on the Bluetooth LE device.
	 * 
	 * @param address
	 *            The device address of the destination device.
	 * 
	 * @return Return true if the connection is initiated successfully.
	 */
	boolean connectToDevice(Intent intent) {
		String address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS);

		if (mBluetoothAdapter == null) {
			Log.e(TAG, "BluetoothAdapter not initialized.");
			return false;
		}

		if (address == null) {
			Log.e(TAG, "No address given in intent.");
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

		final BluetoothDevice device =
			mBluetoothAdapter
			.getRemoteDevice(address);
		
		if (device == null) {
			Log.e(TAG, "Device not found.  Unable to connect.");
			return false;
		}

		// We want to directly connect to the device, so we are
		// setting the autoConnect parameter to false.
		
		// TODO: this might be something we want to
		// revisit. Autoconnect is a planned feature.

		mBluetoothGatt = device.connectGatt(this, false, mGattCallback);

		Log.d(TAG, "Trying to create a new connection.");
		mBluetoothDeviceAddress = address;

		return true;
	}

	/**
	 * Disconnects an existing connection or cancel a pending
	 * connection.
	 */
	void disconnect() {
		Log.i(TAG, "Got disconnect intent.");

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
	void close() {
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
	void readCharacteristic(BluetoothGattCharacteristic characteristic) {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}

		mBluetoothGatt.readCharacteristic(characteristic);
	}

	void readRssi() {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}

		mBluetoothGatt.readRemoteRssi();
	}

	void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
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
	void setCharacteristicNotification(
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
	BluetoothGattService getSupportedGattService() {
		if (mBluetoothGatt == null)
			return null;

		return mBluetoothGatt.getService(UUID_BLE_SHIELD_SERVICE);
	}
}
