// Pink Music Android is distributed under the FreeBSD License
//
// Copyright (c) 2013-2016, Siva Prasad												
// All rights reserved.																
//*******************************************************************************************
//*******************************************************************************************
//	Redistribution and use in source and binary forms, with or without		   
//	modification, are permitted provided that the following conditions are met:     	
//																						**
//	 1. Redistributions of source code must retain the above copyright notice, this		
//       list of conditions and the following disclaimer.									
//	 2. Redistributions in binary form must reproduce the above copyright notice		
//       this list of conditions and the following disclaimer in the documentation			
//       and/or other materials provided with the distribution.							    
//																						**
//	THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND		
//   	ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED		
//	WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE				
//      DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR		
//      ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES		
//      (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;	
//      LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND		
//      ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT		
//      (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS		
//      SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.						
//																						**
//      The views and conclusions contained in the software and documentation are those		
//      of the authors and should not be interpreted as representing official policies,		
//      either expressed or implied, of the FreeBSD Project.								
//********************************************************************************************
//********************************************************************************************

package br.com.siva.pinkmusic.util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

import br.com.siva.pinkmusic.R;
import br.com.siva.pinkmusic.activity.MainHandler;
import br.com.siva.pinkmusic.list.BaseItem;
import br.com.siva.pinkmusic.list.BaseList;
import br.com.siva.pinkmusic.playback.Player;
import br.com.siva.pinkmusic.ui.BgListView;
import br.com.siva.pinkmusic.ui.UI;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public final class BluetoothConnectionManager extends BroadcastReceiver implements Runnable, View.OnClickListener, AlertDialog.OnClickListener, AlertDialog.OnCancelListener, AlertDialog.OnDismissListener, AdapterView.OnItemClickListener {
	public static final int REQUEST_ENABLE = 0x1000;

	public static final int OK = 0;
	public static final int NOT_ENABLED = 1;
	public static final int ERROR = -1;
	public static final int ERROR_NOT_ENABLED = -2;
	public static final int ERROR_DISCOVERY = -3;
	public static final int ERROR_NOTHING_PAIRED = -4;
	public static final int ERROR_STILL_PAIRING = -5;
	public static final int ERROR_CONNECTION = -6;
	public static final int ERROR_COMMUNICATION = -7;

	private static ColorStateList defaultTextColors, highlightTextColors;

	public interface BluetoothObserver {
		void onBluetoothPairingStarted(BluetoothConnectionManager manager, String description, String address);
		void onBluetoothPairingFinished(BluetoothConnectionManager manager, String description, String address, boolean success);
		void onBluetoothCancelled(BluetoothConnectionManager manager);
		void onBluetoothConnected(BluetoothConnectionManager manager);
		void onBluetoothError(BluetoothConnectionManager manager, int error);
	}

	private static final class DeviceItem extends BaseItem {
		public final String name, description, address;
		public final boolean paired, recentlyUsed;

		public DeviceItem(String name, String address, boolean paired, boolean recentlyUsed) {
			this.name = name;
			this.description = name + " - " + address;
			this.address = address;
			this.paired = paired;
			this.recentlyUsed = recentlyUsed;
		}

		@Override
		public String toString() {
			return description;
		}
	}

	private static final class DeviceList extends BaseList<DeviceItem> implements ArraySorter.Comparer<DeviceItem> {
		public DeviceList() {
			super(DeviceItem.class, 256);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TextView txt = (TextView)convertView;
			if (txt == null) {
				txt = new TextView(Player.getService());
				txt.setPadding(UI.dialogMargin, UI.dialogDropDownVerticalMargin, UI.dialogMargin, UI.dialogDropDownVerticalMargin);
				txt.setTypeface(UI.defaultTypeface);
				txt.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI.dialogTextSize);
			}
			txt.setTextColor(items[position].recentlyUsed ? highlightTextColors : defaultTextColors);
			txt.setText(items[position].description);
			return txt;
		}

		@Override
		public int getViewHeight() {
			return 0;
		}

		@Override
		public int compare(DeviceItem a, DeviceItem b) {
			//if (a.recentlyUsed != b.recentlyUsed)
			//	return (a.recentlyUsed ? -1 : 1);
			return a.description.compareToIgnoreCase(b.description);
		}

		public void sort() {
			//synchronized (sync) {
				modificationVersion++;
				ArraySorter.sort(items, 0, this.count, this);
				current = -1;
				firstSel = -1;
				lastSel = -1;
				originalSel = -1;
				indexOfPreviouslyDeletedCurrentItem = -1;
			//}
			notifyDataSetChanged(-1, CONTENT_MOVED);
		}
	}

	private BluetoothObserver observer;
	private Activity activity;
	private BluetoothAdapter btAdapter;
	private BluetoothSocket btSocket;
	private InputStream inputStream;
	private OutputStream outputStream;
	private DeviceList deviceList;
	private Button btnOK;
	private TextView lblTitle;
	private ProgressBar barWait;
	private ListView listView;
	private AlertDialog dialog;
	private boolean closed, connecting, finished;
	private int lastError;
	private volatile DeviceItem deviceItem;
	private volatile boolean cancelled;

	public BluetoothConnectionManager(BluetoothObserver observer) {
		this.observer = observer;
		try {
			btAdapter = BluetoothAdapter.getDefaultAdapter();
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	public InputStream getInputStream() {
		return inputStream;
	}

	public OutputStream getOutputStream() {
		return outputStream;
	}

	public void destroyUI() {
		if (btAdapter != null) {
			try {
				if (btAdapter.isDiscovering())
					btAdapter.cancelDiscovery();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			btAdapter = null;
		}
		if (listView != null) {
			listView.setAdapter(null);
			listView = null;
		}
		if (activity != null) {
			activity.unregisterReceiver(this);
			activity = null;
		}
		deviceList = null;
		btnOK = null;
		lblTitle = null;
		barWait = null;
		dialog = null;
		deviceItem = null;
	}

	public void destroy() {
		if (inputStream != null) {
			try {
				inputStream.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			inputStream = null;
		}
		if (outputStream != null) {
			try {
				outputStream.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			outputStream = null;
		}
		if (btSocket != null) {
			try {
				btSocket.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			btSocket = null;
		}
		destroyUI();
		observer = null;
	}

	@Override
	public void run() {
		final DeviceItem deviceItem = this.deviceItem;
		if (deviceItem == null)
			return;
		if (MainHandler.isOnMainThread()) {
			final boolean success = (inputStream != null && outputStream != null && lastError == OK);
			final boolean callObserver = !finished;
			finished = true;
			if (dialog != null)
				dialog.dismiss();
			destroyUI();
			connecting = false;
			if (observer != null && callObserver) {
				observer.onBluetoothPairingFinished(this, deviceItem.description, deviceItem.address, success);
				if (cancelled)
					observer.onBluetoothCancelled(this);
				else if (lastError == OK)
					observer.onBluetoothConnected(this);
				else
					observer.onBluetoothError(this, lastError);
			}
			return;
		}
		try {
			BluetoothDevice device = btAdapter.getRemoteDevice(deviceItem.address);
			final UUID SERIAL_PORT_SERVICE_CLASS_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
			btSocket = device.createRfcommSocketToServiceRecord(SERIAL_PORT_SERVICE_CLASS_UUID);
			try {
				btSocket.connect();
			} catch (Throwable ex) {
				btSocket = null;
				if (!deviceItem.paired) {
					lastError = ERROR_STILL_PAIRING;
					MainHandler.postToMainThread(this);
					return;
				}
				// try another method for connection, this should work on the HTC desire, credits to Michael Biermann
				try {
					Method mMethod = device.getClass().getMethod("createRfcommSocket", int.class);
					btSocket = (BluetoothSocket)mMethod.invoke(device, 1);
					btSocket.connect();
				} catch (Throwable e1){
					btSocket = null;
					lastError = ERROR_CONNECTION;
					MainHandler.postToMainThread(this);
					return;
				}
			}
			inputStream = btSocket.getInputStream();
			outputStream = btSocket.getOutputStream();
		} catch (Throwable ex) {
			lastError = (!deviceItem.paired ? ERROR_STILL_PAIRING : ERROR_CONNECTION);
			MainHandler.postToMainThread(this);
			return;
		}
		lastError = OK;
		MainHandler.postToMainThread(this);
	}

	public int showDialogAndScan(Activity activity) {
		if (btAdapter == null)
			return ERROR;
		try {
			if (!btAdapter.isEnabled()) {
				activity.startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE);
				return NOT_ENABLED;
			}
		} catch (Throwable ex) {
			return ERROR_NOT_ENABLED;
		}

		this.activity = activity;
		activity.registerReceiver(this, new IntentFilter(BluetoothDevice.ACTION_FOUND));
		activity.registerReceiver(this, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

		LinearLayout l = (LinearLayout)UI.createDialogView(activity, null);

		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		lblTitle = new TextView(activity);
		lblTitle.setText(R.string.bt_scanning);
		lblTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI.dialogTextSize);
		lblTitle.setLayoutParams(p);

		p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		p.topMargin = UI.dialogMargin >> 1;
		barWait = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
		barWait.setIndeterminate(true);
		barWait.setLayoutParams(p);

		p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		p.topMargin = UI.dialogMargin;
		UI.disableEdgeEffect(activity);
		listView = new ListView(activity);
		listView.setLayoutParams(p);
		listView.setOverScrollMode(ListView.OVER_SCROLL_IF_CONTENT_SCROLLS);
		listView.setVerticalScrollBarEnabled(UI.browserScrollBarType != BgListView.SCROLLBAR_NONE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			listView.setVerticalScrollbarPosition(UI.scrollBarToTheLeft ? ListView.SCROLLBAR_POSITION_LEFT : ListView.SCROLLBAR_POSITION_RIGHT);

		l.addView(lblTitle);
		l.addView(barWait);
		l.addView(listView);

		deviceList = new DeviceList();
		defaultTextColors = lblTitle.getTextColors();
		highlightTextColors = ColorStateList.valueOf(UI.isAndroidThemeLight() ? 0xff0099cc : 0xff33b5e5);

		final Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
		if (pairedDevices.size() > 0) {
			for (BluetoothDevice device : pairedDevices)
				deviceList.add(new DeviceItem(device.getName(), device.getAddress(), true, false), -1);
			deviceList.sort();
		}
		listView.setAdapter(deviceList);
		listView.setOnItemClickListener(this);
		try {
			if (btAdapter.isDiscovering())
				btAdapter.cancelDiscovery();
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		try {
			btAdapter.startDiscovery();
		} catch (Throwable ex) {
			return ERROR_DISCOVERY;
		}

		dialog = (new AlertDialog.Builder(activity))
			.setTitle(activity.getText(R.string.bt_devices))
			.setView(l)
			.setPositiveButton(R.string.refresh_list, this)
			.setNegativeButton(R.string.cancel, this)
			.create();
		dialog.setOnCancelListener(this);
		dialog.setOnDismissListener(this);
		btnOK = UI.prepareDialogAndShow(dialog)
			.getButton(Dialog.BUTTON_POSITIVE);
		if (btnOK != null) {
			btnOK.setEnabled(false);
			btnOK.setOnClickListener(this);
		}

		return OK;
	}

	private void stopDialogDiscovery() {
		try {
			if (btAdapter != null && btAdapter.isDiscovering())
				btAdapter.cancelDiscovery();
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		if (lblTitle != null)
			lblTitle.setVisibility(View.GONE);
		if (barWait != null)
			barWait.setVisibility(View.GONE);
		if (btnOK != null)
			btnOK.setEnabled(true);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		if (activity == null || deviceList == null)
			return;
		final String action = intent.getAction();
		if (BluetoothDevice.ACTION_FOUND.equals(action)) {
			final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			final String address = device.getAddress();
			final String name = device.getName();
			boolean paired = false;
			for (int i = deviceList.getCount() - 1; i >= 0; i--) {
				final DeviceItem di = deviceList.getItemT(i);
				if (di.address.equalsIgnoreCase(address)) {
					//an item with this same address/name is already on the list
					if (di.name.equalsIgnoreCase(name))
						return;
					paired = di.paired;
					deviceList.setSelection(i, true);
					deviceList.removeSelection();
					break;
				}
			}
			deviceList.add(new DeviceItem(((name == null || name.length() == 0) ? activity.getText(R.string.bt_null_device_name).toString() : name), address, paired, false), -1);
			deviceList.setSelection(-1, true);
			//stop sorting as the devices are discovered, to prevent the
			//order of the list from changing
			//deviceList.sort();
		} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
			if (!connecting) {
				stopDialogDiscovery();
				if (deviceList.getCount() == 0)
					deviceList.add(new DeviceItem(activity.getText(R.string.bt_not_found).toString(), null, false, false), -1);
			}
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		if (dialog != null && deviceList != null && position >= 0 && position < deviceList.getCount()) {
			final DeviceItem item = deviceList.getItemT(position);
			if (item != null && item.address != null) {
				stopDialogDiscovery();
				deviceItem = item;
				if (lblTitle != null) {
					lblTitle.setText(R.string.bt_connecting);
					lblTitle.setVisibility(View.VISIBLE);
				}
				if (barWait != null)
					barWait.setVisibility(View.VISIBLE);
				if (listView != null)
					listView.setVisibility(View.GONE);
				if (btnOK != null)
					btnOK.setEnabled(false);
				if (observer != null)
					observer.onBluetoothPairingStarted(this, item.description, item.address);
				lastError = OK;
				cancelled = false;
				finished = false;
				connecting = true;
				(new Thread(this, "Bluetooth Manager Thread")).start();
			}
		}
	}

	@Override
	public void onClick(View view) {
		if (view == btnOK && btAdapter != null && !connecting) {
			if (lblTitle != null)
				lblTitle.setVisibility(View.VISIBLE);
			if (barWait != null)
				barWait.setVisibility(View.VISIBLE);
			try {
				btAdapter.startDiscovery();
			} catch (Throwable ex) {
				return;
			}
			btnOK.setEnabled(false);
		}
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (which == AlertDialog.BUTTON_NEGATIVE)
			cancelled = true;
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		if (activity != null)
			UI.reenableEdgeEffect(activity);
		if (!closed) {
			closed = true;
			destroyUI();
			if (!finished) {
				cancelled = true;
				finished = true;
				if (observer != null) {
					if (deviceItem != null)
						observer.onBluetoothPairingFinished(this, deviceItem.description, deviceItem.address, false);
					observer.onBluetoothCancelled(this);
				}
			}
		}
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		onDismiss(dialog);
	}
}
