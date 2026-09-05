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
package com.android.systemui.car.wm.scalableui.configuration

import com.android.car.scalableui.loader.xml.parser.SystemBarParser.TYPE_ATTRIBUTE
import com.android.car.scalableui.loader.xml.parser.SystemBarParser.TYPE_NAVIGATION
import com.android.car.scalableui.loader.xml.parser.SystemBarParser.TYPE_STATUS
import com.android.car.scalableui.panel.PanelPool
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelConfigReadStateMonitor
import com.android.wm.shell.dagger.WMSingleton
import javax.inject.Inject

/**
 * Provides access to all System UI Configuration objects.
 */
@WMSingleton
class SystemUiConfigurationProvider @Inject constructor(
    private val configurationFactory: SystemBarConfiguration.Factory,
    private val panelConfigMonitor: PanelConfigReadStateMonitor
) {
    private var navBarConfigs: List<SystemBarConfiguration> = emptyList()
    private var statusBarConfigs: List<SystemBarConfiguration> = emptyList()

    init {
        panelConfigMonitor.addListener(object : PanelConfigReadStateMonitor.Listener {
            override fun onReady() {
                // clear cached values
                navBarConfigs = emptyList()
                statusBarConfigs = emptyList()
            }
        })
    }

    /**
     * @return status [SystemBarConfiguration]s
     */
    fun getStatusBarConfigs(): List<SystemBarConfiguration> = if (!panelConfigMonitor.isReady()) {
        emptyList()
    } else {
        if (statusBarConfigs.isEmpty()) {
            statusBarConfigs = getBarConfigs(TYPE_STATUS)
        }
        statusBarConfigs
    }

    /**
     * @return navigation [SystemBarConfiguration]s
     */
    fun getNavigationBarConfigs(): List<SystemBarConfiguration> = if (!panelConfigMonitor.isReady())
    {
        emptyList()
    } else {
        if (navBarConfigs.isEmpty()) {
            navBarConfigs = getBarConfigs(TYPE_NAVIGATION)
        }
        navBarConfigs
    }

    /**
     * @return status & navigation [SystemBarConfiguration]s
     */
    fun getSystemBarConfigs(): List<SystemBarConfiguration> {
        return getStatusBarConfigs() + getNavigationBarConfigs()
    }

    /**
     * Returns `true` if the system UI configurations have been populated and are ready for use.
     */
    fun isReady() = panelConfigMonitor.isReady()

    /**
     * Adds a [ConfigurationReadyListener] to be notified when the system UI configuration is ready.
     */
    fun addReadinessListener(listener: ConfigurationReadyListener) {
        panelConfigMonitor.addListener(listener)
    }

    /**
     * Removes a previously added [ConfigurationReadyListener].
     */
    fun removeReadinessListener(listener: ConfigurationReadyListener) {
        panelConfigMonitor.removeListener(listener)
    }

    private fun getBarConfigs(type: String): List<SystemBarConfiguration> {
        val indexOffset = if (type == TYPE_STATUS) 0 else getStatusBarConfigs().size

        return PanelPool.getInstance().getPanels { panel ->
                type == panel.panelControllerMetadata?.configurations?.getString(TYPE_ATTRIBUTE)
            }.mapIndexed { index, panel ->
                configurationFactory.create(
                    panel.panelId,
                    index,
                    indexOffset
                )
            }
    }

    /**
     * A listener interface for receiving notifications about system UI configuration readiness.
     *
     * This abstracts away the concept of panels from System UI elements.
     */
    interface ConfigurationReadyListener : PanelConfigReadStateMonitor.Listener {
        /**
         * Called when the system UI configuration has been successfully read and is ready for use.
         */
        override fun onReady()
    }
}
