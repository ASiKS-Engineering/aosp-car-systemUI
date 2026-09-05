/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.car.wm.scalableui.systemevents;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.drivingstate.CarUxRestrictions;
import android.car.user.CarUserManager;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.testing.TestableLooper;
import android.view.Display;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.car.scalableui.model.Event;
import com.android.car.ui.utils.CarUxRestrictionsUtil;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarDeviceProvisionedListener;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.display.DisplayStateHelper;
import com.android.systemui.car.flags.Flag;
import com.android.systemui.car.flags.FlagManager;
import com.android.systemui.car.shared.R;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.settings.DisplayTracker;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.KeyguardStateController;

import dagger.Lazy;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.Executor;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper
@SmallTest
public class SystemEventHandlerTest extends CarSysuiTestCase {

    private static final int TEST_USER_ID = 10;
    private static final int TEST_DISPLAY_ID = 1;

    @Mock
    private UserManager mUserManager;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private CarServiceProvider mCarServiceProvider;
    @Mock
    private UserTracker mUserTracker;
    @Mock
    private DisplayTracker mDisplayTracker;
    @Mock
    private Lazy<DisplayStateHelper> mDisplayStateHelperLazy;
    @Mock
    private DisplayStateHelper mDisplayStateHelper;
    @Mock
    private KeyguardStateController mKeyguardStateController;
    @Mock
    private CarDeviceProvisionedController mCarDeviceProvisionedController;
    @Mock
    private Car mCar;
    @Mock
    private EventDispatcher mEventDispatcher;
    @Mock
    private FlagManager mFlagManager;
    @Mock
    private CarUserManager mCarUserManager;
    @Mock
    private Display mDisplay;
    @Mock
    private CarUxRestrictionsUtil mCarUxRestrictionsUtil;
    @Mock
    private CarUxRestrictions mCarUxRestrictions;

    @Captor
    private ArgumentCaptor<CarUserManager.UserLifecycleListener> mUserLifecycleListenerCaptor;
    @Captor
    private ArgumentCaptor<KeyguardStateController.Callback> mKeyguardCallbackCaptor;
    @Captor
    private ArgumentCaptor<Event> mEventCaptor;
    @Captor
    private ArgumentCaptor<List<Event>> mEventsCaptor;
    @Captor
    private ArgumentCaptor<CarUxRestrictionsUtil.OnUxRestrictionsChangedListener>
            mUxrListenerCaptor;

    private SystemEventHandler mSystemEventHandler;
    private Executor mExecutor = Runnable::run;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_CAR_SPLITSCREEN_MULTITASKING)).thenReturn(true);
        mContext.setMockPackageManager(mPackageManager);
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_enableScalableUI, true);
        when(mFlagManager.isEnabled(Flag.ScalableUIEnabled)).thenReturn(true);
        when(mDisplayStateHelperLazy.get()).thenReturn(mDisplayStateHelper);
        when(mUserTracker.getUserId()).thenReturn(TEST_USER_ID);
        when(mUserTracker.getUserHandle()).thenReturn(UserHandle.of(TEST_USER_ID));
        when(mDisplayTracker.getAllDisplays()).thenReturn(new Display[]{mDisplay});
        when(mDisplay.getDisplayId()).thenReturn(TEST_DISPLAY_ID);
        when(mCar.getCarManager(CarUserManager.class)).thenReturn(mCarUserManager);
        when(mCarUxRestrictionsUtil.getCurrentRestrictions()).thenReturn(mCarUxRestrictions);

        mSystemEventHandler = new SystemEventHandler(
                mContext,
                mUserManager,
                mExecutor,
                mCarServiceProvider,
                mUserTracker,
                mDisplayTracker,
                mDisplayStateHelperLazy,
                mKeyguardStateController,
                mCarDeviceProvisionedController,
                mEventDispatcher,
                mFlagManager,
                mCarUxRestrictionsUtil
        );
    }

    @Test
    public void onUserUnlocked_sendsUserAuthenticatedEvent() {
        // Arrange
        when(mCarDeviceProvisionedController.isCurrentUserFullySetup()).thenReturn(true);
        mSystemEventHandler.start();
        simulateCarServiceConnection();
        verify(mCarUserManager).addListener(any(), mUserLifecycleListenerCaptor.capture());
        CarUserManager.UserLifecycleListener listener = mUserLifecycleListenerCaptor.getValue();

        // Act
        listener.onEvent(new CarUserManager.UserLifecycleEvent(
                CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED, TEST_USER_ID));

        // Assert
        verify(mEventDispatcher).executeEvent(mEventCaptor.capture());
        Event event = mEventCaptor.getValue();
        assertThat(event.getId()).isEqualTo(
                SystemEventConstants.SYSTEM_USER_AUTHENTICATED_EVENT_ID);
    }

    @Test
    public void onKeyguardHidden_sendsKeyguardHiddenAndUserAuthEvents() {
        // Arrange
        when(mUserManager.isUserUnlocked(anyInt())).thenReturn(false);
        when(mKeyguardStateController.isShowing()).thenReturn(true);
        mSystemEventHandler.start();
        simulateCarServiceConnection();
        verify(mKeyguardStateController).addCallback(mKeyguardCallbackCaptor.capture());
        KeyguardStateController.Callback callback = mKeyguardCallbackCaptor.getValue();
        when(mUserManager.isUserUnlocked(anyInt())).thenReturn(true);
        when(mKeyguardStateController.isShowing()).thenReturn(false);
        Mockito.clearInvocations(mEventDispatcher);

        // Act
        callback.onKeyguardShowingChanged();

        // Assert
        verify(mEventDispatcher).executeEvents(mEventsCaptor.capture());
        List<Event> events = mEventsCaptor.getValue();
        assertThat(events.stream().anyMatch(event -> event.getId().equals(
                SystemEventConstants.SYSTEM_USER_AUTHENTICATED_EVENT_ID))).isTrue();
        assertThat(events.stream().anyMatch(event -> event.getId().equals(
                SystemEventConstants.SYSTEM_KEYGUARD_HIDDEN_EVENT_ID))).isTrue();
    }

    @Test
    public void onKeyguardShown_sendsKeyguardShownEvent() {
        // Arrange
        mSystemEventHandler.start();
        simulateCarServiceConnection();
        verify(mKeyguardStateController).addCallback(mKeyguardCallbackCaptor.capture());
        KeyguardStateController.Callback callback = mKeyguardCallbackCaptor.getValue();
        when(mKeyguardStateController.isShowing()).thenReturn(true);
        Mockito.clearInvocations(mEventDispatcher);

        // Act
        callback.onKeyguardShowingChanged();

        // Assert
        verify(mEventDispatcher).executeEvent(mEventCaptor.capture());
        Event event = mEventCaptor.getValue();
        assertThat(event.getId()).isEqualTo(SystemEventConstants.SYSTEM_KEYGUARD_SHOWN_EVENT_ID);
    }

    @Test
    public void onUserSetupInProgress_sendsEnterSuwEvent() {
        // Arrange
        when(mCarDeviceProvisionedController.isCurrentUserSetupInProgress()).thenReturn(true);

        // Act
        mSystemEventHandler.start();
        simulateCarServiceConnection();

        // Assert
        verify(mEventDispatcher).executeEvent(mEventCaptor.capture());
        Event event = mEventCaptor.getValue();
        assertThat(event.getId()).isEqualTo(SystemEventConstants.SYSTEM_ENTER_SUW_EVENT_ID);
    }

    @Test
    public void onUserSetupComplete_sendsExitSuwEvent() {
        // Arrange
        when(mCarDeviceProvisionedController.isCurrentUserSetupInProgress()).thenReturn(true);
        mSystemEventHandler.start();
        simulateCarServiceConnection();
        ArgumentCaptor<CarDeviceProvisionedListener> listenerCaptor =
                ArgumentCaptor.forClass(CarDeviceProvisionedListener.class);
        verify(mCarDeviceProvisionedController).addCallback(listenerCaptor.capture());
        CarDeviceProvisionedListener listener = listenerCaptor.getValue();
        when(mCarDeviceProvisionedController.isCurrentUserSetupInProgress()).thenReturn(false);
        Mockito.clearInvocations(mEventDispatcher);

        // Act
        listener.onUserSetupInProgressChanged();

        // Assert
        verify(mEventDispatcher).executeEvent(mEventCaptor.capture());
        Event event = mEventCaptor.getValue();
        assertThat(event.getId()).isEqualTo(SystemEventConstants.SYSTEM_EXIT_SUW_EVENT_ID);
    }

    @Test
    public void start_uxrRestricted_sendsUxrStateChangedEvent() {
        // Arrange
        when(mCarUxRestrictions.isRequiresDistractionOptimization()).thenReturn(true);

        // Act
        mSystemEventHandler.start();

        // Assert
        verify(mEventDispatcher, atLeastOnce()).executeEvent(mEventCaptor.capture());
        List<Event> events = mEventCaptor.getAllValues().stream().filter(
                event -> event.getId().equals(
                        SystemEventConstants.SYSTEM_UXR_STATE_CHANGED_EVENT_ID)).toList();
        assertThat(events.size()).isEqualTo(1);
        assertThat(events.getFirst().getTokens().get(
                SystemEventConstants.SYSTEM_UXR_RESTRICTED_TOKEN_ID)).isEqualTo("true");
    }

    @Test
    public void onUxRestrictionsChanged_restricted_sendsUxrStateChangedEvent() {
        // Arrange
        when(mCarUxRestrictions.isRequiresDistractionOptimization()).thenReturn(false);
        mSystemEventHandler.start();
        verify(mCarUxRestrictionsUtil).register(mUxrListenerCaptor.capture());
        CarUxRestrictionsUtil.OnUxRestrictionsChangedListener listener =
                mUxrListenerCaptor.getValue();
        Mockito.clearInvocations(mEventDispatcher);

        // Act
        CarUxRestrictions newRestrictions = Mockito.mock(CarUxRestrictions.class);
        when(newRestrictions.isRequiresDistractionOptimization()).thenReturn(true);
        listener.onRestrictionsChanged(newRestrictions);

        // Assert
        verify(mEventDispatcher).executeEvent(mEventCaptor.capture());
        Event event = mEventCaptor.getValue();
        assertThat(event.getId()).isEqualTo(SystemEventConstants.SYSTEM_UXR_STATE_CHANGED_EVENT_ID);
        assertThat(event.getTokens().get(SystemEventConstants.SYSTEM_UXR_RESTRICTED_TOKEN_ID))
                .isEqualTo("true");
    }

    @Test
    public void onUxRestrictionsChanged_notRestricted_sendsUxrStateChangedEvent() {
        // Arrange
        when(mCarUxRestrictions.isRequiresDistractionOptimization()).thenReturn(true);
        mSystemEventHandler.start();
        verify(mCarUxRestrictionsUtil).register(mUxrListenerCaptor.capture());
        CarUxRestrictionsUtil.OnUxRestrictionsChangedListener listener =
                mUxrListenerCaptor.getValue();
        Mockito.clearInvocations(mEventDispatcher);

        // Act
        CarUxRestrictions newRestrictions = Mockito.mock(CarUxRestrictions.class);
        when(newRestrictions.isRequiresDistractionOptimization()).thenReturn(false);
        listener.onRestrictionsChanged(newRestrictions);

        // Assert
        verify(mEventDispatcher).executeEvent(mEventCaptor.capture());
        Event event = mEventCaptor.getValue();
        assertThat(event.getId()).isEqualTo(SystemEventConstants.SYSTEM_UXR_STATE_CHANGED_EVENT_ID);
        assertThat(event.getTokens().get(SystemEventConstants.SYSTEM_UXR_RESTRICTED_TOKEN_ID))
                .isEqualTo("false");
    }

    @Test
    public void onUxRestrictionsChanged_sameState_doesNotSendEvent() {
        // Arrange
        when(mCarUxRestrictions.isRequiresDistractionOptimization()).thenReturn(true);
        mSystemEventHandler.start();
        verify(mCarUxRestrictionsUtil).register(mUxrListenerCaptor.capture());
        CarUxRestrictionsUtil.OnUxRestrictionsChangedListener listener =
                mUxrListenerCaptor.getValue();
        Mockito.clearInvocations(mEventDispatcher);

        // Act
        CarUxRestrictions newRestrictions = Mockito.mock(CarUxRestrictions.class);
        when(newRestrictions.isRequiresDistractionOptimization()).thenReturn(true);
        listener.onRestrictionsChanged(newRestrictions);

        // Assert
        verify(mEventDispatcher, Mockito.never()).executeEvent(any(Event.class));
    }

    private void simulateCarServiceConnection() {
        ArgumentCaptor<CarServiceProvider.CarServiceOnConnectedListener> listenerCaptor =
                ArgumentCaptor.forClass(CarServiceProvider.CarServiceOnConnectedListener.class);
        verify(mCarServiceProvider).addListener(listenerCaptor.capture());
        CarServiceProvider.CarServiceOnConnectedListener listener = listenerCaptor.getValue();
        listener.onConnected(mCar);
    }
}
