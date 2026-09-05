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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.scalableui.manager.StateManager
import com.android.car.scalableui.model.PanelState
import com.android.car.scalableui.model.Variant
import com.android.car.scalableui.panel.Panel
import com.android.car.scalableui.panel.PanelPool
import com.android.systemui.CarSysuiTestCase
import com.android.systemui.car.CarSystemUiTest
import com.android.systemui.car.wm.scalableui.configuration.SystemBarConfiguration
import com.android.systemui.car.wm.scalableui.configuration.SystemUiConfigurationProvider
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelConfigReadStateMonitor
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import java.util.Optional
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@CarSystemUiTest
@RunWith(AndroidJUnit4::class)
@SmallTest
class SystemUiWindowProviderTest : CarSysuiTestCase() {

    private val panelUpdateConsumer = mock<PanelUpdateConsumer>()
    private val consumer = mock<Optional<PanelUpdateConsumer>> {
        on { isEmpty } doReturn false
        on { get() } doReturn panelUpdateConsumer
    }
    private val hunWindow = mock<HunWindow>()
    private val statusBarConfiguration = mock<SystemBarConfiguration> {
        on { name } doReturn "TestStatusBar"
    }
    private val navBarConfiguration = mock<SystemBarConfiguration> {
        on { name } doReturn "TestNavBar"
    }
    private val topBarLeftConfiguration = mock<SystemBarConfiguration> {
        on { name } doReturn "TestTopBarLeft"
    }
    private val topBarRightConfiguration = mock<SystemBarConfiguration> {
        on { name } doReturn "TestTopBarRight"
    }
    private val bottomBarLeftConfiguration = mock<SystemBarConfiguration> {
        on { name } doReturn "TestBottomBarLeft"
    }
    private val bottomBarCenterConfiguration = mock<SystemBarConfiguration> {
        on { name } doReturn "TestBottomBarCenter"
    }
    private val bottomBarRightConfiguration = mock<SystemBarConfiguration> {
        on { name } doReturn "TestBottomBarRight"
    }

    private val statusBarWindow = mock<SystemBarWindowImpl>()
    private val navBarWindow = mock<SystemBarWindowImpl>()
    private val topBarLeftWindow = mock<SystemBarWindowImpl>()
    private val topBarRightWindow = mock<SystemBarWindowImpl>()
    private val bottomBarLeftWindow = mock<SystemBarWindowImpl>()
    private val bottomBarCenterWindow = mock<SystemBarWindowImpl>()
    private val bottomBarRightWindow = mock<SystemBarWindowImpl>()

    private val windowFactory = mock<SystemBarWindowImpl.Factory> {
        on {
            create(
                panelUpdateConsumer,
                statusBarConfiguration,
                TEST_DISPLAY_ID
            )
        } doReturn statusBarWindow
        on {
            create(panelUpdateConsumer, navBarConfiguration, TEST_DISPLAY_ID)
        } doReturn navBarWindow
        on {
            create(panelUpdateConsumer, topBarLeftConfiguration, TEST_DISPLAY_ID)
        } doReturn topBarLeftWindow
        on {
            create(panelUpdateConsumer, topBarRightConfiguration, TEST_DISPLAY_ID)
        } doReturn topBarRightWindow
        on {
            create(panelUpdateConsumer, bottomBarLeftConfiguration, TEST_DISPLAY_ID)
        } doReturn bottomBarLeftWindow
        on {
            create(panelUpdateConsumer, bottomBarCenterConfiguration, TEST_DISPLAY_ID)
        } doReturn bottomBarCenterWindow
        on {
            create(panelUpdateConsumer, bottomBarRightConfiguration, TEST_DISPLAY_ID)
        } doReturn bottomBarRightWindow
    }
    private val configurationProvider = mock<SystemUiConfigurationProvider> {
        on { getStatusBarConfigs() } doReturn listOf(statusBarConfiguration)
        on { getNavigationBarConfigs() } doReturn listOf(navBarConfiguration)
    }
    private val monitor = mock<PanelConfigReadStateMonitor> {
        on { isReady() } doReturn true
    }

    private lateinit var provider: SystemUiWindowProvider

    private companion object {
        const val TEST_DISPLAY_ID = 0
        val variant = mock<Variant>()
        val panel = mock<Panel>()
        val delegate = mock<PanelPool.PanelCreatorDelegate> {
            on { createPanel(any(), any()) } doReturn panel
        }

        val statusBarPanelState = mock<PanelState> {
            on { getId() } doReturn "TestStatusBar"
            on { getDisplayId() } doReturn TEST_DISPLAY_ID
            on { getCurrentVariant() } doReturn variant
        }

        val navBarPanelState = mock<PanelState> {
            on { getId() } doReturn "TestNavBar"
            on { getDisplayId() } doReturn TEST_DISPLAY_ID
            on { getCurrentVariant() } doReturn variant
        }
        val topBarLeftPanelState = mock<PanelState> {
            on { getId() } doReturn "TestTopBarLeft"
            on { getDisplayId() } doReturn TEST_DISPLAY_ID
            on { getCurrentVariant() } doReturn variant
        }
        val topBarRightPanelState = mock<PanelState> {
            on { getId() } doReturn "TestTopBarRight"
            on { getDisplayId() } doReturn TEST_DISPLAY_ID
            on { getCurrentVariant() } doReturn variant
        }
        val bottomBarLeftPanelState = mock<PanelState> {
            on { getId() } doReturn "TestBottomBarLeft"
            on { getDisplayId() } doReturn TEST_DISPLAY_ID
            on { getCurrentVariant() } doReturn variant
        }
        val bottomBarCenterPanelState = mock<PanelState> {
            on { getId() } doReturn "TestBottomBarCenter"
            on { getDisplayId() } doReturn TEST_DISPLAY_ID
            on { getCurrentVariant() } doReturn variant
        }
        val bottomBarRightPanelState = mock<PanelState> {
            on { getId() } doReturn "TestBottomBarRight"
            on { getDisplayId() } doReturn TEST_DISPLAY_ID
            on { getCurrentVariant() } doReturn variant
        }
    }

    @Before
    fun setUp() {
        PanelPool.getInstance().setDelegate(delegate)
        StateManager.addState(statusBarPanelState)
        StateManager.addState(navBarPanelState)
        StateManager.addState(topBarLeftPanelState)
        StateManager.addState(topBarRightPanelState)
        StateManager.addState(bottomBarLeftPanelState)
        StateManager.addState(bottomBarCenterPanelState)
        StateManager.addState(bottomBarRightPanelState)

        provider = SystemUiWindowProvider(
            consumer,
            windowFactory,
            configurationProvider,
            Lazy { Optional.of(hunWindow) },
            monitor
        )
    }

    @After
    fun tearDown() {
        StateManager.clearStates()
        PanelPool.getInstance().clearPanels()
    }

    @Test
    fun systemBarWindows_returnsAllSystemBarWindows() {
        val windows = provider.getSystemBarWindows()
        assertThat(windows).containsExactly(statusBarWindow, navBarWindow)
    }

    @Test
    fun statusBarWindows_returnsMultiPanelTopBarWindows() {
        whenever(configurationProvider.getStatusBarConfigs()) doReturn
            listOf(topBarLeftConfiguration, topBarRightConfiguration)

        val windows = provider.getStatusBarWindows()

        assertThat(windows).containsExactly(topBarLeftWindow, topBarRightWindow)
    }

    @Test
    fun navigationBarWindows_returnsMultiPanelBottomBarWindows() {
        whenever(configurationProvider.getNavigationBarConfigs()) doReturn
            listOf(bottomBarLeftConfiguration, bottomBarCenterConfiguration,
                bottomBarRightConfiguration)

        val windows = provider.getNavigationBarWindows()

        assertThat(windows).containsExactly(
            bottomBarLeftWindow,
            bottomBarCenterWindow,
            bottomBarRightWindow
        )
    }

    @Test
    fun systemBarWindows_returnsAllMultiPanelSystemBarWindows() {
        whenever(configurationProvider.getStatusBarConfigs()) doReturn
            listOf(topBarLeftConfiguration, topBarRightConfiguration)
        whenever(configurationProvider.getNavigationBarConfigs()) doReturn
            listOf(bottomBarLeftConfiguration, bottomBarCenterConfiguration,
                bottomBarRightConfiguration)

        val windows = provider.getSystemBarWindows()

        assertThat(windows).containsExactly(
            topBarLeftWindow,
            topBarRightWindow,
            bottomBarLeftWindow,
            bottomBarCenterWindow,
            bottomBarRightWindow
        )
    }

    @Test
    fun statusBarWindows_returnsOnlyStatusBarWindows() {
        val windows = provider.getStatusBarWindows()
        assertThat(windows).containsExactly(statusBarWindow)
    }

    @Test
    fun navigationBarWindows_returnsOnlyNavBarWindows() {
        val windows = provider.getNavigationBarWindows()
        assertThat(windows).containsExactly(navBarWindow)
    }

    @Test
    fun getHunWindow_returnsHunWindow() {
        val hunWindow = provider.getHunWindow()
        assertThat(hunWindow.get()).isEqualTo(this@SystemUiWindowProviderTest.hunWindow)
    }

    @Test
    fun getWindows_whenNotReady_returnsEmptyLists() {
        whenever(monitor.isReady()) doReturn false

        assertThat(provider.getSystemBarWindows()).isEmpty()
        assertThat(provider.getStatusBarWindows()).isEmpty()
        assertThat(provider.getNavigationBarWindows()).isEmpty()
        assertThat(provider.getHunWindow()).isEqualTo(Optional.empty<HunWindow>())
    }
}
