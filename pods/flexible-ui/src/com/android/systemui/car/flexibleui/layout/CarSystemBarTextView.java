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

package com.android.systemui.car.flexibleui.layout;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.systemui.car.flexibleui.CarSystemBarElement;
import com.android.systemui.car.flexibleui.CarSystemBarElementFlags;
import com.android.systemui.car.flexibleui.CarSystemBarElementResolver;

/** Implementation of TextView that supports {@link CarSystemBarElement} attributes */
public class CarSystemBarTextView extends TextView implements CarSystemBarElement {
    @Nullable
    private Class<?> mElementControllerClassAttr;
    private int mSystemBarDisableFlags;
    private int mSystemBarDisable2Flags;
    private boolean mDisableForLockTaskModeLocked;

    public CarSystemBarTextView(@NonNull Context context) {
        super(context);
        init(context, /* attrs= */ null);
    }

    public CarSystemBarTextView(@NonNull Context context,
            @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CarSystemBarTextView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public CarSystemBarTextView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        mElementControllerClassAttr =
                CarSystemBarElementResolver.getElementControllerClassFromAttributes(context, attrs);
        mSystemBarDisableFlags =
                CarSystemBarElementFlags.getStatusBarManagerDisableFlagsFromAttributes(context,
                        attrs);
        mSystemBarDisable2Flags =
                CarSystemBarElementFlags.getStatusBarManagerDisable2FlagsFromAttributes(context,
                        attrs);
        mDisableForLockTaskModeLocked =
                CarSystemBarElementFlags.getDisableForLockTaskModeLockedFromAttributes(context,
                        attrs);
    }

    @Override
    public Class<?> getElementControllerClass() {
        return mElementControllerClassAttr;
    }

    @Override
    public int getSystemBarDisableFlags() {
        return mSystemBarDisableFlags;
    }

    @Override
    public int getSystemBarDisable2Flags() {
        return mSystemBarDisable2Flags;
    }

    @Override
    public boolean disableForLockTaskModeLocked() {
        return mDisableForLockTaskModeLocked;
    }
}
