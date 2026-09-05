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

import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_NONE;
import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PASSWORD;
import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PATTERN;
import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PIN;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.app.trust.TrustManager;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.widget.LockPatternUtils;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.settings.UserTracker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@SmallTest
public class PassengerKeyguardCredentialViewControllerFactoryTest extends CarSysuiTestCase {
    private static final int TEST_USER_ID = 1000;

    private PassengerKeyguardCredentialViewControllerFactory mFactory;
    private ViewGroup mViewGroup;
    private LayoutInflater mLayoutInflater;

    @Mock
    private LockPatternUtils mLockPatternUtils;
    @Mock
    private UserTracker mUserTracker;
    @Mock
    private TrustManager mTrustManager;
    @Mock
    private Handler mHandler;
    @Mock
    private CarServiceProvider mCarServiceProvider;
    @Mock
    private PassengerKeyguardLockoutHelper mLockoutHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLayoutInflater = LayoutInflater.from(mContext);
        mFactory = new PassengerKeyguardCredentialViewControllerFactory(mLayoutInflater,
                mLockPatternUtils, mUserTracker, mTrustManager, mHandler, mCarServiceProvider,
                mLockoutHelper);
        mViewGroup = new FrameLayout(mContext);
        when(mUserTracker.getUserId()).thenReturn(TEST_USER_ID);
    }

    @Test(expected = IllegalStateException.class)
    public void onCreate_noCredential_throwsException() {
        when(mLockPatternUtils.getCredentialTypeForUser(TEST_USER_ID)).thenReturn(
                CREDENTIAL_TYPE_NONE);

        mFactory.create(mViewGroup);
    }

    @Test
    public void onCreate_pinCredential_inflatesAndCreatesController() {
        when(mLockPatternUtils.getCredentialTypeForUser(TEST_USER_ID)).thenReturn(
                CREDENTIAL_TYPE_PIN);

        assertViewAndControllerCreated();
    }

    @Test
    public void onCreate_patternCredential_inflatesAndCreatesController() {
        when(mLockPatternUtils.getCredentialTypeForUser(TEST_USER_ID)).thenReturn(
                CREDENTIAL_TYPE_PATTERN);

        assertViewAndControllerCreated();
    }

    @Test
    public void onCreate_passwordCredential_inflatesAndCreatesController() {
        when(mLockPatternUtils.getCredentialTypeForUser(TEST_USER_ID)).thenReturn(
                CREDENTIAL_TYPE_PASSWORD);

        assertViewAndControllerCreated();
    }

    private void assertViewAndControllerCreated() {
        PassengerKeyguardCredentialViewController controller = mFactory.create(mViewGroup);

        assertThat(controller).isNotNull();
    }
}
