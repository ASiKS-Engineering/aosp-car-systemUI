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

package com.android.systemui.car.wm;

import static android.car.VehicleAreaSeat.SEAT_UNKNOWN;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.hardware.display.DisplayManager;
import android.os.UserHandle;
import android.testing.TestableLooper;
import android.view.Display;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarSystemUiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper
@SmallTest
public class CarWMUserHelperTest extends CarSysuiTestCase {
    private static final int TEST_DRIVER_USER_ID = 800;
    private static final int TEST_DRIVER_DISPLAY_ID = 100;
    private static final int TEST_PASSENGER_USER_ID = 1000;
    private static final int TEST_PASSENGER_DISPLAY_ID = 101;

    @Mock
    private DisplayManager mDisplayManager;
    @Mock
    private CarServiceProvider mCarServiceProvider;
    @Mock
    private Car mCar;
    @Mock
    private CarOccupantZoneManager mCarOccupantZoneManager;

    private CarWMUserHelper mUserHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mCarOccupantZoneManager.getUserForDisplayId(TEST_PASSENGER_DISPLAY_ID)).thenReturn(
                TEST_PASSENGER_USER_ID);
        Display display1 = mock(Display.class);
        Display display2 = mock(Display.class);
        when(display1.getDisplayId()).thenReturn(TEST_DRIVER_DISPLAY_ID);
        when(display2.getDisplayId()).thenReturn(TEST_PASSENGER_DISPLAY_ID);
        when(mDisplayManager.getDisplays()).thenReturn(new Display[]{display1, display2});
    }

    @Test
    public void onInit_nonMumdSystem_noListener() {
        createUserHelper(/* isMUMDSystem= */ false);

        verify(mCarServiceProvider, never()).addListener(any());
    }

    @Test
    public void onInit_mumdSystem_addsListener() {
        createUserHelper(/* isMUMDSystem= */ true);

        verify(mCarServiceProvider).addListener(any());
    }

    @Test
    public void getUserIdForDisplay_nonMumd_returnsForegroundUser() {
        createUserHelper(/* isMUMDSystem= */ false);

        int userId = mUserHelper.getUserIdForDisplay(TEST_PASSENGER_DISPLAY_ID);

        assertThat(userId).isEqualTo(ActivityManager.getCurrentUser());
    }

    @Test
    public void getUserIdForDisplay_mumd_returnsMappedUser() {
        createUserHelper(/* isMUMDSystem= */ true);
        setupAndVerifyCarConnection();

        int userId = mUserHelper.getUserIdForDisplay(TEST_PASSENGER_DISPLAY_ID);

        assertThat(userId).isEqualTo(TEST_PASSENGER_USER_ID);
    }

    @Test
    public void getDisplayIdsForUser_nonMumd_returnsAllDisplays() {
        createUserHelper(/* isMUMDSystem= */ false);

        List<Integer> displayIds = mUserHelper.getDisplayIdsForUser(TEST_DRIVER_USER_ID);

        assertThat(displayIds).containsExactly(TEST_DRIVER_DISPLAY_ID, TEST_PASSENGER_DISPLAY_ID);
    }

    @Test
    public void getDisplayIdsForUser_mumd_returnsDisplaysForUser() {
        createUserHelper(/* isMUMDSystem= */ true);
        setupAndVerifyCarConnection();
        Display display = mock(Display.class);
        when(display.getDisplayId()).thenReturn(TEST_PASSENGER_DISPLAY_ID);
        CarOccupantZoneManager.OccupantZoneInfo info = new CarOccupantZoneManager.OccupantZoneInfo(
                0, CarOccupantZoneManager.OCCUPANT_TYPE_FRONT_PASSENGER, SEAT_UNKNOWN);
        when(mCarOccupantZoneManager.getOccupantZoneForUser(
                UserHandle.of(TEST_PASSENGER_USER_ID))).thenReturn(info);
        when(mCarOccupantZoneManager.getAllDisplaysForOccupant(info))
                .thenReturn(Collections.singletonList(display));

        List<Integer> displayIds = mUserHelper.getDisplayIdsForUser(TEST_PASSENGER_USER_ID);

        assertThat(displayIds).containsExactly(TEST_PASSENGER_DISPLAY_ID);
    }

    @Test
    public void addOccupantZoneChangeListener_firstListener_registers() {
        createUserHelper(/* isMUMDSystem= */ true);
        setupAndVerifyCarConnection();

        mUserHelper.addOccupantZoneChangeListener(() -> {});

        verify(mCarOccupantZoneManager).registerOccupantZoneConfigChangeListener(any());

        mUserHelper.addOccupantZoneChangeListener(() -> {});
        // does not register again
        verify(mCarOccupantZoneManager, times(1)).registerOccupantZoneConfigChangeListener(any());
    }

    @Test
    public void removeOccupantZoneChangeListener_lastListener_unregisters() {
        createUserHelper(/* isMUMDSystem= */ true);
        setupAndVerifyCarConnection();
        CarWMUserHelper.OccupantZoneChangeListener listener1 = () -> {};
        CarWMUserHelper.OccupantZoneChangeListener listener2 = () -> {};
        mUserHelper.addOccupantZoneChangeListener(listener1);
        mUserHelper.addOccupantZoneChangeListener(listener2);

        mUserHelper.removeOccupantZoneChangeListener(listener1);
        verify(mCarOccupantZoneManager, never()).unregisterOccupantZoneConfigChangeListener(any());

        mUserHelper.removeOccupantZoneChangeListener(listener2);
        verify(mCarOccupantZoneManager).unregisterOccupantZoneConfigChangeListener(any());
    }

    private void createUserHelper(boolean isMUMDSystem) {
        mUserHelper = new CarWMUserHelper(mCarServiceProvider, mDisplayManager, isMUMDSystem);
    }

    private void setupAndVerifyCarConnection() {
        ArgumentCaptor<CarServiceProvider.CarServiceOnConnectedListener> captor =
                ArgumentCaptor.forClass(CarServiceProvider.CarServiceOnConnectedListener.class);
        verify(mCarServiceProvider).addListener(captor.capture());
        assertThat(captor.getValue()).isNotNull();
        when(mCar.getCarManager(CarOccupantZoneManager.class)).thenReturn(mCarOccupantZoneManager);
        captor.getValue().onConnected(mCar);
    }
}
