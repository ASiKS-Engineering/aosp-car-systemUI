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
package com.android.systemui.car.wm.scalableui.systemwindow;

import static com.android.systemui.car.wm.scalableui.systemwindow.SystemBarWindowKt.HUN_Z_ORDER;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.car.wm.scalableui.configuration.SystemBarConfiguration;
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@SmallTest
public class SystemBarWindowImplTest extends CarSysuiTestCase {

    private static final String TEST_PANEL_ID = "test";
    private static final int TEST_DISPLAY_ID = 1;
    private static final int DISPLAY_WIDTH = 100;
    private static final int DISPLAY_HEIGHT = 100;

    @Rule
    public MockitoRule mRule = MockitoJUnit.rule();

    private SystemBarWindowImpl mSystemBarWindow;

    @Mock
    private Context mContext;
    @Mock
    private Display mDisplay;
    @Mock
    private Resources mResources;
    @Mock
    private WindowManager mWindowManager;
    @Mock
    private DisplayManager mDisplayManager;
    @Mock
    private EventDispatcher mEventDispatcher;
    @Mock
    private PanelUpdateConsumer mPanelUpdateConsumer;
    @Mock
    private SystemBarConfiguration mSystemBarConfiguration;

    @Before
    public void setUp() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        displayMetrics.widthPixels = DISPLAY_WIDTH;
        displayMetrics.heightPixels = DISPLAY_HEIGHT;

        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getDisplayMetrics()).thenReturn(displayMetrics);
        when(mContext.getDisplay()).thenReturn(mDisplay);
        when(mContext.createDisplayContext(mDisplay)).thenReturn(mContext);
        when(mContext.getSystemService(DisplayManager.class)).thenReturn(mDisplayManager);
        when(mContext.getSystemService(WindowManager.class)).thenReturn(mWindowManager);
        when(mDisplayManager.getDisplay(TEST_DISPLAY_ID)).thenReturn(mDisplay);
        when(mSystemBarConfiguration.getName()).thenReturn(TEST_PANEL_ID);
        when(mPanelUpdateConsumer.getBounds(TEST_PANEL_ID)).thenReturn(new Rect(0, 0, 100, 100));

        mSystemBarWindow = new SystemBarWindowImpl(mContext, mDisplayManager, mEventDispatcher,
                mPanelUpdateConsumer, mSystemBarConfiguration, TEST_DISPLAY_ID);
    }

    @Test
    public void getLayoutParams_zOrderAboveHun_returnsNavBarPanelType() {
        when(mSystemBarConfiguration.getZOrder()).thenReturn(HUN_Z_ORDER);

        WindowManager.LayoutParams params = mSystemBarWindow.getLayoutParams();

        assertThat(params.type).isEqualTo(WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL);
    }

    @Test
    public void getLayoutParams_zOrderBelowHun_returnsStatusBarAdditionalType() {
        when(mSystemBarConfiguration.getZOrder()).thenReturn(HUN_Z_ORDER - 1);

        WindowManager.LayoutParams params = mSystemBarWindow.getLayoutParams();

        assertThat(params.type).isEqualTo(WindowManager.LayoutParams.TYPE_STATUS_BAR_ADDITIONAL);
    }

    @Test
    public void getLayoutParams_topBarLeftPanel_setsCorrectTitle() {
        String panelName = "top_bar_left_panel";
        when(mSystemBarConfiguration.getName()).thenReturn(panelName);
        when(mPanelUpdateConsumer.getBounds(panelName)).thenReturn(new Rect(0, 0, 100, 100));
        SystemBarWindowImpl window = new SystemBarWindowImpl(mContext, mDisplayManager,
                mEventDispatcher, mPanelUpdateConsumer, mSystemBarConfiguration, TEST_DISPLAY_ID);

        WindowManager.LayoutParams params = window.getLayoutParams();

        assertThat(params.getTitle()).isEqualTo(panelName);
    }

    @Test
    public void getLayoutParams_topBarRightPanel_setsCorrectTitle() {
        String panelName = "top_bar_right_panel";
        when(mSystemBarConfiguration.getName()).thenReturn(panelName);
        when(mPanelUpdateConsumer.getBounds(panelName)).thenReturn(new Rect(0, 0, 100, 100));
        SystemBarWindowImpl window = new SystemBarWindowImpl(mContext, mDisplayManager,
                mEventDispatcher, mPanelUpdateConsumer, mSystemBarConfiguration, TEST_DISPLAY_ID);

        WindowManager.LayoutParams params = window.getLayoutParams();

        assertThat(params.getTitle()).isEqualTo(panelName);
    }

    @Test
    public void getLayoutParams_bottomBarLeftPanel_setsCorrectTitle() {
        String panelName = "bottom_bar_left_panel";
        when(mSystemBarConfiguration.getName()).thenReturn(panelName);
        when(mPanelUpdateConsumer.getBounds(panelName)).thenReturn(new Rect(0, 0, 100, 100));
        SystemBarWindowImpl window = new SystemBarWindowImpl(mContext, mDisplayManager,
                mEventDispatcher, mPanelUpdateConsumer, mSystemBarConfiguration, TEST_DISPLAY_ID);

        WindowManager.LayoutParams params = window.getLayoutParams();

        assertThat(params.getTitle()).isEqualTo(panelName);
    }

    @Test
    public void getLayoutParams_bottomBarCenterPanel_setsCorrectTitle() {
        String panelName = "bottom_bar_center_panel";
        when(mSystemBarConfiguration.getName()).thenReturn(panelName);
        when(mPanelUpdateConsumer.getBounds(panelName)).thenReturn(new Rect(0, 0, 100, 100));
        SystemBarWindowImpl window = new SystemBarWindowImpl(mContext, mDisplayManager,
                mEventDispatcher, mPanelUpdateConsumer, mSystemBarConfiguration, TEST_DISPLAY_ID);

        WindowManager.LayoutParams params = window.getLayoutParams();

        assertThat(params.getTitle()).isEqualTo(panelName);
    }

    @Test
    public void getLayoutParams_bottomBarRightPanel_setsCorrectTitle() {
        String panelName = "bottom_bar_right_panel";
        when(mSystemBarConfiguration.getName()).thenReturn(panelName);
        when(mPanelUpdateConsumer.getBounds(panelName)).thenReturn(new Rect(0, 0, 100, 100));
        SystemBarWindowImpl window = new SystemBarWindowImpl(mContext, mDisplayManager,
                mEventDispatcher, mPanelUpdateConsumer, mSystemBarConfiguration, TEST_DISPLAY_ID);

        WindowManager.LayoutParams params = window.getLayoutParams();

        assertThat(params.getTitle()).isEqualTo(panelName);
    }

    @Test
    public void getLayoutParams_setsFitInsetsTypesToZero() {
        when(mPanelUpdateConsumer.getBounds(TEST_PANEL_ID)).thenReturn(new Rect(0, 0, 100, 100));
        WindowManager.LayoutParams params = mSystemBarWindow.getLayoutParams();

        assertThat(params.getFitInsetsTypes()).isEqualTo(0);
    }

    @Test
    public void getLayoutParams_fullWidthTopBar_providesCorrectInsets() {
        Rect bounds = new Rect(0, 0, DISPLAY_WIDTH, 20);
        when(mPanelUpdateConsumer.getBounds(TEST_PANEL_ID)).thenReturn(bounds);

        WindowManager.LayoutParams params = mSystemBarWindow.getLayoutParams();

        assertThat(params.providedInsets[0].getInsetsSize()).isEqualTo(Insets.of(0, 20, 0, 0));
    }

    @Test
    public void getLayoutParams_fullWidthBottomBar_providesCorrectInsets() {
        Rect bounds = new Rect(0, DISPLAY_HEIGHT - 20, DISPLAY_WIDTH, DISPLAY_HEIGHT);
        when(mPanelUpdateConsumer.getBounds(TEST_PANEL_ID)).thenReturn(bounds);

        WindowManager.LayoutParams params = mSystemBarWindow.getLayoutParams();

        assertThat(params.providedInsets[0].getInsetsSize()).isEqualTo(Insets.of(0, 0, 0, 20));
    }

    @Test
    public void getLayoutParams_fullHeightLeftBar_providesCorrectInsets() {
        Rect bounds = new Rect(0, 0, 20, DISPLAY_HEIGHT);
        when(mPanelUpdateConsumer.getBounds(TEST_PANEL_ID)).thenReturn(bounds);

        WindowManager.LayoutParams params = mSystemBarWindow.getLayoutParams();

        assertThat(params.providedInsets[0].getInsetsSize()).isEqualTo(Insets.of(20, 0, 0, 0));
    }

    @Test
    public void getLayoutParams_fullHeightRightBar_providesCorrectInsets() {
        Rect bounds = new Rect(DISPLAY_WIDTH - 20, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT);
        when(mPanelUpdateConsumer.getBounds(TEST_PANEL_ID)).thenReturn(bounds);

        WindowManager.LayoutParams params = mSystemBarWindow.getLayoutParams();

        assertThat(params.providedInsets[0].getInsetsSize()).isEqualTo(Insets.of(0, 0, 20, 0));
    }

    @Test
    public void getLayoutParams_partialWidthBottomBar_providesCorrectInsets() {
        Rect bounds = new Rect(10, DISPLAY_HEIGHT - 20, 90, DISPLAY_HEIGHT);
        when(mPanelUpdateConsumer.getBounds(TEST_PANEL_ID)).thenReturn(bounds);

        WindowManager.LayoutParams params = mSystemBarWindow.getLayoutParams();

        assertThat(params.providedInsets[0].getInsetsSize()).isEqualTo(Insets.of(0, 0, 0, 20));
    }

    @Test
    public void getLayoutParams_partialWidthTopBar_providesCorrectInsets() {
        Rect bounds = new Rect(10, 0, 90, 20);
        when(mPanelUpdateConsumer.getBounds(TEST_PANEL_ID)).thenReturn(bounds);

        WindowManager.LayoutParams params = mSystemBarWindow.getLayoutParams();

        assertThat(params.providedInsets[0].getInsetsSize()).isEqualTo(Insets.of(0, 20, 0, 0));
    }

    @Test
    public void getLayoutParams_partialHeightLeftBar_providesCorrectInsets() {
        Rect bounds = new Rect(0, 10, 20, 90);
        when(mPanelUpdateConsumer.getBounds(TEST_PANEL_ID)).thenReturn(bounds);

        WindowManager.LayoutParams params = mSystemBarWindow.getLayoutParams();

        assertThat(params.providedInsets[0].getInsetsSize()).isEqualTo(Insets.of(20, 0, 0, 0));
    }

    @Test
    public void getLayoutParams_partialHeightRightBar_providesCorrectInsets() {
        Rect bounds = new Rect(DISPLAY_WIDTH - 20, 10, DISPLAY_WIDTH, 90);
        when(mPanelUpdateConsumer.getBounds(TEST_PANEL_ID)).thenReturn(bounds);

        WindowManager.LayoutParams params = mSystemBarWindow.getLayoutParams();

        assertThat(params.providedInsets[0].getInsetsSize()).isEqualTo(Insets.of(0, 0, 20, 0));
    }
}