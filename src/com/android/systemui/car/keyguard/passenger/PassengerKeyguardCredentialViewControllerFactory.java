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

import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PASSWORD;
import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PATTERN;
import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PIN;

import android.app.trust.TrustManager;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.internal.widget.LockPatternUtils;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.shared.R;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.settings.UserTracker;

import javax.inject.Inject;

/**
 * Factory class to create credential ViewControllers based on the current user's lock type.
 */
@SysUISingleton
public class PassengerKeyguardCredentialViewControllerFactory {
    private final LayoutInflater mInflater;
    private final LockPatternUtils mLockPatternUtils;
    private final UserTracker mUserTracker;
    private final TrustManager mTrustManager;
    private final Handler mMainHandler;
    private final CarServiceProvider mCarServiceProvider;
    private final PassengerKeyguardLockoutHelper mLockoutHelper;

    @Inject
    public PassengerKeyguardCredentialViewControllerFactory(LayoutInflater inflater,
            LockPatternUtils lockPatternUtils, UserTracker userTracker, TrustManager trustManager,
            @Main Handler mainHandler, CarServiceProvider carServiceProvider,
            PassengerKeyguardLockoutHelper lockoutHelper) {
        mInflater = inflater;
        mLockPatternUtils = lockPatternUtils;
        mUserTracker = userTracker;
        mTrustManager = trustManager;
        mMainHandler = mainHandler;
        mCarServiceProvider = carServiceProvider;
        mLockoutHelper = lockoutHelper;
    }

    /**
     * Inflate a pin, password, or pattern view (depending on the user's currently set credential)
     * and attach the relevant controller. Note that this should only be called for users that have
     * a credential set - otherwise it will throw an exception.
     */
    public PassengerKeyguardCredentialViewController create(ViewGroup root) {
        @LockPatternUtils.CredentialType int credentialType =
                mLockPatternUtils.getCredentialTypeForUser(mUserTracker.getUserId());
        PassengerKeyguardCredentialViewController controller = null;
        if (credentialType == CREDENTIAL_TYPE_PIN) {
            View v = mInflater.inflate(R.layout.passenger_keyguard_pin_view, root, true);
            controller = new PassengerKeyguardPinViewController(v, mLockPatternUtils, mUserTracker,
                    mTrustManager, mMainHandler, mCarServiceProvider, mLockoutHelper);
        } else if (credentialType == CREDENTIAL_TYPE_PASSWORD) {
            View v = mInflater.inflate(R.layout.passenger_keyguard_password_view, root, true);
            controller = new PassengerKeyguardPasswordViewController(v, mLockPatternUtils,
                    mUserTracker, mTrustManager, mMainHandler, mCarServiceProvider, mLockoutHelper);
        } else if (credentialType == CREDENTIAL_TYPE_PATTERN) {
            View v = mInflater.inflate(R.layout.passenger_keyguard_pattern_view, root, true);
            controller = new PassengerKeyguardPatternViewController(v, mLockPatternUtils,
                    mUserTracker, mTrustManager, mMainHandler, mCarServiceProvider, mLockoutHelper);
        }

        if (controller != null) {
            controller.init();
            return controller;
        }

        throw new IllegalStateException("Unknown credential type=" + credentialType);
    }
}
