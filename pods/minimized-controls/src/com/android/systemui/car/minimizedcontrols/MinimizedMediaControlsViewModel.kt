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

import androidx.lifecycle.LiveData
import com.android.car.media.common.MediaItemMetadata
import com.android.car.media.common.browse.MediaItemsRepository
import com.android.car.media.common.playback.PlaybackViewModel
import com.android.car.media.common.source.MediaModels
import com.android.car.media.common.source.MediaSource

/**
 * ViewModel for Minimized Media Controls.
 * Manages [MediaModels] and provides media data to the controller.
 */
class MinimizedMediaControlsViewModel(
    var mediaModels: MediaModels?
) {

    val playbackViewModel: PlaybackViewModel?
        get() = mediaModels?.playbackViewModel

    val playbackState: LiveData<PlaybackViewModel.PlaybackStateWrapper>?
        get() = playbackViewModel?.playbackStateWrapper

    val metadata: LiveData<MediaItemMetadata>?
        get() = playbackViewModel?.metadata

    val mediaSource: LiveData<MediaSource>?
        get() = mediaModels?.mediaSourceViewModel?.primaryMediaSource

    val mediaItemsRepository: MediaItemsRepository?
        get() = mediaModels?.mediaItemsRepository

    fun cleanUp() {
        mediaModels?.onCleared()
        mediaModels = null
    }

    companion object {
        private const val TAG = "MinimizedMediaControlsViewModel"
    }
}
