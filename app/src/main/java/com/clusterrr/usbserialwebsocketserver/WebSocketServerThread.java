package com.clusterrr.usbserialwebsocketserver;

import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class WebSocketServerThread extends WebSocketServer {
    private final UsbSerialWebsocketService mUsbSerialWebsocketService;
    private final List<WebSocket> mClients;
    private boolean mRemoveLf = true;

    public WebSocketServerThread(UsbSerialWebsocketService UsbSerialWebsocketService, InetSocketAddress address) {
        super(address);
        mUsbSerialWebsocketService = UsbSerialWebsocketService;
        mClients = new ArrayList<>();
        // 设置连接超时
        setConnectionLostTimeout(60);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        Log.i(UsbSerialWebsocketService.TAG, "WebSocket connected: " + conn.getRemoteSocketAddress());
        mClients.add(conn);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Log.i(UsbSerialWebsocketService.TAG, "WebSocket disconnected: " + conn.getRemoteSocketAddress());
        mClients.remove(conn);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        // 直接处理文本消息，发送到串口
        Log.i(UsbSerialWebsocketService.TAG, "收到文本消息: " + message);
        
        try {
            // 直接发送文本到串口，不进行任何格式转换
            mUsbSerialWebsocketService.writeSerialPort(message.getBytes("UTF-8"));
            Log.i(UsbSerialWebsocketService.TAG, "文本消息已发送到串口");
        } catch (Exception e) {
            Log.e(UsbSerialWebsocketService.TAG, "发送文本消息到串口失败", e);
        }
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        // 忽略二进制消息，只处理文本消息
        Log.i(UsbSerialWebsocketService.TAG, "收到二进制消息，已忽略");
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        Log.e(UsbSerialWebsocketService.TAG, "WebSocket error", ex);
        if (conn != null) {
            mClients.remove(conn);
        }
    }

    @Override
    public void onStart() {
        Log.i(UsbSerialWebsocketService.TAG, "WebSocket server started on " + getAddress());
    }

    public void write(byte[] data) throws IOException {
        write(data, 0, data.length);
    }

    public void write(byte[] data, int offset, int len) throws IOException {
        List<WebSocket> toRemove = new ArrayList<>();
        
        // 将字节数据转换为文本发送
        String text = new String(data, offset, len, "UTF-8");
        
        for (WebSocket client : mClients) {
            try {
                if (client.isOpen()) {
                    // 直接发送文本消息
                    client.send(text);
                    Log.i(UsbSerialWebsocketService.TAG, "发送文本到客户端: " + text);
                } else {
                    toRemove.add(client);
                }
            } catch (Exception ex) {
                Log.e(UsbSerialWebsocketService.TAG, "发送文本到客户端失败", ex);
                toRemove.add(client);
            }
        }
        
        // Remove disconnected clients
        for (WebSocket client : toRemove) {
            mClients.remove(client);
        }
    }

    public void close() {
        try {
            stop();
        } catch (Exception e) {
            Log.e(UsbSerialWebsocketService.TAG, "Error stopping WebSocket server", e);
        }
        
        // Close all client connections
        for (WebSocket client : new ArrayList<>(mClients)) {
            try {
                client.close();
            } catch (Exception e) {
                Log.e(UsbSerialWebsocketService.TAG, "Error closing WebSocket client", e);
            }
        }
        mClients.clear();
    }

    public void setRemoveLf(boolean removeLf) {
        mRemoveLf = removeLf;
    }
}
