/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.systemui.car.systembar.base;

import android.content.Context;
import android.view.InsetsFrameProvider;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.car.wm.scalableui.systemwindow.SystemUiWindow;

import java.util.List;

/**
 * Interface for classes that provide system bar configurations.
 */
public interface SystemBarConfigs {
    /**
     * Invalidate cached resources and fetch from resources config file.
     *
     * <p>
     * This method should be called when the system bar configurations need to be refreshed,
     * such as when an RRO (Runtime Resource Overlay) is applied.
     * </p>
     */
    void resetSystemBarConfigs();

    /**
     * When creating system bars or overlay windows, use a WindowContext
     * for that particular window type to ensure proper display metrics.
     */
    @Nullable
    Context getWindowContextByName(@NonNull String name);

    /**
     * @return The system bar view for the given name. {@code null} if name is unknown.
     */
    @Nullable
    ViewGroup getSystemBarLayoutByName(@NonNull String name, boolean isSetUp);

    /**
     * @return the systembar window for the given name. {@code null} if name is unknown.
     */
    @Nullable
    ViewGroup getWindowLayoutByName(@NonNull String name);

    /**
     * @return The {@link WindowManager.LayoutParams}, or {@code null} if the name is unknown
     */
    @Nullable
    WindowManager.LayoutParams getLayoutParamsByName(@NonNull String name);

    /**
     * @return {@code true} if the system bar is enabled, {@code false} otherwise.
     */
    boolean getEnabledStatusByName(@NonNull String name);

    /**
     * @return {@code true} if the system bar should be hidden, {@code false} otherwise.
     */
    boolean getHideForKeyboardByName(@NonNull String name);

    /**
     * Applies padding to the given system bar view.
     *
     * @param view The system bar view
     */
    void insetSystemBar(@NonNull String name, ViewGroup view);

    /**
     * @return A list of system bar names sorted by their Z order.
     */
    List<String> getSystemBarNamesByZOrder();

    /**
     * @return one of the following values, or {@code -1} if the name is unknown
     */
    int getSystemBarInsetTypeByName(@NonNull String name);

    /**
     * @return The {@link InsetsFrameProvider}, or {@code null} if the name is unknown
     */
    @Nullable
    InsetsFrameProvider getInsetsFrameProviderByName(@NonNull String name);

    /**
     * @return {@link SystemUiWindow} for name.
     */
    @Nullable
    SystemUiWindow getWindowForName(@NonNull String name);
}
