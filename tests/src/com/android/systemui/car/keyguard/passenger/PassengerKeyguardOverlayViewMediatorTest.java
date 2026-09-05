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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.trust.TrustManager;
import android.car.feature.Flags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.AndroidTestingRunner;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.internal.widget.LockPatternUtils;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.settings.UserTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@SmallTest
@EnableFlags(Flags.FLAG_SUPPORTS_SECURE_PASSENGER_USERS)
public class PassengerKeyguardOverlayViewMediatorTest extends CarSysuiTestCase {
    private static final int TEST_USER_ID = 1000;

    private PassengerKeyguardOverlayViewMediator mMediator;
    private MockitoSession mSession;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private PassengerKeyguardOverlayViewController mViewController;
    @Mock
    private UserTracker mUserTracker;
    @Mock
    private LockPatternUtils mLockPatternUtils;
    @Mock
    private TrustManager mTrustManager;

    @Before
    public void setUp() {
        mSession = ExtendedMockito.mockitoSession()
                .initMocks(this)
                .spyStatic(CarSystemUIUserUtil.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
        when(mUserTracker.getUserId()).thenReturn(TEST_USER_ID);
        when(mLockPatternUtils.isSecure(TEST_USER_ID)).thenReturn(false);
        mMediator = new PassengerKeyguardOverlayViewMediator(mViewController, mUserTracker,
                mLockPatternUtils, mTrustManager);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
            mSession = null;
        }
    }

    @Test
    public void setupController_nonSecondaryMUMDSysUI_controllerNotInitialized() {
        doReturn(false).when(() -> CarSystemUIUserUtil.isSecondaryMUMDSystemUI());

        mMediator.setUpOverlayContentViewControllers();

        verify(mViewController, never()).start();
        verify(mTrustManager, never()).reportEnabledTrustAgentsChanged(anyInt());
    }

    @Test
    public void setupController_nonSecureUser_controllerNotInitialized() {
        doReturn(true).when(() -> CarSystemUIUserUtil.isSecondaryMUMDSystemUI());

        mMediator.setUpOverlayContentViewControllers();

        verify(mViewController, never()).start();
        verify(mTrustManager).reportEnabledTrustAgentsChanged(TEST_USER_ID);
    }

    @Test
    public void setupController_secureUser_controllerInitialized() {
        doReturn(true).when(() -> CarSystemUIUserUtil.isSecondaryMUMDSystemUI());
        when(mLockPatternUtils.isSecure(TEST_USER_ID)).thenReturn(true);

        mMediator.setUpOverlayContentViewControllers();

        verify(mTrustManager, never()).reportEnabledTrustAgentsChanged(TEST_USER_ID);
        verify(mViewController).start();
    }
}
