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

import android.content.res.Configuration
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.test.filters.SmallTest
import com.android.car.media.common.MediaItemMetadata
import com.android.car.media.common.browse.MediaItemsRepository
import com.android.car.media.common.playback.PlaybackProgress
import com.android.car.media.common.playback.PlaybackViewModel
import com.android.car.media.common.source.MediaSource
import com.android.car.media.common.source.MediaSourceColors
import com.android.car.media.common.ui.PlaybackCardController
import com.android.car.media.common.ui.PlaybackCardViewModel
import com.android.systemui.CarSysuiTestCase
import com.android.systemui.car.CarSystemUiTest
import com.android.systemui.lifecycle.InstantTaskExecutorRule
import com.android.wm.shell.common.ShellExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@CarSystemUiTest
@RunWith(AndroidTestingRunner::class)
@TestableLooper.RunWithLooper
@SmallTest
class MinimizedMediaControlsPlaybackCardControllerTest : CarSysuiTestCase() {

    @Mock private lateinit var shellExecutor: ShellExecutor
    @Mock private lateinit var playbackViewModel: PlaybackViewModel
    @Mock private lateinit var playbackCardViewModel: PlaybackCardViewModel
    @Mock private lateinit var mediaItemsRepository: MediaItemsRepository
    @Mock private lateinit var mediaSource: MediaSource

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var controller: MinimizedMediaControlsPlaybackCardController
    private lateinit var rootView: View
    private lateinit var albumArtContainer: View

    private val mediaSourceLiveData = MutableLiveData<MediaSource>()
    private val metadataLiveData = MutableLiveData<MediaItemMetadata>()
    private val progressLiveData = MutableLiveData<PlaybackProgress>()
    private val mediaSourceColorsLiveData = MutableLiveData<MediaSourceColors>()
    private val playbackStateWrapperLiveData =
        MutableLiveData<PlaybackViewModel.PlaybackStateWrapper>()
    private val hasQueueLiveData = MutableLiveData<Boolean>()
    private val playbackControllerLiveData = MutableLiveData<PlaybackViewModel.PlaybackController>()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        doAnswer { invocation ->
            (invocation.arguments[0] as Runnable).run()
            null
        }.`when`(shellExecutor).execute(any())

        val context = mContext
        rootView = FrameLayout(context)
        rootView.id = R.id.card_container

        // Container
        albumArtContainer = FrameLayout(context)
        albumArtContainer.id = R.id.minimized_control_album_art_container

        // Album Art
        val albumArt = ImageView(context)
        albumArt.id = R.id.album_art

        // Add to hierarchy
        (albumArtContainer as ViewGroup).addView(albumArt)
        (rootView as ViewGroup).addView(albumArtContainer)

        // Setup LifecycleOwner
        val lifecycleOwner = object : LifecycleOwner {
            private val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle = registry

            init {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
        }
        rootView.setViewTreeLifecycleOwner(lifecycleOwner)

        `when`(playbackViewModel.mediaSource).thenReturn(mediaSourceLiveData)
        `when`(playbackViewModel.metadata).thenReturn(metadataLiveData)
        `when`(playbackViewModel.progress).thenReturn(progressLiveData)
        `when`(playbackViewModel.mediaSourceColors).thenReturn(mediaSourceColorsLiveData)
        `when`(playbackViewModel.playbackStateWrapper).thenReturn(playbackStateWrapperLiveData)
        `when`(playbackViewModel.hasQueue()).thenReturn(hasQueueLiveData)
        `when`(playbackViewModel.playbackController).thenReturn(playbackControllerLiveData)

        val builder = PlaybackCardController.Builder()
            .setViewGroup(rootView as ViewGroup)
            .setModels(playbackViewModel, playbackCardViewModel, mediaItemsRepository)
            .setContext(context)

        controller = MinimizedMediaControlsPlaybackCardController(builder, shellExecutor)
        controller.setupController()
    }

    @Test
    fun testCardContainerClick_launchesActivity_inPortraitMode() {
        setUpControllerWithOrientation(Configuration.ORIENTATION_PORTRAIT)

        mediaSourceLiveData.value = mediaSource

        val cardContainer = rootView.findViewById<View>(R.id.card_container)
        cardContainer?.performClick()

        verify(mediaSource).launchActivity(any(), any())
    }

    @Test
    fun testRootViewClick_doesNotLaunchActivity_inLandscapeMode() {
        setUpControllerWithOrientation(Configuration.ORIENTATION_LANDSCAPE)

        mediaSourceLiveData.value = mediaSource

        // perform click
        rootView.performClick()

        verify(mediaSource, never()).launchActivity(any(), any())
    }

    @Test
    fun testAlbumArtContainerClick_launchesActivity_inLandscapeMode() {
        setUpControllerWithOrientation(Configuration.ORIENTATION_LANDSCAPE)

        mediaSourceLiveData.value = mediaSource

        // perform click
        albumArtContainer.performClick()

        verify(mediaSource).launchActivity(any(), any())
    }

    private fun setUpControllerWithOrientation(orientation: Int) {
        rootView.setOnClickListener(null)
        albumArtContainer.setOnClickListener(null)
        mContext.resources.configuration.orientation = orientation
        val builder = PlaybackCardController.Builder()
            .setViewGroup(rootView as ViewGroup)
            .setModels(playbackViewModel, playbackCardViewModel, mediaItemsRepository)
            .setContext(mContext)
        controller = MinimizedMediaControlsPlaybackCardController(builder, shellExecutor)
        controller.setupController()
    }
}
