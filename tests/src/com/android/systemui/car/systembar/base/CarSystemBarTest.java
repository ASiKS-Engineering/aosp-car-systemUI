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

import static android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
import static android.view.WindowInsetsController.APPEARANCE_OPAQUE_STATUS_BARS;
import static android.view.WindowInsetsController.BEHAVIOR_DEFAULT;

import static com.android.systemui.car.systembar.SystemBarConstants.BOTTOM_BAR_NAME;
import static com.android.systemui.car.systembar.SystemBarConstants.LEFT_BAR_NAME;
import static com.android.systemui.car.systembar.SystemBarConstants.RIGHT_BAR_NAME;
import static com.android.systemui.car.systembar.SystemBarConstants.TOP_BAR_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.graphics.Rect;
import android.os.Handler;
import android.os.RemoteException;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.testing.TestableResources;
import android.util.ArrayMap;
import android.view.Display;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.test.filters.SmallTest;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.RegisterStatusBarResult;
import com.android.internal.view.AppearanceRegion;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.SysuiTestableContext;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.shared.R;
import com.android.systemui.car.wm.scalableui.systemwindow.SystemUiWindowProvider;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.settings.DisplayTracker;
import com.android.systemui.settings.FakeDisplayTracker;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.phone.AutoHideController;
import com.android.systemui.statusbar.phone.LightBarController;
import com.android.systemui.statusbar.phone.LightBarTransitionsController;
import com.android.systemui.statusbar.phone.PhoneStatusBarPolicy;
import com.android.systemui.statusbar.phone.SysuiDarkIconDispatcher;
import com.android.systemui.statusbar.phone.ui.StatusBarIconController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.concurrency.FakeExecutor;
import com.android.systemui.util.time.FakeSystemClock;
import com.android.systemui.utils.os.FakeHandler;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

/**
 * TODO(b/362280147): move related tests to CarSystemBarControllerTest.
 */
@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class CarSystemBarTest extends CarSysuiTestCase {

    private TestableResources mTestableResources;
    private SysuiTestableContext mSpiedContext;
    private FakeExecutor mExecutor;
    private CarSystemBarControllerImpl mCarSystemBarController;

    @Mock
    private UserTracker mUserTracker;
    @Mock
    private ActivityManager mActivityManager;
    @Mock
    private LightBarController mLightBarController;
    @Mock
    private SysuiDarkIconDispatcher mStatusBarIconController;
    @Mock
    private LightBarTransitionsController mLightBarTransitionsController;
    @Mock
    private WindowManager mWindowManager;
    @Mock
    private CarDeviceProvisionedController mDeviceProvisionedController;
    @Mock
    private AutoHideController mAutoHideController;
    @Mock
    private ButtonSelectionStateListener mButtonSelectionStateListener;
    @Mock
    private IStatusBarService mBarService;
    @Mock
    private KeyguardStateController mKeyguardStateController;
    @Mock
    private PhoneStatusBarPolicy mIconPolicy;
    @Mock
    private ConfigurationController mConfigurationController;
    @Mock
    private CarSystemBarRestartTracker mCarSystemBarRestartTracker;
    @Mock
    private CarSystemBarViewFactory mCarSystemBarViewFactory;
    @Mock
    private CarSystemBarViewController mTopBar;
    @Mock
    private ViewGroup mTopWindow;
    @Mock
    private CarSystemBarViewController mRigthBar;
    @Mock
    private ViewGroup mRightWindow;
    @Mock
    private CarSystemBarViewController mLeftBar;
    @Mock
    private ViewGroup mLeftWindow;
    @Mock
    private CarSystemBarViewController mBottomBar;
    @Mock
    private ViewGroup mBottomWindow;
    @Mock
    private SystemUiWindowProvider mWindowProvider;
    @Mock
    private WindowMetrics mWindowMetrics;
    @Mock
    private DisplayTracker mDisplayTracker;

    private RegisterStatusBarResult mBarResult;
    private AppearanceRegion[] mAppearanceRegions;
    private FakeExecutor mUiBgExecutor;
    private SystemBarConfigs mSystemBarConfigs;
    private Handler mHandler;
    private Map<String, CarSystemBarViewSupplier> mViewSupplierMap;
    private Map<String, CarSystemBarWindowSupplier> mWindowSupplierMap;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mViewSupplierMap = new HashMap<>();
        mWindowSupplierMap = new HashMap<>();
        mTestableResources = mContext.getOrCreateTestableResources();
        mExecutor = new FakeExecutor(new FakeSystemClock());
        mUiBgExecutor = new FakeExecutor(new FakeSystemClock());
        mHandler = new FakeHandler(TestableLooper.get(this).getLooper());
        mSpiedContext = spy(mContext);
        mSpiedContext.addMockSystemService(ActivityManager.class, mActivityManager);
        mSpiedContext.addMockSystemService(WindowManager.class, mWindowManager);
        when(mSpiedContext.createWindowContext(anyInt(), any())).thenReturn(mSpiedContext);
        when(mStatusBarIconController.getTransitionsController()).thenReturn(
                mLightBarTransitionsController);
        when(mTopBar.getView()).thenReturn(mock(CarSystemBarView.class));
        when(mCarSystemBarViewFactory.getSystemBarViewController(eq(TOP_BAR_NAME), anyBoolean()))
                .thenReturn(mTopBar);
        when(mCarSystemBarViewFactory.getSystemBarWindow(eq(TOP_BAR_NAME))).thenReturn(mTopWindow);
        when(mRigthBar.getView()).thenReturn(mock(CarSystemBarView.class));
        when(mCarSystemBarViewFactory.getSystemBarViewController(eq(RIGHT_BAR_NAME), anyBoolean()))
                .thenReturn(mRigthBar);
        when(mCarSystemBarViewFactory.getSystemBarWindow(eq(RIGHT_BAR_NAME))).thenReturn(
                mRightWindow);
        when(mBottomBar.getView()).thenReturn(mock(CarSystemBarView.class));
        when(mCarSystemBarViewFactory.getSystemBarViewController(eq(BOTTOM_BAR_NAME),
                anyBoolean()))
                .thenReturn(mBottomBar);
        when(mCarSystemBarViewFactory.getSystemBarWindow(eq(BOTTOM_BAR_NAME))).thenReturn(
                mBottomWindow);
        when(mLeftBar.getView()).thenReturn(mock(CarSystemBarView.class));
        when(mCarSystemBarViewFactory.getSystemBarViewController(eq(LEFT_BAR_NAME), anyBoolean()))
                .thenReturn(mLeftBar);
        when(mCarSystemBarViewFactory.getSystemBarWindow(eq(LEFT_BAR_NAME)))
                .thenReturn(mLeftWindow);
        mAppearanceRegions = new AppearanceRegion[]{
                new AppearanceRegion(APPEARANCE_LIGHT_STATUS_BARS, new Rect())
        };
        mBarResult = new RegisterStatusBarResult(
                /* icons= */ new ArrayMap<>(),
                /* disabledFlags1= */ 0,
                /* appearance= */ 0,
                mAppearanceRegions,
                /* imeWindowVis= */ 0,
                /* imeBackDisposition= */ 0,
                /* showImeSwitcher= */ false,
                /* disabledFlags2= */ 0,
                /* navbarColorMangedByIme= */ false,
                BEHAVIOR_DEFAULT,
                WindowInsets.Type.defaultVisible(),
                /* packageName= */ null,
                /* transientBarTypes= */ 0,
                /* letterboxDetails= */ null);
        try {
            when(mBarService.registerStatusBar(any())).thenReturn(mBarResult);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        // Needed to inflate top navigation bar.
        mDependency.injectMockDependency(DarkIconDispatcher.class);
        mDependency.injectMockDependency(StatusBarIconController.class);

        when(mWindowManager.getCurrentWindowMetrics()).thenReturn(mWindowMetrics);
        when(mWindowMetrics.getBounds()).thenReturn(new Rect(0, 0, 1920, 1080));

        initCarSystemBar();
    }

    private void initCarSystemBar() {
        SystemBarConfigs systemBarConfigs =
                new SystemBarConfigsImpl(mSpiedContext, mTestableResources.getResources(),
                        mWindowProvider, mViewSupplierMap, mWindowSupplierMap, mDisplayTracker);
        FakeDisplayTracker displayTracker = new FakeDisplayTracker(mContext);
        mCarSystemBarController = spy(new CarSystemBarControllerImpl(mSpiedContext,
                mUserTracker,
                mCarSystemBarViewFactory,
                systemBarConfigs,
                mLightBarController,
                mStatusBarIconController,
                mWindowManager,
                mDeviceProvisionedController,
                new CommandQueue(mContext, displayTracker),
                mAutoHideController,
                mButtonSelectionStateListener,
                mExecutor,
                mBarService,
                () -> mKeyguardStateController,
                () -> mIconPolicy,
                mConfigurationController,
                mCarSystemBarRestartTracker,
                displayTracker,
                mWindowProvider,
                mHandler));
    }

    @Test
    public void restartNavbars_refreshesTaskChanged() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        ArgumentCaptor<CarDeviceProvisionedController.DeviceProvisionedListener>
                deviceProvisionedCallbackCaptor = ArgumentCaptor.forClass(
                CarDeviceProvisionedController.DeviceProvisionedListener.class);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();
        // switching the currentUserSetup value to force restart the navbars.
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(false);
        verify(mDeviceProvisionedController).addCallback(deviceProvisionedCallbackCaptor.capture());

        deviceProvisionedCallbackCaptor.getValue().onUserSwitched();
        waitForDelayableExecutor();

        verify(mButtonSelectionStateListener).onTaskStackChanged();
    }

    @Test
    public void restartNavBars_newUserNotSetupWithKeyguardShowing_showsKeyguardButtons() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        ArgumentCaptor<CarDeviceProvisionedController.DeviceProvisionedListener>
                deviceProvisionedCallbackCaptor = ArgumentCaptor.forClass(
                CarDeviceProvisionedController.DeviceProvisionedListener.class);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();
        when(mKeyguardStateController.isShowing()).thenReturn(true);
        // switching the currentUserSetup value to force restart the navbars.
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(false);
        verify(mDeviceProvisionedController).addCallback(deviceProvisionedCallbackCaptor.capture());

        deviceProvisionedCallbackCaptor.getValue().onUserSwitched();
        waitForDelayableExecutor();

        verify(mCarSystemBarController).showAllKeyguardButtons(false);
    }

    @Test
    public void restartNavbars_newUserIsSetupWithKeyguardHidden_showsNavigationButtons() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        ArgumentCaptor<CarDeviceProvisionedController.DeviceProvisionedListener>
                deviceProvisionedCallbackCaptor = ArgumentCaptor.forClass(
                CarDeviceProvisionedController.DeviceProvisionedListener.class);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();
        when(mKeyguardStateController.isShowing()).thenReturn(true);
        // switching the currentUserSetup value to force restart the navbars.
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(false);
        verify(mDeviceProvisionedController).addCallback(deviceProvisionedCallbackCaptor.capture());
        deviceProvisionedCallbackCaptor.getValue().onUserSwitched();
        waitForDelayableExecutor();
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        when(mKeyguardStateController.isShowing()).thenReturn(false);

        deviceProvisionedCallbackCaptor.getValue().onUserSetupChanged();
        waitForDelayableExecutor();

        verify(mCarSystemBarController).showAllNavigationButtons(true);
    }

    @Test
    public void restartNavBars_lightAppearance_darkensAllIcons() {
        mAppearanceRegions[0] = new AppearanceRegion(APPEARANCE_LIGHT_STATUS_BARS, new Rect());

        mCarSystemBarController.init();

        verify(mLightBarTransitionsController).setIconsDark(
                /* dark= */ true, /* animate= */ false);
    }

    @Test
    public void restartNavBars_opaqueAppearance_lightensAllIcons() {
        mAppearanceRegions[0] = new AppearanceRegion(APPEARANCE_OPAQUE_STATUS_BARS, new Rect());

        mCarSystemBarController.init();

        verify(mLightBarTransitionsController).setIconsDark(
                /* dark= */ false, /* animate= */ false);
    }

    @Test
    public void showTransient_wrongDisplayId_transientModeNotUpdated() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();

        int randomDisplay = Display.DEFAULT_DISPLAY + 10;
        int insetTypes = 0;
        mCarSystemBarController.showTransient(randomDisplay, insetTypes, false);

        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isFalse();
    }

    @Test
    public void showTransient_correctDisplayId_noStatusBarInset_transientModeNotUpdated() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();

        int insetTypes = 0;
        mCarSystemBarController.showTransient(Display.DEFAULT_DISPLAY, insetTypes, false);

        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isFalse();
    }

    @Test
    public void showTransient_correctDisplayId_statusBarInset_transientModeUpdated() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();

        int insetTypes = WindowInsets.Type.statusBars();
        mCarSystemBarController.showTransient(Display.DEFAULT_DISPLAY, insetTypes, false);

        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isTrue();
    }

    @Test
    public void showTransient_correctDisplayId_noNavBarInset_transientModeNotUpdated() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();

        int insetTypes = 0;
        mCarSystemBarController.showTransient(Display.DEFAULT_DISPLAY, insetTypes, false);

        assertThat(mCarSystemBarController.isNavBarTransientShown()).isFalse();
    }

    @Test
    public void showTransient_correctDisplayId_navBarInset_transientModeUpdated() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();

        int insetTypes = WindowInsets.Type.navigationBars();
        mCarSystemBarController.showTransient(Display.DEFAULT_DISPLAY, insetTypes, false);

        assertThat(mCarSystemBarController.isNavBarTransientShown()).isTrue();
    }

    @Test
    public void abortTransient_wrongDisplayId_transientModeNotCleared() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();
        mCarSystemBarController.showTransient(
                Display.DEFAULT_DISPLAY,
                WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars(),
                false);
        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isTrue();
        assertThat(mCarSystemBarController.isNavBarTransientShown()).isTrue();

        int insetTypes = 0;
        int randomDisplay = Display.DEFAULT_DISPLAY + 10;
        mCarSystemBarController.abortTransient(randomDisplay, insetTypes);

        // The transient booleans were not cleared.
        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isTrue();
        assertThat(mCarSystemBarController.isNavBarTransientShown()).isTrue();
    }

    @Test
    public void abortTransient_correctDisplayId_noInsets_transientModeNotCleared() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();
        mCarSystemBarController.showTransient(
                Display.DEFAULT_DISPLAY,
                WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars(),
                false);
        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isTrue();
        assertThat(mCarSystemBarController.isNavBarTransientShown()).isTrue();

        int insetTypes = 0;
        mCarSystemBarController.abortTransient(Display.DEFAULT_DISPLAY, insetTypes);

        // The transient booleans were not cleared.
        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isTrue();
        assertThat(mCarSystemBarController.isNavBarTransientShown()).isTrue();
    }

    @Test
    public void abortTransient_correctDisplayId_statusBarInset_transientModeCleared() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();
        mCarSystemBarController.showTransient(
                Display.DEFAULT_DISPLAY,
                WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars(),
                false);
        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isTrue();
        assertThat(mCarSystemBarController.isNavBarTransientShown()).isTrue();

        int insetTypes = WindowInsets.Type.statusBars();
        mCarSystemBarController.abortTransient(Display.DEFAULT_DISPLAY, insetTypes);

        // The transient booleans were cleared.
        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isFalse();
        assertThat(mCarSystemBarController.isNavBarTransientShown()).isFalse();
    }

    @Test
    public void abortTransient_correctDisplayId_navBarInset_transientModeCleared() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();
        mCarSystemBarController.showTransient(
                Display.DEFAULT_DISPLAY,
                WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars(),
                false);
        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isTrue();
        assertThat(mCarSystemBarController.isNavBarTransientShown()).isTrue();

        int insetTypes = WindowInsets.Type.navigationBars();
        mCarSystemBarController.abortTransient(Display.DEFAULT_DISPLAY, insetTypes);

        // The transient booleans were cleared.
        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isFalse();
        assertThat(mCarSystemBarController.isNavBarTransientShown()).isFalse();
    }

    @Test
    public void disable_wrongDisplayId_notSetStatusBarState() {
        int randomDisplay = Display.DEFAULT_DISPLAY + 10;

        mCarSystemBarController.disable(randomDisplay, 0, 0, false);

        verify(mCarSystemBarController, never()).setSystemBarStates(anyInt(), anyInt());
    }

    @Test
    public void disable_correctDisplayId_setSystemBarStates() {
        mCarSystemBarController.disable(Display.DEFAULT_DISPLAY, 0, 0, false);

        verify(mCarSystemBarController).setSystemBarStates(0, 0);
    }

    @Test
    public void restartSystemBars_newSystemBarConfig_recreatesSystemBars() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, false);
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, false);
        when(mCarSystemBarController.getBarWindow(TOP_BAR_NAME)).thenReturn(mock(ViewGroup.class));
        when(mCarSystemBarController.getBarWindow(BOTTOM_BAR_NAME)).thenReturn(
                mock(ViewGroup.class));
        when(mCarSystemBarController.getBarWindow(LEFT_BAR_NAME)).thenReturn(null);
        when(mCarSystemBarController.getBarWindow(RIGHT_BAR_NAME)).thenReturn(null);

        initCarSystemBar();
        mCarSystemBarController.init();
        assertThat(mCarSystemBarController.getBarWindow(TOP_BAR_NAME)).isNotNull();
        assertThat(mCarSystemBarController.getBarWindow(BOTTOM_BAR_NAME)).isNotNull();
        assertThat(mCarSystemBarController.getBarWindow(LEFT_BAR_NAME)).isNull();
        assertThat(mCarSystemBarController.getBarWindow(RIGHT_BAR_NAME)).isNull();

        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, false);
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, true);
        mSystemBarConfigs = new SystemBarConfigsImpl(mSpiedContext,
                mTestableResources.getResources(), mWindowProvider, mViewSupplierMap,
                mWindowSupplierMap, mDisplayTracker);
        when(mCarSystemBarController.getBarWindow(TOP_BAR_NAME)).thenReturn(mock(ViewGroup.class));
        when(mCarSystemBarController.getBarWindow(BOTTOM_BAR_NAME)).thenReturn(null);
        when(mCarSystemBarController.getBarWindow(LEFT_BAR_NAME)).thenReturn(
                mock(ViewGroup.class));
        when(mCarSystemBarController.getBarWindow(RIGHT_BAR_NAME)).thenReturn(
                mock(ViewGroup.class));
        mCarSystemBarController.restartSystemBars();

        verify(mCarSystemBarController, times(2)).resetSystemBarConfigs();
        assertThat(mCarSystemBarController.getBarWindow(TOP_BAR_NAME)).isNotNull();
        assertThat(mCarSystemBarController.getBarWindow(BOTTOM_BAR_NAME)).isNull();
        assertThat(mCarSystemBarController.getBarWindow(LEFT_BAR_NAME)).isNotNull();
        assertThat(mCarSystemBarController.getBarWindow(RIGHT_BAR_NAME)).isNotNull();
    }

    private void waitForDelayableExecutor() {
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();
    }
}
