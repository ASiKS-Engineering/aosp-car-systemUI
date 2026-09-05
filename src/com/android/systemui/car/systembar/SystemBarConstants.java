/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.car.systembar;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Constants for System Bars.
 */
public final class SystemBarConstants {
    private SystemBarConstants() {}

    public static final String TOP_BAR_NAME = "Top";
    public static final String BOTTOM_BAR_NAME = "Bottom";
    public static final String LEFT_BAR_NAME = "Left";
    public static final String RIGHT_BAR_NAME = "Right";

    public static final int STATUS_BAR = 0;
    public static final int NAVIGATION_BAR = 1;

    @IntDef(prefix = { "SYSTEM_BAR_" }, value = {
            STATUS_BAR,
            NAVIGATION_BAR,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SystemBarType {}

    // System Bar Persistency Configs
    public static final int SYSTEM_BAR_PERSISTENCY_CONFIG_NON_IMMERSIVE = 0;
    public static final int SYSTEM_BAR_PERSISTENCY_CONFIG_IMMERSIVE = 1;
    public static final int SYSTEM_BAR_PERSISTENCY_CONFIG_IMMERSIVE_WITH_NAV = 2;
    public static final int SYSTEM_BAR_PERSISTENCY_CONFIG_BARPOLICY = 3;

    // System Bar SUW Persistency Configs
    public static final int SYSTEM_BAR_SUW_PERSISTENCY_CONFIG_DISABLED = 0;
    public static final int SYSTEM_BAR_SUW_PERSISTENCY_CONFIG_IMMERSIVE = 1;
    public static final int SYSTEM_BAR_SUW_PERSISTENCY_CONFIG_IMMERSIVE_WITH_NAV = 2;
    public static final int SYSTEM_BAR_SUW_PERSISTENCY_CONFIG_IMMERSIVE_WITH_STATUS = 3;

    // Visibility Types Indices
    public static final int VISIBLE_BAR_VISIBILITIES_TYPES_INDEX = 0;
    public static final int INVISIBLE_BAR_VISIBILITIES_TYPES_INDEX = 1;
}
