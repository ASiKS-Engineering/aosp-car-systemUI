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

import android.annotation.IntDef;
import android.view.View;

import androidx.annotation.NonNull;

import com.android.systemui.car.systembar.SystemBarConstants;
import com.android.systemui.statusbar.policy.ConfigurationController;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An interface for controlling system bars.
 */
public interface CarSystemBarController extends ConfigurationController.ConfigurationListener {

    @IntDef(value = {SystemBarConstants.STATUS_BAR, SystemBarConstants.NAVIGATION_BAR})
    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    @Retention(RetentionPolicy.SOURCE)
    @interface SystemBarType {
    }

    /**
     * initializes the system bars.
     */
    void init();

    /**
     * Registers a touch listener callbar for the given system bar side.
     */
    void registerBarTouchListener(@NonNull String name, View.OnTouchListener listener);
}
