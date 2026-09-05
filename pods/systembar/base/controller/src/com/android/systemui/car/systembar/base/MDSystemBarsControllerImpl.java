/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.car.systembar.base;

import android.car.feature.Flags;
import android.content.Context;
import android.content.om.OverlayManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.UserHandle;
import android.util.Log;
import android.view.WindowManager;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.shared.R;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.car.wm.scalableui.systemwindow.SystemUiWindowProvider;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.settings.DisplayTracker;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.phone.AutoHideController;
import com.android.systemui.statusbar.phone.LightBarController;
import com.android.systemui.statusbar.phone.PhoneStatusBarPolicy;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.concurrency.DelayableExecutor;

import dagger.Lazy;

/**
 * Currently because of Bug:b/260206944, RROs are not applied to the secondary user.
 * This class acts as a Mediator, which toggles the Overlay state of the RRO package,
 * which in turn triggers onConfigurationChange. Only after this change start the
 * CarSystemBar with overlaid resources.
 */
public class MDSystemBarsControllerImpl extends CarSystemBarControllerImpl {

    private static final String TAG = MDSystemBarsControllerImpl.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;
    private final Context mContext;
    private final OverlayManager mOverlayManager;

    private boolean mInitialized = false;

    public MDSystemBarsControllerImpl(
            @Main Handler mainHandler,
            Context context,
            UserTracker userTracker,
            CarSystemBarViewFactory carSystemBarViewFactory,
            SystemBarConfigs systemBarConfigs,
            // TODO(b/156052638): Should not need to inject LightBarController
            LightBarController lightBarController,
            DarkIconDispatcher darkIconDispatcher,
            WindowManager windowManager,
            CarDeviceProvisionedController deviceProvisionedController,
            CommandQueue commandQueue,
            AutoHideController autoHideController,
            ButtonSelectionStateListener buttonSelectionStateListener,
            @Main DelayableExecutor mainExecutor,
            IStatusBarService barService,
            Lazy<KeyguardStateController> keyguardStateControllerLazy,
            Lazy<PhoneStatusBarPolicy> iconPolicyLazy,
            ConfigurationController configurationController,
            CarSystemBarRestartTracker restartTracker,
            DisplayTracker displayTracker,
            SystemUiWindowProvider windowProvider) {
        super(context,
                userTracker,
                carSystemBarViewFactory,
                systemBarConfigs,
                lightBarController,
                darkIconDispatcher,
                windowManager,
                deviceProvisionedController,
                commandQueue,
                autoHideController,
                buttonSelectionStateListener,
                mainExecutor,
                barService,
                keyguardStateControllerLazy,
                iconPolicyLazy,
                configurationController,
                restartTracker,
                displayTracker,
                windowProvider,
                mainHandler);
        mContext = context;
        mOverlayManager = context.getSystemService(OverlayManager.class);
    }

    @Override
    public void init() {
        if (Flags.rrosPerOccupantZone()) {
            super.init();
            return;
        }

        mInitialized = false;

        String rroPackageName = mContext.getString(
                R.string.config_secondaryUserSystemUIRROPackageName);
        if (DEBUG) {
            Log.d(TAG, "start(), toggle RRO package:" + rroPackageName);
        }
        // The RRO must be applied to the user that SystemUI is running as.
        // MUPAND SystemUI runs as the system user, not the actual user.
        UserHandle userHandle = CarSystemUIUserUtil.isMUPANDSystemUI() ? UserHandle.SYSTEM
                : mUserTracker.getUserHandle();
        try {
             // TODO(b/260206944): Can remove this after we have a fix for overlaid resources not
             // applied.
             //
             // Currently because of Bug:b/260206944, RROs are not applied to the secondary user.
             // This class acts as a Mediator, which toggles the Overlay state of the RRO package,
             // which in turn triggers onConfigurationChange. Only after this change start the
             // CarSystemBar with overlaid resources.
            mOverlayManager.setEnabled(rroPackageName, false, userHandle);
            mOverlayManager.setEnabled(rroPackageName, true, userHandle);
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "Failed to set overlay package: " + ex);
            mInitialized = true;
            super.init();
        }
    }

    @Override
    public void onConfigChanged(Configuration newConfig) {
        if (Flags.rrosPerOccupantZone()) {
            super.onConfigChanged(newConfig);
            return;
        }
        if (!mInitialized) {
            mInitialized = true;
            super.init();
        } else {
            super.onConfigChanged(newConfig);
        }
    }

    @Override
    protected void createSystemBar() {
        if (!CarSystemUIUserUtil.isSecondaryMUMDSystemUI()) {
            super.createSystemBar();
        } else {
            createNavBar();
        }
    }
}
