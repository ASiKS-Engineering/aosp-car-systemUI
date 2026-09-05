/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.car.hvac;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserHandle;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.systembar.base.CarSystemBarController;
import com.android.systemui.settings.UserTracker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class HvacPanelOverlayViewMediatorTest extends CarSysuiTestCase {
    private static final String SYSTEM_BAR_NAME_1 = "SYSTEM_BAR_NAME_1";
    private static final String SYSTEM_BAR_NAME_2 = "SYSTEM_BAR_NAME_2";

    private HvacPanelOverlayViewMediator mHvacPanelOverlayViewMediator;

    @Mock
    private Context mContext;
    @Mock
    private CarSystemBarController mCarSystemBarController;
    @Mock
    private HvacPanelOverlayViewController mHvacPanelOverlayViewController;
    @Mock
    private BroadcastDispatcher mBroadcastDispatcher;
    @Mock
    private UserTracker mUserTracker;
    @Mock
    private UserHandle mUserHandle;
    @Mock
    private View.OnTouchListener mOnTouchListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mHvacPanelOverlayViewController.getDragCloseTouchListener())
                .thenReturn(mOnTouchListener);
        when(mUserTracker.getUserHandle()).thenReturn(mUserHandle);

        mHvacPanelOverlayViewMediator = new HvacPanelOverlayViewMediator(
                mContext,
                mCarSystemBarController,
                mHvacPanelOverlayViewController,
                mBroadcastDispatcher,
                mUserTracker,
                Arrays.asList(SYSTEM_BAR_NAME_1, SYSTEM_BAR_NAME_2));
    }

    @Test
    public void registerListeners_touchListenersRegistered() {
        mHvacPanelOverlayViewMediator.registerListeners();

        verify(mCarSystemBarController)
                .registerBarTouchListener(eq(SYSTEM_BAR_NAME_1), eq(mOnTouchListener));
        verify(mCarSystemBarController)
                .registerBarTouchListener(eq(SYSTEM_BAR_NAME_2), eq(mOnTouchListener));
    }

    @Test
    public void closeSystemDialogsIntent_hvacPanelIsExpanded_togglesHvacPanel() {
        when(mHvacPanelOverlayViewController.isPanelExpanded()).thenReturn(true);
        Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);

        mHvacPanelOverlayViewMediator.mBroadcastReceiver.onReceive(getContext(), intent);

        verify(mHvacPanelOverlayViewController).toggle();
    }

    @Test
    public void closeSystemDialogsIntent_hvacPanelIsNotExpanded_doesNotToggleHvacPanel() {
        when(mHvacPanelOverlayViewController.isPanelExpanded()).thenReturn(false);
        Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);

        mHvacPanelOverlayViewMediator.mBroadcastReceiver.onReceive(getContext(), intent);

        verify(mHvacPanelOverlayViewController, never()).toggle();
    }

    @Test
    public void notCloseSystemDialogsIntent_hvacPanelIsExpanded_doesNotToggleHvacPanel() {
        when(mHvacPanelOverlayViewController.isPanelExpanded()).thenReturn(true);
        Intent intent = new Intent();

        mHvacPanelOverlayViewMediator.mBroadcastReceiver.onReceive(getContext(), intent);

        verify(mHvacPanelOverlayViewController, never()).toggle();
    }

    @Test
    public void onUserChanged_unregistersAndRegistersBroadcastReceiver() {
        // Capture the callback added to UserTracker during listener registration.
        ArgumentCaptor<UserTracker.Callback> callbackCaptor =
                ArgumentCaptor.forClass(UserTracker.Callback.class);
        mHvacPanelOverlayViewMediator.registerListeners();
        verify(mUserTracker).addCallback(callbackCaptor.capture(), any());

        callbackCaptor.getValue().onUserChanged(/* newUser= */ 1, mContext);

        // Verify that the broadcast receiver is unregistered and then re-registered in order.
        InOrder inOrder = inOrder(mBroadcastDispatcher);
        inOrder.verify(mBroadcastDispatcher).unregisterReceiver(
                mHvacPanelOverlayViewMediator.mBroadcastReceiver);
        inOrder.verify(mBroadcastDispatcher).registerReceiver(
                eq(mHvacPanelOverlayViewMediator.mBroadcastReceiver),
                any(IntentFilter.class),
                isNull(),
                eq(mUserHandle));
    }
}
