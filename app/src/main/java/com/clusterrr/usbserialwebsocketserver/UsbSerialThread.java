package com.clusterrr.usbserialwebsocketserver;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;

public class UsbSerialThread extends Thread {
    final static int WRITE_TIMEOUT = 1000;

    private UsbSerialWebsocketService mUsbSerialWebsocketService;
    private UsbSerialPort mSerialPort;
    private Handler mHandler;

    public UsbSerialThread(UsbSerialWebsocketService UsbSerialWebsocketService, UsbSerialPort serialPort) {
        mUsbSerialWebsocketService = UsbSerialWebsocketService;
        mSerialPort = serialPort;
        mHandler = new Handler();
    }

    @Override
    public void run() {
        byte[] buffer = new byte[1024];

        try {
            while (true) {
                if (mSerialPort == null) break;
                // Read data
                int l = mSerialPort.read(buffer, 0);
                if (l <= 0) break; // disconnect
                if (BuildConfig.DEBUG) {
                    StringBuilder hexStr = new StringBuilder();
                    for (int i = 0; i < l; i++) {
                        hexStr.append(String.format("%02X ", buffer[i]));
                    }
                    Log.d(UsbSerialWebsocketService.TAG, "Received " + l + " bytes from port: " + hexStr.toString().trim());
                }
                // Write data
                mUsbSerialWebsocketService.writeClients(buffer, 0, l);
            }
        }
        catch (IOException e) {
            Log.i(UsbSerialWebsocketService.TAG, "Serial port: " + e.getMessage());
            markStopped();
        }
        catch (Exception e) {
            e.printStackTrace();
            markStopped();
        }
        close();
        Log.i(UsbSerialWebsocketService.TAG, "Serial port closed");
        mUsbSerialWebsocketService.stopSelf();
    }

    public void write(byte[] data) throws IOException {
        if (mSerialPort != null)
            mSerialPort.write(data, WRITE_TIMEOUT);
    }

    public void close() {
        try {
            if (mSerialPort != null)
                mSerialPort.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        mSerialPort = null;
    }

    private void markStopped()
    {
        SharedPreferences prefs = mUsbSerialWebsocketService.getApplicationContext().getSharedPreferences(mUsbSerialWebsocketService.getString(R.string.app_name), Context.MODE_PRIVATE);
        prefs.edit().putBoolean(UsbSerialWebsocketService.KEY_LAST_STATE, false).apply();
    }
}
