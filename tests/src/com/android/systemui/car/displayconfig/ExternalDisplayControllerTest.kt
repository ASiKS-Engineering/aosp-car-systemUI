/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.car.displayconfig

import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.Display
import androidx.test.filters.SmallTest
import com.android.app.displaylib.DisplayRepository.PendingDisplay
import com.android.systemui.CarSysuiTestCase
import com.android.systemui.car.CarSystemUiTest
import com.android.systemui.display.data.repository.DisplayRepository
import com.android.systemui.process.ProcessWrapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@CarSystemUiTest
@RunWith(AndroidTestingRunner::class)
@RunWithLooper
@SmallTest
class ExternalDisplayControllerTest : CarSysuiTestCase() {

    private val testScope = TestScope()
    private val bgDispatcher =
        StandardTestDispatcher(testScope.testScheduler, name = "Background dispatcher")
    private val pendingDisplayMock: PendingDisplay = mock()
    private val fakePendingDisplayFlow = flowOf(null, pendingDisplayMock)
    private val fakeDisplayRepository = FakeDisplayRepository(fakePendingDisplayFlow)
    private val processWrapper: ProcessWrapper = mock()

    private val externalDisplayController = ExternalDisplayController(
        fakeDisplayRepository,
        processWrapper,
        testScope,
        bgDispatcher
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun start_whenSystemUser_enablesPendingDisplays() = testScope.runTest() {
        whenever(processWrapper.isSystemUser).thenReturn(true)

        launch(StandardTestDispatcher(testScheduler)) {
            externalDisplayController.start()
        }
        advanceUntilIdle()
        coroutineContext.cancelChildren()

        verify(pendingDisplayMock).enable()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun start_whenNonSystemUser_noOp() = testScope.runTest {
        whenever(processWrapper.isSystemUser).thenReturn(false)

        launch(StandardTestDispatcher(testScheduler)) {
            externalDisplayController.start()
        }
        advanceUntilIdle()
        coroutineContext.cancelChildren()

        verify(pendingDisplayMock, never()).enable()
    }
}

class FakeDisplayRepository(
    private val fakePendingDisplayFlow: Flow<PendingDisplay?>,
    override val displayChangeEvent: Flow<Int> = emptyFlow(),
    override val displayAdditionEvent: Flow<Display?> = emptyFlow(),
    override val displayRemovalEvent: Flow<Int> = emptyFlow(),
    override val displayIdsWithSystemDecorations: StateFlow<Set<Int>> =
        MutableStateFlow(emptySet()),
    override val defaultDisplayType: StateFlow<Int> =
        MutableStateFlow(Display.TYPE_UNKNOWN),
    override val displays: StateFlow<Set<Display>> = MutableStateFlow(emptySet()),
    override val defaultDisplayOff: StateFlow<Boolean> = MutableStateFlow(false),
    override val pendingDisplay: Flow<PendingDisplay?> = fakePendingDisplayFlow,
    override val displayIds: StateFlow<Set<Int>> = MutableStateFlow(emptySet()),
    override val isMirroringEnabled: StateFlow<Boolean> = MutableStateFlow(false),
) : DisplayRepository {
    override fun getDisplay(displayId: Int): Display? = null
}
