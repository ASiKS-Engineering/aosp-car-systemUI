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
package com.android.systemui.car.wm.scalableui.panel.panelupdates;

import android.annotation.FlaggedApi;
import android.graphics.Insets;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.scalableui.Flags;
import com.android.car.scalableui.model.Corner;
import com.android.car.scalableui.model.PanelControllerMetadata;

/**
 * Defines a contract for components that need to be notified about updates to a panel
 * or query the last known property of a panel.
 */
public interface PanelUpdateConsumer {

    /**
     * Registers a callback to receive panel updates for a specific panel.
     *
     * @param panelId  The ID of the panel to register for.
     * @param callback The callback to be notified of future updates.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_EXT_PANEL_UPDATES)
    void registerCallback(@NonNull String panelId, @NonNull PanelUpdateCallback callback);

    /**
     * Unregisters a previously registered callback for panel updates from all panels.
     *
     * @param callback The callback to remove.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_EXT_PANEL_UPDATES)
    void unregisterCallback(@NonNull PanelUpdateCallback callback);

    /**
     * Unregisters a previously registered callback for a particular panel.
     *
     * @param panelId  The ID of the panel to unregister for.
     * @param callback The callback to remove.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_EXT_PANEL_UPDATES)
    void unregisterCallback(@NonNull String panelId, @NonNull PanelUpdateCallback callback);

    /**
     * Returns the last known bounds for a given panel ID.
     *
     * @param panelId The ID of the panel to query.
     * @return The last known {@link Rect} for the panel, or {@code null} if no bounds has
     * been recorded yet or the panel ID is unknown.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_EXT_PANEL_UPDATES)
    @Nullable
    Rect getBounds(String panelId);

    /**
     * Returns the last known alpha for a given panel ID.
     *
     * @param panelId The ID of the panel to query.
     * @return The last known alpha for the panel, or {@code null} if no alpha has
     * been recorded yet or the panel ID is unknown.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_EXT_PANEL_UPDATES)
    Float getAlpha(String panelId);

    /**
     * Returns the last known corner radius for a given panel ID.
     *
     * @param panelId The ID of the panel to query.
     * @return The last known corner radius for the panel, or {@code null} if no bounds have
     * been recorded yet or the panel ID is unknown.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_EXT_PANEL_UPDATES)
    Corner getCornerRadius(String panelId);

    /**
     * Returns the last known visibility for a given panel ID.
     *
     * @param panelId The ID of the panel to query.
     * @return The last known visibility for the panel, or {@code null} if no visibility have
     * been recorded yet or the panel ID is unknown.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_EXT_PANEL_UPDATES)
    Boolean isVisible(String panelId);

    /**
     * Returns the last known insets for a given panel ID.
     *
     * @param panelId The ID of the panel to query.
     * @return The last known {@link Insets} for the panel, or {@code null} if no bounds have
     * been recorded yet or the panel ID is unknown.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_EXT_PANEL_UPDATES)
    @Nullable
    Insets getInsets(String panelId);

    /**
     * Returns the last known bounds for a given panel ID.
     *
     * @param panelId The ID of the panel to query.
     * @return The last known {@link PanelControllerMetadata} for the panel, or {@code null} if no
     * {@link PanelControllerMetadata} have been recorded yet or the panel ID is unknown.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_EXT_PANEL_UPDATES)
    @Nullable
    PanelControllerMetadata getPanelControllerMetadata(String panelId);

    /**
     * Returns the last known gravity value for a given panel ID.
     *
     * @param panelId The ID of the panel to query.
     * @return The last known gravity value for the panel, or {@code null} if no gravity has been
     * recorded yet or the panel ID is unknown.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_EXT_PANEL_UPDATES)
    int getGravity(@NonNull String panelId);

    /**
     * Callback interface for receiving notifications about panel updates.
     * Implementers can register instances of this callback using
     * {@link PanelUpdateConsumer#registerCallback(String, PanelUpdateCallback)}
     */
    interface PanelUpdateCallback {
        /**
         * Called when the panel's bounds are updated.
         *
         * @param panelId The associated panelId for the bounds change
         * @param rect    The new (or last known replayed) bounds of the panel.
         */
        @FlaggedApi(Flags.FLAG_ENABLE_EXT_PANEL_UPDATES)
        default void onBoundsChange(@NonNull String panelId, @NonNull Rect rect) {
            // Default implementation does nothing, allowing selective overriding.
        }

        /**
         * Called when the panel's task toolbar bounds are updated.
         *
         * @param panelId The associated panelId for the bounds change
         * @param rect    The new (or last known replayed) bounds of the panel.
         */
        @FlaggedApi(Flags.FLAG_ENABLE_EXT_PANEL_UPDATES)
        default void onTaskToolbarBoundsChange(@NonNull String panelId, @NonNull Rect rect) {
            // Default implementation does nothing, allowing selective overriding.
        }

        /**
         * Called when the panel's alpha is updated.
         *
         * @param panelId The associated panelId for the alpha change
         * @param alpha   The new (or last known replayed) alpha of the panel.
         */
        @FlaggedApi(Flags.FLAG_ENABLE_EXT_PANEL_UPDATES)
        default void onAlphaChange(@NonNull String panelId, float alpha) {
            // Default implementation does nothing, allowing selective overriding.
        }

        /**
         * Called when the panel's corner radius is updated.
         *
         * @param panelId The associated panelId for the corner radius change
         * @param radius  The new (or last known replayed) corner radius of the panel.
         */
        @FlaggedApi(Flags.FLAG_ENABLE_EXT_PANEL_UPDATES)
        default void onCornerRadiusChange(@NonNull String panelId, Corner radius) {
            // Default implementation does nothing, allowing selective overriding.
        }

        /**
         * Called when the panel's visibility is updated.
         *
         * @param panelId   The associated panelId for the visibility change
         * @param isVisible The new (or last known replayed) visibility of the panel.
         */
        @FlaggedApi(Flags.FLAG_ENABLE_EXT_PANEL_UPDATES)
        default void onVisibilityChange(@NonNull String panelId, boolean isVisible) {
            // Default implementation does nothing, allowing selective overriding.
        }

        /**
         * Called when the panel's insets are updated.
         *
         * @param panelId The associated panelId for the insets change
         * @param insets  The new (or last known replayed) insets of the panel.
         */
        @FlaggedApi(Flags.FLAG_ENABLE_EXT_PANEL_UPDATES)
        default void onInsetsChange(@NonNull String panelId, @NonNull Insets insets) {
            // Default implementation does nothing, allowing selective overriding.
        }

        /**
         * Called when the panel's gravity is updated.
         *
         * @param panelId The associated panelId for the gravity change.
         * @param gravity The new (or last known replayed) gravity of the panel.
         */
        default void onGravityChange(@NonNull String panelId, int gravity) {
        }
    }
}
