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
import android.os.Handler;
import android.view.View;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockPatternView;
import com.android.internal.widget.LockscreenCredential;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.shared.R;
import com.android.systemui.settings.UserTracker;

import java.util.List;

/**
 * Credential ViewController for the pattern credential type.
 */
public class PassengerKeyguardPatternViewController extends
        PassengerKeyguardCredentialViewController {
    private static final long CLEAR_WRONG_PATTERN_ATTEMPT_TIMEOUT_MS = 1500L;

    private final LockPatternUtils mLockPatternUtils;
    private final UserTracker mUserTracker;

    private LockPatternView mLockPatternView;
    private List<LockPatternView.Cell> mPattern;

    private final Runnable mClearPatternErrorRunnable = () -> {
        if (mLockPatternView != null) {
            mLockPatternView.setEnabled(true);
            mLockPatternView.clearPattern();
        }
        clearError();
    };

    protected PassengerKeyguardPatternViewController(View view,
            LockPatternUtils lockPatternUtils,
            UserTracker userTracker,
            TrustManager trustManager, Handler mainHandler,
            CarServiceProvider carServiceProvider,
            PassengerKeyguardLockoutHelper lockoutHelper) {
        super(view, lockPatternUtils, userTracker, trustManager, mainHandler, carServiceProvider,
                lockoutHelper);
        mLockPatternUtils = lockPatternUtils;
        mUserTracker = userTracker;
    }

    @Override
    protected void onInit() {
        super.onInit();
        mLockPatternView = mView.requireViewById(R.id.lockPattern);

        mLockPatternView.setFadePattern(false);
        mLockPatternView.setInStealthMode(
                !mLockPatternUtils.isVisiblePatternEnabled(mUserTracker.getUserId()));
        mLockPatternView.setOnPatternListener(new LockPatternView.OnPatternListener() {
            @Override
            public void onPatternStart() {
                mLockPatternView.removeCallbacks(mClearPatternErrorRunnable);
                clearError();
            }

            @Override
            public void onPatternCleared() {
                mLockPatternView.removeCallbacks(mClearPatternErrorRunnable);
                mPattern = null;
            }

            @Override
            public void onPatternCellAdded(List<LockPatternView.Cell> pattern) {
            }

            @Override
            public void onPatternDetected(List<LockPatternView.Cell> pattern) {
                mLockPatternView.setEnabled(false);
                mPattern = pattern;

                verifyCredential(() -> {
                    setErrorMessage(
                            getContext().getString(R.string.passenger_keyguard_wrong_pattern));
                    mLockPatternView.removeCallbacks(mClearPatternErrorRunnable);
                    mLockPatternView.postDelayed(mClearPatternErrorRunnable,
                            CLEAR_WRONG_PATTERN_ATTEMPT_TIMEOUT_MS);
                });
            }
        });
    }

    @Override
    protected LockscreenCredential getCurrentCredential() {
        if (mPattern != null) {
            return LockscreenCredential.createPattern(mPattern);
        }
        return LockscreenCredential.createNone();
    }

    @Override
    protected void onLockedOutChanged(boolean isLockedOut) {
        mLockPatternView.setEnabled(!isLockedOut);
        mLockPatternView.clearPattern();
    }

    @Override
    public void clearAllCredentials() {
        mLockPatternView.clearPattern();
        super.clearAllCredentials();
    }
}
