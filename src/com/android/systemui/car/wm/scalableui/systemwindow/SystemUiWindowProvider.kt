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

import com.android.car.scalableui.manager.StateManager
import com.android.systemui.car.systembar.SystemBarConstants.NAVIGATION_BAR
import com.android.systemui.car.systembar.SystemBarConstants.STATUS_BAR
import com.android.systemui.car.wm.scalableui.configuration.SystemUiConfigurationProvider
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelConfigReadStateMonitor
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer
import com.android.wm.shell.dagger.WMSingleton
import dagger.Lazy
import java.util.Optional
import javax.inject.Inject

/**
 * Provides access to all [SystemUiWindow] objects.
 */
@WMSingleton
class SystemUiWindowProvider @Inject constructor(
    private val consumer: Optional<PanelUpdateConsumer>,
    private val windowFactory: SystemBarWindowImpl.Factory,
    private val configurationProvider: SystemUiConfigurationProvider,
    private val hunWindow: Lazy<Optional<HunWindow>>,
    private val panelConfigMonitor: PanelConfigReadStateMonitor
) {
    private var navBarWindows: List<SystemUiWindow> = emptyList()
    private var statusBarWindows: List<SystemUiWindow> = emptyList()

    init {
        panelConfigMonitor.addListener(object : PanelConfigReadStateMonitor.Listener {
            override fun onReady() {
                // clear cached values
                navBarWindows = emptyList()
                statusBarWindows = emptyList()
            }
        })
    }

    /**
     * @return status [SystemUiWindow]s
     */
    fun getStatusBarWindows(): List<SystemUiWindow> = if (!panelConfigMonitor.isReady()) {
        emptyList()
    } else {
        if (statusBarWindows.isEmpty()) {
            statusBarWindows = getBarWindows(STATUS_BAR)
        }
        statusBarWindows
    }

    /**
     * @return navigation [SystemUiWindow]s
     */
    fun getNavigationBarWindows(): List<SystemUiWindow> = if (!panelConfigMonitor.isReady()) {
        emptyList()
    } else {
        if (navBarWindows.isEmpty()) {
            navBarWindows = getBarWindows(NAVIGATION_BAR)
        }
        navBarWindows
    }

    /**
     * @return status & navigation [SystemUiWindow]s
     */
    fun getSystemBarWindows(): List<SystemUiWindow> {
        return getStatusBarWindows() + getNavigationBarWindows()
    }

    private fun getBarWindows(type: Int): List<SystemUiWindow> {
        if (consumer.isEmpty) {
            return emptyList()
        }

        val configs = if (type == STATUS_BAR) {
            configurationProvider.getStatusBarConfigs()
        } else {
            configurationProvider.getNavigationBarConfigs()
        }

        return configs.map { config ->
            val panelState = StateManager.getPanelState(config.name)
            checkNotNull(panelState) { "PanelState must not be null for ${config.name}" }
            windowFactory.create(consumer.get(), config, panelState.displayId)
        }.toList()
    }

    /**
     * @return [HunWindow]
     */
    fun getHunWindow(): Optional<HunWindow> = if (!panelConfigMonitor.isReady()) {
        Optional.empty()
    } else {
        hunWindow.get()
    }

    /**
     * Returns `true` if the system UI windows have been populated and are ready for use.
     */
    fun isReady() = panelConfigMonitor.isReady()

    /**
     * Adds a [WindowReadyListener] to be notified when the system UI windows are ready.
     */
    fun addReadinessListener(listener: WindowReadyListener) {
        panelConfigMonitor.addListener(listener)
    }

    /**
     * Removes a previously added [WindowReadyListener].
     */
    fun removeReadinessListener(listener: WindowReadyListener) {
        panelConfigMonitor.removeListener(listener)
    }

    /**
     * A listener interface for receiving notifications about system UI window readiness.
     *
     * This abstracts away the concept of panels from System UI elements.
     */
    interface WindowReadyListener : PanelConfigReadStateMonitor.Listener {
        /**
         * Called when the system UI windows have been successfully created and are ready for use.
         */
        override fun onReady()
    }
}
