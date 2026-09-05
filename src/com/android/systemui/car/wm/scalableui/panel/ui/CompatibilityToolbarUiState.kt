/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.systemui.car.wm.scalableui.panel.ui

import android.graphics.Rect
import android.os.Build
import android.util.Log
import com.android.systemui.car.wm.scalableui.panel.controller.CompatibilityToolbarController
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents the possible UI states of the TaskToolbar.
 */
sealed class CompatibilityToolbarState {
    /** The toolbar is visible and floating within the task bounds. */
    data object Open : CompatibilityToolbarState()

    /** The toolbar is visible and its height suggests a full-screen or immersive context. */
    data object Immersive : CompatibilityToolbarState()

    /** The toolbar is not visible, has no size, or is outside the display bounds. */
    data object Closed : CompatibilityToolbarState()
}

/**
 * A ViewModel (or state holder) for the TaskToolBar.
 *
 * This class tracks the UI state of the toolbar based on its bounds and provides a reactive
 * [StateFlow] of state updates. It is designed to be injected via Dagger.
 */
class CompatibilityToolbarUiState @Inject constructor() {

    private val _uiState =
        MutableStateFlow<CompatibilityToolbarState>(CompatibilityToolbarState.Closed)
    val uiState: StateFlow<CompatibilityToolbarState> = _uiState.asStateFlow()

    /**
     * Updates the toolbar's state based on its new bounds relative to the display.
     *
     * The state is determined by the following logic:
     * - [CompatibilityToolbarState.Closed] if the toolbar bounds are empty or outside the display bounds.
     * - [CompatibilityToolbarState.Immersive] if the toolbar's height takes up more than 80% of the display's
     *   height.
     * - [CompatibilityToolbarState.Open] otherwise (visible and not immersive).
     *
     * @param toolbarBounds The new bounds of the toolbar view.
     * @param displayBounds The bounds of the display the toolbar is on.
     */
    fun updateToolbarState(toolbarBounds: Rect, displayBounds: Rect) {
        logIfDebuggable("updateToolbarState: $toolbarBounds, $displayBounds")
        _uiState.value = when {
            toolbarBounds.isEmpty || !Rect.intersects(toolbarBounds, displayBounds) -> {
                CompatibilityToolbarState.Closed
            }

            toolbarBounds.height() > (displayBounds.height() * IMMERSIVE_HEIGHT_THRESHOLD) -> {
                CompatibilityToolbarState.Immersive
            }

            else -> {
                CompatibilityToolbarState.Open
            }
        }
    }

    override fun toString(): String {
        return uiState.value.toString()
    }

    companion object {
        private val DEBUG = Build.isDebuggable()
        private const val IMMERSIVE_HEIGHT_THRESHOLD = 0.5f

        const val TAG: String = "ToolbarUiState"
        fun logIfDebuggable(msg: String) {
            if (DEBUG) {
                Log.d(CompatibilityToolbarController.Companion.TAG, msg)
            }
        }
    }
}
