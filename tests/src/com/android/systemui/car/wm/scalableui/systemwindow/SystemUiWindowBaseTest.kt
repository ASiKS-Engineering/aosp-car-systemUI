/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import android.graphics.Insets
import android.hardware.display.DisplayManager
import android.testing.TestableContext
import android.testing.TestableLooper.RunWithLooper
import android.view.Display
import android.view.DisplayAdjustments
import android.view.View
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.scalableui.model.Event
import com.android.systemui.CarSysuiTestCase
import com.android.systemui.car.CarSystemUiTest
import com.android.systemui.car.wm.scalableui.EventDispatcher
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer
import com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_HIDE_PANEL_EVENT_ID
import com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_SHOW_PANEL_EVENT_ID
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times

@CarSystemUiTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
@SmallTest
class SystemUiWindowBaseTest : CarSysuiTestCase() {

    private val display = mock<Display> {
        on { displayAdjustments } doReturn DisplayAdjustments()
    }
    private val resources = mock<Resources>()
    private val windowManager = mock<WindowManager>()
    private val panelUpdateConsumer = mock<PanelUpdateConsumer>()
    private val eventDispatcher = mock<EventDispatcher>()
    private val view = mock<View>()
    private val displayManager = mock<DisplayManager> {
        on { getDisplay(TEST_DISPLAY_ID) } doReturn display
    }

    private lateinit var systemUiWindowBase: SystemUiWindowBase
    private lateinit var testableContext: TestableContext
    private lateinit var panelUpdateCallback: PanelUpdateConsumer.PanelUpdateCallback

    private val layoutParams = WindowManager.LayoutParams()

    @Before
    fun setUp() {
        testableContext = object : TestableContext(mContext) {
            override fun getBasePackageName(): String {
                return "test.pkg"
            }

            override fun createDisplayContext(display: Display): Context {
                return this
            }

            override fun getDisplay(): Display {
                return this@SystemUiWindowBaseTest.display
            }
        }
        testableContext.addMockSystemService(WindowManager::class.java, windowManager)
        testableContext.addMockSystemService(DisplayManager::class.java, displayManager)

        systemUiWindowBase = object : SystemUiWindowBase(
            testableContext,
            displayManager,
            panelUpdateConsumer,
            eventDispatcher,
            TEST_ID,
            TEST_DISPLAY_ID
        ) {
            override fun getLayoutParams(): WindowManager.LayoutParams {
                return this@SystemUiWindowBaseTest.layoutParams
            }
        }
        val captor = argumentCaptor<PanelUpdateConsumer.PanelUpdateCallback>()
        systemUiWindowBase.setRootView(view, layoutParams)
        verify(panelUpdateConsumer).registerCallback(eq(TEST_ID), captor.capture())
        panelUpdateCallback = captor.firstValue
    }

    @Test
    fun setRootView_addsViewToWindowManager() {
        // The view is added in setUp - verify it was added once.
        verify(windowManager).addView(view, layoutParams)
        verify(panelUpdateConsumer).registerCallback(
            eq(TEST_ID),
            any<PanelUpdateConsumer.PanelUpdateCallback>()
        )
    }

    @Test
    fun setRootView_whenAlreadyExists_removesOldView() {
        val newView = mock<View>()
        systemUiWindowBase.setRootView(newView, layoutParams)

        verify(windowManager).removeView(view)
        verify(windowManager).addView(newView, layoutParams)
    }

    @Test
    fun removeRootView_removesViewFromWindowManager() {
        systemUiWindowBase.removeRootView()

        verify(windowManager).removeView(view)
        verify(panelUpdateConsumer).unregisterCallback(
            eq(TEST_ID),
            any<PanelUpdateConsumer.PanelUpdateCallback>()
        )
    }

    @Test
    fun removeRootView_whenNoRootView_doesNothing() {
        systemUiWindowBase.removeRootView()
        systemUiWindowBase.removeRootView()

        verify(windowManager, times(1)).removeView(view)
    }

    @Test
    fun removeRootViewImmediate_removesViewImmediateFromWindowManager() {
        systemUiWindowBase.removeRootViewImmediate()

        verify(windowManager).removeViewImmediate(view)
        verify(panelUpdateConsumer).unregisterCallback(
            eq(TEST_ID),
            any<PanelUpdateConsumer.PanelUpdateCallback>()
        )
    }

    @Test
    fun show_dispatchesShowEvent() {
        systemUiWindowBase.show()

        val captor = ArgumentCaptor.forClass(Event::class.java)
        verify(eventDispatcher).executeEvent(captor.capture())
        assertThat(captor.value.id).isEqualTo(SYSTEM_SHOW_PANEL_EVENT_ID)
        assertThat(captor.value.panelId).isEqualTo(TEST_ID)
    }

    @Test
    fun hide_dispatchesHideEvent() {
        systemUiWindowBase.hide()

        val captor = ArgumentCaptor.forClass(Event::class.java)
        verify(eventDispatcher).executeEvent(captor.capture())
        assertThat(captor.value.id).isEqualTo(SYSTEM_HIDE_PANEL_EVENT_ID)
        assertThat(captor.value.panelId).isEqualTo(TEST_ID)
    }

    @Test
    fun onBoundsChange_updatesViewLayout() {
        panelUpdateCallback.onBoundsChange(TEST_ID, mock())

        verify(windowManager).updateViewLayout(view, layoutParams)
    }

    @Test
    fun onInsetsChange_updatesViewPadding() {
        val insets = Insets.of(1, 2, 3, 4)
        panelUpdateCallback.onInsetsChange(TEST_ID, insets)

        verify(view).setPadding(1, 2, 3, 4)
    }

    @Test
    fun onAlphaChange_updatesViewAlpha() {
        panelUpdateCallback.onAlphaChange(TEST_ID, 0.5f)

        verify(view).setAlpha(0.5f)
    }

    @Test
    fun onVisibilityChange_toVisible_updatesViewVisibility() {
        panelUpdateCallback.onVisibilityChange(TEST_ID, true)

        verify(view).setVisibility(View.VISIBLE)
    }

    @Test
    fun onVisibilityChange_toGone_updatesViewVisibility() {
        panelUpdateCallback.onVisibilityChange(TEST_ID, false)

        verify(view).setVisibility(View.GONE)
    }

    @Test
    fun onGravityChange_updatesViewLayout() {
        panelUpdateCallback.onGravityChange(TEST_ID, 0)

        verify(windowManager).updateViewLayout(view, layoutParams)
    }

    @Test
    fun onBoundsChange_noRootView_doesNothing() {
        systemUiWindowBase.removeRootView()
        panelUpdateCallback.onBoundsChange(TEST_ID, mock())

        verify(windowManager, never()).updateViewLayout(any(), any())
    }

    @Test
    fun onGravityChange_noRootView_doesNothing() {
        systemUiWindowBase.removeRootView()
        panelUpdateCallback.onGravityChange(TEST_ID, 0)

        verify(windowManager, never()).updateViewLayout(any(), any())
    }

    @Test
    fun addAndRemoveCallback_registersAndUnregistersCallback() {
        val callback = mock<SystemUiWindow.WindowUpdateCallback>()
        systemUiWindowBase.addCallback(callback)
        verify(panelUpdateConsumer).registerCallback(TEST_ID, callback)

        systemUiWindowBase.removeCallback(callback)
        verify(panelUpdateConsumer).unregisterCallback(TEST_ID, callback)
    }

    companion object {
        private const val TEST_ID = "test_id"
        private const val TEST_DISPLAY_ID = 1
    }
}
