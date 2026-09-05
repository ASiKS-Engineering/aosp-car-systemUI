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
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.settings.DisplayTracker
import java.util.concurrent.Executor
import javax.inject.Inject

@SysUISingleton
open class DisplayStateHelper @Inject constructor(
    private val displayTracker: DisplayTracker,
    @Background private val bgExecutor: Executor
) {

    private val displayStates = mutableMapOf<Int, Boolean>()
    private val listeners = mutableListOf<Listener>()

    private val displayTrackerCallback = object : DisplayTracker.Callback {
        override fun onDisplayChanged(displayId: Int) {
            val display = displayTracker.getDisplay(displayId) ?: return
            val isOn = display.state == Display.STATE_ON
            var changed = false
            synchronized(displayStates) {
                if (!displayStates.containsKey(displayId) || displayStates[displayId] != isOn) {
                    displayStates[displayId] = isOn
                    changed = true
                }
            }
            if (changed) {
                synchronized(listeners) {
                    listeners.forEach {it.onDisplayPowerStateChanged(displayId, isOn)}
                }
            }
        }
    }

    init {
        synchronized(displayStates) {
            displayTracker.allDisplays.forEach {
                displayStates.put(
                    it.displayId,
                    it.state == Display.STATE_ON
                )
            }
        }
        displayTracker.addDisplayChangeCallback(displayTrackerCallback, bgExecutor)
    }

    fun addListener(listener: Listener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: Listener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    interface Listener {
        fun onDisplayPowerStateChanged(displayId: Int, isOn: Boolean)
    }
}
