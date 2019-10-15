
package com.reactlibrary;

import android.net.TrafficStats;
import android.os.AsyncTask;
import android.os.Handler;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.Executor;

import okhttp3.Dns;

public class RNReactNativePingModule extends ReactContextBaseJavaModule {
    private final String TIMEOUT_KEY = "timeout";
    private final String KEY_COUNT = "count";
    private final ReactApplicationContext reactContext;
    private Executor mExecutor;

    public RNReactNativePingModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        mExecutor = AsyncTask.THREAD_POOL_EXECUTOR;
    }

    @ReactMethod
    public void startWithHost(final String host, ReadableMap option, final Promise promise) {
        try {
            List<InetAddress> addressList = Dns.SYSTEM.lookup(host);
            if (addressList.size() > 0) {
                String address = addressList.get(0).getHostAddress();
                start(address, option, promise);
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
            LHDefinition.PING_ERROR_CODE error = LHDefinition.PING_ERROR_CODE.HostErrorUnknown;
            promise.reject(error.getCode(), error.getMessage());
        }
    }

    @ReactMethod
    public void start(final String ipAddress, ReadableMap option, final Promise promise) {
        if (ipAddress == null || (ipAddress != null && ipAddress.length() == 0)) {
            LHDefinition.PING_ERROR_CODE error = LHDefinition.PING_ERROR_CODE.HostErrorNotSetHost;
            promise.reject(error.getCode(), error.getMessage());
            return;
        }

        final boolean[] isFinish = {false};
        int timeout = 1000;
        if (option.hasKey(TIMEOUT_KEY)) {
            timeout = option.getInt(TIMEOUT_KEY);
        }
        int count = 1;
        if (option.hasKey(KEY_COUNT)) {
            count = option.getInt(KEY_COUNT);
        }
        final int finalTimeout = timeout;
        final int finalCount = count;
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (isFinish[0]) {
                        return;//Prevent multiple calls
                    }
                    int rtt = PingUtil.getAvgRTT(ipAddress, finalCount, finalTimeout);
                    promise.resolve(rtt);
                    isFinish[0] = true;
                } catch (Exception e) {
                    if (isFinish[0]) {//Prevent multiple calls
                        return;
                    }
                    LHDefinition.PING_ERROR_CODE error =
                            LHDefinition.PING_ERROR_CODE.HostErrorUnknown;
                    promise.reject(error.getCode(), error.getMessage());
                    isFinish[0] = true;
                }
            }
        });
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(finalTimeout * finalCount);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (isFinish[0]) {//Prevent multiple calls
                    return;
                }
                LHDefinition.PING_ERROR_CODE error = LHDefinition.PING_ERROR_CODE.Timeout;
                promise.reject(error.getCode(), error.getMessage());
                isFinish[0] = true;
            }
        });

    }

    @ReactMethod
    public void getTrafficStats(final Promise promise) {
        final long receiveTotal = TrafficStats.getTotalRxBytes();
        final long sendTotal = TrafficStats.getTotalTxBytes();
        final String receivedNetworkTotal = bytesToAvaiUnit(receiveTotal);
        final String sendNetworkTotal = bytesToAvaiUnit(sendTotal);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                long newReceivedTotal = TrafficStats.getTotalRxBytes();
                long newSendTotal = TrafficStats.getTotalTxBytes();

                String receivedNetworkSpeed = bytesToAvaiUnit(newReceivedTotal - receiveTotal) +
                        "/s";
                String sendNetworkSpeed = bytesToAvaiUnit(newSendTotal - sendTotal) + "/s";
                WritableMap map = Arguments.createMap();

                map.putString("receivedNetworkTotal", receivedNetworkTotal);
                map.putString("sendNetworkTotal", sendNetworkTotal);
                map.putString("receivedNetworkSpeed", receivedNetworkSpeed);
                map.putString("sendNetworkSpeed", sendNetworkSpeed);

                promise.resolve(map);
            }
        }, 1000);

    }

    String bytesToAvaiUnit(long bytes) {

        if (bytes < 1024) {   // B
            return bytes + "B";
        } else if (bytes >= 1024 && bytes < 1024 * 1024) { // KB
            return String.format("%.1fKB", bytes / 1024.0);
        } else if (bytes >= 1024 * 1024 && bytes < 1024 * 1024 * 1024) { // MB
            return String.format("%.1fMB", bytes / (1024 * 1024.0));
        } else { // GB
            return String.format("%.1fGB", bytes / (1024 * 1024 * 1024.0));
        }
    }

    @Override
    public String getName() {
        return "RNReactNativePing";
    }
}
