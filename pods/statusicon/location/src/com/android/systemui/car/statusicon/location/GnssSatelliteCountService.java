/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.car.statusicon.location;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Intent;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects GNSS status of the foreground user and forwards it to {@link GnssSatelliteCountView}.
 *
 * <p>The main SystemUI process runs as the (headless) system user. The location stack only
 * delivers GNSS status to listeners of the current, location-enabled user, so SystemUI binds
 * this service as the current user (it runs in a separate process) and receives the data via
 * a {@link Messenger}.
 */
public class GnssSatelliteCountService extends Service {
    private static final String TAG = "GnssSatelliteCountService";

    static final int MSG_REGISTER = 1;
    static final int MSG_UNREGISTER = 2;
    /** arg1 = satellites used in fix, arg2 = visible satellites. */
    static final int MSG_STATUS = 3;
    static final int MSG_STOPPED = 4;

    private static final long FOREGROUND_POLL_INTERVAL_MS = 500;
    private final List<Messenger> mClients = new ArrayList<>();
    private LocationManager mLocationManager;
    private boolean mCallbackRegistered;
    private boolean mHasStatus;
    private int mUsed;
    private int mVisible;

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REGISTER:
                    if (msg.replyTo != null) {
                        mClients.add(msg.replyTo);
                        if (mHasStatus) {
                            send(msg.replyTo, MSG_STATUS, mUsed, mVisible);
                        }
                    }
                    break;
                case MSG_UNREGISTER:
                    mClients.remove(msg.replyTo);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    };

    private final Messenger mMessenger = new Messenger(mHandler);

    private final GnssStatus.Callback mGnssStatusCallback = new GnssStatus.Callback() {
        @Override
        public void onStarted() {
            publishStatus(0, 0);
        }

        @Override
        public void onStopped() {
            mHasStatus = false;
            broadcast(MSG_STOPPED, 0, 0);
        }

        @Override
        public void onSatelliteStatusChanged(GnssStatus status) {
            int visible = status.getSatelliteCount();
            int used = 0;
            for (int i = 0; i < visible; i++) {
                if (status.usedInFix(i)) {
                    used++;
                }
            }
            publishStatus(used, visible);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mLocationManager = getSystemService(LocationManager.class);
        registerWhenForeground();
    }

    // The location stack evaluates the location permission app op once at registration time and
    // only for foreground uids, so wait until the bind has raised this process' importance.
    private void registerWhenForeground() {
        ActivityManager.RunningAppProcessInfo info = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(info);
        if (info.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE) {
            mHandler.postDelayed(this::registerWhenForeground, FOREGROUND_POLL_INTERVAL_MS);
            return;
        }
        try {
            mCallbackRegistered = mLocationManager.registerGnssStatusCallback(
                    getMainExecutor(), mGnssStatusCallback);
        } catch (SecurityException e) {
            Log.w(TAG, "Missing ACCESS_FINE_LOCATION, cannot show satellite count", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        if (mCallbackRegistered) {
            mLocationManager.unregisterGnssStatusCallback(mGnssStatusCallback);
            mCallbackRegistered = false;
        }
        mClients.clear();
        super.onDestroy();
    }

    private void publishStatus(int used, int visible) {
        mHasStatus = true;
        mUsed = used;
        mVisible = visible;
        broadcast(MSG_STATUS, used, visible);
    }

    private void broadcast(int what, int arg1, int arg2) {
        for (int i = mClients.size() - 1; i >= 0; i--) {
            if (!send(mClients.get(i), what, arg1, arg2)) {
                mClients.remove(i);
            }
        }
    }

    private static boolean send(Messenger client, int what, int arg1, int arg2) {
        try {
            client.send(Message.obtain(null, what, arg1, arg2));
            return true;
        } catch (RemoteException e) {
            return false;
        }
    }
}
