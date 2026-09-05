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

package com.android.systemui.car.wm;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;

import com.android.systemui.car.shared.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.automotive.AutoCaptionBarViewController;
import com.android.wm.shell.automotive.RootTaskStack;

/**
 * Factory to provide views for caption bar. See
 * {@link com.android.wm.shell.automotive.AutoCaptionController#setCaptionRegion(RootTaskStack,
 * Rect, AutoCaptionBarViewController)}
 * for more details.
 */
@SuppressLint("MissingPermission")
public class AutoCaptionBarViewControllerImpl implements AutoCaptionBarViewController {
    private static final String TAG = AutoCaptionBarViewControllerImpl.class.getSimpleName();

    private final Context mContext;
    private final ShellTaskOrganizer mShellTaskOrganizer;

    public AutoCaptionBarViewControllerImpl(Context context,
            ShellTaskOrganizer shellTaskOrganizer) {
        mContext = context;
        mShellTaskOrganizer = shellTaskOrganizer;
    }

    @NonNull
    @Override
    public View createView(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
        View displayCompatToolbar = LayoutInflater.from(mContext).inflate(
                R.layout.display_compat_toolbar, null);

        Button backButton = displayCompatToolbar.findViewById(R.id.back_button);
        if (backButton != null) {
            backButton.setOnClickListener(view -> sendBackEvent(taskInfo.getDisplayId()));
        }

        Button aspectRatioButton = displayCompatToolbar.findViewById(R.id.aspect_ratio);
        if (aspectRatioButton != null) {
            ComponentName topActivity = taskInfo.topActivity;
            if (topActivity == null) {
                aspectRatioButton.setVisibility(View.GONE);
            } else {
                aspectRatioButton.setOnClickListener(view -> {
                    Intent intent =
                            new Intent(Settings.ACTION_MANAGE_USER_ASPECT_RATIO_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    intent.setData(Uri.parse("package:" + topActivity.getPackageName()));
                    mContext.startActivityAsUser(intent, UserHandle.of(taskInfo.userId));
                });
            }
        }

        Button closeButton = displayCompatToolbar.findViewById(R.id.close_window);
        if (closeButton != null) {
            closeButton.setOnClickListener(view -> {
                WindowContainerTransaction wct = new WindowContainerTransaction();
                wct.removeTask(taskInfo.token);
                mShellTaskOrganizer.applyTransaction(wct);
            });
        }

        return displayCompatToolbar;
    }

    @Override
    public void updateView(@NonNull View captionView,
            @NonNull ActivityManager.RunningTaskInfo taskInfo) {
        // no-op
    }

    private void sendBackEvent(int displayId) {
        final long eventTime = SystemClock.uptimeMillis();
        sendBackEvent(KeyEvent.ACTION_DOWN, displayId, eventTime - 1);
        sendBackEvent(KeyEvent.ACTION_UP, displayId, eventTime);
    }

    private void sendBackEvent(int action, int displayId, long eventTime) {
        final KeyEvent ev = new KeyEvent(eventTime, eventTime, action, KeyEvent.KEYCODE_BACK,
                0 /* repeat */, 0 /* metaState */, KeyCharacterMap.VIRTUAL_KEYBOARD,
                0 /* scancode */, KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                InputDevice.SOURCE_KEYBOARD);

        ev.setDisplayId(displayId);
        if (!mContext.getSystemService(InputManager.class)
                .injectInputEvent(ev, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)) {
            Log.e(TAG, "Inject input event fail");
        }
    }
}
