package com.clusterrr.usbserialwebsocketserver;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.Ch34xSerialDriver;

import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, UsbSerialWebsocketService.IOnStartStopListener, AdapterView.OnItemSelectedListener {
    final static String SETTING_LOCAL_ONLY = "local_only";
    final static String SETTING_WS_PORT = "ws_port";
    final static String SETTING_PORT_ID = "port_id";
    final static String SETTING_BAUD_RATE = "baud_rate";
    final static String SETTING_DATA_BITS = "data_bits";
    final static String SETTING_STOP_BITS = "stop_bits";
    final static String SETTING_PARITY = "parity";
    final static String SETTING_REMOVE_LF = "remove_lf";
    final static String SETTING_AUTOSTART = "autostart";

    final static int AUTOSTART_DISABLED = 0;
    final static int AUTOSTART_ENABLED = 1;
    final static int AUTOSTART_CLOSE = 2;

    private UsbSerialWebsocketService.ServiceBinder mServiceBinder = null;
    private final Handler mHandler = new Handler();
    private boolean mNeedClose = false;
    private AppCompatButton mStartButton;
    private AppCompatButton mStopButton;
    private SwitchCompat mLocalOnly;
    private AppCompatEditText mWsPort;
    private AppCompatSpinner mPortId;
    private AppCompatEditText mBaudRate;
    private AppCompatSpinner mDataBits;
    private AppCompatSpinner mStopBits;
    private AppCompatSpinner mParity;
    private AppCompatTextView mStatus;
    private SwitchCompat mRemoveLF;
    private AppCompatSpinner mAutostart;

    public boolean isStarted() {
        return mServiceBinder != null && mServiceBinder.isStarted();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (BuildConfig.DEBUG) {
            Log.d(UsbSerialWebsocketService.TAG, "Creating activity");
        }
        setContentView(R.layout.activity_main);

        mStartButton = findViewById(R.id.buttonStart);
        mStopButton = findViewById(R.id.buttonStop);
        mLocalOnly = findViewById(R.id.switchLocalOnly);
        mWsPort = findViewById(R.id.editTextTcpPort);
        mPortId = findViewById(R.id.spinnerPortId);
        mBaudRate = findViewById(R.id.editTextNumberBaudRate);
        mDataBits = findViewById(R.id.spinnerDataBits);
        mStopBits = findViewById(R.id.spinnerStopBits);
        mParity = findViewById(R.id.spinnerParity);
        mStatus = findViewById(R.id.textViewStatus);
        mRemoveLF = findViewById(R.id.switchRemoveLf);
        mAutostart = findViewById(R.id.spinnerAutostart);

        mAutostart.setOnItemSelectedListener(this);
        mStartButton.setOnClickListener(this);
        mStopButton.setOnClickListener(this);

        Intent serviceIntent = new Intent(this, UsbSerialWebsocketService.class);
        bindService(serviceIntent, mServiceConnection, 0); // in case if service already started

        updateSettings();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // Start service if need
        super.onNewIntent(intent);
        if (intent == null) return;
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
        String action = intent.getAction();
        if (BuildConfig.DEBUG) {
            Log.d(UsbSerialWebsocketService.TAG, "Received intent: " + action);
        }

        if (isStarted()) {
            return;
        }

        switch(action)
        {
            case Intent.ACTION_BOOT_COMPLETED:
            case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                mNeedClose = prefs.getInt(SETTING_AUTOSTART, AUTOSTART_DISABLED) == AUTOSTART_CLOSE;
            /* fall... */
            case UsbSerialWebsocketService.ACTION_NEED_TO_START:
                break;
            default:
                return;
        }

        switch (UsbSerialWebsocketService.getDeviceStatus(this)) {
            case NO_DEVICE:
                Log.wtf(UsbSerialWebsocketService.TAG, getString(R.string.device_not_found));
                return;
            case NO_PERMISSION:
                Log.e(UsbSerialWebsocketService.TAG, getString(R.string.missing_usb_device_permission));
                Toast.makeText(this, R.string.missing_usb_device_permission, Toast.LENGTH_LONG).show();
                return;
            case OK:
                break;
        }
        start();
    }

    private void requestDevicePermission(Context context) {
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        
        // Create custom ProbeTable for CH340K support
        ProbeTable customTable = new ProbeTable();
        customTable.addProduct(0x1a86, 0x7522, Ch34xSerialDriver.class); // CH340K
        UsbSerialProber prober = new UsbSerialProber(customTable);
        
        // Combine default prober with custom prober
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        availableDrivers.addAll(prober.findAllDrivers(manager));
        
        if (!availableDrivers.isEmpty()) {
            UsbSerialDriver driver = availableDrivers.get(0);
            UsbDevice device = driver.getDevice();
            Intent intent = new Intent(context, MainActivity.class);
            intent.setAction(UsbSerialWebsocketService.ACTION_NEED_TO_START);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            );
            manager.requestPermission(device, pendingIntent);
        }
    }    

    @Override
    protected void onPause() {
        super.onPause();
        saveSettings();
    }

    @Override
    public void onClick(View view)
    {
        switch(view.getId())
        {
            case R.id.buttonStart:
                mNeedClose = false;
                start();
                break;
            case R.id.buttonStop:
                stop();
                break;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent.getId() != R.id.spinnerAutostart) return;
        SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
        prefs.edit()
                .putInt(SETTING_AUTOSTART, position)
                .apply();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Unused
    }

    private void start() {
        saveSettings();

        switch (UsbSerialWebsocketService.getDeviceStatus(this)) {
            case NO_DEVICE:
                Toast.makeText(this, getString(R.string.device_not_found), Toast.LENGTH_SHORT).show();
                return;
            case NO_PERMISSION:
                requestDevicePermission(this);
                return;
            default:
                break;
        }

        Intent ignoreOptimization = prepareIntentForWhiteListingOfBatteryOptimization(
                this, getPackageName(), false);
        if (ignoreOptimization != null) startActivity(ignoreOptimization);

        Intent serviceIntent = new Intent(this, UsbSerialWebsocketService.class);
        SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
        serviceIntent.putExtra(UsbSerialWebsocketService.KEY_LOCAL_ONLY, prefs.getBoolean(SETTING_LOCAL_ONLY, false));
        serviceIntent.putExtra(UsbSerialWebsocketService.KEY_WS_PORT, prefs.getInt(SETTING_WS_PORT, 8080));
        serviceIntent.putExtra(UsbSerialWebsocketService.KEY_PORT_ID, prefs.getInt(SETTING_PORT_ID, 0));
        serviceIntent.putExtra(UsbSerialWebsocketService.KEY_BAUD_RATE, prefs.getInt(SETTING_BAUD_RATE, 115200));
        serviceIntent.putExtra(UsbSerialWebsocketService.KEY_DATA_BITS, prefs.getInt(SETTING_DATA_BITS, 3) + 5);
        switch (prefs.getInt(SETTING_STOP_BITS, 0)) {
            case 0:
                serviceIntent.putExtra(UsbSerialWebsocketService.KEY_STOP_BITS, UsbSerialPort.STOPBITS_1);
                break;
            case 1:
                serviceIntent.putExtra(UsbSerialWebsocketService.KEY_STOP_BITS, UsbSerialPort.STOPBITS_1_5);
                break;
            case 2:
                serviceIntent.putExtra(UsbSerialWebsocketService.KEY_STOP_BITS, UsbSerialPort.STOPBITS_2);
                break;
        }
        serviceIntent.putExtra(UsbSerialWebsocketService.KEY_PARITY, prefs.getInt(SETTING_PARITY, 0));
        serviceIntent.putExtra(UsbSerialWebsocketService.KEY_REMOVE_LF, prefs.getBoolean(SETTING_REMOVE_LF, true));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        bindService(serviceIntent, mServiceConnection, 0);
    }

    private void stop() {
        Intent serviceIntent = new Intent(this, UsbSerialWebsocketService.class);
        stopService(serviceIntent);
        mServiceBinder = null;
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
        prefs.edit().putBoolean(UsbSerialWebsocketService.KEY_LAST_STATE, false).commit();
        updateSettings();
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            mServiceBinder = (UsbSerialWebsocketService.ServiceBinder) service;
            mServiceBinder.setOnStartStopListener(MainActivity.this);
            updateSettings();
            SharedPreferences prefs = getApplicationContext().getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
            prefs.edit().putBoolean(UsbSerialWebsocketService.KEY_LAST_STATE, isStarted()).apply();
            if (BuildConfig.DEBUG) {
                Log.d(UsbSerialWebsocketService.TAG, "Service connected");
            }
            // Close if autoclose enabled
            if (isStarted() && mNeedClose) {
                // Delay to prevent race condition
                mHandler.postDelayed(() -> {
                    if (mNeedClose) finish();
                }, 1000);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mServiceBinder = null;
            if (BuildConfig.DEBUG) {
                Log.d(UsbSerialWebsocketService.TAG, "Service disconnected");
            }
        }
    };

    @Override
    public void usbSerialServiceStarted() {
    }

    @Override
    public void usbSerialServiceStopped() {
        updateSettings();
    }

    private void saveSettings() {
        SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
        int wsPort;
        try {
            wsPort = Integer.parseInt(mWsPort.getText().toString());
        }
        catch (NumberFormatException e) {
            wsPort = 8080;
        }
        int baudRate;
        try {
            baudRate = Integer.parseInt(mBaudRate.getText().toString());
        }
        catch (NumberFormatException e) {
            baudRate = 115200;
        }
        prefs.edit()
                .putBoolean(SETTING_LOCAL_ONLY, mLocalOnly.isChecked())
                .putInt(SETTING_WS_PORT, wsPort)
                .putInt(SETTING_PORT_ID, mPortId.getSelectedItemPosition())
                .putInt(SETTING_BAUD_RATE, baudRate)
                .putInt(SETTING_DATA_BITS, mDataBits.getSelectedItemPosition())
                .putInt(SETTING_STOP_BITS, mStopBits.getSelectedItemPosition())
                .putInt(SETTING_PARITY, mParity.getSelectedItemPosition())
                .putBoolean(SETTING_REMOVE_LF, mRemoveLF.isChecked())
                .apply();
    }

    private void updateSettings() {
        SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
        boolean started = isStarted();
        mStartButton.setEnabled(!started);
        mStopButton.setEnabled(started);
        mLocalOnly.setEnabled(!started);
        mWsPort.setEnabled(!started);
        mBaudRate.setEnabled(!started);
        mDataBits.setEnabled(!started);
        mStopBits.setEnabled(!started);
        mParity.setEnabled(!started);
        mRemoveLF.setEnabled(!started);
        mLocalOnly.setChecked(prefs.getBoolean(SETTING_LOCAL_ONLY, false));
        mWsPort.setText(String.valueOf(prefs.getInt(SETTING_WS_PORT, 8080)));
        mBaudRate.setText(String.valueOf(prefs.getInt(SETTING_BAUD_RATE, 115200)));
        mDataBits.setSelection(prefs.getInt(SETTING_DATA_BITS, 3));
        mStopBits.setSelection(prefs.getInt(SETTING_STOP_BITS, 0));
        mParity.setSelection(prefs.getInt(SETTING_PARITY, 0));
        mRemoveLF.setChecked(prefs.getBoolean(SETTING_REMOVE_LF, true));
        mAutostart.setSelection(prefs.getInt(SETTING_AUTOSTART, AUTOSTART_DISABLED));
        if (started)
            mStatus.setText(getString(R.string.started_please_connect) + " ws://" + (prefs.getBoolean(SETTING_LOCAL_ONLY, false) ? "127.0.0.1" : UsbSerialWebsocketService.getIPAddress()) + ":"+ mWsPort.getText());
        else
            mStatus.setText(R.string.not_started);
    }

    public static Intent prepareIntentForWhiteListingOfBatteryOptimization(Context context, String packageName, boolean alsoWhenWhiteListed) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return null;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) == PackageManager.PERMISSION_DENIED)
            return null;
        final WhiteListedInBatteryOptimizations appIsWhiteListedFromPowerSave = getIfAppIsWhiteListedFromBatteryOptimizations(context, packageName);
        Intent intent = null;
        switch (appIsWhiteListedFromPowerSave) {
            case WHITE_LISTED:
                if (alsoWhenWhiteListed)
                    intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                break;
            case NOT_WHITE_LISTED:
                intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:" + packageName));
                break;
            case ERROR_GETTING_STATE:
            case UNKNOWN_TOO_OLD_ANDROID_API_FOR_CHECKING:
            case IRRELEVANT_OLD_ANDROID_API:
            default:
                break;
        }
        return intent;
    }

    public enum WhiteListedInBatteryOptimizations {
        WHITE_LISTED, NOT_WHITE_LISTED, ERROR_GETTING_STATE, UNKNOWN_TOO_OLD_ANDROID_API_FOR_CHECKING, IRRELEVANT_OLD_ANDROID_API
    }

    public static WhiteListedInBatteryOptimizations getIfAppIsWhiteListedFromBatteryOptimizations(Context context, String packageName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return WhiteListedInBatteryOptimizations.UNKNOWN_TOO_OLD_ANDROID_API_FOR_CHECKING;
        final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm == null)
            return WhiteListedInBatteryOptimizations.ERROR_GETTING_STATE;
        return pm.isIgnoringBatteryOptimizations(packageName) ? WhiteListedInBatteryOptimizations.WHITE_LISTED : WhiteListedInBatteryOptimizations.NOT_WHITE_LISTED;
    }
}
