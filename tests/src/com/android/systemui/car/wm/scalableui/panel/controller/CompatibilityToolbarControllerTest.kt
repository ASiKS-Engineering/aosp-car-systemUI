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
package com.android.systemui.car.wm.scalableui.panel.controller

import android.app.ActivityManager
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ActivityInfo.RESIZE_MODE_RESIZEABLE
import android.content.pm.ActivityInfo.RESIZE_MODE_UNRESIZEABLE
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.CarSysuiTestCase
import com.android.systemui.car.CarSystemUiTest
import com.android.systemui.car.wm.scalableui.EventDispatcher
import com.android.systemui.car.wm.scalableui.panel.ui.CompatibilityToolbar
import com.android.systemui.car.wm.scalableui.panel.ui.CompatibilityToolbarUiState
import com.android.wm.shell.ShellTaskOrganizer
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@CarSystemUiTest
@RunWith(AndroidJUnit4::class)
@SmallTest
class CompatibilityToolbarControllerTest : CarSysuiTestCase() {
    private val TEST_PANEL_ID = "TEST_PANEL_ID"
    private val TEST_TOP_ACTIVITY_COMPONENT = ComponentName("test.pkg", "test.class")

    private val shellTaskOrganizer = mock<ShellTaskOrganizer>()
    private val eventDispatcher = mock<EventDispatcher>()
    private val uiState = mock<CompatibilityToolbarUiState>()
    private val runningTaskInfo = mock<ActivityManager.RunningTaskInfo>()
    private val activityInfo = mock<ActivityInfo>()

    private lateinit var compatibilityToolbarController: CompatibilityToolbarController
    private lateinit var testCompatibilityToolbar: CompatibilityToolbar

    @Before
    fun setUp() {
        testCompatibilityToolbar = CompatibilityToolbar(mContext)
        compatibilityToolbarController = CompatibilityToolbarController(
            mContext, shellTaskOrganizer, eventDispatcher, uiState, TEST_PANEL_ID
        )
    }

    @Test
    fun createView_topActivyNotResizable_aspectRatioButton_visible() {
        runningTaskInfo.topActivity = TEST_TOP_ACTIVITY_COMPONENT
        activityInfo.resizeMode = RESIZE_MODE_UNRESIZEABLE
        runningTaskInfo.topActivityInfo = activityInfo

        val toolbar = compatibilityToolbarController.createView(runningTaskInfo)

        assertThat(
            (toolbar as CompatibilityToolbar).aspectRatioButton?.visibility
        ).isEqualTo(View.VISIBLE)
    }

    @Test
    fun createView_topActivyResizable_aspectRatioButton_gone() {
        runningTaskInfo.topActivity = TEST_TOP_ACTIVITY_COMPONENT
        activityInfo.resizeMode = RESIZE_MODE_RESIZEABLE
        runningTaskInfo.topActivityInfo = activityInfo

        val toolbar = compatibilityToolbarController.createView(runningTaskInfo)

        assertThat(
            (toolbar as CompatibilityToolbar).aspectRatioButton?.visibility
        ).isEqualTo(View.GONE)
    }

    @Test
    fun createView_topActivyNotPresent_aspectRatioButton_gone() {
        runningTaskInfo.topActivity = null
        activityInfo.resizeMode = RESIZE_MODE_RESIZEABLE
        runningTaskInfo.topActivityInfo = activityInfo

        val toolbar = compatibilityToolbarController.createView(runningTaskInfo)

        assertThat(
            (toolbar as CompatibilityToolbar).aspectRatioButton?.visibility
        ).isEqualTo(View.GONE)
    }

    @Test
    fun createView_topActivyPresent_displayDensityButton_visible() {
        runningTaskInfo.topActivity = TEST_TOP_ACTIVITY_COMPONENT
        runningTaskInfo.topActivityInfo = activityInfo

        val toolbar = compatibilityToolbarController.createView(runningTaskInfo)

        assertThat((toolbar as CompatibilityToolbar).displayDensityButton?.visibility).isEqualTo(
            View.VISIBLE
        )
    }

    @Test
    fun createView_topActivyNotPresent_displayDensityButton_gone() {
        runningTaskInfo.topActivity = null

        val toolbar = compatibilityToolbarController.createView(runningTaskInfo)

        assertThat((toolbar as CompatibilityToolbar).displayDensityButton?.visibility).isEqualTo(
            View.GONE
        )
    }

    @Test
    fun updateView_topActivyNotResizable_aspectRatioButton_visible() {
        runningTaskInfo.topActivity = TEST_TOP_ACTIVITY_COMPONENT
        activityInfo.resizeMode = RESIZE_MODE_UNRESIZEABLE
        runningTaskInfo.topActivityInfo = activityInfo
        testCompatibilityToolbar.aspectRatioButton?.visibility = View.GONE

        compatibilityToolbarController.updateView(testCompatibilityToolbar, runningTaskInfo)

        assertThat((testCompatibilityToolbar).aspectRatioButton?.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun updateView_topActivyResizable_aspectRatioButton_gone() {
        runningTaskInfo.topActivity = TEST_TOP_ACTIVITY_COMPONENT
        activityInfo.resizeMode = RESIZE_MODE_RESIZEABLE
        runningTaskInfo.topActivityInfo = activityInfo
        testCompatibilityToolbar.aspectRatioButton?.visibility = View.VISIBLE

        compatibilityToolbarController.updateView(testCompatibilityToolbar, runningTaskInfo)

        assertThat((testCompatibilityToolbar).aspectRatioButton?.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun updateView_topActivyNotPresent_aspectRatioButton_gone() {
        runningTaskInfo.topActivity = null
        activityInfo.resizeMode = RESIZE_MODE_RESIZEABLE
        runningTaskInfo.topActivityInfo = activityInfo
        testCompatibilityToolbar.aspectRatioButton?.visibility = View.VISIBLE

        compatibilityToolbarController.updateView(testCompatibilityToolbar, runningTaskInfo)

        assertThat((testCompatibilityToolbar).aspectRatioButton?.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun updateView_topActivyPresent_displayDensityButton_visible() {
        runningTaskInfo.topActivity = TEST_TOP_ACTIVITY_COMPONENT
        runningTaskInfo.topActivityInfo = activityInfo
        testCompatibilityToolbar.displayDensityButton?.visibility = View.GONE

        compatibilityToolbarController.updateView(testCompatibilityToolbar, runningTaskInfo)

        assertThat(
            (testCompatibilityToolbar).displayDensityButton?.visibility
        ).isEqualTo(View.VISIBLE)
    }

    @Test
    fun updateView_topActivyNotPresent_displayDensityButton_gone() {
        runningTaskInfo.topActivity = null
        testCompatibilityToolbar.displayDensityButton?.visibility = View.VISIBLE

        compatibilityToolbarController.updateView(testCompatibilityToolbar, runningTaskInfo)

        assertThat((testCompatibilityToolbar).displayDensityButton?.visibility).isEqualTo(View.GONE)
    }
}
