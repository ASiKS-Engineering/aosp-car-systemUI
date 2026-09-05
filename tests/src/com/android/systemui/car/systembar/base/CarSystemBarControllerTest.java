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

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;
import static android.app.StatusBarManager.DISABLE_HOME;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.systemui.car.systembar.SystemBarConstants.BOTTOM_BAR_NAME;
import static com.android.systemui.car.systembar.SystemBarConstants.LEFT_BAR_NAME;
import static com.android.systemui.car.systembar.SystemBarConstants.RIGHT_BAR_NAME;
import static com.android.systemui.car.systembar.SystemBarConstants.TOP_BAR_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertNotNull;

import android.app.ActivityManager;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.testing.TestableResources;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.test.filters.SmallTest;

import com.android.car.ui.FocusParkingView;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.LetterboxDetails;
import com.android.internal.statusbar.RegisterStatusBarResult;
import com.android.internal.view.AppearanceRegion;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.SysuiTestableContext;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.flexibleui.CarSystemBarElementController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.systembar.base.element.CarSystemBarElementInitializer;
import com.android.systemui.car.systembar.home.HomeButtonController;
import com.android.systemui.car.systembar.passengerhome.PassengerHomeButtonController;
import com.android.systemui.car.tests.baselib.R;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.car.window.OverlayVisibilityMediator;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.car.wm.scalableui.systemwindow.SystemUiWindowProvider;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.settings.DisplayTracker;
import com.android.systemui.settings.FakeDisplayTracker;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.phone.AutoHideController;
import com.android.systemui.statusbar.phone.LightBarController;
import com.android.systemui.statusbar.phone.PhoneStatusBarPolicy;
import com.android.systemui.statusbar.phone.SysuiDarkIconDispatcher;
import com.android.systemui.statusbar.phone.ui.StatusBarIconController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.concurrency.FakeExecutor;
import com.android.systemui.util.time.FakeSystemClock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Provider;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class CarSystemBarControllerTest extends CarSysuiTestCase {
    private static final String TOP_NOTIFICATION_PANEL =
            "com.android.systemui.car.notification.TopNotificationPanelViewMediator";
    private static final String BOTTOM_NOTIFICATION_PANEL =
            "com.android.systemui.car.notification.BottomNotificationPanelViewMediator";
    private CarSystemBarControllerImpl mCarSystemBarController;
    private CarSystemBarViewFactory mCarSystemBarViewFactory;
    private TestableResources mTestableResources;
    private SysuiTestableContext mSpiedContext;
    private MockitoSession mSession;
    private Map<String, CarSystemBarViewSupplier> mViewSupplierMap;
    private Map<String, CarSystemBarWindowSupplier> mWindowSupplierMap;

    @Mock
    private UserTracker mUserTracker;
    @Mock
    private ActivityManager mActivityManager;
    @Mock
    private ButtonRoleHolderController mButtonRoleHolderController;
    @Mock
    private LightBarController mLightBarController;
    @Mock
    private SysuiDarkIconDispatcher mStatusBarIconController;
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
    private OverlayVisibilityMediator mOverlayVisibilityMediator;
    @Mock
    private SystemUiWindowProvider mWindowProvider;
    @Mock
    private DisplayTracker mDisplayTracker;
    @Mock
    private WindowMetrics mWindowMetrics;

    private RegisterStatusBarResult mRegisterStatusBarResult;
    private SystemBarConfigs mSystemBarConfigs;
    private HandlerThread mThread;
    private Handler mHandler;

    @Before
    public void setUp() throws Exception {
        mViewSupplierMap = new HashMap<>();
        mWindowSupplierMap = new HashMap<>();
        mSession = ExtendedMockito.mockitoSession()
            .initMocks(this)
            .spyStatic(CarSystemUIUserUtil.class)
            .strictness(Strictness.LENIENT)
            .startMocking();
        mTestableResources = mContext.getOrCreateTestableResources();
        mSpiedContext = spy(mContext);
        LayoutInflater inflater = LayoutInflater.from(mSpiedContext);
        CarSystemBarViewSupplier viewSupplier = (ctx, isSetUp) -> (ViewGroup) inflater.inflate(
                R.layout.car_top_system_bar, null);
        CarSystemBarWindowSupplier windowSupplier = ctx -> (ViewGroup) inflater.inflate(
                R.layout.car_top_system_bar, null);
        CarSystemBarViewSupplier bottomViewSupplier = (ctx, isSetUp) ->
                (ViewGroup) inflater.inflate(
                        com.android.systemui.car.systembar.standard.R.layout.car_bottom_system_bar,
                        null);
        CarSystemBarWindowSupplier bottomWindowSupplier = ctx -> (ViewGroup) inflater.inflate(
                com.android.systemui.car.systembar.standard.R.layout.car_bottom_system_bar, null);
        mViewSupplierMap.put(TOP_BAR_NAME, viewSupplier);
        mWindowSupplierMap.put(TOP_BAR_NAME, windowSupplier);
        mViewSupplierMap.put(BOTTOM_BAR_NAME, bottomViewSupplier);
        mWindowSupplierMap.put(BOTTOM_BAR_NAME, bottomWindowSupplier);
        mViewSupplierMap.put(LEFT_BAR_NAME, viewSupplier);
        mWindowSupplierMap.put(LEFT_BAR_NAME, windowSupplier);
        mViewSupplierMap.put(RIGHT_BAR_NAME, viewSupplier);
        mWindowSupplierMap.put(RIGHT_BAR_NAME, windowSupplier);
        mSpiedContext.addMockSystemService(ActivityManager.class, mActivityManager);
        mSpiedContext.addMockSystemService(WindowManager.class, mWindowManager);
        when(mSpiedContext.createWindowContext(anyInt(), any())).thenReturn(mSpiedContext);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        when(mDeviceProvisionedController.isCurrentUserSetupInProgress()).thenReturn(false);
        Map<Class<?>, Provider<CarSystemBarElementController.Factory>> controllerFactoryMap =
                new ArrayMap<>();
        Provider<CarSystemBarElementController.Factory> homeButtonControllerProvider =
                () -> new HomeButtonController.Factory() {
                    @Override
                    public HomeButtonController create(CarSystemBarButton view) {
                        return new HomeButtonController(view,
                                mock(CarSystemBarElementStatusBarDisableController.class),
                                mock(CarSystemBarElementStateController.class),
                                mUserTracker, mock(EventDispatcher.class),
                                mock(ButtonSelectionStateController.class));
                    }
                };
        controllerFactoryMap.put(HomeButtonController.class, homeButtonControllerProvider);
        Provider<CarSystemBarElementController.Factory> passengerHomeButtonControllerProvider =
                () -> new PassengerHomeButtonController.Factory() {
                    @Override
                    public PassengerHomeButtonController create(CarSystemBarButton view) {
                        return new PassengerHomeButtonController(view,
                                mock(CarSystemBarElementStatusBarDisableController.class),
                                mock(CarSystemBarElementStateController.class),
                                mUserTracker, mock(EventDispatcher.class),
                                mock(ButtonSelectionStateController.class));
                    }
                };
        controllerFactoryMap.put(PassengerHomeButtonController.class,
                passengerHomeButtonControllerProvider);
        CarSystemBarElementInitializer carSystemBarElementInitializer =
                new CarSystemBarElementInitializer(controllerFactoryMap,
                        mock(CarSystemBarElementStateController.class),
                        mock(CarSystemBarRestartTracker.class));
        when(mWindowManager.getCurrentWindowMetrics()).thenReturn(mWindowMetrics);
        when(mWindowMetrics.getBounds()).thenReturn(new Rect(0, 0, 1920, 1080));
        mSystemBarConfigs = new SystemBarConfigsImpl(mSpiedContext,
                mTestableResources.getResources(), mWindowProvider, mViewSupplierMap,
                mWindowSupplierMap, mDisplayTracker);
        mThread = new HandlerThread("TestThread");
        mThread.start();
        mHandler = Handler.createAsync(mThread.getLooper());
        CarSystemBarViewControllerFactory carSystemBarViewControllerFactory =
                new CarSystemBarViewControllerImpl.Factory() {
                    @Override
                    public CarSystemBarViewControllerImpl create(String name, ViewGroup view) {
                        return spy(new CarSystemBarViewControllerImpl(mSpiedContext, mUserTracker,
                                carSystemBarElementInitializer, mSystemBarConfigs,
                                mButtonRoleHolderController,
                                mOverlayVisibilityMediator,
                                name, view));
                    }
                };
        Map<String, CarSystemBarViewControllerFactory<?>> factoriesMap =
                new HashMap<>();
        factoriesMap.put(LEFT_BAR_NAME, carSystemBarViewControllerFactory);
        factoriesMap.put(TOP_BAR_NAME, carSystemBarViewControllerFactory);
        factoriesMap.put(RIGHT_BAR_NAME, carSystemBarViewControllerFactory);
        factoriesMap.put(BOTTOM_BAR_NAME, carSystemBarViewControllerFactory);
        mCarSystemBarViewFactory =
                new CarSystemBarViewFactoryImpl(factoriesMap, mSystemBarConfigs);

        mRegisterStatusBarResult = new RegisterStatusBarResult(new ArrayMap<>(), 0, 0,
                new AppearanceRegion[0], 0, 0, false, 0, false, 0, 0, "", 0,
                new LetterboxDetails[0]);
        when(mBarService.registerStatusBar(any())).thenReturn(mRegisterStatusBarResult);

        // Needed to inflate top navigation bar.
        mDependency.injectMockDependency(DarkIconDispatcher.class);
        mDependency.injectMockDependency(StatusBarIconController.class);

        initCarSystemBar();
    }

    @After
    public void tearDown() throws Exception {
        if (mSession != null) {
            mSession.finishMocking();
        }
        if (mThread != null) {
            mThread.quit();
        }
    }

    private void initCarSystemBar() {
        FakeDisplayTracker displayTracker = new FakeDisplayTracker(mSpiedContext);
        FakeExecutor executor = new FakeExecutor(new FakeSystemClock());

        mCarSystemBarController = new CarSystemBarControllerImpl(mSpiedContext,
                mUserTracker,
                mCarSystemBarViewFactory,
                mSystemBarConfigs,
                mLightBarController,
                mStatusBarIconController,
                mWindowManager,
                mDeviceProvisionedController,
                new CommandQueue(mSpiedContext, displayTracker),
                mAutoHideController,
                mButtonSelectionStateListener,
                executor,
                mBarService,
                () -> mKeyguardStateController,
                () -> mIconPolicy,
                mConfigurationController,
                mCarSystemBarRestartTracker,
                displayTracker,
                mWindowProvider,
                mHandler);
    }

    @Test
    public void testGetTopWindow_topDisabled_returnsNull() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, false);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        // If Top Notification Panel is used but top navigation bar is not enabled, SystemUI is
        // expected to crash.
        mTestableResources.addOverride(
                com.android.systemui.car.notification.R.string.config_notificationPanelViewMediator,
                BOTTOM_NOTIFICATION_PANEL);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(TOP_BAR_NAME);

        assertThat(window).isNull();
    }

    @Test
    public void testGetTopWindow_topEnabled_returnsWindow() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(TOP_BAR_NAME);

        assertThat(window).isNotNull();
    }

    @Test
    public void testGetTopWindow_topEnabled_calledTwice_returnsSameWindow() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window1 = mCarSystemBarController.getBarWindow(TOP_BAR_NAME);
        ViewGroup window2 = mCarSystemBarController.getBarWindow(TOP_BAR_NAME);

        assertThat(window1).isEqualTo(window2);
    }

    @Test
    public void testGetBottomWindow_bottomDisabled_returnsNull() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, false);
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        // If Bottom Notification Panel is used but bottom navigation bar is not enabled,
        // SystemUI is expected to crash.
        mTestableResources.addOverride(
                com.android.systemui.car.notification.R.string.config_notificationPanelViewMediator,
                TOP_NOTIFICATION_PANEL);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(BOTTOM_BAR_NAME);

        assertThat(window).isNull();
    }

    @Test
    public void testGetBottomWindow_bottomEnabled_returnsWindow() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(BOTTOM_BAR_NAME);

        assertThat(window).isNotNull();
    }

    @Test
    public void testGetBottomWindow_bottomEnabled_calledTwice_returnsSameWindow() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window1 = mCarSystemBarController.getBarWindow(BOTTOM_BAR_NAME);
        ViewGroup window2 = mCarSystemBarController.getBarWindow(BOTTOM_BAR_NAME);

        assertThat(window1).isEqualTo(window2);
    }

    @Test
    public void testGetLeftWindow_leftDisabled_returnsNull() {
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, false);
        mCarSystemBarController.init();
        ViewGroup window = mCarSystemBarController.getBarWindow(LEFT_BAR_NAME);
        assertThat(window).isNull();
    }

    @Test
    public void testGetLeftWindow_leftEnabled_returnsWindow() {
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(LEFT_BAR_NAME);

        assertThat(window).isNotNull();
    }

    @Test
    public void testGetLeftWindow_leftEnabled_calledTwice_returnsSameWindow() {
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window1 = mCarSystemBarController.getBarWindow(LEFT_BAR_NAME);
        ViewGroup window2 = mCarSystemBarController.getBarWindow(LEFT_BAR_NAME);

        assertThat(window1).isEqualTo(window2);
    }

    @Test
    public void testGetRightWindow_rightDisabled_returnsNull() {
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, false);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(RIGHT_BAR_NAME);

        assertThat(window).isNull();
    }

    @Test
    public void testGetRightWindow_rightEnabled_returnsWindow() {
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(RIGHT_BAR_NAME);

        assertThat(window).isNotNull();
    }

    @Test
    public void testGetRightWindow_rightEnabled_calledTwice_returnsSameWindow() {
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window1 = mCarSystemBarController.getBarWindow(RIGHT_BAR_NAME);
        ViewGroup window2 = mCarSystemBarController.getBarWindow(RIGHT_BAR_NAME);

        assertThat(window1).isEqualTo(window2);
    }

    @Test
    public void testSetTopWindowVisibility_setTrue_isVisible() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(TOP_BAR_NAME);
        mCarSystemBarController.setWindowVisibility(window, View.VISIBLE);

        assertThat(window.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testSetTopWindowVisibility_setFalse_isGone() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(TOP_BAR_NAME);
        mCarSystemBarController.setWindowVisibility(window, View.GONE);

        assertThat(window.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testSetBottomWindowVisibility_setTrue_isVisible() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(BOTTOM_BAR_NAME);
        mCarSystemBarController.setWindowVisibility(window, View.VISIBLE);

        assertThat(window.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testSetBottomWindowVisibility_setFalse_isGone() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(BOTTOM_BAR_NAME);
        mCarSystemBarController.setWindowVisibility(window, View.GONE);

        assertThat(window.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testSetLeftWindowVisibility_setTrue_isVisible() {
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(LEFT_BAR_NAME);
        mCarSystemBarController.setWindowVisibility(window, View.VISIBLE);

        assertThat(window.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testSetLeftWindowVisibility_setFalse_isGone() {
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(LEFT_BAR_NAME);
        mCarSystemBarController.setWindowVisibility(window, View.GONE);

        assertThat(window.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testSetRightWindowVisibility_setTrue_isVisible() {
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(RIGHT_BAR_NAME);
        mCarSystemBarController.setWindowVisibility(window, View.VISIBLE);

        assertThat(window.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testSetRightWindowVisibility_setFalse_isGone() {
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(RIGHT_BAR_NAME);
        mCarSystemBarController.setWindowVisibility(window, View.GONE);

        assertThat(window.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testRegisterBottomBarTouchListener_createViewFirst_registrationSuccessful() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();

        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(
                BOTTOM_BAR_NAME, /* isSetUp= */ true);
        View.OnTouchListener mockOnTouchListener = mock(View.OnTouchListener.class);
        Set<View.OnTouchListener> listeners = new ArraySet<>();
        listeners.add(mockOnTouchListener);
        mCarSystemBarController.registerBarTouchListener(BOTTOM_BAR_NAME, mockOnTouchListener);

        ArgumentCaptor<Set<View.OnTouchListener>> captor = ArgumentCaptor.forClass(Set.class);
        // called 3 times - once for init, once for test getBarViewController call, and once for
        // test registerBarTouchListener call
        verify(bottomBar, times(3)).setSystemBarTouchListeners(captor.capture());

        List<Set<View.OnTouchListener>> allValues = captor.getAllValues();
        assertThat(allValues.contains(listeners));
    }

    @Test
    public void testRegisterBottomBarTouchListener_registerFirst_registrationSuccessful() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();

        View.OnTouchListener mockOnTouchListener = mock(View.OnTouchListener.class);
        Set<View.OnTouchListener> listeners = new ArraySet<>();
        listeners.add(mockOnTouchListener);
        mCarSystemBarController.registerBarTouchListener(BOTTOM_BAR_NAME, mockOnTouchListener);
        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(
                BOTTOM_BAR_NAME, /* isSetUp= */ true);

        ArgumentCaptor<Set<View.OnTouchListener>> captor = ArgumentCaptor.forClass(Set.class);
        // called 3 times - once for init, once for test registerBarTouchListener
        // call, and once for test getBarViewController call
        verify(bottomBar, times(3)).setSystemBarTouchListeners(captor.capture());

        List<Set<View.OnTouchListener>> allValues = captor.getAllValues();
        assertThat(allValues.contains(listeners));
    }

    @Test
    public void testShowAllNavigationButtons_bottomEnabled_bottomNavigationButtonsVisible() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(
                BOTTOM_BAR_NAME, /* isSetUp= */ true);
        View bottomNavButtons = bottomBar.getView().findViewById(R.id.nav_buttons);

        mCarSystemBarController.showAllNavigationButtons();

        assertThat(bottomNavButtons.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testShowAllNavigationButtons_bottomEnabled_bottomKeyguardButtonsGone() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(
                BOTTOM_BAR_NAME, /* isSetUp= */ true);
        View bottomKeyguardButtons = bottomBar.getView().findViewById(R.id.lock_screen_nav_buttons);

        mCarSystemBarController.showAllNavigationButtons();

        assertThat(bottomKeyguardButtons.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testShowAllNavigationButtons_bottomEnabled_bottomOcclusionButtonsGone() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(
                BOTTOM_BAR_NAME, /* isSetUp= */ true);
        View occlusionButtons = bottomBar.getView().findViewById(R.id.occlusion_buttons);

        mCarSystemBarController.showAllNavigationButtons();

        assertThat(occlusionButtons.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testShowAllKeyguardButtons_bottomEnabled_bottomKeyguardButtonsVisible() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(
                BOTTOM_BAR_NAME, /* isSetUp= */ true);
        View bottomKeyguardButtons = bottomBar.getView().findViewById(R.id.lock_screen_nav_buttons);

        mCarSystemBarController.showAllKeyguardButtons();

        assertThat(bottomKeyguardButtons.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testShowAllKeyguardButtons_bottomEnabled_bottomNavigationButtonsGone() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(
                BOTTOM_BAR_NAME, /* isSetUp= */ true);
        View bottomNavButtons = bottomBar.getView().findViewById(R.id.nav_buttons);

        mCarSystemBarController.showAllKeyguardButtons();

        assertThat(bottomNavButtons.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testShowAllKeyguardButtons_bottomEnabled_bottomOcclusionButtonsGone() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(
                BOTTOM_BAR_NAME, /* isSetUp= */ true);
        View occlusionButtons = bottomBar.getView().findViewById(R.id.occlusion_buttons);

        mCarSystemBarController.showAllKeyguardButtons();

        assertThat(occlusionButtons.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testShowOcclusionButtons_bottomEnabled_bottomOcclusionButtonsVisible() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(
                BOTTOM_BAR_NAME, /* isSetUp= */ true);
        View occlusionButtons = bottomBar.getView().findViewById(R.id.occlusion_buttons);

        mCarSystemBarController.showAllOcclusionButtons();

        assertThat(occlusionButtons.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testShowOcclusionButtons_bottomEnabled_bottomNavigationButtonsGone() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(
                BOTTOM_BAR_NAME, /* isSetUp= */ true);
        View bottomNavButtons = bottomBar.getView().findViewById(R.id.nav_buttons);

        mCarSystemBarController.showAllOcclusionButtons();

        assertThat(bottomNavButtons.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testShowOcclusionButtons_bottomEnabled_bottomKeyguardButtonsGone() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(
                BOTTOM_BAR_NAME, /* isSetUp= */ true);
        View keyguardButtons = bottomBar.getView().findViewById(R.id.lock_screen_nav_buttons);

        mCarSystemBarController.showAllOcclusionButtons();

        assertThat(keyguardButtons.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testSetSystemBarStates_stateUpdated() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        clearSystemBarStates();

        mCarSystemBarController.setSystemBarStates(DISABLE_HOME, /* state2= */ 0);

        assertThat(mCarSystemBarController.getStatusBarState()).isEqualTo(DISABLE_HOME);
    }

    @Test
    public void testSetSystemBarStates_state2Updated() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        clearSystemBarStates();

        mCarSystemBarController.setSystemBarStates(0, DISABLE2_QUICK_SETTINGS);

        assertThat(mCarSystemBarController.getStatusBarState2()).isEqualTo(DISABLE2_QUICK_SETTINGS);
    }

    @Test
    public void cacheAndHideFocus_doesntCallHideFocus_if_focusParkingViewIsFocused() {
        mCarSystemBarController.init();
        View mockFocusParkingView = mock(FocusParkingView.class);
        View mockContainerView = mock(View.class);
        when(mockContainerView.findFocus()).thenReturn(mockFocusParkingView);

        int returnFocusedViewId =
                CarSystemBarViewControllerImpl.cacheAndHideFocus(mockContainerView);

        assertThat(returnFocusedViewId).isEqualTo(View.NO_ID);
    }

    @Test
    public void testDriverHomeOnDriverSystemUI_isVisible() {
        doReturn(false).when(() ->
                CarSystemUIUserUtil.isSecondaryMUMDSystemUI());
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, /* value= */ true);
        mCarSystemBarController.init();

        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(
                BOTTOM_BAR_NAME, /* isSetUp= */ true);
        View driverHomeButton = bottomBar.getView().findViewById(R.id.home);
        View passengerHomeButton = bottomBar.getView().findViewById(
                com.android.systemui.car.systembar.standard.R.id.passenger_home);

        assertThat(driverHomeButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(passengerHomeButton.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testPassengerHomeOnSecondarySystemUI_isVisible() {
        doReturn(true).when(() ->
                CarSystemUIUserUtil.isSecondaryMUMDSystemUI());
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();

        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(
                BOTTOM_BAR_NAME, /* isSetUp= */ true);
        View driverHomeButton = bottomBar.getView().findViewById(R.id.home);
        View passengerHomeButton = bottomBar.getView().findViewById(
                com.android.systemui.car.systembar.standard.R.id.passenger_home);

        assertThat(driverHomeButton.getVisibility()).isEqualTo(View.GONE);
        assertThat(passengerHomeButton.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testAllBarWindowsRegistered() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, true);
        mCarSystemBarController.init();

        assertThat(mCarSystemBarController.getBarWindow(TOP_BAR_NAME)).isNotNull();
        assertThat(mCarSystemBarController.getBarWindow(BOTTOM_BAR_NAME)).isNotNull();
        assertThat(mCarSystemBarController.getBarWindow(LEFT_BAR_NAME)).isNotNull();
        assertThat(mCarSystemBarController.getBarWindow(RIGHT_BAR_NAME)).isNotNull();
    }

    @Test
    public void testTopLeftPanel_inflatesSuccessfully_andHasRequiredElements() {
        LayoutInflater inflater = LayoutInflater.from(mSpiedContext);
        View topLeftPanel = inflater.inflate(
                com.android.systemui.car.systembar.split.R.layout.car_top_left_system_bar, null);

        View bluetoothPanelButton = topLeftPanel.findViewById(R.id.bluetooth_panel_button);
        assertNotNull(bluetoothPanelButton);
    }

    @Test
    public void testTopRightPanel_inflatesSuccessfully_andHasRequiredElements() {
        LayoutInflater inflater = LayoutInflater.from(mSpiedContext);
        View topRightPanel = inflater.inflate(
                com.android.systemui.car.systembar.split.R.layout.car_top_right_system_bar, null);

        assertNotNull(topRightPanel.findViewById(R.id.clock));
        assertNotNull(topRightPanel.findViewById(
                com.android.systemui.car.notification.R.id.notifications));
        assertNotNull(topRightPanel.findViewById(
                com.android.systemui.car.systembar.privacy.mic.R.id.mic_privacy_chip));
        assertNotNull(topRightPanel.findViewById(
                com.android.systemui.car.systembar.privacy.camera.R.id.camera_privacy_chip));
    }

    @Test
    public void testBottomLeftPanel_inflatesSuccessfully_andHasRequiredElements() {
        LayoutInflater inflater = LayoutInflater.from(mSpiedContext);
        View bottomLeftPanel = inflater.inflate(
                com.android.systemui.car.systembar.split.R.layout.car_bottom_left_system_bar, null);

        assertNotNull(bottomLeftPanel.findViewById(
                com.android.systemui.car.hvac.ui.R.id.driver_hvac));
    }

    @Test
    public void testBottomCenterPanel_inflatesSuccessfully_andHasRequiredElements() {
        LayoutInflater inflater = LayoutInflater.from(mSpiedContext);
        View bottomCenterPanel = inflater.inflate(
                com.android.systemui.car.systembar.split.R.layout.car_bottom_center_system_bar,
                null);

        assertNotNull(bottomCenterPanel.findViewById(
                com.android.systemui.car.systembar.split.R.id.grid_nav));
        assertNotNull(bottomCenterPanel.findViewById(R.id.dock));
        assertNotNull(bottomCenterPanel.findViewById(R.id.assistant));
    }

    @Test
    public void testBottomRightPanel_inflatesSuccessfully_andHasRequiredElements() {
        LayoutInflater inflater = LayoutInflater.from(mSpiedContext);
        View bottomRightPanel = inflater.inflate(
                com.android.systemui.car.systembar.split.R.layout.car_bottom_right_system_bar,
                null);

        assertNotNull(bottomRightPanel.findViewById(
                com.android.systemui.car.systembar.split.R.id.passenger_hvac));
    }

    private void clearSystemBarStates() {
        if (mCarSystemBarController != null) {
            mCarSystemBarController.setSystemBarStates(/* state= */ 0, /* state2= */ 0);
        }
        setLockTaskModeLocked(false);
    }

    private void setLockTaskModeLocked(boolean locked) {
        when(mActivityManager.getLockTaskModeState()).thenReturn(locked
                ? ActivityManager.LOCK_TASK_MODE_LOCKED
                : ActivityManager.LOCK_TASK_MODE_NONE);
        mCarSystemBarController.setSystemBarStates(/* state= */ 0, /* state2= */ 0);
    }
}
