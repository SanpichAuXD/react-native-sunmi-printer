package com.reactnativesunmiprinter;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import androidx.core.content.ContextCompat;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class SunmiScanModule extends ReactContextBaseJavaModule {

    private static ReactApplicationContext reactContext;
    private static final int START_SCAN = 0x0000;
    private static final String E_ACTIVITY_DOES_NOT_EXIST = "E_ACTIVITY_DOES_NOT_EXIST";
    private static final String E_FAILED_TO_SHOW_SCAN = "E_FAILED_TO_SHOW_SCAN";
    private static final String ACTION_DATA_CODE_RECEIVED = "com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED";
    private static final String DATA = "data";
    private static final String SOURCE = "source_byte";
    private Promise mPickerPromise;
    private boolean isScanning = false;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_DATA_CODE_RECEIVED.equals(action)) {
                String code = intent.getStringExtra(DATA);
                byte[] arr = intent.getByteArrayExtra(SOURCE);
                if (code != null && !code.isEmpty()) {
                    sendEvent(code);
                }
            }
        }
    };

    private final ActivityEventListener mActivityEventListener = new BaseActivityEventListener() {
        @Override
        public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent intent) {
            if (requestCode != START_SCAN) {
                // This result is not from the scanner, so ignore it.
                return;
            } 
            if (intent != null) {
                Bundle bundle = intent.getExtras();
                ArrayList<HashMap<String, String>> result = (ArrayList<HashMap<String, String>>) bundle.getSerializable("data");
                if (null != result) {
                    Iterator<HashMap<String, String>> it = result.iterator();
                    while (it.hasNext()) {
                        HashMap hashMap = it.next();
                        sendEvent(hashMap.get("VALUE").toString());
                    }
                }
            }
        }
    };

    public SunmiScanModule(ReactApplicationContext context) {
        super(context);
        reactContext = context;
        reactContext.addActivityEventListener(mActivityEventListener);
        registerReceiver();
    }

    @Override
    public String getName() {
        return "SunmiScanModule";
    }

    @ReactMethod
    public void cancelScan() {
        if (isScanning) {
            // Logic to cancel or stop the scan
            isScanning = false;
            sendEvent("Scan cancelled.");
        }
    }

    @ReactMethod
    public void scan(final Promise promise) {
        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            promise.reject(E_ACTIVITY_DOES_NOT_EXIST, "Activity doesn't exist");
            return;
        }

        if (isScanning) {
            promise.reject("E_SCAN_IN_PROGRESS", "Scan is already in progress. Please cancel first.");
            return;
        }

        mPickerPromise = promise;
        try {
            isScanning = true;
            Intent intent = new Intent("com.sunmi.scan");
            intent.setPackage("com.sunmi.sunmiqrcodescanner");
            intent.putExtra("PLAY_SOUND", true);
            currentActivity.startActivityForResult(intent, START_SCAN);
        } catch (Exception e) {
            mPickerPromise.reject("E_FAILED_TO_SHOW_SCAN", e);
            mPickerPromise = null;
        }
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_DATA_CODE_RECEIVED);

        try {
            if (Build.VERSION.SDK_INT >= 33) { // API level 33
                // Use reflection to access Context.RECEIVER_EXPORTED
                int receiverExportedFlag = Context.class.getField("RECEIVER_EXPORTED").getInt(null);
                reactContext.registerReceiver(receiver, filter, receiverExportedFlag);
            } else {
                reactContext.registerReceiver(receiver, filter);
            }
        } catch (Exception e) {
            // Handle reflection failure gracefully
            e.printStackTrace();
            reactContext.registerReceiver(receiver, filter);
        }

    }

    private static void sendEvent(String msg) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("onScanSuccess", msg);
    }
}
