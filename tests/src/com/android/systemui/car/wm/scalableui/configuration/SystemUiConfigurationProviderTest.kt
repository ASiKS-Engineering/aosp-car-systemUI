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
package com.android.systemui.car.wm.scalableui.configuration

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.scalableui.loader.xml.parser.SystemBarParser
import com.android.car.scalableui.model.PanelControllerMetadata
import com.android.car.scalableui.model.PanelType
import com.android.car.scalableui.panel.Panel
import com.android.car.scalableui.panel.PanelPool
import com.android.systemui.CarSysuiTestCase
import com.android.systemui.car.CarSystemUiTest
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelConfigReadStateMonitor
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@CarSystemUiTest
@RunWith(AndroidJUnit4::class)
@SmallTest
class SystemUiConfigurationProviderTest : CarSysuiTestCase() {

    private lateinit var provider: SystemUiConfigurationProvider
    private lateinit var panelPool: PanelPool

    private val statusBundle = Bundle()
    private val navBundle = Bundle()
    private val mockMonitor = mock<PanelConfigReadStateMonitor> {
        on { isReady() } doReturn true
    }
    private val mockStatusMetadata = mock<PanelControllerMetadata> {
        on { configurations } doReturn statusBundle
    }
    private val mockNavMetadata = mock<PanelControllerMetadata> {
        on { configurations } doReturn navBundle
    }
    private val mockStatusPanel = mock<Panel> {
        on { panelControllerMetadata } doReturn mockStatusMetadata
        on { panelId } doReturn STATUS_PANEL_ID
    }
    private val mockNavPanel = mock<Panel> {
        on { panelControllerMetadata } doReturn mockNavMetadata
        on { panelId } doReturn NAV_PANEL_ID
    }
    private val mockDelegate = mock<PanelPool.PanelCreatorDelegate> {
        on { createPanel(eq(STATUS_PANEL_ID), any()) } doReturn mockStatusPanel
        on { createPanel(eq(NAV_PANEL_ID), any()) } doReturn mockNavPanel
    }
    private val mockFactory = mock<SystemBarConfiguration.Factory> {
        on { create(any(), any(), any()) } doReturn
                SystemBarConfiguration(Bundle(), "dummy", 0, 0)
    }

    @Before
    fun setUp() {
        panelPool = PanelPool.getInstance()
        panelPool.setDelegate(mockDelegate)
        provider = SystemUiConfigurationProvider(mockFactory, mockMonitor)

        statusBundle.putString(
            SystemBarParser.TYPE_ATTRIBUTE,
            SystemBarParser.TYPE_STATUS
        )
        navBundle.putString(
            SystemBarParser.TYPE_ATTRIBUTE,
            SystemBarParser.TYPE_NAVIGATION
        )

        panelPool.getOrCreatePanel(STATUS_PANEL_ID, PanelType.SYSTEM_BAR)
        panelPool.getOrCreatePanel(NAV_PANEL_ID, PanelType.SYSTEM_BAR)
    }

    @After
    fun tearDown() {
        panelPool.clearPanels()
    }

    @Test
    fun getSystemBarConfigs_returnsAllConfigs() {
        val configs = provider.getSystemBarConfigs()
        assertThat(configs).hasSize(2)
    }

    @Test
    fun getStatusBarConfigs_returnsOnlyStatusBarConfigs() {
        val configs = provider.getStatusBarConfigs()
        assertThat(configs).hasSize(1)
    }

    @Test
    fun getNavigationBarConfigs_returnsOnlyNavBarConfigs() {
        val configs = provider.getNavigationBarConfigs()
        assertThat(configs).hasSize(1)
    }

    @Test
    fun getConfigs_whenNotReady_returnsEmptyLists() {
        whenever(mockMonitor.isReady()) doReturn false

        assertThat(provider.getSystemBarConfigs()).isEmpty()
        assertThat(provider.getStatusBarConfigs()).isEmpty()
        assertThat(provider.getNavigationBarConfigs()).isEmpty()
    }

    companion object {
        private const val STATUS_PANEL_ID = "status"
        private const val NAV_PANEL_ID = "nav"
    }
}
