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

import android.content.Context
import android.view.View
import com.android.car.scalableui.panel.DecorPanelController
import com.android.systemui.car.wm.scalableui.panel.controller.DecorPanelViewMap
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/** Dagger module for minimized controls panel/view bindings. */
@Module
abstract class MinimizedControlsPanelModule {
    @Binds
    @IntoMap
    @ClassKey(MinimizedMediaControlsPanelController::class)
    abstract fun bindMinimizedMediaControlsControllerFactory(
        factory: MinimizedMediaControlsPanelController.Factory
    ): DecorPanelController.Factory<*>

    @Binds
    @IntoMap
    @ClassKey(MinimizedDialerControlsPanelController::class)
    abstract fun bindMinimizedDialerControlsControllerFactory(
        factory: MinimizedDialerControlsPanelController.Factory
    ): DecorPanelController.Factory<*>

    companion object {
        @Provides
        @IntoMap
        @ClassKey(MinimizedMediaControlsView::class)
        @DecorPanelViewMap
        fun provideMinimizedMediaControlsView(context: Context): View {
            return MinimizedMediaControlsView(context)
        }

        @Provides
        @IntoMap
        @ClassKey(MinimizedDialerControlsView::class)
        @DecorPanelViewMap
        fun provideMinimizedDialerControlsView(context: Context): View {
            return MinimizedDialerControlsView(context)
        }
    }
}
