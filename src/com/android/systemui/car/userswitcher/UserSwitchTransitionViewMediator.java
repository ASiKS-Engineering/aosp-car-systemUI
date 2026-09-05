/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.car.userswitcher;

import static com.android.systemui.car.Flags.userSwitchKeyguardShownTimeout;

import android.app.KeyguardManager;
import android.car.user.CarUserManager;
import android.content.Context;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.view.IWindowManager;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.car.window.OverlayViewMediator;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.concurrency.DelayableExecutor;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

/**
 * Registers listeners that subscribe to events that show or hide CarUserSwitchingDialog that is
 * mounted to SystemUiOverlayWindow.
 */
public class UserSwitchTransitionViewMediator implements OverlayViewMediator,
        CarUserManager.UserHandleSwitchUiCallback {
    private static final String TAG = "UserSwitchTransitionVM";
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    // Amount of time to wait for keyguard to show before restarting SysUI (in seconds)
    private static final int KEYGUARD_SHOW_TIMEOUT = 20;

    private final DelayableExecutor mMainExecutor;
    private final CarServiceProvider mCarServiceProvider;
    private final UserTracker mUserTracker;
    private final UserManager mUserManager;
    private final IWindowManager mWindowManagerService;
    private final KeyguardManager mKeyguardManager;
    private final KeyguardStateController mKeyguardStateController;
    private final UserSwitchTransitionViewController mUserSwitchTransitionViewController;

    // Lock for keyguard-related operations to prevent possible timing issues
    private final Object mKeyguardLock = new Object();
    // Represents the actual current keyguard showing state
    @GuardedBy("mKeyguardLock")
    private boolean mIsKeyguardShowing;
    // Bit to represent the user switch has attempted to trigger keyguard and is waiting for it
    // to show up.
    @GuardedBy("mKeyguardLock")
    private boolean mPendingKeyguardShow;
    @GuardedBy("mKeyguardLock")
    private Runnable mCancelKeyguardTimeout;

    @VisibleForTesting
    final UserTracker.Callback mUserChangedCallback = new UserTracker.Callback() {
        @Override
        public void onBeforeUserSwitching(int newUser) {
            mUserSwitchTransitionViewController.showSwitchingUI(newUser);
            try {
                mWindowManagerService.setSwitchingUser(true);
            } catch (RemoteException e) {
                Log.e(TAG, "unable to notify window manager service regarding user switch");
            }

            if (mKeyguardManager.isDeviceSecure(newUser)) {
                // Setup keyguard timeout but don't lock the device just yet.
                // The device cannot be locked until we receive a user switching event - otherwise
                // the KeyguardViewMediator will not have the new userId.
                setupKeyguardShownTimeout();
            }
        }

        @Override
        public void onUserChanging(int newUser, @NonNull Context userContext) {
            if (!mKeyguardManager.isDeviceSecure(newUser)) {
                return;
            }
            try {
                if (DEBUG) {
                    Log.d(TAG, "Notifying WM to lock device");
                }
                mWindowManagerService.lockNow(null);
            } catch (RemoteException e) {
                throw new RuntimeException("Error notifying WM of lock state", e);
            }
        }

        @Override
        public void onUserChanged(int newUser, @NonNull Context userContext) {
            hideSwitchingUI();
        }
    };

    @Inject
    public UserSwitchTransitionViewMediator(
            Context context,
            @Main DelayableExecutor delayableExecutor,
            CarServiceProvider carServiceProvider,
            UserTracker userTracker,
            UserManager userManager,
            IWindowManager windowManagerService,
            KeyguardStateController keyguardStateController,
            UserSwitchTransitionViewController userSwitchTransitionViewController) {
        mMainExecutor = delayableExecutor;
        mCarServiceProvider = carServiceProvider;
        mUserTracker = userTracker;
        mUserManager = userManager;
        mWindowManagerService = windowManagerService;
        mKeyguardManager = context.getSystemService(KeyguardManager.class);
        mKeyguardStateController = keyguardStateController;
        mUserSwitchTransitionViewController = userSwitchTransitionViewController;
    }

    @Override
    public void registerListeners() {
        mCarServiceProvider.addListener(car -> {
            CarUserManager carUserManager = car.getCarManager(CarUserManager.class);

            if (carUserManager != null) {
                if (!CarSystemUIUserUtil.isSecondaryMUMDSystemUI()) {
                    // TODO(b/335664913): allow for callback from non-system user (and per user).
                    carUserManager.setUserSwitchUiCallback(mMainExecutor, this);
                }

                carUserManager.addListener(mMainExecutor,
                        this::handleUserLifecycleEvent);
                if (mUserManager.isUserUnlocked(mUserTracker.getUserId())) {
                    hideSwitchingUI();
                }

            } else {
                Log.e(TAG, "registerListeners: CarUserManager could not be obtained.");
            }
        });

        mUserTracker.addCallback(mUserChangedCallback, mMainExecutor);
        if (mUserTracker.isUserSwitching()
                || !mUserManager.isUserUnlocked(mUserTracker.getUserId())
                && !mKeyguardManager.isDeviceSecure(mUserTracker.getUserId())) {
            mUserSwitchTransitionViewController.showSwitchingUI(mUserTracker.getUserId());
        }

        synchronized (mKeyguardLock) {
            mKeyguardStateController.addCallback(new KeyguardStateController.Callback() {
                @Override
                public void onKeyguardShowingChanged() {
                    keyguardShowingChanged(mKeyguardStateController.isShowing());
                }
            });
            mIsKeyguardShowing = mKeyguardStateController.isShowing();
            if (mIsKeyguardShowing) {
                hideSwitchingUI();
            } else if (mKeyguardManager.isDeviceLocked(mUserTracker.getUserId())) {
                mUserSwitchTransitionViewController.showSwitchingUI(mUserTracker.getUserId());
            }
        }
    }

    @Override
    public void setUpOverlayContentViewControllers() {
        // no-op.
    }

    @Override
    public void onUserSwitchStart(@NonNull UserHandle userHandle) {
        mUserSwitchTransitionViewController.showSwitchingUI(userHandle.getIdentifier());
    }

    void handleUserLifecycleEvent(CarUserManager.UserLifecycleEvent event) {
        if (event.getUserId() != mUserTracker.getUserId()) {
            return;
        }

        if (event.getEventType() == CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED) {
            hideSwitchingUI();
        }
    }

    private boolean shouldHideSwitchingUI() {
        synchronized (mKeyguardLock) {
            if (mIsKeyguardShowing) {
                return true;
            }
            if (mPendingKeyguardShow) {
                return false;
            }
        }

        if (mKeyguardManager.isDeviceLocked(mUserTracker.getUserId())) {
            // keyguard is not showing but device is locked - should not hide UI
            return false;
        }
        return mUserManager.isUserUnlocked(mUserTracker.getUserId());
    }

    private void hideSwitchingUI() {
        if (!shouldHideSwitchingUI()) {
            return;
        }
        mUserSwitchTransitionViewController.hideSwitchingUI();
    }

    private void keyguardShowingChanged(boolean showing) {
        synchronized (mKeyguardLock) {
            if (mIsKeyguardShowing == showing) {
                return;
            }
            mIsKeyguardShowing = showing;
            if (DEBUG) {
                Log.d(TAG, "Keyguard state change keyguardShowing=" + mIsKeyguardShowing);
            }

            if (mPendingKeyguardShow && mIsKeyguardShowing) {
                mPendingKeyguardShow = false;
                mUserSwitchTransitionViewController.setShouldSkipTimeout(false);
                if (mCancelKeyguardTimeout != null) {
                    mCancelKeyguardTimeout.run();
                    mCancelKeyguardTimeout = null;
                }
                hideSwitchingUI();
            } else if (mIsKeyguardShowing
                    && mKeyguardManager.isDeviceSecure(mUserTracker.getUserId())) {
                hideSwitchingUI();
            }
        }
    }

    /**
     * Wait for keyguard to be shown before hiding this blocking view.
     * This method does the following (in-order):
     * - Checks if the keyguard is already locked (and if so, do nothing else).
     * - Register a KeyguardLockedStateListener to be notified when the keyguard is locked.
     * - Start a 20 second timeout for keyguard to be shown. If it is not shown within this
     *   timeframe, SysUI/WM is in a bad state - crash SysUI and allow it to recover on restart.
     */
    @VisibleForTesting
    void setupKeyguardShownTimeout() {
        if (!userSwitchKeyguardShownTimeout()) {
            return;
        }
        synchronized (mKeyguardLock) {
            if (mPendingKeyguardShow) {
                Log.w(TAG, "Attempted to setup timeout while pending keyguard show");
                return;
            }
            if (mIsKeyguardShowing) {
                return;
            }

            if (DEBUG) {
                Log.d(TAG, "Setting up keyguard show timeout");
            }
            mPendingKeyguardShow = true;
            mUserSwitchTransitionViewController.setShouldSkipTimeout(true);
            Runnable keyguardTimeoutRunnable = () -> {
                // Keyguard did not show up in the expected timeframe - this indicates something is
                // very wrong. Crash SystemUI and allow it to recover on re-initialization.
                throw new RuntimeException(String.format("Keyguard was not shown in %d seconds",
                        KEYGUARD_SHOW_TIMEOUT));
            };
            mCancelKeyguardTimeout = mMainExecutor.executeDelayed(keyguardTimeoutRunnable,
                    KEYGUARD_SHOW_TIMEOUT, TimeUnit.SECONDS);
        }
    }
}
