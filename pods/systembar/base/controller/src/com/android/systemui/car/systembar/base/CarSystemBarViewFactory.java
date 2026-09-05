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

import android.view.ViewGroup;

import androidx.annotation.NonNull;

/** A factory that creates and caches views for navigation bars. */
public interface CarSystemBarViewFactory {

    /** Gets the window by side. */
    @NonNull
    ViewGroup getSystemBarWindow(@NonNull String name);

    /** Gets the bar view by side. */
    @NonNull
    CarSystemBarViewController getSystemBarViewController(@NonNull String name, boolean isSetUp);

    /** Resets the cached system bar views. */
    void resetSystemBarViewCache();

    /** Resets the cached system bar windows and system bar views. */
    void resetSystemBarWindowCache();
}
