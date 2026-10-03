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

import android.content.Context;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.TextView;

/**
 * Shows the number of satellites used in the current GNSS fix and the number of visible
 * satellites, formatted as "used/visible". Shows "-" while the GNSS engine is not running.
 */
public class GnssSatelliteCountView extends TextView {
    private static final String TAG = "GnssSatelliteCountView";
    private static final String NO_STATUS_TEXT = "-";

    private final LocationManager mLocationManager;
    private boolean mRegistered;

    private final GnssStatus.Callback mGnssStatusCallback = new GnssStatus.Callback() {
        @Override
        public void onStarted() {
            setText(getContext().getString(R.string.gnss_satellite_count, 0, 0));
        }

        @Override
        public void onStopped() {
            setText(NO_STATUS_TEXT);
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
            setText(getContext().getString(R.string.gnss_satellite_count, used, visible));
        }
    };

    public GnssSatelliteCountView(Context context) {
        this(context, null);
    }

    public GnssSatelliteCountView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLocationManager = context.getSystemService(LocationManager.class);
        setText(NO_STATUS_TEXT);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        try {
            mRegistered = mLocationManager.registerGnssStatusCallback(
                    getContext().getMainExecutor(), mGnssStatusCallback);
        } catch (SecurityException e) {
            Log.w(TAG, "Missing ACCESS_FINE_LOCATION, cannot show satellite count", e);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mRegistered) {
            mLocationManager.unregisterGnssStatusCallback(mGnssStatusCallback);
            mRegistered = false;
        }
    }
}
