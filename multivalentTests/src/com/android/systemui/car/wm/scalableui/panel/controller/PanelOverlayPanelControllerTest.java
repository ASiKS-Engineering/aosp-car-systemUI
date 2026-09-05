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

package com.android.systemui.car.wm.scalableui.panel.controller;

import static com.android.car.scalableui.loader.xml.parser.PanelControllerParser.OVERLAY_PANEL_ID_TAG;
import static com.android.car.scalableui.loader.xml.parser.PanelControllerParser.VIEW_TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.testing.TestableLooper;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewRootImpl;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.car.scalableui.model.Variant;
import com.android.internal.graphics.drawable.BackgroundBlurDrawable;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.wm.scalableui.panel.PanelUtils;
import com.android.systemui.car.wm.scalableui.panel.TaskPanel;
import com.android.systemui.car.wm.scalableui.view.PanelOverlay;
import com.android.systemui.car.wm.scalableui.view.PanelOverlayController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import javax.inject.Provider;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper
@SmallTest
public class PanelOverlayPanelControllerTest extends CarSysuiTestCase {

    private static final String PANEL_ID = "test_panel";
    private static final String OVERLAY_PANEL_ID = "overlay_panel";
    private static final String TEST_PACKAGE = "com.android.car.test.app";

    private PanelOverlayController mController;
    private Context mSpyContext;

    @Mock
    private Resources mMockResources;
    @Mock
    private PanelControllerMetadata mMetadata;
    @Mock
    private PanelUtils mPanelUtils;
    @Mock
    private PanelOverlay mPanelOverlay;
    @Mock
    private TaskPanel mTaskPanel;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private Drawable mDrawable;
    @Mock
    private ViewRootImpl mViewRootImpl;
    @Mock
    private BackgroundBlurDrawable mBackgroundBlurDrawable;
    @Mock
    private Variant mVariant;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mSpyContext = spy(mContext);

        // Mock metadata
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (VIEW_TAG.equals(key)) {
                return PanelOverlay.class.getName();
            }
            if (OVERLAY_PANEL_ID_TAG.equals(key)) {
                return OVERLAY_PANEL_ID;
            }
            return null;
        }).when(mMetadata).getStringConfiguration(anyString());
        assertThat(mMetadata.getStringConfiguration(VIEW_TAG)).isEqualTo(
                PanelOverlay.class.getName());

        // Mock context and resources
        when(mSpyContext.getPackageManager()).thenReturn(mPackageManager);
        when(mSpyContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getDimensionPixelSize(anyInt())).thenReturn(100);
        when(mMockResources.getInteger(anyInt())).thenReturn(10);
        when(mMockResources.getColor(anyInt(), any())).thenReturn(Color.BLACK);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        displayMetrics.density = 1.0f;
        when(mMockResources.getDisplayMetrics()).thenReturn(displayMetrics);
        when(mMockResources.getConfiguration()).thenReturn(new Configuration());

        // Mock panel and view interactions
        when(mPanelUtils.getTaskPanel(any(Predicate.class))).thenAnswer(invocation -> {
            Predicate<TaskPanel> predicate = invocation.getArgument(0);
            return predicate.test(mTaskPanel) ? mTaskPanel : null;
        });
        when(mTaskPanel.getPanelId()).thenReturn(OVERLAY_PANEL_ID);
        when(mTaskPanel.getTopTaskPackageName()).thenReturn(TEST_PACKAGE);
        when(mPackageManager.getApplicationIcon(TEST_PACKAGE)).thenReturn(mDrawable);
        when(mPanelOverlay.getContext()).thenReturn(mSpyContext);
        when(mPanelOverlay.getViewRootImpl()).thenReturn(mViewRootImpl);
        when(mViewRootImpl.createBackgroundBlurDrawable()).thenReturn(mBackgroundBlurDrawable);
        when(mPanelUtils.getCurrentVariant(PANEL_ID)).thenReturn(mVariant);

        Map<Class<?>, Provider<View>> decorPanelViewMap = new HashMap<>();
        decorPanelViewMap.put(PanelOverlay.class, () -> mPanelOverlay);

        mController = new PanelOverlayController(PANEL_ID, mMetadata, decorPanelViewMap,
                mSpyContext,
                mPanelUtils);
        mController.getView(); // Trigger view creation
    }

    @Test
    public void onBeforePanelStateChanged_panelBecomesVisible_updatesOverlay() {
        // GIVEN panel is not visible
        when(mVariant.isVisible()).thenReturn(false);
        mController.onBeforePanelStateChanged(Collections.singleton(PANEL_ID),
                Collections.emptyMap());

        // WHEN panel becomes visible
        when(mVariant.isVisible()).thenReturn(true);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(mPanelOverlay).post(any(Runnable.class));
        mController.onBeforePanelStateChanged(Collections.singleton(PANEL_ID),
                Collections.emptyMap());

        // THEN overlay is updated
        verify(mPanelOverlay).setBackground(mBackgroundBlurDrawable);
        verify(mPanelOverlay).addView(any());
    }

    @Test
    public void onBeforePanelStateChanged_panelBecomesInvisible_doesNothing() {
        // GIVEN panel is already visible
        when(mVariant.isVisible()).thenReturn(true);
        mController.onBeforePanelStateChanged(Collections.singleton(PANEL_ID),
                Collections.emptyMap());
        reset(mPanelOverlay);
        // WHEN panel becomes invisible
        when(mVariant.isVisible()).thenReturn(false);
        mController.onBeforePanelStateChanged(Collections.singleton(PANEL_ID),
                Collections.emptyMap());

        // THEN post is never called
        verify(mPanelOverlay, never()).post(any(Runnable.class));
    }

    @Test
    public void updateVail_packageNotFound_usesDefaultIcon() throws Exception {
        // GIVEN package manager will fail to find the icon
        when(mPackageManager.getApplicationIcon(anyString())).thenThrow(
                new PackageManager.NameNotFoundException());
        when(mSpyContext.getDrawable(anyInt())).thenReturn(mDrawable);
        when(mVariant.isVisible()).thenReturn(true);

        // WHEN panel becomes visible, triggering the update
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(mPanelOverlay).post(any(Runnable.class));
        mController.onBeforePanelStateChanged(Collections.singleton(PANEL_ID),
                Collections.emptyMap());

        // THEN the default drawable is requested and the view is added, called twice due to the
        // setup
        verify(mSpyContext, times(2)).getDrawable(anyInt());
        verify(mPanelOverlay).addView(any());
    }
}
