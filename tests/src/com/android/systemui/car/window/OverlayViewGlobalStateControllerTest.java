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

package com.android.systemui.car.window;

import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;
import static android.view.WindowInsets.Type.systemBars;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;

@CarSystemUiTest
@SmallTest
public class OverlayViewGlobalStateControllerTest extends CarSysuiTestCase {

    private OverlayViewGlobalStateController mOverlayViewGlobalStateController;

    @Mock
    private SystemUIOverlayWindowController mSystemUIOverlayWindowController;
    @Mock
    private OverlayViewMediator mOverlayViewMediator;
    @Mock
    private OverlayViewController mOverlayViewController1;
    @Mock
    private OverlayViewController mOverlayViewController2;
    @Mock
    private OverlayPanelViewController mOverlayPanelViewController;
    @Mock
    private Runnable mRunnable;
    private OverlayVisibilityMediator mOverlayVisibilityMediator;

    @Mock
    private ViewGroup mContainer1;
    @Mock
    private ViewGroup mContainer2;
    @Mock
    private ViewGroup mPanelContainer;

    private static final String TEST_TYPE_1 = "keyguard";
    private static final String TEST_TYPE_2 = "fullscreen_user_switcher";
    private static final String TEST_TYPE_PANEL = "hvac_panel";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(/* testClass= */ this);

        when(mSystemUIOverlayWindowController.getContainerForType(TEST_TYPE_1))
                .thenReturn(mContainer1);
        when(mSystemUIOverlayWindowController.getContainerForType(TEST_TYPE_2))
                .thenReturn(mContainer2);
        when(mSystemUIOverlayWindowController.getContainerForType(TEST_TYPE_PANEL))
                .thenReturn(mPanelContainer);
        ViewGroup mockBaseLayout = mock(ViewGroup.class);
        when(mSystemUIOverlayWindowController.getBaseLayout()).thenReturn(mockBaseLayout);

        mOverlayVisibilityMediator =
                new OverlayVisibilityMediatorImpl(mSystemUIOverlayWindowController);
        mOverlayViewGlobalStateController = new OverlayViewGlobalStateController(
                mSystemUIOverlayWindowController, mOverlayVisibilityMediator);
    }

    @Test
    public void registerMediator_overlayViewMediatorListenersRegistered() {
        mOverlayViewGlobalStateController.registerMediator(mOverlayViewMediator);

        verify(mOverlayViewMediator).registerListeners();
    }

    @Test
    public void registerMediator_overlayViewMediatorViewControllerSetup() {
        mOverlayViewGlobalStateController.registerMediator(mOverlayViewMediator);

        verify(mOverlayViewMediator).setUpOverlayContentViewControllers();
    }

    @Test
    public void init_callsSystemUIOverlayWindowControllerInit() {
        // Action
        mOverlayViewGlobalStateController.init();

        // Verification
        verify(mSystemUIOverlayWindowController).init();
    }

    @Test
    public void showView_nothingVisible_windowNotFocusable_shouldShowNavBar_navBarsVisible() {
        setupOverlayViewController1();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(false);
        when(mOverlayViewController1.shouldShowNavigationBarInsets()).thenReturn(true);

        setOverlayViewControllerAsShowing(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController).showInsets(navigationBars());
    }

    @Test
    public void showView_nothingVisible_windowNotFocusable_shouldHideNavBar_notHidden() {
        setupOverlayViewController1();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(false);
        when(mOverlayViewController1.shouldShowNavigationBarInsets()).thenReturn(false);

        setOverlayViewControllerAsShowing(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController, never()).hideInsets(navigationBars());
    }

    @Test
    public void showView_nothingVisible_windowNotFocusable_shouldShowStatusBar_statusBarsVisible() {
        setupOverlayViewController1();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(false);
        when(mOverlayViewController1.shouldShowStatusBarInsets()).thenReturn(true);

        setOverlayViewControllerAsShowing(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController).showInsets(statusBars());
    }

    @Test
    public void showView_nothingVisible_windowNotFocusable_shouldHideStatusBar_notHidden() {
        setupOverlayViewController1();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(false);
        when(mOverlayViewController1.shouldShowStatusBarInsets()).thenReturn(false);

        setOverlayViewControllerAsShowing(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController, never()).hideInsets(statusBars());
    }

    @Test
    public void showView_nothingAlreadyShown_shouldShowNavBarFalse_navigationBarsHidden() {
        setupOverlayViewController1();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController1.shouldShowNavigationBarInsets()).thenReturn(false);

        setOverlayViewControllerAsShowing(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController, times(1)).hideInsets(navigationBars());
    }

    @Test
    public void showView_nothingAlreadyShown_shouldShowNavBarTrue_navigationBarsShown() {
        setupOverlayViewController1();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController1.shouldShowNavigationBarInsets()).thenReturn(true);

        setOverlayViewControllerAsShowing(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController).showInsets(navigationBars());
    }

    @Test
    public void showView_nothingAlreadyShown_shouldShowStatusBarFalse_statusBarsHidden() {
        setupOverlayViewController1();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController1.shouldShowStatusBarInsets()).thenReturn(false);

        setOverlayViewControllerAsShowing(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController).hideInsets(statusBars());
    }

    @Test
    public void showView_nothingAlreadyShown_shouldShowStatusBarTrue_statusBarsShown() {
        setupOverlayViewController1();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController1.shouldShowStatusBarInsets()).thenReturn(true);

        setOverlayViewControllerAsShowing(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController).showInsets(statusBars());
    }

    @Test
    public void showView_nothingAlreadyShown_fitsNavBarInsets_insetsAdjusted() {
        setupOverlayViewController1();
        when(mOverlayViewController1.getInsetTypesToFit()).thenReturn(navigationBars());

        setOverlayViewControllerAsShowing(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController).setFitInsetsTypes(navigationBars());
    }

    @Test
    public void showView_nothingAlreadyShown_windowIsSetVisible() {
        setupOverlayViewController1();

        setOverlayViewControllerAsShowing(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController).setWindowVisible(true);
    }

    @Test
    public void showView_nothingAlreadyShown_newHighestZOrder() {
        setupOverlayViewController1();

        setOverlayViewControllerAsShowing(mOverlayViewController1, mRunnable);

        assertThat(mOverlayVisibilityMediator.getHighestZOrderOverlayViewController()).isEqualTo(
                mOverlayViewController1);
    }

    @Test
    public void showView_nothingAlreadyShown_newHighestZOrder_isVisible() {
        setupOverlayViewController1();

        setOverlayViewControllerAsShowing(mOverlayViewController1, mRunnable);

        assertThat(mOverlayVisibilityMediator
                .isOverlayViewVisible(mOverlayViewController1)).isTrue();
    }

    @Test
    public void showView_nothingAlreadyShown_descendantsFocusable() {
        setupOverlayViewController1();

        setOverlayViewControllerAsShowing(mOverlayViewController1, mRunnable);

        verify(mOverlayViewController1).setAllowRotaryFocus(true);
    }

    @Test
    public void showView_newHighestZOrder() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2, mRunnable);

        assertThat(mOverlayVisibilityMediator.getHighestZOrderOverlayViewController()).isEqualTo(
                mOverlayViewController2);
    }

    @Test
    public void showView_newHighestZOrder_shouldShowNavBarFalse_navigationBarsHidden() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldShowNavigationBarInsets()).thenReturn(false);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2, mRunnable);

        verify(mSystemUIOverlayWindowController, times(2)).hideInsets(navigationBars());
    }

    @Test
    public void showView_newHighestZOrder_shouldShowNavBarTrue_navigationBarsShown() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldShowNavigationBarInsets()).thenReturn(true);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2, mRunnable);

        verify(mSystemUIOverlayWindowController).showInsets(navigationBars());
    }

    @Test
    public void showView_newHighestZOrder_shouldShowStatusBarFalse_statusBarsHidden() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldShowStatusBarInsets()).thenReturn(false);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2, mRunnable);

        verify(mSystemUIOverlayWindowController, times(2)).hideInsets(statusBars());
    }

    @Test
    public void showView_newHighestZOrder_shouldShowStatusBarTrue_statusBarsShown() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldShowStatusBarInsets()).thenReturn(true);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2, mRunnable);

        verify(mSystemUIOverlayWindowController).showInsets(statusBars());
    }

    @Test
    public void showView_newHighestZOrder_fitsNavBarInsets_insetsAdjusted() {
        setupOverlayViewController1();
        when(mOverlayViewController1.getInsetTypesToFit()).thenReturn(statusBars());
        setupOverlayViewController2();
        when(mOverlayViewController2.getInsetTypesToFit()).thenReturn(navigationBars());
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2, mRunnable);

        verify(mSystemUIOverlayWindowController).setFitInsetsTypes(navigationBars());
    }

    @Test
    public void showView_newHighestZOrder_correctViewsShown() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2, mRunnable);

        assertThat(new ArrayList(mOverlayVisibilityMediator.getVisibleOverlayViewsByZOrder()))
                .isEqualTo(Arrays.asList(mOverlayViewController1, mOverlayViewController2));
    }

    @Test
    public void showView_newHighestZOrder_topDescendantsFocusable() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2, mRunnable);

        verify(mOverlayViewController1).setAllowRotaryFocus(false);
        verify(mOverlayViewController2).setAllowRotaryFocus(true);
    }

    @Test
    public void showView_newHighestZOrder_refreshTopFocus() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2, mRunnable);

        verify(mOverlayViewController1, times(1)).refreshRotaryFocusIfNeeded();
        verify(mOverlayViewController2).refreshRotaryFocusIfNeeded();
    }

    @Test
    public void showView_newHighestZOrder_setDimAmount() {
        float oldDim = 0.1f;
        float newDim = 0.5f;
        setupOverlayViewController1();
        when(mOverlayViewController1.getDefaultDimAmount()).thenReturn(oldDim);
        setupOverlayViewController2();
        when(mOverlayViewController2.getDefaultDimAmount()).thenReturn(newDim);

        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2, mRunnable);

        verify(mSystemUIOverlayWindowController).setDimBehind(newDim);
    }

    @Test
    public void showView_oldHighestZOrder() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1, mRunnable);

        assertThat(mOverlayVisibilityMediator.getHighestZOrderOverlayViewController()).isEqualTo(
                mOverlayViewController2);
    }

    @Test
    public void showView_oldHighestZOrder_shouldShowNavBarFalse_navigationBarsHidden() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController1.shouldShowNavigationBarInsets()).thenReturn(true);
        when(mOverlayViewController2.shouldShowNavigationBarInsets()).thenReturn(false);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1, mRunnable);

        verify(mSystemUIOverlayWindowController, times(2)).hideInsets(navigationBars());
    }

    @Test
    public void showView_oldHighestZOrder_shouldShowNavBarTrue_navigationBarsShown() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController1.shouldShowNavigationBarInsets()).thenReturn(false);
        when(mOverlayViewController2.shouldShowNavigationBarInsets()).thenReturn(true);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1, mRunnable);

        verify(mSystemUIOverlayWindowController, times(2)).showInsets(navigationBars());
    }

    @Test
    public void showView_oldHighestZOrder_shouldShowStatusBarFalse_statusBarsHidden() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController1.shouldShowStatusBarInsets()).thenReturn(true);
        when(mOverlayViewController2.shouldShowStatusBarInsets()).thenReturn(false);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1, mRunnable);

        verify(mSystemUIOverlayWindowController, times(2)).hideInsets(statusBars());
    }

    @Test
    public void showView_oldHighestZOrder_shouldShowStatusBarTrue_statusBarsShown() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController1.shouldShowStatusBarInsets()).thenReturn(false);
        when(mOverlayViewController2.shouldShowStatusBarInsets()).thenReturn(true);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1, mRunnable);

        verify(mSystemUIOverlayWindowController, times(2)).showInsets(statusBars());
    }

    @Test
    public void showView_oldHighestZOrder_fitsNavBarInsets_insetsAdjusted() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController1.getInsetTypesToFit()).thenReturn(statusBars());
        when(mOverlayViewController2.getInsetTypesToFit()).thenReturn(navigationBars());
        when(mOverlayViewController1.getInsetSidesToFit()).thenReturn(
                OverlayViewController.INVALID_INSET_SIDE);
        when(mOverlayViewController2.getInsetSidesToFit()).thenReturn(
                OverlayViewController.INVALID_INSET_SIDE);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1, mRunnable);

        verify(mSystemUIOverlayWindowController, times(2)).setFitInsetsTypes(navigationBars());
    }

    @Test
    public void showView_oldHighestZOrder_correctViewsShown() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1, mRunnable);

        assertThat(new ArrayList(mOverlayVisibilityMediator.getVisibleOverlayViewsByZOrder()))
                .isEqualTo(Arrays.asList(mOverlayViewController1, mOverlayViewController2));
    }

    @Test
    public void showView_oldHighestZOrder_topDescendantsFocusable() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1, mRunnable);

        verify(mOverlayViewController1).setAllowRotaryFocus(false);
        verify(mOverlayViewController2, times(2)).setAllowRotaryFocus(true);
    }

    @Test
    public void showView_oldHighestZOrder_refreshTopFocus() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1, mRunnable);

        verify(mOverlayViewController1, never()).refreshRotaryFocusIfNeeded();
        verify(mOverlayViewController2, times(2)).refreshRotaryFocusIfNeeded();
    }

    @Test
    public void showView_oldHighestZOrder_setDimAmount() {
        float oldDim = 0.1f;
        float newDim = 0.5f;
        setupOverlayViewController1();
        when(mOverlayViewController1.getDefaultDimAmount()).thenReturn(oldDim);
        setupOverlayViewController2();
        when(mOverlayViewController2.getDefaultDimAmount()).thenReturn(newDim);

        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2, mRunnable);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1, mRunnable);

        verify(mSystemUIOverlayWindowController, never()).setDimBehind(oldDim);
        // called twice - once for when each view is shown
        verify(mSystemUIOverlayWindowController, times(2)).setDimBehind(newDim);
    }

    @Test
    public void showView_somethingAlreadyShown_windowVisibleNotCalled() {
        setupOverlayViewController1();
        setupOverlayViewController2();

        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2, mRunnable);

        verify(mSystemUIOverlayWindowController, times(1)).setWindowVisible(true);
    }

    @Test
    public void showView_viewControllerNotInflated_inflateViewController() {
        setupOverlayViewController2();
        when(mOverlayViewController2.isInflated()).thenReturn(false);

        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2, mRunnable);

        verify(mOverlayViewController2).inflate();
    }

    @Test
    public void showView_viewControllerInflated_inflateViewControllerNotCalled() {
        setupOverlayViewController2();
        when(mOverlayViewController2.isInflated()).thenReturn(true);

        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2, mRunnable);

        verify(mOverlayViewController2, never()).inflate();
    }

    @Test
    public void showView_showRunnableCalled() {
        setupOverlayViewController1();

        setOverlayViewControllerAsShowing(mOverlayViewController1, mRunnable);

        verify(mRunnable).run();
    }

    @Test
    public void hideView_viewControllerNotInflated_hideRunnableNotCalled() {
        setupOverlayViewController2();

        mOverlayViewGlobalStateController.hideView(mOverlayViewController2, mRunnable);

        verify(mRunnable, never()).run();
    }

    @Test
    public void hideView_nothingShown_hideRunnableNotCalled() {
        setupOverlayViewController2();
        when(mOverlayViewController2.isInflated()).thenReturn(true);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController2, mRunnable);

        verify(mRunnable, never()).run();
    }

    @Test
    public void hideView_viewControllerNotShown_hideRunnableNotCalled() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController2.isInflated()).thenReturn(true);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController2, mRunnable);

        verify(mRunnable, never()).run();
    }

    @Test
    public void hideView_viewControllerShown_hideRunnableCalled() {
        setupOverlayViewController1();
        setOverlayViewControllerAsShowing(mOverlayViewController1);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController1, mRunnable);

        verify(mRunnable).run();
    }

    @Test
    public void hideView_viewControllerOnlyShown_noHighestZOrder() {
        setupOverlayViewController1();
        setOverlayViewControllerAsShowing(mOverlayViewController1);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController1, mRunnable);

        assertThat(mOverlayVisibilityMediator.getHighestZOrderOverlayViewController()).isNull();
    }

    @Test
    public void hideView_viewControllerOnlyShown_nothingShown() {
        setupOverlayViewController1();
        setOverlayViewControllerAsShowing(mOverlayViewController1);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController1, mRunnable);

        assertThat(mOverlayVisibilityMediator.isAnyOverlayViewVisible()).isFalse();
    }

    @Test
    public void hideView_newHighestZOrder_twoViewsShown() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController2, mRunnable);

        assertThat(mOverlayVisibilityMediator.getHighestZOrderOverlayViewController()).isEqualTo(
                mOverlayViewController1);
    }

    @Test
    public void hideView_newHighestZOrder_threeViewsShown() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        setupOverlayPanelViewController();
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);
        setOverlayViewControllerAsShowing(mOverlayPanelViewController);

        mOverlayViewGlobalStateController.hideView(mOverlayPanelViewController, mRunnable);

        assertThat(mOverlayVisibilityMediator.getHighestZOrderOverlayViewController()).isEqualTo(
                mOverlayViewController2);
    }

    @Test
    public void hideView_newHighestZOrder_shouldShowNavBarFalse_navigationBarHidden() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController1.shouldShowNavigationBarInsets()).thenReturn(false);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController2, mRunnable);

        verify(mSystemUIOverlayWindowController, times(3)).hideInsets(navigationBars());
    }

    @Test
    public void hideView_newHighestZOrder_shouldShowNavBarTrue_navigationBarShown() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController1.shouldShowNavigationBarInsets()).thenReturn(true);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController2, mRunnable);

        verify(mSystemUIOverlayWindowController, times(2)).showInsets(navigationBars());
    }

    @Test
    public void hideView_newHighestZOrder_shouldShowStatusBarFalse_statusBarHidden() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController1.shouldShowStatusBarInsets()).thenReturn(false);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController2, mRunnable);

        verify(mSystemUIOverlayWindowController, times(3)).hideInsets(statusBars());
    }

    @Test
    public void hideView_newHighestZOrder_shouldShowStatusBarTrue_statusBarShown() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController1.shouldShowStatusBarInsets()).thenReturn(true);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController2, mRunnable);

        verify(mSystemUIOverlayWindowController, times(2)).showInsets(statusBars());
    }

    @Test
    public void hideView_newHighestZOrder_fitsNavBarInsets_insetsAdjusted() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController1.getInsetTypesToFit()).thenReturn(navigationBars());
        when(mOverlayViewController2.getInsetTypesToFit()).thenReturn(statusBars());
        when(mOverlayViewController1.getInsetSidesToFit()).thenReturn(
                OverlayViewController.INVALID_INSET_SIDE);
        when(mOverlayViewController2.getInsetSidesToFit()).thenReturn(
                OverlayViewController.INVALID_INSET_SIDE);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController2, mRunnable);

        verify(mSystemUIOverlayWindowController, times(2)).setFitInsetsTypes(navigationBars());
    }

    @Test
    public void hideView_oldHighestZOrder() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController1, mRunnable);

        assertThat(mOverlayVisibilityMediator.getHighestZOrderOverlayViewController()).isEqualTo(
                mOverlayViewController2);
    }

    @Test
    public void hideView_oldHighestZOrder_shouldShowNavBarFalse_navigationBarHidden() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldShowNavigationBarInsets()).thenReturn(false);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController, times(3)).hideInsets(navigationBars());
    }

    @Test
    public void hideView_oldHighestZOrder_shouldShowNavBarTrue_navigationBarShown() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldShowNavigationBarInsets()).thenReturn(true);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController, times(2)).showInsets(navigationBars());
    }

    @Test
    public void hideView_oldHighestZOrder_shouldShowStatusBarFalse_statusBarHidden() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldShowStatusBarInsets()).thenReturn(false);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController, times(3)).hideInsets(statusBars());
    }

    @Test
    public void hideView_oldHighestZOrder_shouldShowStatusBarTrue_statusBarShown() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldFocusWindow()).thenReturn(true);
        when(mOverlayViewController2.shouldShowStatusBarInsets()).thenReturn(true);
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController, times(2)).showInsets(statusBars());
    }

    @Test
    public void hideView_oldHighestZOrder_fitsNavBarInsets_insetsAdjusted() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController1.getInsetSidesToFit()).thenReturn(
                OverlayViewController.INVALID_INSET_SIDE);
        when(mOverlayViewController2.getInsetSidesToFit()).thenReturn(
                OverlayViewController.INVALID_INSET_SIDE);
        when(mOverlayViewController1.getInsetTypesToFit()).thenReturn(statusBars());
        when(mOverlayViewController2.getInsetTypesToFit()).thenReturn(navigationBars());
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController, times(2)).setFitInsetsTypes(navigationBars());
    }

    @Test
    public void hideView_viewControllerNotOnlyShown_windowNotCollapsed() {
        setupOverlayViewController1();
        setupOverlayViewController2();
        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController2, mRunnable);

        verify(mSystemUIOverlayWindowController, never()).setWindowVisible(false);
    }

    @Test
    public void hideView_viewControllerOnlyShown_navigationBarShown() {
        setupOverlayViewController1();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        setOverlayViewControllerAsShowing(mOverlayViewController1);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController).showInsets(navigationBars());
    }

    @Test
    public void hideView_viewControllerOnlyShown_statusBarShown() {
        setupOverlayViewController1();
        when(mOverlayViewController1.shouldFocusWindow()).thenReturn(true);
        setOverlayViewControllerAsShowing(mOverlayViewController1);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController).showInsets(statusBars());
    }

    @Test
    public void hideView_viewControllerOnlyShown_insetsAdjustedToDefault() {
        setupOverlayViewController1();
        setOverlayViewControllerAsShowing(mOverlayViewController1);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController).setFitInsetsTypes(statusBars());
    }

    @Test
    public void hideView_viewControllerOnlyShown_windowCollapsed() {
        setupOverlayViewController1();
        setOverlayViewControllerAsShowing(mOverlayViewController1);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController).setWindowVisible(false);
    }

    @Test
    public void setOccludedTrue_viewToHideWhenOccludedVisible_viewHidden() {
        setupOverlayViewController1();
        when(mOverlayViewController1.shouldShowWhenOccluded()).thenReturn(false);
        setOverlayViewControllerAsShowing(mOverlayViewController1);

        mOverlayViewGlobalStateController.setOccluded(true);

        assertThat(mOverlayVisibilityMediator.getVisibleOverlayViewsByZOrder().contains(
                mOverlayViewController1)).isFalse();
    }

    @Test
    public void setOccludedTrue_viewToNotHideWhenOccludedVisible_viewShown() {
        setupOverlayViewController1();
        when(mOverlayViewController1.shouldShowWhenOccluded()).thenReturn(true);
        setOverlayViewControllerAsShowing(mOverlayViewController1);

        mOverlayViewGlobalStateController.setOccluded(true);

        assertThat(mOverlayVisibilityMediator.getVisibleOverlayViewsByZOrder().contains(
                mOverlayViewController1)).isTrue();
    }

    @Test
    public void hideViewAndThenSetOccludedTrue_viewHiddenForOcclusion_viewHiddenAfterOcclusion() {
        setupOverlayViewController1();
        when(mOverlayViewController1.shouldShowWhenOccluded()).thenReturn(false);
        setOverlayViewControllerAsShowing(mOverlayViewController1);
        mOverlayViewGlobalStateController.setOccluded(true);

        mOverlayViewGlobalStateController.hideView(mOverlayViewController1, /* runnable= */ null);
        mOverlayViewGlobalStateController.setOccluded(false);

        assertThat(mOverlayVisibilityMediator.getVisibleOverlayViewsByZOrder().contains(
                mOverlayViewController1)).isFalse();
    }

    @Test
    public void setOccludedTrueAndThenShowView_viewToNotHideForOcclusion_viewShown() {
        setupOverlayViewController1();
        when(mOverlayViewController1.shouldShowWhenOccluded()).thenReturn(true);

        mOverlayViewGlobalStateController.setOccluded(true);
        setOverlayViewControllerAsShowing(mOverlayViewController1);

        assertThat(mOverlayVisibilityMediator.getVisibleOverlayViewsByZOrder().contains(
                mOverlayViewController1)).isTrue();
    }

    @Test
    public void setOccludedTrueAndThenShowView_viewToHideForOcclusion_viewHidden() {
        setupOverlayViewController1();
        when(mOverlayViewController1.shouldShowWhenOccluded()).thenReturn(false);

        mOverlayViewGlobalStateController.setOccluded(true);
        setOverlayViewControllerAsShowing(mOverlayViewController1);

        assertThat(mOverlayVisibilityMediator.getVisibleOverlayViewsByZOrder().contains(
                mOverlayViewController1)).isFalse();
    }

    @Test
    public void setOccludedFalse_viewShownAfterSetOccludedTrue_viewToHideForOcclusion_viewShown() {
        setupOverlayViewController1();
        when(mOverlayViewController1.shouldShowWhenOccluded()).thenReturn(false);
        mOverlayViewGlobalStateController.setOccluded(true);
        setOverlayViewControllerAsShowing(mOverlayViewController1);

        mOverlayViewGlobalStateController.setOccluded(false);

        assertThat(mOverlayVisibilityMediator.getVisibleOverlayViewsByZOrder()
                .contains(mOverlayViewController1)).isTrue();
    }

    @Test
    public void inflateView_notInflated_inflates() {
        setupOverlayViewController2();
        when(mOverlayViewController2.isInflated()).thenReturn(false);

        mOverlayViewGlobalStateController.ensureInflated(mOverlayViewController2);

        verify(mOverlayViewController2).inflate();
    }

    @Test
    public void inflateView_alreadyInflated_doesNotInflate() {
        setupOverlayViewController2();
        when(mOverlayViewController2.isInflated()).thenReturn(true);

        mOverlayViewGlobalStateController.ensureInflated(mOverlayViewController2);

        verify(mOverlayViewController2, never()).inflate();
    }

    @Test
    public void showView_setInsetsToFitByType_setsFitInsetsType() {
        int insetTypeToFit = navigationBars();
        setupOverlayViewController1();
        when(mOverlayViewController1.getInsetTypesToFit()).thenReturn(insetTypeToFit);

        setOverlayViewControllerAsShowing(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController).setFitInsetsTypes(insetTypeToFit);
    }

    @Test
    public void refreshInsetsToFit_setInsetsToFitBySide_setsFitInsetsSides() {
        int insetSidesToFit = WindowInsets.Side.LEFT;
        setupOverlayViewController1();
        when(mOverlayViewController1.getInsetSidesToFit()).thenReturn(insetSidesToFit);

        setOverlayViewControllerAsShowing(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController).setFitInsetsSides(insetSidesToFit);
    }

    @Test
    public void refreshInsetsToFit_setInsetsToFitBySideUsed_firstFitsAllSystemBars() {
        int insetSidesToFit = WindowInsets.Side.LEFT;
        setupOverlayViewController1();
        when(mOverlayViewController1.getInsetSidesToFit()).thenReturn(insetSidesToFit);

        setOverlayViewControllerAsShowing(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController).setFitInsetsTypes(systemBars());
    }

    @Test
    public void refreshInsetsToFit_bothInsetTypeAndSideDefined_insetSideTakesPrecedence() {
        int insetTypesToFit = navigationBars();
        int insetSidesToFit = WindowInsets.Side.LEFT;
        setupOverlayViewController1();
        when(mOverlayViewController1.getInsetTypesToFit()).thenReturn(insetTypesToFit);
        when(mOverlayViewController1.getInsetSidesToFit()).thenReturn(insetSidesToFit);

        setOverlayViewControllerAsShowing(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController).setFitInsetsSides(insetSidesToFit);
    }

    @Test
    public void refreshInsetsToFit_bothInsetTypeAndSideDefined_insetTypeIgnored() {
        int insetTypesToFit = navigationBars();
        int insetSidesToFit = WindowInsets.Side.LEFT;
        setupOverlayViewController1();
        when(mOverlayViewController1.getInsetTypesToFit()).thenReturn(insetTypesToFit);
        when(mOverlayViewController1.getInsetSidesToFit()).thenReturn(insetSidesToFit);

        setOverlayViewControllerAsShowing(mOverlayViewController1, mRunnable);

        verify(mSystemUIOverlayWindowController, never()).setFitInsetsTypes(insetTypesToFit);
    }

    @Test
    public void updateWindowDimBehind_highestZOrder_updatesDimAmount() {
        float newDim = 0.5f;
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController1.getDefaultDimAmount()).thenReturn(0.0f);
        when(mOverlayViewController2.getDefaultDimAmount()).thenReturn(0.0f);

        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);

        mOverlayViewGlobalStateController.updateWindowDimBehind(mOverlayViewController2, newDim);

        verify(mSystemUIOverlayWindowController).setDimBehind(newDim);
    }

    @Test
    public void updateWindowDimBehind_notHighestZOrder_noDimAmountUpdate() {
        float newDim = 0.5f;
        setupOverlayViewController1();
        setupOverlayViewController2();
        when(mOverlayViewController1.getDefaultDimAmount()).thenReturn(0.0f);
        when(mOverlayViewController2.getDefaultDimAmount()).thenReturn(0.0f);

        setOverlayViewControllerAsShowing(mOverlayViewController1, /* zOrder= */ 1);
        setOverlayViewControllerAsShowing(mOverlayViewController2, /* zOrder= */ 2);

        mOverlayViewGlobalStateController.updateWindowDimBehind(mOverlayViewController1, newDim);

        verify(mSystemUIOverlayWindowController, never()).setDimBehind(newDim);
    }

    private void setupOverlayViewController1() {
        setupOverlayViewController(mOverlayViewController1, TEST_TYPE_1);
    }

    private void setupOverlayViewController2() {
        setupOverlayViewController(mOverlayViewController2, TEST_TYPE_2);
    }

    private void setupOverlayPanelViewController() {
        setupOverlayViewController(mOverlayPanelViewController, TEST_TYPE_PANEL);
    }

    private void setupOverlayViewController(OverlayViewController overlayViewController,
            String type) {
        when(overlayViewController.getOverlayType()).thenReturn(type);
        when(overlayViewController.isInflated()).thenReturn(false);
        doAnswer(invocation -> {
            View mockView = new View(mContext);
            when(overlayViewController.isInflated()).thenReturn(true);
            when(overlayViewController.getLayout()).thenReturn(mockView);
            return mockView;
        }).when(overlayViewController).inflate();
        when(overlayViewController.getInsetSidesToFit()).thenReturn(
                OverlayViewController.INVALID_INSET_SIDE);
    }

    private void setOverlayViewControllerAsShowing(OverlayViewController overlayViewController,
            int zOrder, Runnable runnable) {
        ViewGroup mockBaseLayout = (ViewGroup) mSystemUIOverlayWindowController.getBaseLayout();
        ViewGroup container = mSystemUIOverlayWindowController.getContainerForType(
                overlayViewController.getOverlayType());
        when(mockBaseLayout.indexOfChild(container)).thenReturn(zOrder);
        mOverlayViewGlobalStateController.showView(overlayViewController, runnable);
    }

    private void setOverlayViewControllerAsShowing(OverlayViewController overlayViewController,
            int zOrder) {
        setOverlayViewControllerAsShowing(overlayViewController, zOrder, /* runnable= */ null);
    }

    private void setOverlayViewControllerAsShowing(OverlayViewController overlayViewController,
            Runnable runnable) {
        setOverlayViewControllerAsShowing(overlayViewController, /* zOrder= */ 0, runnable);
    }

    private void setOverlayViewControllerAsShowing(OverlayViewController overlayViewController) {
        setOverlayViewControllerAsShowing(overlayViewController, /* zOrder= */ 0,
                /* runnable= */ null);
    }
}
