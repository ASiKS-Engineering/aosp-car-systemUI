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

import android.os.Build
import android.util.Log
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.display.data.repository.DisplayRepository
import com.android.systemui.process.ProcessWrapper
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@SysUISingleton
class ExternalDisplayController @Inject constructor(
    private val displayRepository: DisplayRepository,
    private val processWrapper: ProcessWrapper,
    @Application private val scope: CoroutineScope,
    @Background private val bgDispatcher: CoroutineDispatcher,
) : CoreStartable {

    override fun start() {
        if (!processWrapper.isSystemUser) {
            if (DEBUG) {
                Log.d(TAG, "no-op for non-system users")
            }
            return
        }
        scope.launch(bgDispatcher) {
            displayRepository.pendingDisplay.collect {
                if (DEBUG) {
                    Log.d(TAG, "Enabling pending display")
                }
                it?.enable()
            }
        }
    }

    companion object {
        private val TAG: String = ExternalDisplayController::class.java.simpleName
        private val DEBUG: Boolean = Build.IS_ENG || Build.IS_USERDEBUG
    }

    @Module
    interface StartableModule {
        @Binds
        @IntoMap
        @ClassKey(ExternalDisplayController::class)
        fun bindsExternalDisplayController(impl: ExternalDisplayController): CoreStartable
    }
}
