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

package com.android.systemui.car.systembar.panel;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

/**
 * Provides the content and configuration for a panel.
 */
public interface PanelContentProvider {

    /**
     * Creates and returns the content view for the panel.
     *
     * @param context The context to use for inflating the view.
     * @return The content view for the panel.
     */
    @Nullable
    ViewGroup createPanelContentView(Context context);

    /**
     * @return The width of the panel in pixels.
     */
    int getPanelWidthPx();

    /**
     * @return The X offset of the panel in pixels.
     */
    default int getXOffsetPx() {
        return 0;
    }

    /**
     * @return The Y offset of the panel in pixels.
     */
    default int getYOffsetPx() {
        return 0;
    }

    /**
     * @return The gravity of the panel.
     */
    default int getPanelGravity() {
        return Gravity.TOP | Gravity.START;
    }

    /**
     * @return Whether the panel is disabled while driving.
     */
    default boolean isDisabledWhileDriving() {
        return false;
    }

    /**
     * @return Whether the panel is disabled while unprovisioned.
     */
    default boolean isDisabledWhileUnprovisioned() {
        return false;
    }

    /**
     * @return Whether the panel should be shown as a drop down.
     */
    default boolean getShowAsDropDown() {
        return true;
    }
}
