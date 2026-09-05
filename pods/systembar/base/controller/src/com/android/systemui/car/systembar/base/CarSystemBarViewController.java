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
package com.android.systemui.car.systembar.base;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Set;

/**
 * A controller for initializing the system bar views.
 */
public interface CarSystemBarViewController {
    @IntDef(value = {BUTTON_TYPE_NAVIGATION, BUTTON_TYPE_KEYGUARD, BUTTON_TYPE_OCCLUSION})
    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    @interface ButtonsType {
    }
    int BUTTON_TYPE_NAVIGATION = 0;
    int BUTTON_TYPE_KEYGUARD = 1;
    int BUTTON_TYPE_OCCLUSION = 2;

    /**
     * Call to initialize the internal state.
     */
    void init();

    /**
     * Call to save the internal state.
     */
    void onSaveInstanceState(@NonNull Bundle outState);

    /**
     * Call to restore the internal state.
     */
    void onRestoreInstanceState(@Nullable Bundle savedInstanceState);

    /**
     * Only visible so that this view can be attached to the window.
     */
    ViewGroup getView();

    /**
     * Sets the touch listeners that will be called from onInterceptTouchEvent and onTouchEvent
     *
     * @param statusBarWindowTouchListeners List of listeners to call from touch and intercept touch
     */
    void setSystemBarTouchListeners(Set<View.OnTouchListener> statusBarWindowTouchListeners);

    /**
     * Shows buttons of the specified {@link ButtonsType}.
     *
     * NOTE: Only one type of buttons can be shown at a time, so showing buttons of one type will
     * hide all buttons of other types.
     *
     * @param buttonsType see {@link ButtonsType}
     */
    void showButtonsOfType(@ButtonsType int buttonsType);
}
