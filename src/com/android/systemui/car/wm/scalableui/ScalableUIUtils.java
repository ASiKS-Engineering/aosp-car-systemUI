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
package com.android.systemui.car.wm.scalableui;

import android.content.Context;
import android.content.pm.PackageManager;

import com.android.systemui.car.flags.Flag;
import com.android.systemui.car.flags.FlagManager;
import com.android.systemui.car.shared.R;

public class ScalableUIUtils {

    private ScalableUIUtils() {}

    public static boolean isScalableUIEnabled(Context context, FlagManager flagManager) {
        return flagManager.isEnabled(Flag.ScalableUIEnabled)
                && context.getResources().getBoolean(R.bool.config_enableScalableUI)
                && context.getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_CAR_SPLITSCREEN_MULTITASKING);
    }
}
