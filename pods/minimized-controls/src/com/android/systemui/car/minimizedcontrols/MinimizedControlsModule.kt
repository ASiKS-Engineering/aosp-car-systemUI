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

import com.android.car.media.common.source.MediaModels
import com.android.car.media.common.ui.PlaybackCardController
import com.android.car.media.common.ui.PlaybackCardViewModel
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.shared.annotations.ShellMainThread
import dagger.Module
import dagger.Provides

/** Dagger module for minimized controls. */
@Module
abstract class MinimizedControlsModule {
    companion object {
        @Provides
        fun providePlaybackCardViewModelFactory():
            MinimizedMediaControlsPanelController.PlaybackCardViewModelFactory {
            return MinimizedMediaControlsPanelController.PlaybackCardViewModelFactory {
                app, context, models ->
                PlaybackCardViewModel(app).apply { init(context, models) }
            }
        }

        @Provides
        fun provideUserContextFactory(): UserContextUtils.UserContextFactory {
            return UserContextUtils.UserContextFactory { context, userId ->
                UserContextUtils.createWrappedUserContext(context, userId)
            }
        }

        @Provides
        fun provideMinimizedMediaControlsPlaybackCardControllerFactory(
            @ShellMainThread shellExecutor: ShellExecutor
        ): MinimizedMediaControlsPlaybackCardController.Factory {
            return MinimizedMediaControlsPlaybackCardController.Factory {
                view, pViewModel, pcViewModel, repo, context ->
                val builder = PlaybackCardController.Builder()
                    .setViewGroup(view)
                    .setModels(pViewModel, pcViewModel, repo)
                    .setContext(context)
                MinimizedMediaControlsPlaybackCardController(builder, shellExecutor)
            }
        }

        @Provides
        fun provideMediaModelsFactory(): MinimizedMediaControlsPanelController.MediaModelsFactory {
            return MinimizedMediaControlsPanelController.MediaModelsFactory {
                context, notificationProvider, sessionProvider, ignoreBrowser ->
                MediaModels(context, notificationProvider, sessionProvider, ignoreBrowser)
            }
        }
    }
}
