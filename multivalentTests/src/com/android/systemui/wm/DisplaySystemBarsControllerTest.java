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

package com.android.systemui.wm;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.settings.CarSettings;
import android.content.ComponentName;
import android.os.Handler;
import android.os.UserManager;
import android.provider.Settings;
import android.testing.TestableLooper;
import android.view.IWindowManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.wm.CarWMUserHelper;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.car.wm.scalableui.systemwindow.SystemUiWindowProvider;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.sysui.ShellController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper
@SmallTest
public class DisplaySystemBarsControllerTest extends CarSysuiTestCase {

    private static final int DISPLAY_ID = 1;

    private DisplaySystemBarsController mController;

    @Mock
    private UserManager mUserManager;
    @Mock
    private CarServiceProvider mCarServiceProvider;
    @Mock
    private IWindowManager mIWindowManager;
    @Mock
    private DisplayController mDisplayController;
    @Mock
    private DisplayLayout mDisplayLayout;
    @Mock
    private DisplayInsetsController mDisplayInsetsController;
    @Mock
    private Handler mHandler;
    @Mock
    private CarWMUserHelper mUserHelper;
    @Mock
    private ShellController mShellController;
    @Mock
    private SystemUiWindowProvider mWindowProvider;
    @Mock
    private EventDispatcher mEventDispatcher;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mDisplayLayout.rotation()).thenReturn(0);
        when(mDisplayController.getDisplayLayout(anyInt())).thenReturn(mDisplayLayout);

        mController = new DisplaySystemBarsController(
                mContext,
                mUserManager,
                mCarServiceProvider,
                mIWindowManager,
                mDisplayController,
                mDisplayInsetsController,
                mHandler,
                mUserHelper,
                mShellController,
                mWindowProvider,
                mEventDispatcher
        );

        verify(mWindowProvider).addReadinessListener(mController);
    }

    @Test
    public void onDisplayAdded_loadsBarControlPolicyFilters() {
        String text = "immersive.full=+sample.app";
        Settings.Global.putString(
                mContext.getContentResolver(),
                CarSettings.Global.SYSTEM_BAR_VISIBILITY_OVERRIDE,
                text
        );

        mController.onDisplayAdded(DISPLAY_ID);

        assertThat(mController.getBarPolicyString()).isEqualTo(text);
    }

    @Test
    public void onReady_callsGetSystemBarWindows() {
        mController.onDisplayAdded(DISPLAY_ID);
        mController.onReady();

        verify(mWindowProvider, times(2)).getSystemBarWindows();
    }

    @Test
    public void onReady_updatesDisplayWindowRequestedVisibleTypes() throws Exception {
        mController.onDisplayAdded(DISPLAY_ID);

        // Capture the listener to simulate interaction
        ArgumentCaptor<DisplayInsetsController.OnInsetsChangedListener> listenerCaptor =
                ArgumentCaptor.forClass(DisplayInsetsController.OnInsetsChangedListener.class);
        verify(mDisplayInsetsController)
                .addInsetsChangedListener(anyInt(), listenerCaptor.capture());
        DisplayInsetsController.OnInsetsChangedListener listener = listenerCaptor.getValue();

        // Set package name to ensure update happens
        ComponentName component = new ComponentName("com.example", "MainActivity");
        listener.topFocusedWindowChanged(component, 0);

        mController.onReady();

        // Verify that the WM service is called to update visibility
        verify(mIWindowManager, times(2)).updateDisplayWindowRequestedVisibleTypes(
                anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    public void onReady_noPackageName_updatesVisibility() throws Exception {
        mController.onDisplayAdded(DISPLAY_ID);

        // onReady should trigger an update even if topFocusedWindowChanged was never called
        // (mPackageName is null)
        mController.onReady();

        // Verify that the WM service is called
        verify(mIWindowManager).updateDisplayWindowRequestedVisibleTypes(
                anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    public void topFocusedWindowChanged_differentArgs_updatesTwice() throws Exception {
        mController.onDisplayAdded(DISPLAY_ID);

        ArgumentCaptor<DisplayInsetsController.OnInsetsChangedListener> listenerCaptor =
                ArgumentCaptor.forClass(DisplayInsetsController.OnInsetsChangedListener.class);
        verify(mDisplayInsetsController)
                .addInsetsChangedListener(anyInt(), listenerCaptor.capture());
        DisplayInsetsController.OnInsetsChangedListener listener = listenerCaptor.getValue();

        ComponentName component = new ComponentName("com.example", "MainActivity");

        // First call
        listener.topFocusedWindowChanged(component, 1);
        // Second call with different requested bits.
        // In non-immersive mode, the final system bar visibility bits will be the same
        // (default), so force=true is required to trigger the second update.
        listener.topFocusedWindowChanged(component, 2);

        // Should be called twice due to force = true
        verify(mIWindowManager, times(2))
                .updateDisplayWindowRequestedVisibleTypes(anyInt(), anyInt(), anyInt(), any());
    }
}
