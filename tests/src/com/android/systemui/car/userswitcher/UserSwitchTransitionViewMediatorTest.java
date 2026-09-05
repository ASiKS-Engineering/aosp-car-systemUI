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

package com.android.systemui.car.userswitcher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.KeyguardManager;
import android.car.user.CarUserManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.IWindowManager;

import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.concurrency.FakeExecutor;
import com.android.systemui.util.time.FakeSystemClock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class UserSwitchTransitionViewMediatorTest extends CarSysuiTestCase {
    private static final int TEST_USER = 100;

    private UserSwitchTransitionViewMediator mUserSwitchTransitionViewMediator;
    private FakeExecutor mFakeExecutor;

    @Mock
    private CarServiceProvider mCarServiceProvider;
    @Mock
    private UserTracker mUserTracker;
    @Mock
    private UserManager mUserManager;
    @Mock
    private IWindowManager mWindowManagerService;
    @Mock
    private KeyguardManager mKeyguardManager;
    @Mock
    private KeyguardStateController mKeyguardStateController;
    @Mock
    private UserSwitchTransitionViewController mUserSwitchTransitionViewController;

    private final List<KeyguardStateController.Callback> mKeyguardCallbacks = new ArrayList<>();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mFakeExecutor = new FakeExecutor(new FakeSystemClock());

        mContext.addMockSystemService(KeyguardManager.class, mKeyguardManager);
        doAnswer(invocation -> {
            mKeyguardCallbacks.add(invocation.getArgument(0));
            return null;
        }).when(mKeyguardStateController).addCallback(any());

        mUserSwitchTransitionViewMediator = new UserSwitchTransitionViewMediator(mContext,
                mFakeExecutor, mCarServiceProvider, mUserTracker, mUserManager,
                mWindowManagerService, mKeyguardStateController,
                mUserSwitchTransitionViewController);
    }

    @Test
    public void registerListeners_addsUserTrackerCallback() {
        mUserSwitchTransitionViewMediator.registerListeners();

        verify(mUserTracker).addCallback(any(), eq(mFakeExecutor));
    }

    @Test
    public void onBeforeUserSwitching_showsSwitchingUI() throws RemoteException {
        mUserSwitchTransitionViewMediator.mUserChangedCallback.onBeforeUserSwitching(TEST_USER);

        verify(mUserSwitchTransitionViewController).showSwitchingUI(TEST_USER);
        verify(mWindowManagerService).setSwitchingUser(true);
    }

    @Test
    public void onUserChanging_deviceIsSecure_locksDevice() throws RemoteException {
        when(mKeyguardManager.isDeviceSecure(TEST_USER)).thenReturn(true);

        mUserSwitchTransitionViewMediator.mUserChangedCallback.onUserChanging(TEST_USER, mContext);

        verify(mWindowManagerService).lockNow(null);
    }

    @Test
    public void onUserChanging_deviceIsNotSecure_doesNotLockDevice() throws RemoteException {
        when(mKeyguardManager.isDeviceSecure(TEST_USER)).thenReturn(false);

        mUserSwitchTransitionViewMediator.mUserChangedCallback.onUserChanging(TEST_USER, mContext);

        verify(mWindowManagerService, never()).lockNow(null);
    }

    @Test
    public void onUserChanged_hidesSwitchingUI() {
        when(mUserManager.isUserUnlocked(anyInt())).thenReturn(true);

        mUserSwitchTransitionViewMediator.mUserChangedCallback.onUserChanged(TEST_USER, mContext);

        verify(mUserSwitchTransitionViewController).hideSwitchingUI();
    }

    @Test
    public void onUserSwitchStart_showsSwitchingUI() {
        // Call onUserSwitchStart, which should directly call to show the switching UI.
        mUserSwitchTransitionViewMediator.onUserSwitchStart(UserHandle.of(TEST_USER));

        // Verify that the switching UI is shown without needing to execute any pending runnables.
        verify(mUserSwitchTransitionViewController).showSwitchingUI(TEST_USER);
    }

    @Test
    public void onUserUnlockedEvent_hidesSwitchingUI() {
        when(mUserTracker.getUserId()).thenReturn(TEST_USER);
        when(mUserManager.isUserUnlocked(TEST_USER)).thenReturn(true);
        CarUserManager.UserLifecycleEvent event = new CarUserManager.UserLifecycleEvent(
                CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED, TEST_USER);

        mUserSwitchTransitionViewMediator.handleUserLifecycleEvent(event);

        verify(mUserSwitchTransitionViewController).hideSwitchingUI();
    }

    @Test
    public void onKeyguardShowing_hidesSwitchingUI() {
        when(mKeyguardManager.isDeviceSecure(anyInt())).thenReturn(true);
        when(mKeyguardStateController.isShowing()).thenReturn(false);
        mUserSwitchTransitionViewMediator.registerListeners();
        when(mKeyguardStateController.isShowing()).thenReturn(true);

        for (KeyguardStateController.Callback callback : mKeyguardCallbacks) {
            callback.onKeyguardShowingChanged();
        }

        verify(mUserSwitchTransitionViewController).hideSwitchingUI();
    }
}
