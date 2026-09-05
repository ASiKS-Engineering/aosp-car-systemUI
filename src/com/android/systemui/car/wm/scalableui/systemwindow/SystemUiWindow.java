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
package com.android.systemui.car.wm.scalableui.systemwindow;

import android.graphics.Insets;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.scalableui.model.Corner;
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer;

/**
 * An abstraction that hides the complexities of managing a
 * {@link com.android.car.scalableui.panel.Panel} from SystemUI
 */
public interface SystemUiWindow {
    /**
     * Update the given {@link WindowManager.LayoutParams} by translating {@link Rect} & display
     * size.
     */
    static void updateLayoutParams(@NonNull WindowManager.LayoutParams params, @NonNull Rect bounds,
            @NonNull DisplayMetrics displayMetrics) {
        int leftMargin = bounds.left;
        int rightMargin = displayMetrics.widthPixels - bounds.right;

        int topMargin = bounds.top;
        int bottomMargin = displayMetrics.heightPixels - bounds.bottom;

        int gravity = leftMargin > rightMargin ? Gravity.END : Gravity.START;
        gravity = topMargin > bottomMargin ? gravity | Gravity.BOTTOM : gravity | Gravity.TOP;
        int marginHorizontalPx = Math.min(leftMargin, rightMargin);
        int marginVerticalPx = Math.min(topMargin, bottomMargin);

        params.height = bounds.height();
        params.width = bounds.width();
        params.gravity = gravity;
        params.horizontalMargin = ((float) marginHorizontalPx) / displayMetrics.widthPixels;
        params.verticalMargin = ((float) marginVerticalPx) / displayMetrics.heightPixels;
    }

    /**
     * @return name of the {@link SystemUiWindow}
     */
    @NonNull
    String getName();

    /**
     * Attaches {@link View} to WindowManager with the provided {@link WindowManager.LayoutParams}
     */
    void setRootView(@NonNull View view, @Nullable WindowManager.LayoutParams layoutParams);

    /**
     * Detaches the root view
     */
    void removeRootView();

    /**
     * Detaches the root view immediately. See {@link WindowManager#removeViewImmediate}
     */
    void removeRootViewImmediate();

    /**
     * @return {@code true} if root view is visible
     */
    boolean isVisible();

    /**
     * Hides the root view
     */
    void hide();

    /**
     * Shows the root view
     */
    void show();

    /**
     * @return {@link WindowManager.LayoutParams} that will be used to attach root view
     */
    @Nullable
    WindowManager.LayoutParams getLayoutParams();

    /**
     * @return {@link Rect} of window
     */
    @Nullable
    Rect getBounds();

    /**
     * @return height of the window
     */
    int getHeight();

    /**
     * @return width of the window
     */
    int getWidth();

    /**
     * @return alpha of the window
     */
    float getAlpha();

    /**
     * @return {@link Insets} of the window
     */
    @Nullable
    Insets getInsets();

    /**
     * @return corner radius of the window
     */
    Corner getCornerRadius();

    /**
     * @return display ID of the window
     */
    int getDisplayId();

    /**
     * Attach a {@link WindowUpdateCallback}
     */
    void addCallback(@NonNull WindowUpdateCallback callback);

    /**
     * Removes a {@link WindowUpdateCallback}
     */
    void removeCallback(@NonNull WindowUpdateCallback callback);

    /**
     * An abstraction that hides the concept of {@link PanelUpdateConsumer.PanelUpdateCallback}s
     * from SystemUI
     */
    interface WindowUpdateCallback extends PanelUpdateConsumer.PanelUpdateCallback {
    }
}

