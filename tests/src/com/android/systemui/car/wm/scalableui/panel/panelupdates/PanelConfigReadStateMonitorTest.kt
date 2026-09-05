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
package com.android.systemui.car.wm.scalableui.panel.panelupdates

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.CarSysuiTestCase
import com.android.systemui.car.CarSystemUiTest
import com.android.systemui.car.flags.Flag
import com.android.systemui.car.flags.FlagManager
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@CarSystemUiTest
@RunWith(AndroidJUnit4::class)
@SmallTest
class PanelConfigReadStateMonitorTest : CarSysuiTestCase() {

    private lateinit var monitor: PanelConfigReadStateMonitor
    private val listener = mock<PanelConfigReadStateMonitor.Listener>()
    private val flagManager = mock<FlagManager> {
        on { isEnabled(Flag.EnableExtPanelUpdates) } doReturn true
    }

    @Before
    fun setUp() {
        monitor = PanelConfigReadStateMonitor(flagManager)
    }

    @Test
    fun isReady_initially_returnsFalse() {
        assertThat(monitor.isReady()).isFalse()
    }

    @Test
    fun setReady_true_isReadyReturnsTrue() {
        monitor.setReady(true)
        assertThat(monitor.isReady()).isTrue()
    }

    @Test
    fun setReady_false_isReadyReturnsFalse() {
        monitor.setReady(true)
        monitor.setReady(false)
        assertThat(monitor.isReady()).isFalse()
    }

    @Test
    fun setReady_true_notifiesListeners() {
        monitor.addListener(listener)
        monitor.setReady(true)
        verify(listener).onReady()
    }

    @Test
    fun setReady_false_doesNotNotifyListeners() {
        monitor.addListener(listener)
        monitor.setReady(false)
        verify(listener, never()).onReady()
    }

    @Test
    fun setReady_trueTwice_notifiesListenersOnlyOnce() {
        monitor.addListener(listener)
        monitor.setReady(true)
        monitor.setReady(true)
        verify(listener).onReady()
    }

    @Test
    fun removeListener_listenerIsNotNotified() {
        monitor.addListener(listener)
        monitor.removeListener(listener)
        monitor.setReady(true)
        verify(listener, never()).onReady()
    }
}
