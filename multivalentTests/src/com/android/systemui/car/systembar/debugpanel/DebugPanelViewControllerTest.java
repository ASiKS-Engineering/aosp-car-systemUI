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

package com.android.systemui.car.systembar.debugpanel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;
import android.os.Handler;
import android.testing.TestableLooper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.systembar.panel.CarSystemBarPanelButtonView;
import com.android.systemui.car.systembar.panel.PanelViewController;
import com.android.systemui.util.settings.GlobalSettings;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.inject.Provider;

// TODO(b/370766893): add more tests for various situations after finding ways to mock static
//  functions.
@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper
@SmallTest
public class DebugPanelViewControllerTest extends CarSysuiTestCase {
    @Mock
    private CarSystemBarPanelButtonView mView;
    @Mock
    private CarSystemBarElementStatusBarDisableController mDisableController;
    @Mock
    private CarSystemBarElementStateController mStateController;
    @Mock
    private Provider<PanelViewController.Factory> mStatusIconPanelFactoryProvider;
    @Mock
    private Handler mMainHandler;
    @Mock
    private GlobalSettings mGlobalSettings;

    private DebugPanelViewController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mView.getContext()).thenReturn(mContext);
        mController = new DebugPanelViewController(mView, mDisableController,
                mStateController, mStatusIconPanelFactoryProvider, mMainHandler, mGlobalSettings);
        mController.onViewAttached();
    }

    @Test
    public void onViewAttached_registerContentObserver() {
        verify(mGlobalSettings).registerContentObserverAsync((Uri) any(), any());
    }

    @Test
    public void onViewDetached_unregistersListeners() {
        mController.onViewDetached();

        verify(mGlobalSettings).unregisterContentObserverAsync(any());
    }
}
