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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.trust.TrustManager;
import android.os.Handler;
import android.testing.AndroidTestingRunner;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.internal.widget.LockPatternChecker;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.internal.widget.VerifyCredentialResponse;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.settings.UserTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.time.Duration;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@SmallTest
public class PassengerKeyguardCredentialViewControllerTest extends CarSysuiTestCase {

    private TestPassengerKeyguardCredentialViewController mController;
    private MockitoSession mSession;

    @Mock
    private LockPatternUtils mLockPatternUtils;
    @Mock
    private UserTracker mUserTracker;
    @Mock
    private TrustManager mTrustManager;
    @Mock
    private Handler mMainHandler;
    @Mock
    private CarServiceProvider mCarServiceProvider;
    @Mock
    private PassengerKeyguardLockoutHelper mLockoutHelper;
    @Mock
    private PassengerKeyguardCredentialViewController.OnAuthSucceededCallback mCallback;

    @Before
    public void setUp() {
        mSession = ExtendedMockito.mockitoSession()
                .initMocks(this)
                .mockStatic(LockPatternChecker.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
        View view = new View(mContext);
        mController = new TestPassengerKeyguardCredentialViewController(view, mLockPatternUtils,
                mUserTracker, mTrustManager, mMainHandler, mCarServiceProvider, mLockoutHelper);
        mController.setAuthSucceededCallback(mCallback);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
            mSession = null;
        }
    }

    @Test
    public void verifyCredential_invalidCredential_postFailureRunnable() {
        Runnable failureRunnable = mock(Runnable.class);
        ArgumentCaptor<LockPatternChecker.OnVerifyCallback> captor = ArgumentCaptor.forClass(
                LockPatternChecker.OnVerifyCallback.class);

        mController.verifyCredential(failureRunnable);

        ExtendedMockito.verify(() -> LockPatternChecker.verifyCredential(any(), any(), anyInt(),
                anyInt(), captor.capture()));
        captor.getValue().onVerified(VerifyCredentialResponse.credIncorrect());
        verify(mMainHandler).post(failureRunnable);
    }

    @Test
    public void verifyCredential_invalidCredential_timeout() {
        Duration throttleTimeout = Duration.ofSeconds(1);
        Runnable failureRunnable = mock(Runnable.class);
        ArgumentCaptor<LockPatternChecker.OnVerifyCallback> captor = ArgumentCaptor.forClass(
                LockPatternChecker.OnVerifyCallback.class);

        mController.verifyCredential(failureRunnable);

        ExtendedMockito.verify(() -> LockPatternChecker.verifyCredential(any(), any(), anyInt(),
                anyInt(), captor.capture()));
        captor.getValue().onVerified(VerifyCredentialResponse.credIncorrect(throttleTimeout));
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mMainHandler).post(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        verify(mLockoutHelper).onCheckCompletedWithTimeout(throttleTimeout);
    }

    @Test
    public void verifyCredential_validCredential_authSucceeded() {
        Runnable failureRunnable = mock(Runnable.class);
        ArgumentCaptor<LockPatternChecker.OnVerifyCallback> captor = ArgumentCaptor.forClass(
                LockPatternChecker.OnVerifyCallback.class);

        mController.verifyCredential(failureRunnable);

        ExtendedMockito.verify(() -> LockPatternChecker.verifyCredential(any(), any(), anyInt(),
                anyInt(), captor.capture()));
        captor.getValue().onVerified(VerifyCredentialResponse.OK);
        verify(mMainHandler, never()).post(failureRunnable);
        verify(mTrustManager).reportEnabledTrustAgentsChanged(anyInt());
        verify(mCallback).onAuthSucceeded();
    }

    private static class TestPassengerKeyguardCredentialViewController
            extends PassengerKeyguardCredentialViewController {

        TestPassengerKeyguardCredentialViewController(View view,
                LockPatternUtils lockPatternUtils,
                UserTracker userTracker,
                TrustManager trustManager, Handler mainHandler,
                CarServiceProvider carServiceProvider,
                PassengerKeyguardLockoutHelper lockoutHelper) {
            super(view, lockPatternUtils, userTracker, trustManager, mainHandler,
                    carServiceProvider, lockoutHelper);
        }

        @Override
        protected LockscreenCredential getCurrentCredential() {
            return LockscreenCredential.createPin("1234");
        }

        @Override
        protected void onLockedOutChanged(boolean isLockedOut) {
            // no-op
        }
    }
}
