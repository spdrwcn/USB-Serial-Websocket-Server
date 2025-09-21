package com.clusterrr.usbserialwebsocketserver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbDevice;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.Ch34xSerialDriver;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;

public class UsbSerialWebsocketService extends Service {
    final static String TAG = "UsbSerialWebSocket";
    final static String ACTION_NEED_TO_START = "need_to_start";
    final static String KEY_LOCAL_ONLY = "local_only";
    final static String KEY_WS_PORT = "ws_port";
    final static String KEY_PORT_ID = "port_id";
    final static String KEY_BAUD_RATE = "baud_rate";
    final static String KEY_DATA_BITS = "data_bits";
    final static String KEY_STOP_BITS = "stop_bits";
    final static String KEY_PARITY = "parity";
    final static String KEY_REMOVE_LF = "remove_lf";
    final static String KEY_LAST_STATE = "last_state";

    boolean mStarted = false;
    //UsbSerialPort mSerialPort = null;
    UsbSerialThread mUsbSerialThread = null;
    WebSocketServerThread mWebSocketServerThread = null;

    int mWsPort = 8080;

    public UsbSerialWebsocketService() {
    }

    public enum UsbDeviceStatus {
        NO_DEVICE,
        NO_PERMISSION,
        OK
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        if (mStarted) {
            // Already started
            //new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(UsbSerialWebsocketService.this.getApplicationContext(), getString(R.string.already_started), Toast.LENGTH_LONG).show());
            return START_STICKY;
        }

        String message = getString(R.string.app_name) + " " + getString(R.string.started);

        Intent mainActivityIntent = new Intent(this, MainActivity.class);
        PendingIntent mainActivityPendingIntent = PendingIntent.getActivity(this, 0, mainActivityIntent, PendingIntent.FLAG_IMMUTABLE);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(TAG,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(getString(R.string.app_name));
            nm.createNotificationChannel(channel);
        }
        Notification notification = new NotificationCompat.Builder(this, TAG)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.mipmap.ic_launcher))
                .setContentTitle(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setShowWhen(false)
                .setContentIntent(mainActivityPendingIntent)
                .setSound(null)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(1, notification);
        }

        boolean success = false;

        try {
            // Find all available drivers from attached devices.
            UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            
            // Create custom ProbeTable for CH340K support
            ProbeTable customTable = new ProbeTable();
            customTable.addProduct(0x1a86, 0x7522, Ch34xSerialDriver.class); // CH340K
            UsbSerialProber prober = new UsbSerialProber(customTable);
            
            // Combine default prober with custom prober
            List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
            availableDrivers.addAll(prober.findAllDrivers(manager));
            
            if (availableDrivers.isEmpty()) {
                message = getString(R.string.device_not_found);
            } else {
                // Open a connection to the first available driver.
                UsbSerialDriver driver = availableDrivers.get(0);
                UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
                if (connection == null) {
                    message = null; // "Please grant permission and try again";
                    Intent mainActivityStartIntent = new Intent(this, MainActivity.class);
                    mainActivityStartIntent.setAction(ACTION_NEED_TO_START);
                    PendingIntent mainActivityStartPendingIntent = PendingIntent.getActivity(this, 0, mainActivityStartIntent, PendingIntent.FLAG_IMMUTABLE);
                    manager.requestPermission(driver.getDevice(), mainActivityStartPendingIntent);
                } else {
                    UsbSerialPort serialPort = null;
                    try {
                        serialPort = driver.getPorts().get(intent.getIntExtra(KEY_PORT_ID, 0));
                    }
                    catch (IndexOutOfBoundsException ex)
                    {
                        message = getString(R.string.invalid_port_id);
                    }
                    if (serialPort != null) {
                        serialPort.open(connection);
                        serialPort.setParameters(
                                intent.getIntExtra(KEY_BAUD_RATE, 115200),
                                intent.getIntExtra(KEY_DATA_BITS, 8),
                                intent.getIntExtra(KEY_STOP_BITS, UsbSerialPort.STOPBITS_1),
                                intent.getIntExtra(KEY_PARITY, UsbSerialPort.PARITY_NONE));
                        InetSocketAddress address = intent.getBooleanExtra(KEY_LOCAL_ONLY, false) ?
                                new InetSocketAddress("127.0.0.1", intent.getIntExtra(KEY_WS_PORT, 8080)) :
                                new InetSocketAddress(intent.getIntExtra(KEY_WS_PORT, 8080));
                        mUsbSerialThread = new UsbSerialThread(this, serialPort);
                        mWebSocketServerThread = new WebSocketServerThread(this, address);
                        mWebSocketServerThread.setRemoveLf(intent.getBooleanExtra(KEY_REMOVE_LF, true));
                        mUsbSerialThread.start();
                        mWebSocketServerThread.start();
                        success = true;
                    }
                }
            }
        }
        catch (Exception ex) {
            message = getString(R.string.error) + " " + ex.getMessage();
            ex.printStackTrace();
        }

        if (message != null) {
            final String msg = message;
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(UsbSerialWebsocketService.this.getApplicationContext(), msg, Toast.LENGTH_SHORT).show());
        }

        if (success) {
            if (message != null)
                Log.i(TAG, message);
            mStarted = true;
            mBinder.started();
        } else {
            if (message != null)
                Log.e(TAG, message);
            stopSelf();
            mStarted = false;
        }

        return START_STICKY;
    }

    public static UsbDeviceStatus getDeviceStatus(Context context) {
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
            return manager.hasPermission(device) ? UsbDeviceStatus.OK :UsbDeviceStatus.NO_PERMISSION;
        }
        return UsbDeviceStatus.NO_DEVICE;
    }

    public static String getIPAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (networkInterface.isUp()) {
                    String name = networkInterface.getName();
                    if (name.toLowerCase().equals("wlan0") || name.toLowerCase().equals("rmnet0")) {
                        List<InterfaceAddress> interfaceAddresses = networkInterface.getInterfaceAddresses();
                        for (InterfaceAddress interfaceAddress : interfaceAddresses) {
                            InetAddress address = interfaceAddress.getAddress();
                            if (address instanceof Inet4Address){
                                return address.getHostAddress();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "localhost";
    }

    @Override
    public void onDestroy()
    {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(1);
        if (mWebSocketServerThread != null) {
            mWebSocketServerThread.close();
            mWebSocketServerThread = null;
        }
        if (mUsbSerialThread != null) {
            mUsbSerialThread.close();
            mUsbSerialThread = null;
        }
        if (mStarted)
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(UsbSerialWebsocketService.this.getApplicationContext(),
                    getString(R.string.app_name) + " " + getString(R.string.stopped), Toast.LENGTH_SHORT).show());
        Log.i(TAG, "Service stopped");
        mStarted = false;
        mBinder.stopped();
    }

    private final ServiceBinder mBinder = new ServiceBinder();
    public class ServiceBinder extends Binder {
        private IOnStartStopListener onStartStopListener = null;
        public boolean isStarted()
        {
            return mStarted;
        }
        public void setOnStartStopListener(IOnStartStopListener listener) { onStartStopListener = listener; }
        public void started() { if (onStartStopListener != null) onStartStopListener.usbSerialServiceStarted(); }
        public void stopped() { if (onStartStopListener != null) onStartStopListener.usbSerialServiceStopped(); }
    }
    public interface IOnStartStopListener
    {
        public void usbSerialServiceStarted();
        public void usbSerialServiceStopped();
    }

    public void writeSerialPort(byte[] buffer) throws IOException {
        if (mUsbSerialThread == null) return;
        mUsbSerialThread.write(buffer);
        if (BuildConfig.DEBUG) {
            Log.d(UsbSerialWebsocketService.TAG, "Written " + buffer.length + " bytes to the port");
        }
    }

    public void writeSerialPort(byte[] buffer, int pos, int len) throws IOException {
        if (mUsbSerialThread == null) return;
        if ((pos != 0) || (buffer.length != len)) {
            byte[] writeBuffer = new byte[len];
            System.arraycopy(buffer, pos, writeBuffer, 0, len);
            mUsbSerialThread.write(writeBuffer);
        } else {
            mUsbSerialThread.write(buffer);
        }
    }

    public void writeClients(byte[] buffer) throws IOException {
        if (mWebSocketServerThread == null) return;
        mWebSocketServerThread.write(buffer);
    }

    public void writeClients(byte[] buffer, int pos, int len) throws IOException {
        if (mWebSocketServerThread == null) return;
        mWebSocketServerThread.write(buffer, pos, len);
    }
}
