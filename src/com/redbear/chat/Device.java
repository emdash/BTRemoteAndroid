package com.redbear.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleAdapter;

public class Device extends Activity implements OnItemClickListener {

	ArrayList<BluetoothDevice> devices;
	List<Map<String, String>> listItems = new ArrayList<Map<String, String>>();
	SimpleAdapter adapter;
	Map<String, String> map = null;
	ListView listView;
	String DEVICE_NAME = "name";
	String DEVICE_ADDRESS = "address";

	public static final int RESULT_CODE = 31;
	public final static String EXTRA_DEVICE_ADDRESS = "EXTRA_DEVICE_ADDRESS";
	public final static String EXTRA_DEVICE_NAME = "EXTRA_DEVICE_NAME";
	
	final static String TAG = Device.class.getSimpleName();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.device_list);

		setTitle("Device");

		listView = (ListView) findViewById(R.id.listView);

		devices = (ArrayList<BluetoothDevice>) Main.mDevices;
		for (BluetoothDevice device : devices) {
			map = new HashMap<String, String>();
			map.put(DEVICE_NAME, device.getName());
			map.put(DEVICE_ADDRESS, device.getAddress());
			listItems.add(map);
		}

		adapter = new SimpleAdapter(getApplicationContext(), listItems,
				R.layout.list_item, new String[] { "name", "address" },
				new int[] { R.id.deviceName, R.id.deviceAddr });
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(this);
	}

	@Override
	public void onItemClick(AdapterView<?> adapterView, View view,
			int position, long id) {
		Log.i(TAG, "onItemCLick");

		HashMap<String, String> hashMap = (HashMap<String, String>) listItems
				.get(position);
		String addr = hashMap.get(DEVICE_ADDRESS);
		String name = hashMap.get(DEVICE_NAME);

		Log.i(TAG, "addr: " + addr);
		Log.i(TAG, "name: " + name);

		Intent intent = new Intent(RBLService.ACTION_CHOOSE_DEVICE);
		intent.putExtra(RBLService.EXTRA_DEVICE_ADDRESS, addr);
		sendBroadcast(intent);
		finish();
	}
}
