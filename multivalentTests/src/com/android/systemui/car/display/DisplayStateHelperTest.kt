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
package com.android.systemui.car.display

import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.CarSysuiTestCase
import com.android.systemui.car.CarSystemUiTest
import com.android.systemui.settings.DisplayTracker
import java.util.concurrent.Executor
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@CarSystemUiTest
@RunWith(AndroidJUnit4::class)
@SmallTest
class DisplayStateHelperTest : CarSysuiTestCase() {

    private val DISPLAY_ID_1 = 100
    private val DISPLAY_ID_2 = 101

    @Mock
    private lateinit var displayTracker: DisplayTracker
    @Mock
    private lateinit var listener: DisplayStateHelper.Listener

    private lateinit var displayStateHelper: DisplayStateHelper
    private lateinit var displayTrackerCallback: DisplayTracker.Callback

    private lateinit var callbackCaptor: KArgumentCaptor<DisplayTracker.Callback>

    private val bgExecutor: Executor = Executor { it.run() }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        callbackCaptor = argumentCaptor<DisplayTracker.Callback>()
    }

    @Test
    fun testInitialDisplayStates() {
        val display1 = mock<Display>()
        whenever(display1.displayId).thenReturn(DISPLAY_ID_1)
        whenever(display1.state).thenReturn(Display.STATE_ON)
        val display2 = mock<Display>()
        whenever(display2.displayId).thenReturn(DISPLAY_ID_2)
        whenever(display2.state).thenReturn(Display.STATE_OFF)
        whenever(displayTracker.allDisplays).thenReturn(arrayOf(display1, display2))

        displayStateHelper = DisplayStateHelper(displayTracker, bgExecutor)
        verify(displayTracker).addDisplayChangeCallback(callbackCaptor.capture(), any())
        displayTrackerCallback = callbackCaptor.firstValue

        displayTrackerCallback.onDisplayChanged(1)
        verify(listener, never()).onDisplayPowerStateChanged(DISPLAY_ID_1, true)

        displayTrackerCallback.onDisplayChanged(2)
        verify(listener, never()).onDisplayPowerStateChanged(DISPLAY_ID_2, false)
    }

    @Test
    fun testDisplayPowerStateChanged_on() {
        val display = mock<Display>()
        whenever(display.displayId).thenReturn(1)
        whenever(display.state).thenReturn(Display.STATE_OFF)
        whenever(displayTracker.allDisplays).thenReturn(arrayOf(display))
        whenever(displayTracker.getDisplay(1)).thenReturn(display)

        displayStateHelper = DisplayStateHelper(displayTracker, bgExecutor)
        displayStateHelper.addListener(listener)
        verify(displayTracker).addDisplayChangeCallback(callbackCaptor.capture(), any())
        displayTrackerCallback = callbackCaptor.firstValue

        whenever(display.state).thenReturn(Display.STATE_ON)
        displayTrackerCallback.onDisplayChanged(1)

        verify(listener).onDisplayPowerStateChanged(1, true)
    }

    @Test
    fun testDisplayPowerStateChanged_off() {
        val display = mock<Display>()
        whenever(display.displayId).thenReturn(1)
        whenever(display.state).thenReturn(Display.STATE_ON)
        whenever(displayTracker.allDisplays).thenReturn(arrayOf(display))
        whenever(displayTracker.getDisplay(1)).thenReturn(display)

        displayStateHelper = DisplayStateHelper(displayTracker, bgExecutor)
        displayStateHelper.addListener(listener)
        verify(displayTracker).addDisplayChangeCallback(callbackCaptor.capture(), any())
        displayTrackerCallback = callbackCaptor.firstValue

        whenever(display.state).thenReturn(Display.STATE_OFF)
        displayTrackerCallback.onDisplayChanged(1)

        verify(listener).onDisplayPowerStateChanged(1, false)
    }

    @Test
    fun testRemoveListener() {
        val display = mock<Display>()
        whenever(display.displayId).thenReturn(1)
        whenever(display.state).thenReturn(Display.STATE_ON)
        whenever(displayTracker.allDisplays).thenReturn(arrayOf(display))
        whenever(displayTracker.getDisplay(1)).thenReturn(display)

        displayStateHelper = DisplayStateHelper(displayTracker, bgExecutor)
        displayStateHelper.addListener(listener)
        verify(displayTracker).addDisplayChangeCallback(callbackCaptor.capture(), any())
        displayTrackerCallback = callbackCaptor.firstValue

        displayStateHelper.removeListener(listener)
        whenever(display.state).thenReturn(Display.STATE_OFF)
        displayTrackerCallback.onDisplayChanged(1)

        verify(listener, never()).onDisplayPowerStateChanged(1, false)
    }
}
