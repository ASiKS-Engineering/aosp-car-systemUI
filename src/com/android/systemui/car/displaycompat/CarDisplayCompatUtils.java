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

package com.android.systemui.car.displaycompat;

import static android.car.Car.PERMISSION_MANAGE_DISPLAY_COMPATIBILITY;

import android.app.ActivityManager;
import android.car.content.pm.CarPackageManager;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

/**
 * Utility class for display compatibility
 */
public class CarDisplayCompatUtils {
    private static final String TAG = "CarDisplayCompatUtils";

    /**
     * @return the package name associated with the taskInfo
     */
    @Nullable
    public static String getPackageName(ActivityManager.RunningTaskInfo taskInfo) {
        if (taskInfo.topActivity != null) {
            return taskInfo.topActivity.getPackageName();
        }
        if (taskInfo.baseIntent.getComponent() != null) {
            return taskInfo.baseIntent.getComponent().getPackageName();
        }
        return null;
    }

    /**
     * @return {@code true} if the {@code packageName} requires display compatibility
     */
    @RequiresPermission(allOf = {PERMISSION_MANAGE_DISPLAY_COMPATIBILITY,
            android.Manifest.permission.QUERY_ALL_PACKAGES})
    public static boolean requiresDisplayCompat(
            @Nullable String packageName, int userId,
            @Nullable CarPackageManager carPackageManager) {
        if (packageName == null) {
            return false;
        }
        if (carPackageManager == null) {
            return false;
        }
        boolean result = false;
        try {
            result = carPackageManager.requiresDisplayCompatForUser(packageName, userId);
        } catch (PackageManager.NameNotFoundException e) {
            Log.v(TAG, e.toString());
        }
        return result;
    }
}
