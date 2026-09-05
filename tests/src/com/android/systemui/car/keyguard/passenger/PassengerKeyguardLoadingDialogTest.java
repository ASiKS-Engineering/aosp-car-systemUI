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

import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.feature.Flags;
import android.car.user.CarUserManager;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.Display;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.internal.widget.LockPatternUtils;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.utils.os.FakeHandler;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.Set;
import java.util.concurrent.Executor;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
@EnableFlags(Flags.FLAG_SUPPORTS_SECURE_PASSENGER_USERS)
public class PassengerKeyguardLoadingDialogTest extends CarSysuiTestCase {
    private static final int TEST_USER_ID = 1000;
    private static final int TEST_DRIVER_DISPLAY_ID = 100;
    private static final int TEST_PASSENGER_DISPLAY_ID = 101;

    private PassengerKeyguardLoadingDialog mLoadingDialog;
    private MockitoSession mSession;
    private FakeHandler mMainHandler;

    @Nullable
    private CarUserManager.UserLifecycleListener mUserLifecycleListener;
    @Nullable
    private DisplayManager.DisplayListener mDisplayListener;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private CarServiceProvider mCarServiceProvider;
    @Mock
    private Executor mBackgroundExecutor;

    @Mock
    private Handler mBackgroundHandler;
    @Mock
    private LockPatternUtils mLockPatternUtils;
    @Mock
    private UserManager mUserManager;
    @Mock
    private DisplayManager mDisplayManager;
    @Mock
    private CarUserManager mCarUserManager;
    @Mock
    private CarOccupantZoneManager mCarOccupantZoneManager;


    @Before
    public void setUp() {
        mSession = ExtendedMockito.mockitoSession()
                .initMocks(this)
                .spyStatic(CarSystemUIUserUtil.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
        mMainHandler = new FakeHandler(TestableLooper.get(this).getLooper());
        mContext.addMockSystemService(UserManager.class, mUserManager);
        mContext.addMockSystemService(DisplayManager.class, mDisplayManager);
        mLoadingDialog = new TestPassengerKeyguardLoadingDialog(mContext, mCarServiceProvider,
                mBackgroundExecutor, mMainHandler, mBackgroundHandler, mLockPatternUtils);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
            mSession = null;
        }
    }

    @Test
    public void onStart_notDriverMUMDSysUI_notInitialized() {
        doReturn(false).when(() -> CarSystemUIUserUtil.isDriverMUMDSystemUI());

        mLoadingDialog.start();

        verify(mCarServiceProvider, never()).addListener(any());
        verify(mDisplayManager, never()).registerDisplayListener(any(), any());
    }

    @Test
    public void onStart_driverMUMDSysUI_initialized() {
        doReturn(true).when(() -> CarSystemUIUserUtil.isDriverMUMDSystemUI());

        mLoadingDialog.start();

        verify(mCarServiceProvider).addListener(any());
        verify(mDisplayManager).registerDisplayListener(any(), any());
    }

    @Test
    public void onUserStart_nonSecureUser_presentationNotCreated() {
        when(mLockPatternUtils.isSecure(TEST_USER_ID)).thenReturn(false);
        when(mUserManager.isUserUnlocked(TEST_USER_ID)).thenReturn(true);
        startAndRegisterMocks();
        assertThat(mUserLifecycleListener).isNotNull();

        mUserLifecycleListener.onEvent(
                new CarUserManager.UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_STARTING,
                        TEST_USER_ID));

        assertThat(mLoadingDialog.mPresentations.containsKey(TEST_USER_ID)).isFalse();
    }

    @Test
    public void onUserStart_secureUser_presentationCreated() {
        when(mLockPatternUtils.isSecure(TEST_USER_ID)).thenReturn(true);
        when(mUserManager.isUserUnlocked(TEST_USER_ID)).thenReturn(false);
        startAndRegisterMocks();
        assertThat(mUserLifecycleListener).isNotNull();

        mUserLifecycleListener.onEvent(
                new CarUserManager.UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_STARTING,
                        TEST_USER_ID));

        assertThat(mLoadingDialog.mPresentations.containsKey(TEST_USER_ID)).isTrue();
    }

    @Test
    public void onInit_nonSecureUserVisible_presentationNotCreated() {
        when(mLockPatternUtils.isSecure(TEST_USER_ID)).thenReturn(false);
        when(mUserManager.isUserUnlocked(TEST_USER_ID)).thenReturn(true);
        when(mUserManager.getVisibleUsers()).thenReturn(Set.of(UserHandle.of(TEST_USER_ID)));

        startAndRegisterMocks();

        assertThat(mLoadingDialog.mPresentations.containsKey(TEST_USER_ID)).isFalse();
    }

    @Test
    public void onInit_secureUserVisible_presentationCreated() {
        when(mLockPatternUtils.isSecure(TEST_USER_ID)).thenReturn(true);
        when(mUserManager.isUserUnlocked(TEST_USER_ID)).thenReturn(false);
        when(mUserManager.getVisibleUsers()).thenReturn(Set.of(UserHandle.of(TEST_USER_ID)));

        startAndRegisterMocks();

        assertThat(mLoadingDialog.mPresentations.containsKey(TEST_USER_ID)).isTrue();
    }

    @Test
    public void onDisplayRemoved_presentationRemoved() {
        when(mLockPatternUtils.isSecure(TEST_USER_ID)).thenReturn(true);
        when(mUserManager.isUserUnlocked(TEST_USER_ID)).thenReturn(false);
        when(mUserManager.getVisibleUsers()).thenReturn(Set.of(UserHandle.of(TEST_USER_ID)));
        startAndRegisterMocks();
        assertThat(mLoadingDialog.mPresentations.containsKey(TEST_USER_ID)).isTrue();
        assertThat(mDisplayListener).isNotNull();

        mDisplayListener.onDisplayRemoved(TEST_PASSENGER_DISPLAY_ID);

        assertThat(mLoadingDialog.mPresentations.containsKey(TEST_USER_ID)).isFalse();
    }

    @Test
    public void onUserUnlocked_presentationRemoved() {
        when(mLockPatternUtils.isSecure(TEST_USER_ID)).thenReturn(true);
        when(mUserManager.isUserUnlocked(TEST_USER_ID)).thenReturn(false);
        when(mUserManager.getVisibleUsers()).thenReturn(Set.of(UserHandle.of(TEST_USER_ID)));
        startAndRegisterMocks();
        assertThat(mLoadingDialog.mPresentations.containsKey(TEST_USER_ID)).isTrue();
        assertThat(mUserLifecycleListener).isNotNull();

        mUserLifecycleListener.onEvent(
                new CarUserManager.UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED,
                        TEST_USER_ID));

        assertThat(mLoadingDialog.mPresentations.containsKey(TEST_USER_ID)).isFalse();
    }

    @Test
    public void onUserStopped_presentationRemoved() {
        when(mLockPatternUtils.isSecure(TEST_USER_ID)).thenReturn(true);
        when(mUserManager.isUserUnlocked(TEST_USER_ID)).thenReturn(false);
        when(mUserManager.getVisibleUsers()).thenReturn(Set.of(UserHandle.of(TEST_USER_ID)));
        startAndRegisterMocks();
        assertThat(mLoadingDialog.mPresentations.containsKey(TEST_USER_ID)).isTrue();
        assertThat(mUserLifecycleListener).isNotNull();

        mUserLifecycleListener.onEvent(
                new CarUserManager.UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_STOPPED,
                        TEST_USER_ID));

        assertThat(mLoadingDialog.mPresentations.containsKey(TEST_USER_ID)).isFalse();
    }

    /**
     * Start the CoreStartable and setup mocks related to the CarService and DisplayManager
     */
    private void startAndRegisterMocks() {
        doReturn(true).when(() -> CarSystemUIUserUtil.isDriverMUMDSystemUI());
        Car mockCar = mock(Car.class);
        when(mockCar.getCarManager(CarUserManager.class)).thenReturn(mCarUserManager);
        when(mockCar.getCarManager(CarOccupantZoneManager.class)).thenReturn(
                mCarOccupantZoneManager);
        when(mCarOccupantZoneManager.getDisplayIdForDriver(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN)).thenReturn(TEST_DRIVER_DISPLAY_ID);
        CarOccupantZoneManager.OccupantZoneInfo passengerZoneInfo = mock(
                CarOccupantZoneManager.OccupantZoneInfo.class);
        Display passengerDisplay = mock(Display.class);
        when(passengerDisplay.getDisplayId()).thenReturn(TEST_PASSENGER_DISPLAY_ID);
        when(mCarOccupantZoneManager.getOccupantZoneForUser(
                UserHandle.of(TEST_USER_ID))).thenReturn(passengerZoneInfo);
        when(mCarOccupantZoneManager.getDisplayForOccupant(passengerZoneInfo,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN)).thenReturn(passengerDisplay);
        when(mCarOccupantZoneManager.getUserForDisplayId(TEST_PASSENGER_DISPLAY_ID)).thenReturn(
                TEST_USER_ID);

        ArgumentCaptor<CarServiceProvider.CarServiceOnConnectedListener> carConnectedListener =
                ArgumentCaptor.forClass(CarServiceProvider.CarServiceOnConnectedListener.class);
        ArgumentCaptor<CarUserManager.UserLifecycleListener> userLifecycleListener =
                ArgumentCaptor.forClass(CarUserManager.UserLifecycleListener.class);
        ArgumentCaptor<DisplayManager.DisplayListener> displayListener =
                ArgumentCaptor.forClass(DisplayManager.DisplayListener.class);

        mLoadingDialog.start();

        verify(mCarServiceProvider).addListener(carConnectedListener.capture());
        carConnectedListener.getValue().onConnected(mockCar);

        verify(mCarUserManager).addListener(any(), userLifecycleListener.capture());
        mUserLifecycleListener = userLifecycleListener.getValue();
        verify(mDisplayManager).registerDisplayListener(displayListener.capture(), any());
        mDisplayListener = displayListener.getValue();
    }

    private static class TestPassengerKeyguardLoadingDialog extends PassengerKeyguardLoadingDialog {
        TestPassengerKeyguardLoadingDialog(Context context,
                CarServiceProvider carServiceProvider,
                Executor bgExecutor, Handler mainHandler, Handler bgHandler,
                LockPatternUtils lockPatternUtils) {
            super(context, carServiceProvider, bgExecutor, mainHandler, bgHandler,
                    lockPatternUtils);
        }

        // Use mock for loading presentation to not depend on real display
        @Override
        LoadingPresentation createLoadingPresentation(Display display) {
            return mock(LoadingPresentation.class);
        }
    }
}
