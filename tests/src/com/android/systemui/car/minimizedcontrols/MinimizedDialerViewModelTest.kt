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

import android.telecom.Call
import android.telecom.CallAudioState
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import androidx.lifecycle.Observer
import androidx.test.filters.SmallTest
import com.android.car.telephony.calling.InCallServiceManager
import com.android.systemui.car.CarSystemUiTest
import com.android.systemui.car.telecom.InCallServiceImpl
import com.android.systemui.lifecycle.InstantTaskExecutorRule
import com.android.systemui.utils.leaks.LeakCheckedTest
import java.beans.PropertyChangeEvent
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@CarSystemUiTest
@RunWith(AndroidTestingRunner::class)
@TestableLooper.RunWithLooper
@SmallTest
class MinimizedDialerViewModelTest : LeakCheckedTest() {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var inCallServiceManager: InCallServiceManager
    @Mock
    private lateinit var inCallService: InCallServiceImpl
    @Mock
    private lateinit var primaryCallObserver: Observer<Call?>
    @Mock
    private lateinit var isMutedObserver: Observer<Boolean>

    private lateinit var viewModel: MinimizedDialerViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        `when`(inCallServiceManager.inCallService).thenReturn(inCallService)

        viewModel = MinimizedDialerViewModel(inCallServiceManager)
        viewModel.primaryCallLiveData.observeForever(primaryCallObserver)
        viewModel.isMutedLiveData.observeForever(isMutedObserver)
    }

    @After
    fun tearDown() {
        viewModel.primaryCallLiveData?.removeObserver(primaryCallObserver)
        viewModel.isMutedLiveData?.removeObserver(isMutedObserver)
        viewModel.tearDown()
    }

    @Test
    fun init_registersObservers() {
        verify(inCallServiceManager).addObserver(viewModel)
        verify(inCallService).addListener(viewModel)
    }

    @Test
    fun propertyChange_inCallServiceConnected_addsListener() {
        val mockNewService = mock(InCallServiceImpl::class.java)
        `when`(inCallServiceManager.inCallService).thenReturn(mockNewService)

        val event =
            PropertyChangeEvent(this, InCallServiceManager.PROPERTY_IN_CALL_SERVICE, null, null)
        viewModel.propertyChange(event)

        verify(mockNewService).addListener(viewModel)
    }

    @Test
    fun propertyChange_inCallServiceNull_clearsPrimaryCall() {
        `when`(inCallServiceManager.inCallService).thenReturn(null)
        org.mockito.Mockito.clearInvocations(primaryCallObserver)

        val event =
            PropertyChangeEvent(this, InCallServiceManager.PROPERTY_IN_CALL_SERVICE, null, null)
        viewModel.propertyChange(event)

        verify(primaryCallObserver).onChanged(null)
    }

    @Test
    fun propertyChange_serviceSwapped_listenersAreUpdated() {
        val oldService = inCallService // from setUp
        val newService = mock(InCallServiceImpl::class.java)
        `when`(inCallServiceManager.inCallService).thenReturn(newService)

        val event = PropertyChangeEvent(
            this,
            InCallServiceManager.PROPERTY_IN_CALL_SERVICE,
            oldService,
            newService
        )
        viewModel.propertyChange(event)

        verify(oldService).removeListener(viewModel)
        verify(newService).addListener(viewModel)
    }

    @Test
    fun tearDown_removesObservers() {
        viewModel.tearDown()

        verify(inCallServiceManager).removeObserver(viewModel)
        verify(inCallService).removeListener(viewModel)
    }

    @Test
    fun onCallAudioStateChanged_updatesMuteState() {
        val audioState =
            CallAudioState(true, CallAudioState.ROUTE_EARPIECE, CallAudioState.ROUTE_EARPIECE)
        viewModel.onCallAudioStateChanged(audioState)

        verify(isMutedObserver).onChanged(true)
    }
}
