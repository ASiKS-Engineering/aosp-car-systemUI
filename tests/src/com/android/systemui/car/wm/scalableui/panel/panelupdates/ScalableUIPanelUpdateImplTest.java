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

package com.android.systemui.car.wm.scalableui.panel.panelupdates;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.testing.TestableLooper;
import android.view.Gravity;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.car.scalableui.model.Corner;
import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@SmallTest
@TestableLooper.RunWithLooper
public class ScalableUIPanelUpdateImplTest extends CarSysuiTestCase {

    private static final String TEST_PANEL_ID_1 = "PANEL_ID_1";
    private static final String TEST_PANEL_ID_2 = "PANEL_ID_2";

    private ScalableUIPanelUpdateImpl mPanelUpdateManager;
    private Handler mMainHandler;

    @Mock
    private PanelUpdateConsumer.PanelUpdateCallback mMockCallback1;
    @Mock
    private PanelUpdateConsumer.PanelUpdateCallback mMockCallback2;
    @Mock
    private PanelControllerMetadata mMockMetadata;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mPanelUpdateManager = new ScalableUIPanelUpdateImpl();
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    @Test
    public void registerCallback_receivesLastKnownState() {
        // Arrange: Post all types of state updates for a panel.
        Rect testBounds = new Rect(0, 0, 100, 100);
        float testAlpha = 0.5f;
        Corner testRadius = new Corner.Builder().setRadius(10).build();
        boolean testVisibility = true;
        Insets testInsets = Insets.of(1, 2, 3, 4);
        int testGravity = Gravity.CENTER;

        mPanelUpdateManager.postBounds(TEST_PANEL_ID_1, testBounds);
        mPanelUpdateManager.postAlpha(TEST_PANEL_ID_1, testAlpha);
        mPanelUpdateManager.postCornerRadius(TEST_PANEL_ID_1, testRadius);
        mPanelUpdateManager.postVisibility(TEST_PANEL_ID_1, testVisibility);
        mPanelUpdateManager.postInsets(TEST_PANEL_ID_1, testInsets);
        mPanelUpdateManager.postGravity(TEST_PANEL_ID_1, testGravity);
        mPanelUpdateManager.postControllerMetadata(TEST_PANEL_ID_1, mMockMetadata);
        mMainHandler.runWithScissors(() -> {}, 0);

        // Act: Register a new callback.
        mPanelUpdateManager.registerCallback(TEST_PANEL_ID_1, mMockCallback1);
        mMainHandler.runWithScissors(() -> {}, 0);

        // Assert: The new callback immediately receives all the cached values.
        verify(mMockCallback1).onBoundsChange(TEST_PANEL_ID_1, testBounds);
        verify(mMockCallback1).onAlphaChange(TEST_PANEL_ID_1, testAlpha);
        verify(mMockCallback1).onCornerRadiusChange(TEST_PANEL_ID_1, testRadius);
        verify(mMockCallback1).onVisibilityChange(TEST_PANEL_ID_1, testVisibility);
        verify(mMockCallback1).onInsetsChange(TEST_PANEL_ID_1, testInsets);
        verify(mMockCallback1).onGravityChange(TEST_PANEL_ID_1, testGravity);
    }

    @Test
    public void postBounds_notifiesRegisteredCallbacks() {
        mPanelUpdateManager.registerCallback(TEST_PANEL_ID_1, mMockCallback1);
        Rect testBounds = new Rect(10, 20, 30, 40);

        mPanelUpdateManager.postBounds(TEST_PANEL_ID_1, testBounds);
        mMainHandler.runWithScissors(() -> {}, 0);

        verify(mMockCallback1).onBoundsChange(TEST_PANEL_ID_1, testBounds);
    }

    @Test
    public void postAlpha_notifiesRegisteredCallbacks() {
        mPanelUpdateManager.registerCallback(TEST_PANEL_ID_1, mMockCallback1);
        float testAlpha = 0.8f;

        mPanelUpdateManager.postAlpha(TEST_PANEL_ID_1, testAlpha);
        mMainHandler.runWithScissors(() -> {}, 0);

        verify(mMockCallback1).onAlphaChange(TEST_PANEL_ID_1, testAlpha);
    }

    @Test
    public void postCornerRadius_notifiesRegisteredCallbacks() {
        mPanelUpdateManager.registerCallback(TEST_PANEL_ID_1, mMockCallback1);
        Corner testRadius = new Corner.Builder().setRadius(25).build();

        mPanelUpdateManager.postCornerRadius(TEST_PANEL_ID_1, testRadius);
        mMainHandler.runWithScissors(() -> {}, 0);

        verify(mMockCallback1).onCornerRadiusChange(TEST_PANEL_ID_1, testRadius);
    }

    @Test
    public void postVisibility_notifiesRegisteredCallbacks() {
        mPanelUpdateManager.registerCallback(TEST_PANEL_ID_1, mMockCallback1);
        boolean testVisibility = false;

        mPanelUpdateManager.postVisibility(TEST_PANEL_ID_1, testVisibility);
        mMainHandler.runWithScissors(() -> {}, 0);

        verify(mMockCallback1).onVisibilityChange(TEST_PANEL_ID_1, testVisibility);
    }

    @Test
    public void postInsets_notifiesRegisteredCallbacks() {
        mPanelUpdateManager.registerCallback(TEST_PANEL_ID_1, mMockCallback1);
        Insets testInsets = Insets.of(5, 10, 15, 20);

        mPanelUpdateManager.postInsets(TEST_PANEL_ID_1, testInsets);
        mMainHandler.runWithScissors(() -> {}, 0);

        verify(mMockCallback1).onInsetsChange(TEST_PANEL_ID_1, testInsets);
    }

    @Test
    public void postGravity_notifiesRegisteredCallbacks() {
        mPanelUpdateManager.registerCallback(TEST_PANEL_ID_1, mMockCallback1);
        int testGravity = Gravity.BOTTOM | Gravity.END;

        mPanelUpdateManager.postGravity(TEST_PANEL_ID_1, testGravity);
        mMainHandler.runWithScissors(() -> {}, 0);

        verify(mMockCallback1).onGravityChange(TEST_PANEL_ID_1, testGravity);
    }

    @Test
    public void unregisterCallback_singleArgument_stopsNotifications() {
        mPanelUpdateManager.registerCallback(TEST_PANEL_ID_1, mMockCallback1);
        mPanelUpdateManager.registerCallback(TEST_PANEL_ID_2, mMockCallback1);

        mPanelUpdateManager.unregisterCallback(mMockCallback1);

        mPanelUpdateManager.postVisibility(TEST_PANEL_ID_1, true);
        mPanelUpdateManager.postVisibility(TEST_PANEL_ID_2, true);
        mMainHandler.runWithScissors(() -> {}, 0);

        verify(mMockCallback1, never()).onVisibilityChange(TEST_PANEL_ID_1, true);
        verify(mMockCallback1, never()).onVisibilityChange(TEST_PANEL_ID_2, true);
    }

    @Test
    public void unregisterCallback_twoArguments_stopsNotificationsForOnePanel() {
        mPanelUpdateManager.registerCallback(TEST_PANEL_ID_1, mMockCallback1);
        mPanelUpdateManager.registerCallback(TEST_PANEL_ID_2, mMockCallback1);

        mPanelUpdateManager.unregisterCallback(TEST_PANEL_ID_1, mMockCallback1);

        mPanelUpdateManager.postVisibility(TEST_PANEL_ID_1, true);
        mPanelUpdateManager.postVisibility(TEST_PANEL_ID_2, false);
        mMainHandler.runWithScissors(() -> {}, 0);

        verify(mMockCallback1, never()).onVisibilityChange(TEST_PANEL_ID_1, true);
        verify(mMockCallback1).onVisibilityChange(TEST_PANEL_ID_2, false);
    }

    @Test
    public void multipleCallbacks_allNotified() {
        mPanelUpdateManager.registerCallback(TEST_PANEL_ID_1, mMockCallback1);
        mPanelUpdateManager.registerCallback(TEST_PANEL_ID_1, mMockCallback2);

        mPanelUpdateManager.postVisibility(TEST_PANEL_ID_1, true);
        mMainHandler.runWithScissors(() -> {}, 0);

        verify(mMockCallback1).onVisibilityChange(TEST_PANEL_ID_1, true);
        verify(mMockCallback2).onVisibilityChange(TEST_PANEL_ID_1, true);
    }

    @Test
    public void unregisterOneOfMultipleCallbacks_othersStillNotified() {
        mPanelUpdateManager.registerCallback(TEST_PANEL_ID_1, mMockCallback1);
        mPanelUpdateManager.registerCallback(TEST_PANEL_ID_1, mMockCallback2);

        mPanelUpdateManager.unregisterCallback(TEST_PANEL_ID_1, mMockCallback1);

        mPanelUpdateManager.postVisibility(TEST_PANEL_ID_1, true);
        mMainHandler.runWithScissors(() -> {}, 0);

        verify(mMockCallback1, never()).onVisibilityChange(TEST_PANEL_ID_1, true);
        verify(mMockCallback2).onVisibilityChange(TEST_PANEL_ID_1, true);
    }


    @Test
    public void getters_returnCorrectValues() {
        Rect testBounds = new Rect(0, 0, 100, 100);
        float testAlpha = 0.5f;
        Corner testRadius = new Corner.Builder().setRadius(10).build();
        boolean testVisibility = true;
        Insets testInsets = Insets.of(1, 2, 3, 4);
        int testGravity = Gravity.CENTER;

        mPanelUpdateManager.postBounds(TEST_PANEL_ID_1, testBounds);
        mPanelUpdateManager.postAlpha(TEST_PANEL_ID_1, testAlpha);
        mPanelUpdateManager.postCornerRadius(TEST_PANEL_ID_1, testRadius);
        mPanelUpdateManager.postVisibility(TEST_PANEL_ID_1, testVisibility);
        mPanelUpdateManager.postInsets(TEST_PANEL_ID_1, testInsets);
        mPanelUpdateManager.postGravity(TEST_PANEL_ID_1, testGravity);
        mPanelUpdateManager.postControllerMetadata(TEST_PANEL_ID_1, mMockMetadata);

        assertThat(mPanelUpdateManager.getBounds(TEST_PANEL_ID_1)).isEqualTo(testBounds);
        assertThat(mPanelUpdateManager.getAlpha(TEST_PANEL_ID_1)).isEqualTo(testAlpha);
        assertThat(mPanelUpdateManager.getCornerRadius(TEST_PANEL_ID_1)).isEqualTo(testRadius);
        assertThat(mPanelUpdateManager.isVisible(TEST_PANEL_ID_1)).isEqualTo(testVisibility);
        assertThat(mPanelUpdateManager.getInsets(TEST_PANEL_ID_1)).isEqualTo(testInsets);
        assertThat(mPanelUpdateManager.getGravity(TEST_PANEL_ID_1)).isEqualTo(testGravity);
        assertThat(mPanelUpdateManager.getPanelControllerMetadata(TEST_PANEL_ID_1)).isEqualTo(
                mMockMetadata);
    }

    @Test
    public void getters_returnNullOrDefaults_whenNoStateExists() {
        assertThat(mPanelUpdateManager.getBounds(TEST_PANEL_ID_1)).isNull();
        assertThat(mPanelUpdateManager.getAlpha(TEST_PANEL_ID_1)).isNull();
        assertThat(mPanelUpdateManager.getCornerRadius(TEST_PANEL_ID_1)).isNull();
        assertThat(mPanelUpdateManager.isVisible(TEST_PANEL_ID_1)).isNull();
        assertThat(mPanelUpdateManager.getInsets(TEST_PANEL_ID_1)).isNull();
        assertThat(mPanelUpdateManager.getPanelControllerMetadata(TEST_PANEL_ID_1)).isNull();
        assertThat(mPanelUpdateManager.getGravity(TEST_PANEL_ID_1)).isEqualTo(Gravity.NO_GRAVITY);
    }
}
