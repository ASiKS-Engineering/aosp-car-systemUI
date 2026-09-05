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

import com.android.systemui.car.flags.Flag
import com.android.systemui.car.flags.FlagManager
import com.android.wm.shell.dagger.WMSingleton
import javax.inject.Inject

/**
 * Monitors the read state of the panel configuration file.
 *
 * The panel configuration is read from a file, a process that can race with other components that
 * depend on this configuration. This monitor provides a centralized way for dependent components to
 * track whether the configuration has been successfully read.
 *
 * Components can add a [Listener] to be notified when the configuration is ready.
 */
@WMSingleton
class PanelConfigReadStateMonitor @Inject constructor(
    private val flagManager: FlagManager
) {
    private var state = false
    private val listeners: MutableSet<Listener> = mutableSetOf()

    /**
     * Returns `true` if the panel configuration has been successfully read, `false` otherwise.
     */
    fun isReady(): Boolean = !flagManager.isEnabled(Flag.EnableExtPanelUpdates) || state

    /**
     * Sets the readiness state of the panel configuration.
     *
     * When the state changes to `true`, all registered [Listener]s are notified via their
     * [Listener.onReady] callback.
     *
     * @param newState The new readiness state.
     */
    fun setReady(newState: Boolean) {
        if (newState == state || !flagManager.isEnabled(Flag.EnableExtPanelUpdates)) {
            return
        }
        state = newState
        if (state) {
            listeners.forEach { listener ->
                listener.onReady()
            }
        }
    }

    /**
     * Adds a [Listener] to be notified of readiness state changes.
     */
    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    /**
     * Removes a previously added [Listener].
     */
    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /**
     * A listener interface for receiving notifications about panel configuration readiness.
     */
    interface Listener {
        /**
         * Called when the panel configuration has been successfully read and is ready for use.
         */
        fun onReady()
    }
}
