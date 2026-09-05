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

import android.app.trust.TrustManager;
import android.car.feature.Flags;

import com.android.internal.widget.LockPatternUtils;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.car.window.OverlayViewMediator;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.settings.UserTracker;

import javax.inject.Inject;

/**
 * Mediator for Passenger Keyguard overlay. This is the entry point to all other relevant elements.
 */
@SysUISingleton
public class PassengerKeyguardOverlayViewMediator implements OverlayViewMediator {
    private final PassengerKeyguardOverlayViewController mViewController;
    private final UserTracker mUserTracker;
    private final LockPatternUtils mLockPatternUtils;
    private final TrustManager mTrustManager;

    @Inject
    public PassengerKeyguardOverlayViewMediator(
            PassengerKeyguardOverlayViewController viewController, UserTracker userTracker,
            LockPatternUtils lockPatternUtils, TrustManager trustManager) {
        mViewController = viewController;
        mUserTracker = userTracker;
        mLockPatternUtils = lockPatternUtils;
        mTrustManager = trustManager;
    }

    @Override
    public void registerListeners() {
        // no-op
    }

    @Override
    public void setUpOverlayContentViewControllers() {
        if (!CarSystemUIUserUtil.isSecondaryMUMDSystemUI()) {
            return;
        }
        if (!mLockPatternUtils.isSecure(mUserTracker.getUserId())) {
            mTrustManager.reportEnabledTrustAgentsChanged(mUserTracker.getUserId());
            return;
        }
        if (Flags.supportsSecurePassengerUsers()) {
            mViewController.start();
        }
    }
}
