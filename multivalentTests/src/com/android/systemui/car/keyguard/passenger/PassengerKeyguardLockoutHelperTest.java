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

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.SystemClock;
import android.testing.TestableLooper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.widget.LockPatternUtils;
import com.android.settingslib.utils.StringUtil;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.shared.R;
import com.android.systemui.settings.UserTracker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper
@SmallTest
public class PassengerKeyguardLockoutHelperTest extends CarSysuiTestCase {
    private static final int TEST_USER_ID = 1000;
    private static final Duration TEST_TIMEOUT_LENGTH = Duration.ofSeconds(1);
    private static final int TEST_TIMEOUT_LENGTH_MS = (int) TEST_TIMEOUT_LENGTH.toMillis();

    private PassengerKeyguardLockoutHelper mLockoutHelper;

    @Mock
    private PassengerKeyguardLockoutHelper.Callback mCallback;
    @Mock
    private LockPatternUtils mLockPatternUtils;
    @Mock
    private UserTracker mUserTracker;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mUserTracker.getUserId()).thenReturn(TEST_USER_ID);
        mLockoutHelper = new PassengerKeyguardLockoutHelper(mContext, mLockPatternUtils,
                mUserTracker);
        mLockoutHelper.setCallback(mCallback);
    }

    @Test
    public void onUIShown_lockedOut_notifiesLockState() {
        when(mLockPatternUtils.getLockoutEndTime(TEST_USER_ID)).thenReturn(Duration.ofSeconds(1));

        mLockoutHelper.onUIShown();

        verify(mCallback).refreshUI(true);
    }

    @Test
    public void onUIShown_notLockedOut_notifiesLockState() {
        when(mLockPatternUtils.getLockoutEndTime(TEST_USER_ID)).thenReturn(Duration.ZERO);

        mLockoutHelper.onUIShown();

        verify(mCallback).refreshUI(false);
    }

    @Test
    public void onCheckCompletedWithTimeout_checksTimeout() {
        Duration lockoutEndTime = Duration.ofMillis(SystemClock.elapsedRealtime())
                .plus(TEST_TIMEOUT_LENGTH);
        when(mLockPatternUtils.getLockoutEndTime(TEST_USER_ID))
                .thenReturn(lockoutEndTime);

        mLockoutHelper.onCheckCompletedWithTimeout(TEST_TIMEOUT_LENGTH);

        verify(mLockPatternUtils, times(2)).getLockoutEndTime(TEST_USER_ID);
        verify(mCallback).refreshUI(true);
    }

    @Test
    public void onCountdown_setsErrorMessage() {
        Duration lockoutEndTime = Duration.ofMillis(SystemClock.elapsedRealtime())
                .plus(TEST_TIMEOUT_LENGTH);
        when(mLockPatternUtils.getLockoutEndTime(TEST_USER_ID))
                .thenReturn(lockoutEndTime);

        mLockoutHelper.onCheckCompletedWithTimeout(TEST_TIMEOUT_LENGTH);
        mLockoutHelper.getCountDownTimer().onTick(TEST_TIMEOUT_LENGTH_MS);

        int testTimeoutLengthSeconds = (int) TEST_TIMEOUT_LENGTH.toSeconds();
        verify(mCallback).setErrorText(StringUtil.getIcuPluralsString(mContext,
                testTimeoutLengthSeconds, R.string.passenger_keyguard_too_many_failed_attempts));
    }
}
