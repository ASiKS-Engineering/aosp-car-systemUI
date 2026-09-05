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

package com.android.systemui.car.keyguard.passenger;

import static android.car.CarOccupantZoneManager.DISPLAY_TYPE_MAIN;
import static android.car.CarOccupantZoneManager.INVALID_USER_ID;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_VISIBLE;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG;

import android.app.ActivityManager;
import android.app.Presentation;
import android.car.CarOccupantZoneManager;
import android.car.feature.Flags;
import android.car.user.CarUserManager;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.view.Display;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.android.car.oem.tokens.Token;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.widget.LockPatternUtils;
import com.android.systemui.CoreStartable;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.shared.R;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.inject.Inject;

/**
 * Loading presentation to be shown while the passenger keyguard is initializing.
 */
public class PassengerKeyguardLoadingDialog implements CoreStartable {
    private static final String TAG = PassengerKeyguardLoadingDialog.class.getName();
    private static final boolean DEBUG = false;
    private final Context mContext;
    private final CarServiceProvider mCarServiceProvider;
    private final Executor mBackgroundExecutor;
    private final Handler mMainHandler;
    private final Handler mBackgroundHandler;
    private final LockPatternUtils mLockPatternUtils;
    private final UserManager mUserManager;
    private final DisplayManager mDisplayManager;
    /** UserId -> Presentation mapping*/
    @VisibleForTesting
    final Map<Integer, LoadingPresentation> mPresentations = new HashMap<>();

    private boolean mStarted = false;
    private CarUserManager mCarUserManager;
    private CarOccupantZoneManager mCarOccupantZoneManager;

    private final CarUserManager.UserLifecycleListener mUserLifecycleListener =
            new CarUserManager.UserLifecycleListener() {
                @Override
                public void onEvent(@NonNull CarUserManager.UserLifecycleEvent event) {
                    if (event.getUserId() == ActivityManager.getCurrentUser()
                            || event.getUserHandle().isSystem()) {
                        // don't show for foreground or system user
                        return;
                    }

                    if (event.getEventType() == USER_LIFECYCLE_EVENT_TYPE_STARTING
                            || event.getEventType() == USER_LIFECYCLE_EVENT_TYPE_VISIBLE) {
                        handleUserStarting(event.getUserHandle());
                    } else if (event.getEventType() == USER_LIFECYCLE_EVENT_TYPE_UNLOCKED
                            || event.getEventType() == USER_LIFECYCLE_EVENT_TYPE_STOPPED) {
                        mMainHandler.post(() -> hideDialog(event.getUserId()));
                    }
                }
            };

    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                    // no-op
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                    if (mCarOccupantZoneManager == null) {
                        return;
                    }
                    int userId = mCarOccupantZoneManager.getUserForDisplayId(displayId);
                    if (userId != INVALID_USER_ID) {
                        mMainHandler.post(() -> hideDialog(userId));
                    }
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    // no-op
                }
            };

    @Inject
    public PassengerKeyguardLoadingDialog(Context context, CarServiceProvider carServiceProvider,
            @Background Executor bgExecutor, @Main Handler mainHandler,
            @Background Handler bgHandler, LockPatternUtils lockPatternUtils) {
        mContext = context;
        mCarServiceProvider = carServiceProvider;
        mBackgroundExecutor = bgExecutor;
        mMainHandler = mainHandler;
        mBackgroundHandler = bgHandler;
        mLockPatternUtils = lockPatternUtils;
        mUserManager = mContext.getSystemService(UserManager.class);
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
    }

    @Override
    public void start() {
        if (!Flags.supportsSecurePassengerUsers()) {
            return;
        }

        if (!CarSystemUIUserUtil.isDriverMUMDSystemUI()) {
            // only start for user 0 SysUI on MUMD system
            return;
        }

        mCarServiceProvider.addListener(car -> {
            mCarUserManager = car.getCarManager(CarUserManager.class);
            mCarOccupantZoneManager = car.getCarManager(CarOccupantZoneManager.class);

            if (mCarUserManager != null && mCarOccupantZoneManager != null) {
                mCarUserManager.addListener(mBackgroundExecutor, mUserLifecycleListener);

                if (mStarted) {
                    return;
                }

                // In the case of a SystemUI restart, re-show dialogs for any user that is not
                // unlocked.
                mUserManager.getVisibleUsers().forEach(userHandle -> {
                    if (userHandle.isSystem()
                            || userHandle.getIdentifier() == ActivityManager.getCurrentUser()) {
                        return;
                    }
                    handleUserStarting(userHandle);
                });
                mStarted = true;
            }
        });
        mDisplayManager.registerDisplayListener(mDisplayListener, mBackgroundHandler);
    }

    private void handleUserStarting(UserHandle userHandle) {
        if (mCarOccupantZoneManager == null) {
            Log.w(TAG, "CarOccupantZoneManager is unexpectedly null");
            return;
        }

        int userId = userHandle.getIdentifier();
        if (!mLockPatternUtils.isSecure(userId) || mUserManager.isUserUnlocked(userId)) {
            return;
        }

        int driverDisplayId = mCarOccupantZoneManager.getDisplayIdForDriver(DISPLAY_TYPE_MAIN);
        CarOccupantZoneManager.OccupantZoneInfo zoneInfo =
                mCarOccupantZoneManager.getOccupantZoneForUser(userHandle);
        if (zoneInfo == null) {
            Log.w(TAG, "unable to get zone info for user=" + userHandle.getIdentifier());
            return;
        }
        Display displayForUser = mCarOccupantZoneManager.getDisplayForOccupant(zoneInfo,
                DISPLAY_TYPE_MAIN);
        if (displayForUser == null || displayForUser.getDisplayId() == driverDisplayId) {
            return;
        }

        mMainHandler.post(() -> showDialog(displayForUser, userId));
    }

    @MainThread
    private void showDialog(Display display, int userId) {
        if (mPresentations.containsKey(userId)) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "showing presentation on display=" + display + " for user=" + userId);
        }
        LoadingPresentation presentation = createLoadingPresentation(display);
        mPresentations.put(userId, presentation);
        presentation.show();
    }

    @MainThread
    private void hideDialog(int userId) {
        if (!mPresentations.containsKey(userId)) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "removing presentation for user " + userId);
        }
        LoadingPresentation presentation = mPresentations.remove(userId);
        if (presentation != null) {
            presentation.dismiss();
        }
    }

    @VisibleForTesting
    LoadingPresentation createLoadingPresentation(Display display) {
        return new LoadingPresentation(mContext, display);
    }

    @VisibleForTesting
    static class LoadingPresentation extends Presentation {
        LoadingPresentation(Context outerContext, Display display) {
            super(outerContext, display, /* theme= */ 0, TYPE_SYSTEM_DIALOG);
            Token.applyOemTokenStyle(getContext());
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            setContentView(R.layout.passenger_keyguard_loading_dialog);
        }
    }
}
