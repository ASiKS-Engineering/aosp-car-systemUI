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
package com.android.systemui.car.wm.scalableui.panel

import android.content.Context
import android.content.res.Resources
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.scalableui.model.PanelControllerMetadata
import com.android.car.scalableui.model.Role
import com.android.car.scalableui.panel.DecorPanelController
import com.android.car.scalableui.panel.PanelUpdatePublisher
import com.android.systemui.CarSysuiTestCase
import com.android.systemui.ShellSyncExecutor
import com.android.systemui.car.CarSystemUiTest
import com.android.systemui.car.wm.scalableui.panel.controller.PanelControllerInitializer
import com.android.wm.shell.automotive.AutoDecorManager
import com.android.wm.shell.automotive.AutoSurfaceTransaction
import com.android.wm.shell.automotive.AutoSurfaceTransactionFactory
import com.android.wm.shell.common.ShellExecutor
import java.util.Optional
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn

@CarSystemUiTest
@RunWith(AndroidJUnit4::class)
@SmallTest
class DecorPanelUnitTest : CarSysuiTestCase() {

    private lateinit var decorPanel: DecorPanel
    private lateinit var mainExecutor: ShellExecutor
    private lateinit var shellMainExecutor: ShellExecutor
    private lateinit var shellBgExecutor: ShellExecutor

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var resources: Resources
    @Mock
    private lateinit var mockDecorView: View
    @Mock
    private lateinit var role: Role

    @Mock
    private lateinit var autoDecorManager: AutoDecorManager
    @Mock
    private lateinit var panelUtils: PanelUtils
    @Mock
    private lateinit var panelControllerInitializer: PanelControllerInitializer
    @Mock
    private lateinit var autoSurfaceTransactionFactory: AutoSurfaceTransactionFactory
    @Mock
    private lateinit var panelUpdatePublisher: PanelUpdatePublisher
    @Mock
    private lateinit var autoSurfaceTransaction: AutoSurfaceTransaction
    @Mock
    private lateinit var decorPanelController: DecorPanelController

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        mainExecutor = ShellSyncExecutor()
        shellMainExecutor = ShellSyncExecutor()
        shellBgExecutor = ShellSyncExecutor()

        decorPanel = spy(
            DecorPanel(
                context,
                autoDecorManager,
                panelUtils,
                panelControllerInitializer,
                mainExecutor,
                shellMainExecutor,
                shellBgExecutor,
                autoSurfaceTransactionFactory,
                Optional.of(panelUpdatePublisher),
                DECOR_PANEL_ID
            )
        )

        doReturn(resources).`when`(context).getResources()
        doReturn(mockDecorView).`when`(role).getView(any())
        doReturn(role).`when`(decorPanel).role

        `when`(autoSurfaceTransactionFactory.createTransaction(any())).thenReturn(
            autoSurfaceTransaction
        )
    }

    @Test
    fun destroy_destroysController() {
        decorPanel.mDecorPanelController = decorPanelController

        decorPanel.destroy()

        verify(decorPanelController).destroy()
    }

    @Test
    fun refreshTheme_refreshesControllerAndResetsPanel() {
        `when`(panelControllerInitializer.createDecorPanelController(any(), any()))
            .thenReturn(decorPanelController)
        decorPanel.setPanelControllerMetadata(mock(PanelControllerMetadata::class.java))
        decorPanel.mDecorPanelController = decorPanelController

        decorPanel.refreshTheme()

        verify(decorPanelController).refreshTheme()
        verify(decorPanel).reset()
    }

    companion object {
        private const val DECOR_PANEL_ID = "TestDecorPanel"
    }
}
