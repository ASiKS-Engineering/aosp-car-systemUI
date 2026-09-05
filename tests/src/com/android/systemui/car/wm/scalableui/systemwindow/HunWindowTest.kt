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

package com.android.systemui.car.wm.scalableui.systemwindow

import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import androidx.test.filters.SmallTest
import com.android.car.scalableui.loader.xml.parser.HunPanelParser
import com.android.systemui.CarSysuiTestCase
import com.android.systemui.car.wm.scalableui.EventDispatcher
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidTestingRunner::class)
@TestableLooper.RunWithLooper
class HunWindowTest : CarSysuiTestCase() {

    private lateinit var hunWindow: HunWindow

    @Mock
    private lateinit var context: Context
    @Mock
    private lateinit var displayManager: DisplayManager
    @Mock
    private lateinit var consumer: PanelUpdateConsumer
    @Mock
    private lateinit var eventDispatcher: EventDispatcher
    @Mock
    private lateinit var display: Display
    @Mock
    private lateinit var displayContext: Context
    @Mock
    private lateinit var resources: Resources

    private val displayId = 1
    private val displayMetrics = DisplayMetrics()

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        whenever(displayManager.getDisplay(displayId)).thenReturn(display)
        whenever(context.createDisplayContext(display)).thenReturn(displayContext)
        whenever(displayContext.resources).thenReturn(resources)
        whenever(resources.displayMetrics).thenReturn(displayMetrics)
        displayMetrics.widthPixels = 1000
        displayMetrics.heightPixels = 500
        hunWindow = HunWindow(context, displayManager, consumer, eventDispatcher, displayId)
    }

    @Test
    fun getLayoutParams_boundsExist_layoutParamsAreReturned() {
        val bounds = Rect(100, 100, 900, 200)
        whenever(consumer.getBounds(HunPanelParser.HUN_PANEL_ID)).thenReturn(bounds)
        whenever(consumer.getGravity(HunPanelParser.HUN_PANEL_ID)).thenReturn(Gravity.TOP)

        val layoutParams = hunWindow.getLayoutParams()

        assertThat(layoutParams).isNotNull()
        assertThat(layoutParams?.width).isEqualTo(bounds.width())
        assertThat(layoutParams?.title).isEqualTo(HunWindow.WINDOW_TITLE)
        assertThat(layoutParams?.gravity).isEqualTo(Gravity.TOP)
    }

    @Test
    fun getLayoutParams_boundsDontExist_nullIsReturned() {
        whenever(consumer.getBounds(HunPanelParser.HUN_PANEL_ID)).thenReturn(null)

        val layoutParams = hunWindow.getLayoutParams()

        assertThat(layoutParams).isNull()
    }

    @Test
    fun getGravity_returnsGravityFromConsumer() {
        whenever(consumer.getGravity(HunPanelParser.HUN_PANEL_ID))
                .thenReturn(Gravity.BOTTOM)

        val gravity = hunWindow.gravity

        assertThat(gravity).isEqualTo(Gravity.BOTTOM)
    }
}
