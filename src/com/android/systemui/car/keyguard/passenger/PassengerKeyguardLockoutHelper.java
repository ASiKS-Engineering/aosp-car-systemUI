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

import android.annotation.Nullable;
import android.content.Context;
import android.os.CountDownTimer;
import android.os.SystemClock;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.widget.LockPatternUtils;
import com.android.settingslib.utils.StringUtil;
import com.android.systemui.car.shared.R;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.settings.UserTracker;

import java.time.Duration;

import javax.inject.Inject;

/**
 * Helper class to handle the locked out state of the passenger keyguard.
 */
@SysUISingleton
public class PassengerKeyguardLockoutHelper {
    private final Context mContext;
    private final LockPatternUtils mLockPatternUtils;
    private final int mUserId;
    private final Object mLock = new Object();
    private CountDownTimer mCountDownTimer;
    @Nullable
    @GuardedBy("mLock")
    private Callback mCallback;

    @Inject
    public PassengerKeyguardLockoutHelper(Context context, LockPatternUtils lockPatternUtils,
            UserTracker userTracker) {
        mContext = context;
        mLockPatternUtils = lockPatternUtils;
        mUserId = userTracker.getUserId();
    }

    void setCallback(@Nullable Callback callback) {
        synchronized (mLock) {
            mCallback = callback;
        }
    }

    /** Called when lock UI is shown */
    void onUIShown() {
        if (isLockedOut()) {
            handleAttemptLockout(mLockPatternUtils.getLockoutEndTime(mUserId).toMillis());
        } else {
            notifyRefresh(isLockedOut());
        }
    }

    /** Called when lock UI is hidden */
    void onUIHidden() {
        if (mCountDownTimer != null) {
            mCountDownTimer.cancel();
        }
        notifyErrorText("");
    }

    /** Handles when the lock check is completed but returns a timeout. */
    void onCheckCompletedWithTimeout(Duration timeout) {
        if (!timeout.isPositive()) {
            return;
        }

        long deadline = mLockPatternUtils.getLockoutEndTime(mUserId).toMillis();
        handleAttemptLockout(deadline);
    }

    private void handleAttemptLockout(long deadline) {
        long elapsedRealtime = SystemClock.elapsedRealtime();
        notifyRefresh(isLockedOut());
        mCountDownTimer = newCountDownTimer(deadline - elapsedRealtime).start();
    }

    private boolean isLockedOut() {
        return !mLockPatternUtils.getLockoutEndTime(mUserId).isZero();
    }

    private void notifyRefresh(boolean isLockedOut) {
        synchronized (mLock) {
            if (mCallback != null) {
                mCallback.refreshUI(isLockedOut);
            }
        }
    }

    private void notifyErrorText(String msg) {
        synchronized (mLock) {
            if (mCallback != null) {
                mCallback.setErrorText(msg);
            }
        }
    }

    private CountDownTimer newCountDownTimer(long countDownMillis) {
        return new CountDownTimer(countDownMillis,
                LockPatternUtils.FAILED_ATTEMPT_COUNTDOWN_INTERVAL_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsCountdown = (int) (millisUntilFinished / 1000);
                notifyErrorText(StringUtil.getIcuPluralsString(mContext, secondsCountdown,
                        R.string.passenger_keyguard_too_many_failed_attempts));
            }

            @Override
            public void onFinish() {
                notifyRefresh(false);
                notifyErrorText("");
            }
        };
    }

    @VisibleForTesting
    @Nullable
    CountDownTimer getCountDownTimer() {
        return mCountDownTimer;
    }

    /** Interface for controlling the associated lock timeout UI. */
    public interface Callback {
        /** Sets the error text with the given string. */
        void setErrorText(String text);
        /** Refreshes the UI based on the locked out state. */
        void refreshUI(boolean isLockedOut);
    }
}
