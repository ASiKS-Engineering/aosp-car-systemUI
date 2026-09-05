/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.systemui.car.window.OverlayPanelViewController.OVERLAY_FROM_BOTTOM_BAR;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.res.Configuration;
import android.os.Handler;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.testing.TestableResources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.window.OverlayViewGlobalStateController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.wm.shell.animation.FlingAnimationUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.InOrderImpl;

import java.util.Collections;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class HvacPanelOverlayViewControllerTest extends CarSysuiTestCase {
    HvacPanelOverlayViewController mHvacPanelOverlayViewController;
    TestableResources mTestableResources;

    @Mock
    HvacController mHvacController;
    @Mock
    OverlayViewGlobalStateController mOverlayViewGlobalStateController;
    @Mock
    private FlingAnimationUtils.Builder mFlingAnimationUtilsBuilder;
    @Mock
    private FlingAnimationUtils mFlingAnimationUtils;
    @Mock
    CarDeviceProvisionedController mCarDeviceProvisionedController;
    @Mock
    ConfigurationController mConfigurationController;
    @Mock
    private Handler mHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mFlingAnimationUtilsBuilder.setMaxLengthSeconds(anyFloat())).thenReturn(
                mFlingAnimationUtilsBuilder);
        when(mFlingAnimationUtilsBuilder.setSpeedUpFactor(anyFloat())).thenReturn(
                mFlingAnimationUtilsBuilder);
        when(mFlingAnimationUtilsBuilder.build()).thenReturn(mFlingAnimationUtils);

        mTestableResources = getContext().getOrCreateTestableResources();
    }

    @Test
    public void onScroll_updateDim() {
        createHvacPanelOverlayViewController();
        int height = 100;
        View mockLayout = mock(View.class);
        when(mockLayout.getHeight()).thenReturn(height);
        mHvacPanelOverlayViewController.setLayout(mockLayout);
        mHvacPanelOverlayViewController.setOverlayDirection(OVERLAY_FROM_BOTTOM_BAR);

        mHvacPanelOverlayViewController.onScroll(50);

        verify(mOverlayViewGlobalStateController).updateWindowDimBehind(
                eq(mHvacPanelOverlayViewController), anyFloat());
    }

    @Test
    public void onAnimateExpandPanel_noTimeout_timeoutNotSet() {
        mTestableResources.addOverride(
                com.android.systemui.car.hvac.ui.R.integer.config_hvacAutoDismissDurationMs, 0);
        createHvacPanelOverlayViewController();
        View mockLayout = mock(View.class);
        mHvacPanelOverlayViewController.setLayout(mockLayout);

        mHvacPanelOverlayViewController.onAnimateExpandPanel();

        verify(mHandler, never()).postDelayed(any(), anyLong());
    }

    @Test
    public void onAnimateExpandPanel_timeoutSet() {
        mTestableResources.addOverride(
                com.android.systemui.car.hvac.ui.R.integer.config_hvacAutoDismissDurationMs, 1000);
        createHvacPanelOverlayViewController();
        View mockLayout = mock(View.class);
        mHvacPanelOverlayViewController.setLayout(mockLayout);

        mHvacPanelOverlayViewController.onAnimateExpandPanel();

        verify(mHandler).postDelayed(any(), anyLong());
    }

    @Test
    public void onAnimateCollapsePanel_timeoutCancelled() {
        mTestableResources.addOverride(
                com.android.systemui.car.hvac.ui.R.integer.config_hvacAutoDismissDurationMs, 1000);
        createHvacPanelOverlayViewController();
        View mockLayout = mock(View.class);
        mHvacPanelOverlayViewController.setLayout(mockLayout);

        mHvacPanelOverlayViewController.onAnimateCollapsePanel();

        verify(mHandler).removeCallbacks(any());
    }

    @Test
    public void onConfigChanged_oldHVACViewRemoved_newHVACViewAdded() {
        createHvacPanelOverlayViewController();
        Configuration config = new Configuration();
        config.uiMode = Configuration.UI_MODE_NIGHT_YES;
        int mockIndex = 3;
        View mockLayout = mock(View.class);
        HvacPanelView mockHvacPanelView = mock(HvacPanelView.class);
        ViewGroup mockHvacPanelParentView = mock(ViewGroup.class);
        when(mockHvacPanelParentView.indexOfChild(mockHvacPanelView)).thenReturn(mockIndex);
        when(mockHvacPanelParentView.generateLayoutParams(any())).thenReturn(
                mock(ViewGroup.LayoutParams.class));
        when(mockHvacPanelParentView.generateLayoutParams(any(), any())).thenReturn(
                mock(ViewGroup.LayoutParams.class));
        when(mockHvacPanelView.getParent()).thenReturn(mockHvacPanelParentView);
        when(mockHvacPanelView.getLayoutParams()).thenReturn(mock(ViewGroup.LayoutParams.class));
        when(mockHvacPanelView.findViewById(
                com.android.systemui.car.hvac.ui.R.id.hvac_temperature_text)).thenReturn(
                mock(TextView.class));
        when(mockLayout.findViewById(com.android.systemui.car.hvac.ui.R.id.hvac_panel)).thenReturn(
                mockHvacPanelView);
        mHvacPanelOverlayViewController.setLayout(mockLayout);

        mHvacPanelOverlayViewController.onConfigChanged(config);

        InOrder inOrder = new InOrderImpl(Collections.singletonList(mockHvacPanelParentView));
        inOrder.verify(mockHvacPanelParentView).removeView(mockHvacPanelView);
        inOrder.verify(mockHvacPanelParentView).addView(
                argThat(view -> view.hashCode() != mockHvacPanelView.hashCode()),
                eq(mockIndex));
    }

    private void createHvacPanelOverlayViewController() {
        mHvacPanelOverlayViewController = new HvacPanelOverlayViewController(
                mContext, mTestableResources.getResources(), mHandler,
                mHvacController, mOverlayViewGlobalStateController, mFlingAnimationUtilsBuilder,
                mCarDeviceProvisionedController, mConfigurationController);
    }
}
