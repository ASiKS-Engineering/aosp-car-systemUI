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

package com.android.systemui.car.systembar.base;

import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import static com.android.systemui.car.systembar.SystemBarConstants.BOTTOM_BAR_NAME;
import static com.android.systemui.car.systembar.SystemBarConstants.LEFT_BAR_NAME;
import static com.android.systemui.car.systembar.SystemBarConstants.RIGHT_BAR_NAME;
import static com.android.systemui.car.systembar.SystemBarConstants.TOP_BAR_NAME;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.ArrayMap;
import android.view.WindowManager;

import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.notification.NotificationPanelViewController;
import com.android.systemui.car.notification.NotificationPanelViewMediator;
import com.android.systemui.car.notification.PowerManagerHelper;
import com.android.systemui.car.notification.TopNotificationPanelViewMediator;
import com.android.systemui.car.shared.R;
import com.android.systemui.car.wm.scalableui.systemwindow.SystemUiWindowProvider;
import com.android.systemui.settings.DisplayTracker;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.ConfigurationController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class SystemBarConfigsTest extends CarSysuiTestCase {
    private static final int SYSTEM_BAR_GIRTH = 100;

    private SystemBarConfigsImpl mSystemBarConfigs;
    @Mock
    private Resources mResources;
    @Mock
    private SystemUiWindowProvider mWindowProvider;
    @Mock
    private DisplayTracker mDisplayTracker;
    private Map<String, CarSystemBarViewSupplier> mViewSupplierMap;
    private Map<String, CarSystemBarWindowSupplier> mWindowSupplierMap;

    @Before
    public void setUp() {
        mViewSupplierMap = new HashMap<>();
        mWindowSupplierMap = new HashMap<>();
        MockitoAnnotations.initMocks(this);
        setDefaultValidConfig();
    }

    @Test
    public void onInit_allSystemBarsEnabled_eachHasUniqueBarTypes_doesNotThrowException() {
        mSystemBarConfigs =
                new SystemBarConfigsImpl(mContext, mResources, mWindowProvider, mViewSupplierMap,
                        mWindowSupplierMap, mDisplayTracker);
    }

    @Test
    public void onInit_allSystemBarsEnabled_systemBarTypesSortedByZOrder() {
        mSystemBarConfigs =
                new SystemBarConfigsImpl(mContext, mResources, mWindowProvider, mViewSupplierMap,
                        mWindowSupplierMap, mDisplayTracker);
        List<String> actualOrder = mSystemBarConfigs.getSystemBarNamesByZOrder();
        List<String> expectedOrder = new ArrayList<>();
        expectedOrder.add(LEFT_BAR_NAME);
        expectedOrder.add(RIGHT_BAR_NAME);
        expectedOrder.add(TOP_BAR_NAME);
        expectedOrder.add(BOTTOM_BAR_NAME);

        assertTrue(actualOrder.equals(expectedOrder));
    }

    @Test(expected = RuntimeException.class)
    public void onInit_intersectingBarsHaveSameZOrder_throwsRuntimeException() {
        when(mResources.getInteger(R.integer.config_topSystemBarZOrder)).thenReturn(33);
        when(mResources.getInteger(R.integer.config_leftSystemBarZOrder)).thenReturn(33);

        mSystemBarConfigs =
                new SystemBarConfigsImpl(mContext, mResources, mWindowProvider, mViewSupplierMap,
                        mWindowSupplierMap, mDisplayTracker);
    }

    @Test(expected = RuntimeException.class)
    public void onInit_hideBottomSystemBarForKeyboardValueDoNotSync_throwsRuntimeException() {
        when(mResources.getBoolean(R.bool.config_hideBottomSystemBarForKeyboard)).thenReturn(false);
        when(mResources.getBoolean(
                com.android.internal.R.bool.config_hideNavBarForKeyboard)).thenReturn(
                true);

        mSystemBarConfigs =
                new SystemBarConfigsImpl(mContext, mResources, mWindowProvider, mViewSupplierMap,
                        mWindowSupplierMap, mDisplayTracker);
    }

    @Test
    public void onInit_topNotifPanelViewMediatorUsed_topBarEnabled_doesNotThrowException() {
        when(mResources.getBoolean(R.bool.config_enableTopSystemBar)).thenReturn(true);
        when(mResources.getString(
                com.android.systemui.car.notification.R.string.config_notificationPanelViewMediator)
                        ).thenReturn(TestTopNotificationPanelViewMediator.class.getName());

        mSystemBarConfigs =
                new SystemBarConfigsImpl(mContext, mResources, mWindowProvider, mViewSupplierMap,
                        mWindowSupplierMap, mDisplayTracker);
    }

    @Test
    public void onInit_notificationPanelViewMediatorUsed_topBarNotEnabled_doesNotThrowException() {
        when(mResources.getBoolean(R.bool.config_enableTopSystemBar)).thenReturn(false);
        when(mResources.getString(
                com.android.systemui.car.notification.R.string.config_notificationPanelViewMediator)
                ).thenReturn(NotificationPanelViewMediator.class.getName());

        mSystemBarConfigs =
                new SystemBarConfigsImpl(mContext, mResources, mWindowProvider, mViewSupplierMap,
                        mWindowSupplierMap, mDisplayTracker);
    }

    @Test
    public void getTopSystemBarLayoutParams_topBarEnabled_returnsTopSystemBarLayoutParams() {
        mSystemBarConfigs =
                new SystemBarConfigsImpl(mContext, mResources, mWindowProvider, mViewSupplierMap,
                        mWindowSupplierMap, mDisplayTracker);
        WindowManager.LayoutParams lp = mSystemBarConfigs.getLayoutParamsByName(TOP_BAR_NAME);

        assertNotNull(lp);
    }

    @Test
    public void getTopSystemBarLayoutParams_containsLayoutInDisplayCutoutMode() {
        mSystemBarConfigs =
                new SystemBarConfigsImpl(mContext, mResources, mWindowProvider, mViewSupplierMap,
                        mWindowSupplierMap, mDisplayTracker);
        WindowManager.LayoutParams lp = mSystemBarConfigs.getLayoutParamsByName(TOP_BAR_NAME);

        assertNotNull(lp);
        assertEquals(lp.layoutInDisplayCutoutMode, LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS);
    }

    @Test
    public void getTopSystemBarLayoutParams_topBarNotEnabled_returnsNull() {
        when(mResources.getBoolean(R.bool.config_enableTopSystemBar)).thenReturn(false);
        mSystemBarConfigs =
                new SystemBarConfigsImpl(mContext, mResources, mWindowProvider, mViewSupplierMap,
                        mWindowSupplierMap, mDisplayTracker);
        WindowManager.LayoutParams lp = mSystemBarConfigs.getLayoutParamsByName(TOP_BAR_NAME);

        assertNull(lp);
    }

    @Test
    public void getTopSystemBarHideForKeyboard_hideBarForKeyboard_returnsTrue() {
        when(mResources.getBoolean(R.bool.config_hideTopSystemBarForKeyboard)).thenReturn(true);
        mSystemBarConfigs =
                new SystemBarConfigsImpl(mContext, mResources, mWindowProvider, mViewSupplierMap,
                        mWindowSupplierMap, mDisplayTracker);
        boolean hideKeyboard = mSystemBarConfigs.getHideForKeyboardByName(TOP_BAR_NAME);

        assertTrue(hideKeyboard);
    }

    @Test
    public void getTopSystemBarHideForKeyboard_topBarNotEnabled_returnsFalse() {
        when(mResources.getBoolean(R.bool.config_enableTopSystemBar)).thenReturn(false);
        mSystemBarConfigs =
                new SystemBarConfigsImpl(mContext, mResources, mWindowProvider, mViewSupplierMap,
                        mWindowSupplierMap, mDisplayTracker);
        boolean hideKeyboard = mSystemBarConfigs.getHideForKeyboardByName(TOP_BAR_NAME);

        assertFalse(hideKeyboard);
    }

    @Test
    public void topSystemBarHasHigherZOrderThanHuns_topSystemBarIsSystemBarPanelType() {
        when(mResources.getInteger(R.integer.config_topSystemBarZOrder)).thenReturn(
                SystemBarConfigsImpl.HUN_Z_ORDER + 1);
        mSystemBarConfigs =
                new SystemBarConfigsImpl(mContext, mResources, mWindowProvider, mViewSupplierMap,
                        mWindowSupplierMap, mDisplayTracker);
        WindowManager.LayoutParams lp = mSystemBarConfigs.getLayoutParamsByName(TOP_BAR_NAME);

        assertEquals(lp.type, WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL);
    }

    @Test
    public void topSystemBarHasLowerZOrderThanHuns_topSystemBarIsStatusBarAdditionalType() {
        when(mResources.getInteger(R.integer.config_topSystemBarZOrder)).thenReturn(
                SystemBarConfigsImpl.HUN_Z_ORDER - 1);
        mSystemBarConfigs =
                new SystemBarConfigsImpl(mContext, mResources, mWindowProvider, mViewSupplierMap,
                        mWindowSupplierMap, mDisplayTracker);
        WindowManager.LayoutParams lp = mSystemBarConfigs.getLayoutParamsByName(TOP_BAR_NAME);

        assertEquals(lp.type, WindowManager.LayoutParams.TYPE_STATUS_BAR_ADDITIONAL);
    }

    @Test
    public void updateInsetPaddings_overlappingBarWithHigherZOrderDisappeared_removesInset() {
        mSystemBarConfigs =
                new SystemBarConfigsImpl(mContext, mResources, mWindowProvider, mViewSupplierMap,
                        mWindowSupplierMap, mDisplayTracker);
        CarSystemBarView leftBar = new CarSystemBarView(mContext, /* attrs= */ null);
        Map<String, Boolean> visibilities = new ArrayMap<>();
        visibilities.put(TOP_BAR_NAME, false);
        visibilities.put(BOTTOM_BAR_NAME, true);
        visibilities.put(LEFT_BAR_NAME, true);
        visibilities.put(RIGHT_BAR_NAME, true);

        mSystemBarConfigs.updateInsetPaddings(LEFT_BAR_NAME, visibilities);
        mSystemBarConfigs.insetSystemBar(LEFT_BAR_NAME, leftBar);

        assertEquals(0, leftBar.getPaddingTop());
    }

    @Test
    public void updateInsetPaddings_overlappingBarWithHigherZOrderReappeared_addsInset() {
        mSystemBarConfigs =
                new SystemBarConfigsImpl(mContext, mResources, mWindowProvider, mViewSupplierMap,
                        mWindowSupplierMap, mDisplayTracker);
        CarSystemBarView leftBar = new CarSystemBarView(mContext, /* attrs= */ null);
        Map<String, Boolean> visibilities = new ArrayMap<>();
        visibilities.put(TOP_BAR_NAME, false);
        visibilities.put(BOTTOM_BAR_NAME, true);
        visibilities.put(LEFT_BAR_NAME, true);
        visibilities.put(RIGHT_BAR_NAME, true);

        mSystemBarConfigs.updateInsetPaddings(LEFT_BAR_NAME, visibilities);
        mSystemBarConfigs.insetSystemBar(LEFT_BAR_NAME, leftBar);
        visibilities.put(TOP_BAR_NAME, true);
        mSystemBarConfigs.updateInsetPaddings(LEFT_BAR_NAME, visibilities);
        mSystemBarConfigs.insetSystemBar(LEFT_BAR_NAME, leftBar);

        assertEquals(SYSTEM_BAR_GIRTH, leftBar.getPaddingTop());
    }

    @Test
    public void updateInsetPaddings_overlappingBars_addsPaddingsByOrders() {
        int horizontalBarHorizontalPadding = 150;
        int verticalBarVerticalPadding = 200;

        when(mResources.getDimensionPixelSize(R.dimen.car_top_system_bar_left_padding))
                .thenReturn(horizontalBarHorizontalPadding);
        when(mResources.getDimensionPixelSize(R.dimen.car_top_system_bar_right_padding))
                .thenReturn(horizontalBarHorizontalPadding);
        when(mResources.getDimensionPixelSize(R.dimen.car_bottom_system_bar_left_padding))
                .thenReturn(horizontalBarHorizontalPadding);
        when(mResources.getDimensionPixelSize(R.dimen.car_bottom_system_bar_right_padding))
                .thenReturn(horizontalBarHorizontalPadding);

        when(mResources.getDimensionPixelSize(R.dimen.car_left_system_bar_top_padding))
                .thenReturn(verticalBarVerticalPadding);
        when(mResources.getDimensionPixelSize(R.dimen.car_left_system_bar_bottom_padding))
                .thenReturn(verticalBarVerticalPadding);
        when(mResources.getDimensionPixelSize(R.dimen.car_right_system_bar_top_padding))
                .thenReturn(verticalBarVerticalPadding);
        when(mResources.getDimensionPixelSize(R.dimen.car_right_system_bar_bottom_padding))
                .thenReturn(verticalBarVerticalPadding);

        when(mResources.getDimensionPixelSize(
                R.dimen.car_left_system_bar_width)).thenReturn(SYSTEM_BAR_GIRTH);
        when(mResources.getDimensionPixelSize(
                R.dimen.car_right_system_bar_width)).thenReturn(SYSTEM_BAR_GIRTH);

        when(mResources.getDimensionPixelSize(
                R.dimen.car_top_system_bar_height)).thenReturn(SYSTEM_BAR_GIRTH);
        when(mResources.getDimensionPixelSize(
                R.dimen.car_bottom_system_bar_height)).thenReturn(SYSTEM_BAR_GIRTH);

        when(mResources.getBoolean(R.bool.config_enableTopSystemBar)).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_enableBottomSystemBar)).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_enableLeftSystemBar)).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_enableRightSystemBar)).thenReturn(true);

        when(mResources.getInteger(R.integer.config_topSystemBarZOrder)).thenReturn(7);
        when(mResources.getInteger(R.integer.config_bottomSystemBarZOrder)).thenReturn(10);
        when(mResources.getInteger(R.integer.config_leftSystemBarZOrder)).thenReturn(8);
        when(mResources.getInteger(R.integer.config_rightSystemBarZOrder)).thenReturn(6);

        mSystemBarConfigs =
                new SystemBarConfigsImpl(mContext, mResources, mWindowProvider, mViewSupplierMap,
                        mWindowSupplierMap, mDisplayTracker);
        CarSystemBarView topBar = new CarSystemBarView(mContext, /* attrs= */ null);
        CarSystemBarView bottomBar = new CarSystemBarView(mContext, /* attrs= */ null);
        CarSystemBarView leftBar = new CarSystemBarView(mContext, /* attrs= */ null);
        CarSystemBarView rightBar = new CarSystemBarView(mContext, /* attrs= */ null);

        Map<String, Boolean> visibilities = new ArrayMap<>();
        visibilities.put(TOP_BAR_NAME, true);
        visibilities.put(BOTTOM_BAR_NAME, true);
        visibilities.put(LEFT_BAR_NAME, true);
        visibilities.put(RIGHT_BAR_NAME, true);

        mSystemBarConfigs.updateInsetPaddings(TOP_BAR_NAME, visibilities);
        mSystemBarConfigs.insetSystemBar(TOP_BAR_NAME, topBar);
        mSystemBarConfigs.updateInsetPaddings(BOTTOM_BAR_NAME, visibilities);
        mSystemBarConfigs.insetSystemBar(BOTTOM_BAR_NAME, bottomBar);
        mSystemBarConfigs.updateInsetPaddings(LEFT_BAR_NAME, visibilities);
        mSystemBarConfigs.insetSystemBar(LEFT_BAR_NAME, leftBar);
        mSystemBarConfigs.updateInsetPaddings(RIGHT_BAR_NAME, visibilities);
        mSystemBarConfigs.insetSystemBar(RIGHT_BAR_NAME, rightBar);

        assertEquals(horizontalBarHorizontalPadding, bottomBar.getPaddingLeft());
        assertEquals(horizontalBarHorizontalPadding, bottomBar.getPaddingRight());
        assertEquals(horizontalBarHorizontalPadding, topBar.getPaddingRight());
        assertEquals(SYSTEM_BAR_GIRTH, topBar.getPaddingLeft());
        assertEquals(verticalBarVerticalPadding, leftBar.getPaddingTop());
        assertEquals(SYSTEM_BAR_GIRTH, leftBar.getPaddingBottom());
        assertEquals(SYSTEM_BAR_GIRTH, rightBar.getPaddingTop());
        assertEquals(SYSTEM_BAR_GIRTH, rightBar.getPaddingBottom());
    }

    // Set valid config where all system bars are enabled.
    private void setDefaultValidConfig() {
        when(mResources.getBoolean(R.bool.config_enableTopSystemBar)).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_enableBottomSystemBar)).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_enableLeftSystemBar)).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_enableRightSystemBar)).thenReturn(true);

        when(mResources.getDimensionPixelSize(
                R.dimen.car_top_system_bar_height)).thenReturn(SYSTEM_BAR_GIRTH);
        when(mResources.getDimensionPixelSize(
                R.dimen.car_bottom_system_bar_height)).thenReturn(SYSTEM_BAR_GIRTH);
        when(mResources.getDimensionPixelSize(R.dimen.car_left_system_bar_width)).thenReturn(
                SYSTEM_BAR_GIRTH);
        when(mResources.getDimensionPixelSize(R.dimen.car_right_system_bar_width)).thenReturn(
                SYSTEM_BAR_GIRTH);

        when(mResources.getInteger(R.integer.config_topSystemBarType)).thenReturn(0);
        when(mResources.getInteger(R.integer.config_bottomSystemBarType)).thenReturn(1);
        when(mResources.getInteger(R.integer.config_leftSystemBarType)).thenReturn(2);
        when(mResources.getInteger(R.integer.config_rightSystemBarType)).thenReturn(3);

        when(mResources.getInteger(R.integer.config_topSystemBarZOrder)).thenReturn(5);
        when(mResources.getInteger(R.integer.config_bottomSystemBarZOrder)).thenReturn(10);
        when(mResources.getInteger(R.integer.config_leftSystemBarZOrder)).thenReturn(2);
        when(mResources.getInteger(R.integer.config_rightSystemBarZOrder)).thenReturn(3);

        when(mResources.getBoolean(R.bool.config_hideTopSystemBarForKeyboard)).thenReturn(false);
        when(mResources.getBoolean(
                com.android.internal.R.bool.config_hideNavBarForKeyboard)).thenReturn(
                false);
        when(mResources.getBoolean(R.bool.config_hideLeftSystemBarForKeyboard)).thenReturn(
                false);
        when(mResources.getBoolean(R.bool.config_hideRightSystemBarForKeyboard)).thenReturn(
                false);
    }

    // Intentionally using a subclass of TopNotificationPanelViewMediator for testing purposes to
    // ensure that OEM's will be able to implement and use their own NotificationPanelViewMediator.
    private class TestTopNotificationPanelViewMediator extends
            TopNotificationPanelViewMediator {
        TestTopNotificationPanelViewMediator(
                Context context,
                CarSystemBarController carSystemBarController,
                NotificationPanelViewController notificationPanelViewController,
                PowerManagerHelper powerManagerHelper,
                BroadcastDispatcher broadcastDispatcher,
                UserTracker userTracker,
                ConfigurationController configurationController) {
            super(context, carSystemBarController, notificationPanelViewController,
                    powerManagerHelper, broadcastDispatcher, userTracker,
                    configurationController, new ArrayList<>(), new ArrayList<>());
        }
    }
}
