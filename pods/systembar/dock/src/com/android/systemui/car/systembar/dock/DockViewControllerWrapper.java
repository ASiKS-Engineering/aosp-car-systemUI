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

package com.android.systemui.car.systembar.dock;

import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;
import static android.view.Display.INVALID_DISPLAY;

import static com.android.car.dockutil.events.DockCompatUtils.isDockSupportedOnDisplay;

import android.car.Car;
import android.car.user.CarUserManager;
import android.content.Context;
import android.os.Build;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.car.docklib.DockViewController;
import com.android.car.docklib.data.DockProtoDataController;
import com.android.car.docklib.view.DockView;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.flexibleui.CarSystemBarElementController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.flexibleui.layout.CarSystemBarFrameLayout;
import com.android.systemui.car.shared.R;
import com.android.systemui.settings.UserFileManager;
import com.android.systemui.settings.UserTracker;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.io.File;

/** Wrapper controller class for instantiating {@link DockViewController} inside CarSystemUI */
public class DockViewControllerWrapper extends
        CarSystemBarElementController<CarSystemBarFrameLayout> {
    private static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;

    private static final String TAG = DockViewControllerWrapper.class.getSimpleName();

    private final Context mContext;
    private final UserManager mUserManager;
    private final UserTracker mUserTracker;
    private final UserFileManager mUserFileManager;
    private final CarServiceProvider mCarServiceProvider;

    private DockViewController mDockViewController;
    private CarUserManager mCarUserManager;
    private int mActiveUnlockedUserId = -1;

    private final CarUserManager.UserLifecycleListener mUserLifecycleListener =
            new CarUserManager.UserLifecycleListener() {
                @Override
                public void onEvent(@NonNull CarUserManager.UserLifecycleEvent event) {
                    if (event.getUserHandle().isSystem()) {
                        return;
                    }

                    switch (event.getEventType()) {
                        case USER_LIFECYCLE_EVENT_TYPE_UNLOCKED -> {
                            if (event.getUserId() == mUserTracker.getUserId()) {
                                setupDock();
                            }
                        }
                        case USER_LIFECYCLE_EVENT_TYPE_SWITCHING -> {
                            if (event.getPreviousUserId() == mActiveUnlockedUserId) {
                                destroyDock();
                            }
                        }
                    }
                }
            };

    private final CarServiceProvider.CarServiceOnConnectedListener mCarOnConnectedListener =
            new CarServiceProvider.CarServiceOnConnectedListener() {
                @Override
                public void onConnected(Car car) {
                    mCarUserManager = car.getCarManager(CarUserManager.class);
                    if (mCarUserManager != null) {
                        mCarUserManager.addListener(mContext.getMainExecutor(),
                                mUserLifecycleListener);
                    }
                }
            };

    @AssistedInject
    DockViewControllerWrapper(@Assisted CarSystemBarFrameLayout view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController, Context context,
            UserTracker userTracker, UserFileManager userFileManager,
            CarServiceProvider carServiceProvider) {
        super(view, disableController, stateController);
        mContext = context;
        mUserManager = context.getSystemService(UserManager.class);
        mUserTracker = userTracker;
        mUserFileManager = userFileManager;
        mCarServiceProvider = carServiceProvider;
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<CarSystemBarFrameLayout,
                    DockViewControllerWrapper> {
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        setupDock();
        mCarServiceProvider.addListener(mCarOnConnectedListener);
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        mCarServiceProvider.removeListener(mCarOnConnectedListener);
        if (mCarUserManager != null) {
            mCarUserManager.removeListener(mUserLifecycleListener);
            mCarUserManager = null;
        }
        destroyDock();
    }

    protected DockViewController createDockViewController(DockView dockView, Context userContext,
            File dataFile) {
        return new DockViewController(dockView, userContext, dataFile);
    }

    private void setupDock() {
        if (mDockViewController != null) {
            if (mDockViewController.getUserContext().getUserId() == mUserTracker.getUserId()) {
                if (DEBUG) {
                    Log.d(TAG, "Dock already initialized for user: " + mUserTracker.getUserId());
                }
                return;
            }
            if (DEBUG) {
                // This is unexpected. We should not have a leaked instance of
                // DockViewController for a different user. This indicates a potential
                // issue with how we're managing the DockViewController lifecycle.
                Log.w(TAG, "DockViewController: Leaked instance detected for user "
                        + mDockViewController.getUserContext().getUserId() + ". Destroying now.");
            }
            mDockViewController.destroy();
        }
        int currentDisplayId = mView.getDisplay() != null ? mView.getDisplay().getDisplayId()
                : INVALID_DISPLAY;
        if (!isDockSupportedOnDisplay(mContext, currentDisplayId)) {
            Log.e(TAG, "Dock cannot be initialised: Tried to launch on unsupported display "
                    + currentDisplayId);
            return;
        }
        DockView dockView = mView.findViewById(R.id.dock);
        if (dockView == null) {
            if (DEBUG) {
                Log.d(TAG, "Dock cannot be initialised: Cannot find dock view");
            }
            return;
        }
        if (!mUserManager.isUserUnlocked(mUserTracker.getUserId())) {
            if (DEBUG) {
                Log.d(TAG, "Dock cannot be initialised: User not unlocked");
            }
            return;
        }
        mDockViewController = createDockViewController(
                dockView,
                mUserTracker.getUserContext(),
                mUserFileManager.getFile(
                        DockProtoDataController.FILE_NAME,
                        mUserTracker.getUserId()
                )
        );
        mActiveUnlockedUserId = mUserTracker.getUserId();
    }

    private void destroyDock() {
        if (mDockViewController != null) {
            mDockViewController.destroy();
            mDockViewController = null;
        }
    }
}
