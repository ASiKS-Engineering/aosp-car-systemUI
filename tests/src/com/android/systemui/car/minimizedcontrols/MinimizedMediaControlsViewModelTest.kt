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
import androidx.test.filters.SmallTest
import com.android.car.media.common.playback.PlaybackViewModel
import com.android.car.media.common.source.MediaModels
import com.android.car.media.common.source.MediaSourceViewModel
import com.android.systemui.CarSysuiTestCase
import com.android.systemui.car.CarSystemUiTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@CarSystemUiTest
@RunWith(AndroidTestingRunner::class)
@SmallTest
class MinimizedMediaControlsViewModelTest : CarSysuiTestCase() {

    @Mock private lateinit var mediaModels: MediaModels
    @Mock private lateinit var playbackViewModel: PlaybackViewModel
    @Mock private lateinit var mediaSourceViewModel: MediaSourceViewModel

    private lateinit var viewModel: MinimizedMediaControlsViewModel

    companion object {
        private const val TEST_USER_ID = 100
    }

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        // Setup MediaModels
        `when`(mediaModels.playbackViewModel).thenReturn(playbackViewModel)
        `when`(mediaModels.mediaSourceViewModel).thenReturn(mediaSourceViewModel)

        viewModel = MinimizedMediaControlsViewModel(mediaModels)
    }

    @Test
    fun testInit_createsMediaModels() {
        // verify properties are accessible immediately
        assertEquals(playbackViewModel, viewModel.playbackViewModel)
        verify(mediaModels).playbackViewModel // Check property access on mock
    }

    @Test
    fun testCleanUp_clearsMediaModels() {
        viewModel.cleanUp()

        verify(mediaModels).onCleared()
        assertNull(viewModel.playbackViewModel)
    }
}
