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
import android.view.Display
import android.view.View
import androidx.lifecycle.MutableLiveData
import androidx.test.filters.SmallTest
import com.android.car.media.common.MediaItemMetadata
import com.android.car.media.common.browse.MediaItemsRepository
import com.android.car.media.common.playback.PlaybackViewModel
import com.android.car.media.common.source.MediaModels
import com.android.car.media.common.source.MediaSource
import com.android.car.media.common.ui.PlaybackCardViewModel
import com.android.car.scalableui.loader.xml.parser.PanelControllerParser.VIEW_TAG
import com.android.car.scalableui.model.PanelControllerMetadata
import com.android.systemui.CarSysuiTestCase
import com.android.systemui.ShellSyncExecutor
import com.android.systemui.car.CarSystemUiTest
import com.android.systemui.car.minimizedcontrols.MinimizedMediaControlsPanelController.PlaybackCardViewModelFactory
import com.android.systemui.lifecycle.InstantTaskExecutorRule
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.UserChangeListener
import javax.inject.Provider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@CarSystemUiTest
@RunWith(AndroidTestingRunner::class)
@TestableLooper.RunWithLooper
@SmallTest
class MinimizedMediaControlsPanelControllerTest : CarSysuiTestCase() {

    @Mock private lateinit var shellController: ShellController
    @Mock private lateinit var viewModel: MinimizedMediaControlsViewModel
    @Mock private lateinit var playbackCardViewModelFactory: PlaybackCardViewModelFactory
    @Mock private lateinit var playbackCardViewModel: PlaybackCardViewModel
    @Mock private lateinit var userContextFactory: UserContextUtils.UserContextFactory
    @Mock private lateinit var minimizedMediaControlsPlaybackCardControllerFactory:
        MinimizedMediaControlsPlaybackCardController.Factory
    @Mock private lateinit var mediaModelsFactory:
        MinimizedMediaControlsPanelController.MediaModelsFactory
    @Mock private lateinit var playbackCardController: MinimizedMediaControlsPlaybackCardController
    @Mock private lateinit var view: MinimizedMediaControlsView
    @Mock private lateinit var display: Display
    @Mock private lateinit var playbackViewModel: PlaybackViewModel
    @Mock private lateinit var mediaModels: MediaModels
    @Mock private lateinit var mediaItemsRepository: MediaItemsRepository
    @Mock private lateinit var application: android.app.Application

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val mainExecutor = ShellSyncExecutor()

    // LiveData Mocks
    private val playbackState = MutableLiveData<PlaybackViewModel.PlaybackStateWrapper>()
    private val metadata = MutableLiveData<MediaItemMetadata>()
    private val mediaSource = MutableLiveData<MediaSource>()

    private lateinit var controller: MinimizedMediaControlsPanelController

    companion object {
        private const val TEST_USER_ID = 100
        private const val SECONDARY_USER_ID = 101
    }

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        // Setup View
        `when`(view.context).thenReturn(application)
        `when`(application.applicationContext).thenReturn(application)
        `when`(view.display).thenReturn(display)
        `when`(display.displayId).thenReturn(Display.DEFAULT_DISPLAY)

        // Setup ViewModel
        `when`(viewModel.playbackState).thenReturn(playbackState)
        `when`(viewModel.metadata).thenReturn(metadata)
        `when`(viewModel.mediaSource).thenReturn(mediaSource)
        `when`(viewModel.playbackViewModel).thenReturn(playbackViewModel)
        `when`(viewModel.mediaModels).thenReturn(mediaModels)
        `when`(viewModel.mediaItemsRepository).thenReturn(mediaItemsRepository)
        `when`(mediaModels.playbackViewModel).thenReturn(playbackViewModel)

        // Create Controller
        val map: Map<Class<*>, Provider<View>> =
            mapOf(MinimizedMediaControlsPanelController::class.java to Provider { view })
        controller = MinimizedMediaControlsPanelController(
            "panel_id",
            PanelControllerMetadata.builder("panel_id")
                .addConfiguration(VIEW_TAG, MinimizedMediaControlsPanelController::class.java.name)
                .build(),
            map,
            shellController,
            mainExecutor,
            mainExecutor,
            playbackCardViewModelFactory,
            userContextFactory,
            minimizedMediaControlsPlaybackCardControllerFactory,
            mediaModelsFactory
        )

        // Inject Factory behavior
        `when`(
            playbackCardViewModelFactory.create(
                safeAny(),
                safeAny(),
                safeAny()
            )
        ).thenReturn(playbackCardViewModel)
        `when`(userContextFactory.create(safeAny(), anyInt())).thenReturn(application)
        `when`(
            minimizedMediaControlsPlaybackCardControllerFactory.create(
                safeAny(),
                safeAny(),
                safeAny(),
                safeAny(),
                safeAny()
            )
        ).thenReturn(playbackCardController)
        `when`(
            mediaModelsFactory.create(
                safeAny(),
                safeAny(),
                safeAny(),
                org.mockito.Mockito.anyBoolean()
            )
        ).thenReturn(mediaModels)

        // Override factory to return mock ViewModel
        controller.viewModelFactory = { _, _, _ -> viewModel }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> safeAny(): T {
        org.mockito.Mockito.any<T>()
        return null as T
    }

    @Test
    fun testInit_initializesViewModel() {
        `when`(shellController.currentUserId).thenReturn(TEST_USER_ID)

        controller.view
        TestableLooper.get(this).processAllMessages()

        // Verify factory was called (implied by viewModel existence, but we can't verify lambda easily)
        verify(shellController).addUserChangeListener(any())
    }

    @Test
    fun testUserChange_sameUser_doesNotReinitialize() {
        `when`(shellController.currentUserId).thenReturn(TEST_USER_ID)
        controller.view // Trigger init
        TestableLooper.get(this).processAllMessages()

        // Capture listener
        val captor = ArgumentCaptor.forClass(UserChangeListener::class.java)
        verify(shellController).addUserChangeListener(captor.capture())

        // Simulate SAME User Change
        captor.value.onUserChanged(TEST_USER_ID, mContext)
        TestableLooper.get(this).processAllMessages()

        // Verify cleanUp was NOT called (meaning no re-initialization)
        verify(viewModel, org.mockito.Mockito.never()).cleanUp()
    }

    @Test
    fun testUserChange_reinitializesViewModel() {
        `when`(shellController.currentUserId).thenReturn(TEST_USER_ID)
        controller.view // Trigger getView logic if it was lazy
        TestableLooper.get(this).processAllMessages()

        // Simulate User Change
        val captor = ArgumentCaptor.forClass(UserChangeListener::class.java)
        verify(shellController).addUserChangeListener(captor.capture())

        captor.value.onUserChanged(SECONDARY_USER_ID, mContext)
        TestableLooper.get(this).processAllMessages()

        verify(viewModel).cleanUp() // Should be called before creating new one
    }

    @Test
    fun testDestroy_cleansUp() {
        controller.view
        TestableLooper.get(this).processAllMessages()

        controller.destroy()
        TestableLooper.get(this).processAllMessages()

        verify(viewModel).cleanUp()
        verify(shellController).removeUserChangeListener(any())
    }

    @Test
    fun testLifecycle_isAttachedAndResumed() {
        controller.view // Initialize
        TestableLooper.get(this).processAllMessages()

        // We can check the controller's lifecycle directly as it implements LifecycleOwner
        org.junit.Assert.assertEquals(
            "Lifecycle should be RESUMED",
            androidx.lifecycle.Lifecycle.State.RESUMED,
            controller.lifecycle.currentState
        )
    }

    @Test
    fun testDestroy_lifecycleDestroyed() {
        controller.view
        TestableLooper.get(this).processAllMessages()

        controller.destroy()
        TestableLooper.get(this).processAllMessages()

        org.junit.Assert.assertEquals(
            "Lifecycle should be DESTROYED",
            androidx.lifecycle.Lifecycle.State.DESTROYED,
            controller.lifecycle.currentState
        )
    }
}
