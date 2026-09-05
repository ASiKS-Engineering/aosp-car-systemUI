/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.car.minimizedcontrols

import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.filters.SmallTest
import com.android.car.scalableui.model.PanelControllerMetadata
import com.android.systemui.CarSysuiTestCase
import com.android.systemui.ShellSyncExecutor
import com.android.systemui.car.CarSystemUiTest
import com.android.systemui.lifecycle.InstantTaskExecutorRule
import javax.inject.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@CarSystemUiTest
@RunWith(AndroidTestingRunner::class)
@TestableLooper.RunWithLooper
@SmallTest
class BaseMinimizedControlsPanelControllerTest : CarSysuiTestCase() {

    @Mock private lateinit var view: View
    private val mainExecutor = ShellSyncExecutor()
    private lateinit var controller: TestBaseMinimizedControlsPanelController

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    // Concrete implementation of the abstract class for testing
    private inner class TestBaseMinimizedControlsPanelController(
        panelId: String,
        metadata: PanelControllerMetadata,
        decorPanelViewMap: Map<Class<*>, Provider<View>>
    ) : BaseMinimizedControlsPanelController<View>(
        panelId,
        metadata,
        decorPanelViewMap,
        mainExecutor
    ) {

        var onViewCreatedCalled = false
        var capturedView: View? = null

        override fun onViewCreated(view: View) {
            onViewCreatedCalled = true
            this.capturedView = view
        }

        // Expose protected method for testing
        fun publicResetLifecycle() {
            resetLifecycle()
        }
    }

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        val map: Map<Class<*>, Provider<View>> =
            mapOf(TestBaseMinimizedControlsPanelController::class.java to Provider { view })

        controller = TestBaseMinimizedControlsPanelController(
            "panel_id",
            PanelControllerMetadata.builder("panel_id")
                .addConfiguration(
                    "View",
                    TestBaseMinimizedControlsPanelController::class.java.name
                )
                .build(),
            map
        )
    }

    @Test
    fun testGetView_initializesLifecycleAndCallsOnViewCreated() {
        val returnedView = controller.view

        assertEquals("getView should return the injected view", view, returnedView)
        assertEquals("onViewCreated should be called", true, controller.onViewCreatedCalled)
        assertEquals("onViewCreated should receive the view", view, controller.capturedView)
        assertEquals(
            "Lifecycle should be RESUMED after getView",
            Lifecycle.State.RESUMED,
            controller.lifecycle.currentState
        )
    }

    @Test
    fun testResetLifecycle_transitionsStateToCreatedTwice_cleansUpOldLifecycle() {
        // Trigger initialization
        controller.view
        assertEquals(Lifecycle.State.RESUMED, controller.lifecycle.currentState)

        val initialLifecycle = controller.lifecycle

        // Reset lifecycle
        controller.publicResetLifecycle()

        assertNotSame(
            "A new LifecycleRegistry should have been created",
            initialLifecycle,
            controller.lifecycle
        )
        assertEquals(
            "New lifecycle should be in CREATED state",
            Lifecycle.State.CREATED,
            controller.lifecycle.currentState
        )
        assertEquals(
            "Old lifecycle should be DESTROYED",
            Lifecycle.State.DESTROYED,
            initialLifecycle.currentState
        )
    }

    @Test
    fun testDestroy_transitionsLifecycleToDestroyed() {
        // Trigger initialization
        controller.view
        assertEquals(Lifecycle.State.RESUMED, controller.lifecycle.currentState)

        // Destroy the controller
        controller.destroy()

        assertEquals(
            "Lifecycle should be DESTROYED after controller is destroyed",
            Lifecycle.State.DESTROYED,
            controller.lifecycle.currentState
        )
    }
}
