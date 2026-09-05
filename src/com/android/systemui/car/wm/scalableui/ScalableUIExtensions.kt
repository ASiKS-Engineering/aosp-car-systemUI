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
package com.android.systemui.car.wm.scalableui

import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics

/**
 * Gets the full physical bounds of a given display.
 *
 * This function uses [Display.getRealMetrics] to retrieve the raw physical dimensions
 * of the display, including any system decorations like the status and navigation bars.
 *
 * @param displayId The ID of the target display.
 * @return A [Rect] representing the full bounds of the display. If the display is not found,
 *         an empty [Rect] is returned.
 */
fun Context.getDisplayBounds(displayId: Int): Rect {
    val displayManager = getSystemService(DisplayManager::class.java)
    val display = displayManager?.getDisplay(displayId) ?: return Rect()

    // Should update to use window manager instead of DisplayManager
    val metrics = DisplayMetrics()
    display.getRealMetrics(metrics)
    return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
}
