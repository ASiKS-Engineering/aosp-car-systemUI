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
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.TextView;

/**
 * Shows the number of satellites used in the current GNSS fix and the number of visible
 * satellites, formatted as "used/visible". Shows "-" while the GNSS engine is not running.
 *
 * <p>SystemUI itself runs as the system user, for which the location stack never delivers GNSS
 * status. The data is therefore collected by {@link GnssSatelliteCountService}, which is bound
 * as the current foreground user.
 */
public class GnssSatelliteCountView extends TextView {
    private static final String TAG = "GnssSatelliteCountView";
    private static final String NO_STATUS_TEXT = "-";

    private final Messenger mReplyMessenger = new Messenger(new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case GnssSatelliteCountService.MSG_STATUS:
                    setText(getContext().getString(
                            R.string.gnss_satellite_count, msg.arg1, msg.arg2));
                    break;
                case GnssSatelliteCountService.MSG_STOPPED:
                    setText(NO_STATUS_TEXT);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    });

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = new Messenger(service);
            sendToService(GnssSatelliteCountService.MSG_REGISTER);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            setText(NO_STATUS_TEXT);
        }
    };

    private final BroadcastReceiver mUserSwitchReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            bindToCurrentUser();
        }
    };

    private Messenger mService;
    private boolean mBound;

    public GnssSatelliteCountView(Context context) {
        this(context, null);
    }

    public GnssSatelliteCountView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setText(NO_STATUS_TEXT);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getContext().registerReceiverAsUser(mUserSwitchReceiver, UserHandle.ALL,
                new IntentFilter(Intent.ACTION_USER_SWITCHED), null, null);
        bindToCurrentUser();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getContext().unregisterReceiver(mUserSwitchReceiver);
        unbind();
    }

    private void bindToCurrentUser() {
        unbind();
        UserHandle user = UserHandle.of(ActivityManager.getCurrentUser());
        Intent intent = new Intent(getContext(), GnssSatelliteCountService.class);
        try {
            // The location stack only serves foreground apps with the location capability;
            // this raises the service process to the foreground service level and passes on
            // the capabilities of the persistent SystemUI process.
            mBound = getContext().bindServiceAsUser(intent, mConnection,
                    Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE
                            | Context.BIND_INCLUDE_CAPABILITIES, user);
        } catch (SecurityException e) {
            Log.w(TAG, "Cannot bind GNSS status service as " + user, e);
        }
        if (!mBound) {
            Log.w(TAG, "Could not bind GNSS status service as " + user);
        }
    }

    private void unbind() {
        if (mService != null) {
            sendToService(GnssSatelliteCountService.MSG_UNREGISTER);
            mService = null;
        }
        if (mBound) {
            getContext().unbindService(mConnection);
            mBound = false;
        }
        setText(NO_STATUS_TEXT);
    }

    private void sendToService(int what) {
        Message msg = Message.obtain(null, what);
        msg.replyTo = mReplyMessenger;
        try {
            mService.send(msg);
        } catch (RemoteException e) {
            Log.w(TAG, "GNSS status service died", e);
        }
    }
}
