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

package com.android.systemui.car.notification;

import static com.android.systemui.car.wm.scalableui.systemwindow.HunWindow.WINDOW_TITLE;

import android.content.Context;
import android.graphics.PixelFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.android.car.notification.R;
import com.android.car.notification.headsup.CarHeadsUpNotificationContainer;
import com.android.car.notification.headsup.animationhelper.CarHeadsUpNotificationBottomAnimationHelper;
import com.android.car.notification.headsup.animationhelper.CarHeadsUpNotificationTopAnimationHelper;
import com.android.car.notification.headsup.animationhelper.HeadsUpNotificationAnimationHelper;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.window.OverlayViewGlobalStateController;
import com.android.systemui.car.wm.scalableui.systemwindow.HunWindow;
import com.android.systemui.car.wm.scalableui.systemwindow.SystemUiWindowProvider;
import com.android.systemui.dagger.SysUISingleton;

import java.util.Optional;

import javax.inject.Inject;

/**
 * A controller for SysUI's HUN display.
 *
 * Used to attach HUNs views to window and determine whether to show HUN panel.
 */
@SysUISingleton
public class CarHeadsUpNotificationSystemContainer extends CarHeadsUpNotificationContainer {
    private static final String TAG = "CarHeadsUpNotificationSystemContainer";
    private final CarDeviceProvisionedController mCarDeviceProvisionedController;
    private final OverlayViewGlobalStateController mOverlayViewGlobalStateController;
    private final Optional<HunWindow> mHunWindow;

    @Inject
    CarHeadsUpNotificationSystemContainer(Context context,
            CarDeviceProvisionedController deviceProvisionedController,
            OverlayViewGlobalStateController overlayViewGlobalStateController,
            SystemUiWindowProvider systemUiWindowProvider) {
        super(context);
        mCarDeviceProvisionedController = deviceProvisionedController;
        mOverlayViewGlobalStateController = overlayViewGlobalStateController;
        mHunWindow = systemUiWindowProvider.getHunWindow();
        inflateLayout(context);
        initializeVisibility();
        attachToWindow();
    }

    /**
     * Returns the animation helper for the HUN container.
     *
     * <p>If a {@link HunWindow} is present, this method determines the animation helper based on
     * the window's gravity. Otherwise, it defaults to the behavior defined in the superclass.
     * This allows the Scalable UI framework to control the HUN's animation while maintaining
     * compatibility with non-scalable environments.
     */
    @Override
    public HeadsUpNotificationAnimationHelper getAnimationHelper() {
        if (mHunWindow.isPresent()) {
            return shouldShowHunOnBottom() ? new CarHeadsUpNotificationBottomAnimationHelper()
                    : new CarHeadsUpNotificationTopAnimationHelper();
        }
        return super.getAnimationHelper();
    }
    /**
     * Inflates the layout for the HUN container.
     *
     * <p>If a {@link HunWindow} is present, this method determines the layout based on the
     * window's gravity. Otherwise, it defaults to the behavior defined in the superclass.
     * This allows the Scalable UI framework to control the HUN's position while maintaining
     * compatibility with non-scalable environments.
     */
    @Override
    protected void inflateLayout(Context context) {
        if (!mHunWindow.isPresent()) {
            super.inflateLayout(context);
            return;
        }
        WindowManager.LayoutParams lp = mHunWindow.get().getLayoutParams();
        if (lp == null) {
            Log.e(TAG, "HUN window layout params are null, can't attach to window");
            // fallback to the super class's implementation which will inflate the layout
            // based on the config value.
            super.inflateLayout(context);
            return;
        }
        mShowHunOnBottom = lp.gravity == Gravity.BOTTOM;
        mHunRootView = (ViewGroup) LayoutInflater.from(context).inflate(
                mShowHunOnBottom ? R.layout.headsup_container_bottom
                        : R.layout.headsup_container, /* root= */ null,
                /* attachToRoot= */ false);
        mHunContent = mHunRootView.findViewById(R.id.headsup_content);
    }

    @Override
    protected void initializeVisibility() {
        // If the HunWindow is present, the Scalable UI framework is responsible for the initial
        // visibility state, so we do nothing. Otherwise, we fall back to the default behavior.
        if (!mHunWindow.isPresent()) {
            super.initializeVisibility();
        }
    }

    @Override
    protected void presentContainer() {
        if (mHunWindow.isPresent()) {
            mHunWindow.get().show();
        } else {
            super.presentContainer();
        }
    }

    @Override
    protected void dismissContainer() {
        if (mHunWindow.isPresent()) {
            mHunWindow.get().hide();
        } else {
            super.dismissContainer();
        }
    }

     /**
     * Attaches the Heads-Up Notification (HUN) container view to the window.
     *
     * <p>If a dedicated {@link HunWindow} is available, the HUN container's root view is set on
     * that window. Otherwise, it falls back to adding the view directly to the
     * {@link WindowManager} using the default layout parameters.
     */
    private void attachToWindow() {
        if (mHunWindow.isPresent()) {
            WindowManager.LayoutParams lp = mHunWindow.get().getLayoutParams();
            if (lp == null) {
                Log.e(TAG, "HUN window layout params are null, can't attach to window");
                return;
            }
            mHunWindow.get().setRootView(getHunRootView(), lp);
            return;
        }
        WindowManager wm = getContext().getSystemService(WindowManager.class);
        wm.addView(getHunRootView(), getWindowManagerLayoutParams());
    }

    /**
     * @return {@link WindowManager.LayoutParams} to be used when adding HUN Window to {@link
     * WindowManager}.
     */
    private WindowManager.LayoutParams getWindowManagerLayoutParams() {
        if (mHunWindow.isPresent()) {
            return mHunWindow.get().getLayoutParams();
        }
        // Use TYPE_STATUS_BAR_SUB_PANEL window type since we need to find a window that is
        // above status bar but below navigation bar.
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        // Needed for passing through touches through HUN's scrim to application content
        // underneath
        lp.privateFlags = WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY;

        lp.gravity = shouldShowHunOnBottom() ? Gravity.BOTTOM : Gravity.TOP;
        lp.setTitle(WINDOW_TITLE);

        return lp;
    }

    @Override
    public boolean shouldShowHunPanel() {
        return mCarDeviceProvisionedController.isCurrentUserFullySetup()
                && mOverlayViewGlobalStateController.shouldShowHUN();
    }

}
