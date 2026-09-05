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

import android.app.ActivityManager
import android.car.Car
import android.car.app.CarActivityManager
import android.content.ComponentName
import android.content.Context
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.scalableui.model.Decor
import com.android.car.scalableui.model.PanelControllerMetadata
import com.android.car.scalableui.model.Variant
import com.android.car.scalableui.panel.PanelUpdatePublisher
import com.android.car.scalableui.panel.TaskPanelController
import com.android.systemui.CarSysuiTestCase
import com.android.systemui.ShellSyncExecutor
import com.android.systemui.car.CarServiceProvider
import com.android.systemui.car.CarSystemUiTest
import com.android.systemui.car.flags.Flag
import com.android.systemui.car.flags.FlagManager
import com.android.systemui.car.wm.scalableui.AutoTaskStackHelper
import com.android.systemui.car.wm.scalableui.EventDispatcher
import com.android.systemui.car.wm.scalableui.panel.controller.PanelControllerInitializer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.automotive.AutoCaptionController
import com.android.wm.shell.automotive.AutoDecor
import com.android.wm.shell.automotive.AutoDecorManager
import com.android.wm.shell.automotive.AutoLayoutManager
import com.android.wm.shell.automotive.AutoSurfaceTransaction
import com.android.wm.shell.automotive.AutoSurfaceTransactionFactory
import com.android.wm.shell.automotive.AutoTaskStackController
import com.android.wm.shell.automotive.RootTaskStack
import com.android.wm.shell.automotive.RootTaskStackListener
import com.android.wm.shell.common.ShellExecutor
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

@CarSystemUiTest
@RunWith(AndroidJUnit4::class)
@SmallTest
class TaskPanelUnitTest : CarSysuiTestCase() {

    private lateinit var taskPanel: TaskPanel
    private lateinit var mainExecutor: ShellExecutor
    private lateinit var shellMainExecutor: ShellExecutor
    private lateinit var shellBgExecutor: ShellExecutor

    @Mock
    private lateinit var autoTaskStackController: AutoTaskStackController
    @Mock
    private lateinit var carServiceProvider: CarServiceProvider
    @Mock
    private lateinit var carActivityManager: CarActivityManager
    @Mock
    private lateinit var autoTaskStackHelper: AutoTaskStackHelper
    @Mock
    private lateinit var autoCaptionController: AutoCaptionController
    @Mock
    private lateinit var shellTaskOrganizer: ShellTaskOrganizer
    @Mock
    private lateinit var rootTaskStack: RootTaskStack
    @Mock
    private lateinit var autoDecorManager: AutoDecorManager
    @Mock
    private lateinit var panelUtils: PanelUtils
    @Mock
    private lateinit var taskPanelInfoRepository: TaskPanelInfoRepository
    @Mock
    private lateinit var eventDispatcher: EventDispatcher
    @Mock
    private lateinit var panelControllerInitializer: PanelControllerInitializer
    @Mock
    private lateinit var autoLayoutManager: AutoLayoutManager
    @Mock
    private lateinit var autoSurfaceTransactionFactory: AutoSurfaceTransactionFactory
    @Mock
    private lateinit var autoSurfaceTransaction: AutoSurfaceTransaction
    @Mock
    private lateinit var panelUpdatePublisher: PanelUpdatePublisher
    @Mock
    private lateinit var flagManager: FlagManager
    @Mock
    private lateinit var userContext: Context
    @Mock
    private lateinit var taskPanelController: TaskPanelController
    @Mock
    private lateinit var runningTaskInfo: ActivityManager.RunningTaskInfo

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        mainExecutor = ShellSyncExecutor()
        shellMainExecutor = ShellSyncExecutor()
        shellBgExecutor = ShellSyncExecutor()
        taskPanel = spy(
            TaskPanel(
                autoTaskStackController,
                userContext,
                carServiceProvider,
                autoTaskStackHelper,
                shellTaskOrganizer,
                autoCaptionController,
                panelUtils,
                taskPanelInfoRepository,
                autoDecorManager,
                eventDispatcher,
                panelControllerInitializer,
                autoLayoutManager,
                mainExecutor,
                shellMainExecutor,
                shellBgExecutor,
                autoSurfaceTransactionFactory,
                Optional.of(panelUpdatePublisher),
                flagManager,
                TASK_PANEL_ID
            )
        )

        `when`(autoSurfaceTransactionFactory.createTransaction(any())).thenReturn(
            autoSurfaceTransaction
        )

        // Capture and store the RootTaskStackListener after init()
        taskPanel.init()
        val carServiceConnectCaptor =
            argumentCaptor<CarServiceProvider.CarServiceOnConnectedListener>()
        verify(carServiceProvider).addListener(carServiceConnectCaptor.capture())
        val car = mock(Car::class.java)
        `when`(car.getCarManager(CarActivityManager::class.java)).thenReturn(carActivityManager)
        carServiceConnectCaptor.firstValue.onConnected(car)

        val listenerArgumentCaptor = argumentCaptor<RootTaskStackListener>()
        verify(autoTaskStackController).createRootTaskStack(
            any(),
            any(),
            listenerArgumentCaptor.capture()
        )

        // Set up the rootTaskStack after it's created
        `when`(rootTaskStack.id).thenReturn(ROOT_TASK_STACK_ID)
        `when`(rootTaskStack.rootTaskInfo).thenReturn(runningTaskInfo)
        runningTaskInfo.taskId = ROOT_TASK_ID
        taskPanel.setRootTaskStack(rootTaskStack)
    }

    @Test
    fun refreshTheme_clearsExistingDecorsAndUpdates() {
        val decor = mock(AutoDecor::class.java)
        taskPanel.mExistingAutoDecors["decor1"] = decor
        `when`(flagManager.isEnabled(Flag.EnableDecor)).thenReturn(true)

        // Create a real map for the decor views, as getDecorViewMap will be called.
        val decorViewMap = hashMapOf<String, View>()
        // Spy on the panel to mock the getDecorViewMap call.
        doReturn(decorViewMap).`when`(taskPanel).getDecorViewMap(any())

        // WHEN
        taskPanel.refreshTheme()

        // THEN
        // Verify old decors are removed
        verify(autoDecorManager).removeAutoDecor(decor)
        assertThat(taskPanel.mExistingAutoDecors).isEmpty()

        // Verify updateDecors is called with the correct arguments
        verify(taskPanel).updateDecors(eq(autoSurfaceTransaction), eq(null), eq(decorViewMap))

        // Verify the transaction is applied
        verify(autoSurfaceTransaction).apply()
    }

    @Test
    fun updateDecors_addNewDecor() {
        `when`(flagManager.isEnabled(Flag.EnableDecor)).thenReturn(true)
        val decorModel = mock(Decor::class.java)
        val decorView = mock(View::class.java)
        val newAutoDecor = mock(AutoDecor::class.java)
        val decorViewMap = hashMapOf("decor1" to decorView)

        `when`(decorModel.layer).thenReturn(1)
        `when`(decorModel.id).thenReturn("decor1")
        val decors = mapOf("decor1" to decorModel)
        val variant = mock(Variant::class.java)
        `when`(variant.decors).thenReturn(decors)
        `when`(autoDecorManager.createAutoDecor(any(), any(), any(), any()))
            .thenReturn(newAutoDecor)

        taskPanel.updateDecors(autoSurfaceTransaction, variant, decorViewMap)

        verify(autoDecorManager).createAutoDecor(decorView, 1, taskPanel.safeBounds, "decor1")
        verify(autoDecorManager).attachAutoDecorToTask(newAutoDecor, ROOT_TASK_ID)
        assertThat(taskPanel.mExistingAutoDecors).containsEntry("decor1", newAutoDecor)
        verify(taskPanel).updateAutoDecor(eq(newAutoDecor), eq(decorModel), any())
    }

    @Test
    fun updateDecors_removesObsoleteDecor() {
        `when`(flagManager.isEnabled(Flag.EnableDecor)).thenReturn(true)
        val existingDecor = mock(AutoDecor::class.java)
        val decorViewMap = hashMapOf<String, View>()
        taskPanel.mExistingAutoDecors["obsolete_decor"] = existingDecor
        val variant = mock(Variant::class.java)
        `when`(variant.decors).thenReturn(emptyMap()) // New variant has no decors

        taskPanel.updateDecors(autoSurfaceTransaction, variant, decorViewMap)

        verify(autoDecorManager).removeAutoDecor(existingDecor)
        assertThat(taskPanel.mExistingAutoDecors).isEmpty()
    }

    @Test
    fun handles_withController_returnsControllerResult() {
        val componentName = ComponentName("com.test", ".TestActivity")
        `when`(panelControllerInitializer.createTaskPanelController(any(), any()))
            .thenReturn(taskPanelController)
        `when`(taskPanelController.handles(componentName)).thenReturn(true)
        taskPanel.setPanelControllerMetadata(mock(PanelControllerMetadata::class.java))

        assertThat(taskPanel.handles(componentName)).isTrue()
        verify(taskPanelController).handles(componentName)
    }

    @Test
    fun destroy_destroysTaskStackAndController() {
        `when`(panelControllerInitializer.createTaskPanelController(any(), any()))
            .thenReturn(taskPanelController)
        taskPanel.setPanelControllerMetadata(mock(PanelControllerMetadata::class.java))

        taskPanel.destroy()

        verify(autoTaskStackController).destroyTaskStack(ROOT_TASK_STACK_ID)
        verify(taskPanelController).destroy()
    }

    companion object {
        private const val TASK_PANEL_ID = "TestTaskPanel"
        private const val ROOT_TASK_ID = 123
        private const val ROOT_TASK_STACK_ID = 456
    }
}
