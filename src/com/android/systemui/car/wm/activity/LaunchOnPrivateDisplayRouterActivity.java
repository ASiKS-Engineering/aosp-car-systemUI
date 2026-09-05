/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.car.wm.activity;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.car.shared.R;

/**
 * Router activity used to launch the intended activity on the desired private display.
 */
public class LaunchOnPrivateDisplayRouterActivity extends Activity {
    private static final String TAG = "LaunchRouterActivity";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private static final String NAMESPACE_KEY = "com.android.car.app.launch_redirect";
    @VisibleForTesting
    static final String LAUNCH_ACTIVITY = NAMESPACE_KEY + ".launch_activity";
    @VisibleForTesting
    static final String LAUNCH_ACTIVITY_OPTIONS =
            NAMESPACE_KEY + ".launch_activity_options";
    @VisibleForTesting
    static final String LAUNCH_ACTIVITY_DISPLAY_ID =
            NAMESPACE_KEY + ".launch_activity_display_id";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.launch_on_private_display_router_activity);

        Intent launchIntent = getIntent();
        if (launchIntent != null && launchIntent.hasExtra(LAUNCH_ACTIVITY)) {
            int launchDisplayId = launchIntent.getExtras().getInt(LAUNCH_ACTIVITY_DISPLAY_ID);
            Intent appIntent = (Intent) launchIntent.getExtras().get(LAUNCH_ACTIVITY);
            Bundle options;
            if (launchIntent.hasExtra(LAUNCH_ACTIVITY_OPTIONS)) {
                options = launchIntent.getBundleExtra(LAUNCH_ACTIVITY_OPTIONS);
            } else {
                options = ActivityOptions.makeBasic().toBundle();
            }
            ActivityOptions activityOptions = ActivityOptions.fromBundle(options);
            activityOptions.setLaunchDisplayId(launchDisplayId);
            appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (DBG) {
                Slog.d(TAG, "launchDisplayId: " + launchDisplayId + ", appIntent: " + appIntent);
            }
            startActivity(appIntent, activityOptions.toBundle());
        } else {
            Slog.e(TAG, "failed to dispatch intent " + getIntent()
                    + " since key for launching on the private display does not exist.");
        }
        finish();
    }
}
