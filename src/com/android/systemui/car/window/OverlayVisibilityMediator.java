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
package com.android.systemui.car.window;

import androidx.annotation.Nullable;

import java.util.Collection;

/**
 * Manages the visibility state of the {@link OverlayViewController} on the screen.
 */
public interface OverlayVisibilityMediator {

    /**
     * Called when a panel requests to become visible.
     */
    void showView(OverlayViewController controller);

    /**
     * Called when a panel requests to become hidden.
     */
    void hideView(OverlayViewController viewController);

    /**
     * Returns true if there is any visible overlays.
     */
    boolean isAnyOverlayViewVisible();

    /**
     * Returns true if the given ovelray has been shown before.
     */
    boolean hasOverlayViewBeenShown(OverlayViewController viewController);

    /**
     * Returns true if the given ovelray is currently visible.
     */
    boolean isOverlayViewVisible(OverlayViewController viewController);

    /**
     * Returns the overlay that has the highest Z order.
     */
    @Nullable
    OverlayViewController getHighestZOrderOverlayViewController();

    /**
     * Returns the {@link Collection} of currently visible overlays sorted by Z order.
     */
    Collection<OverlayViewController> getVisibleOverlayViewsByZOrder();
}
