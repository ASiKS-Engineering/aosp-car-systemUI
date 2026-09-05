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
package com.android.systemui.car.flags

import android.annotation.BoolRes
import com.android.systemui.car.shared.R

/**
 * An enum to represent all supported feature flags.
 *
 * It is initialized using the string constants from `FlagManager` to ensure a single
 * source of truth. It automatically parses the full identifier into a class name and method name.
 *
 * ### Adding a new Flag
 * To add a new feature flag, follow these steps and naming conventions:
 *
 * 1.  **Define the flag in `.aconfig` file**:
 * - The flag `name` should be in **snake_case** (e.g., `my_new_feature`).
 * - Example (`scalableui.aconfig`):
 * ```
 * flag {
 * name: "my_new_feature"
 * namespace: "car_sys_exp"
 * description: "Controls my new feature."
 * bug: "123456"
 * }
 * ```
 *
 * 2.  **Add a fallback resource in `flags.xml`**:
 * - The `<bool>` `name` must match the **snake_case** name from the `.aconfig` file.
 * - Example (`res/values/flags.xml`):
 * ```xml
 * <bool name="my_new_feature">true</bool>
 * ```
 *
 * 3.  **Add the entry to this `Flag` enum**:
 * - The enum entry name should be in **PascalCase** (e.g., `MyNewFlag`).
 * - The `fullFlagIdentifier` is a string combining the package name, `.Flags.`, and the
 * **camelCase** version of the flag name (e.g., `"com.android.car.scalableui.Flags.MyNewFlag"`).
 * - The `resourceId` must point to the boolean resource created in step 2 (e.g., `R.bool.my_new_feature`).
 * - Example (`Flag.kt`):
 * ```kotlin
 * MyNewFlag(
 * "com.android.car.scalableui.Flags.MyNewFlag",
 * R.bool.my_new_feature
 * )
 * ```
 */
enum class Flag(
    /**
     * The full string identifier for the modern AOSP flag, taken from FlagManager constants.
     */
    fullFlagIdentifier: String,

    /**
     * The resource ID for the legacy boolean flag, used as a fallback.
     */
    @BoolRes val resourceId: Int
) {

    /**
     * Checks if the Scalable UI feature is enabled.
     */
    ScalableUIEnabled(
        "com.android.systemui.car.Flags.scalableUi",
        R.bool.scalable_ui
    ),

    /**
     * Checks if the Scalable UI design compose feature is enabled.
     */
    ScalableUiDesignCompose(
        "com.android.systemui.car.Flags.scalableUiDesignCompose",
        R.bool.scalable_ui_design_compose
    ),

    /**
     * Checks if the decor feature is enabled.
     */
    EnableDecor("com.android.car.scalableui.Flags.enableDecor", R.bool.enable_decor),

    /**
     * Checks if the display compatibility auto decor safe region feature is enabled.
     */
    DisplayCompatibilityAutoDecorSafeRegion(
        "com.android.systemui.car.Flags.displayCompatibilityAutoDecorSafeRegion",
        R.bool.display_compatibility_auto_decor_safe_region
    ),

    /**
     * Checks if the Scalable UI actions feature is enabled.
     */
    ScalableUiActions(
        "com.android.systemui.car.Flags.scalableUiActions",
        R.bool.scalable_ui_actions
    ),

    /**
     * Checks if the external panel updates feature is enabled.
     */
    EnableExtPanelUpdates(
        "com.android.car.scalableui.Flags.enableExtPanelUpdates",
        R.bool.enable_ext_panel_updates
    ),

    ScalableUiHandleConfigurationChange(
        "com.android.car.scalableui.Flags.scalableUiHandleConfigurationChange",
        R.bool.scalable_ui_handle_configuration_change
    ),

    /**
     * Checks if the DisplayCompatibilityV2 is enabled.
     */
    DisplayCompatibilityV2(
        "com.android.systemui.car.Flags.displayCompatibilityV2",
        R.bool.display_compatibility_v2
    ),

    /**
     * Checks if the DisplayCompatibilityV2 is enabled.
     */
    DisplayCompatV2(
        "com.android.systemui.car.Flags.displayCompatV2",
        R.bool.display_compat_v2
    ),

    /**
     * Checks if the Media Projection indicator feature is enabled.
     */
    ShowMediaProjectionIndicator(
        "com.android.systemui.car.Flags.showMediaProjectionIndicator",
        R.bool.show_media_projection_indicator
    ),

    /**
     * Checks if the SUW as non-home feature is enabled.
     */
    ScalableUiNoSuwHome(
        "com.android.systemui.car.Flags.scalableUiNoSuwHome",
        R.bool.scalable_ui_no_suw_home
    ),

    /**
     * Checks if Minimized Controls is enabled.
     */
    ScalableUiMinimizedControls(
        "com.android.systemui.car.Flags.scalableUiMinimizedControls",
        R.bool.scalable_ui_minimized_controls
    ),

    /**
     * Checks if special handling of promoted notifications is enabled.
     */
    PromotedNotifications(
        "com.android.systemui.car.Flags.promotedNotifications",
        R.bool.promoted_notifications
    );

    // These properties are now calculated from the fullFlagIdentifier.
    val flagClassName: String
    val flagMethodName: String

    init {
        val lastDotIndex = fullFlagIdentifier.lastIndexOf('.')
        // Ensure the dot is present and not the last character.
        if (lastDotIndex > 0 && lastDotIndex < fullFlagIdentifier.length - 1) {
            flagClassName = fullFlagIdentifier.substring(0, lastDotIndex)
            flagMethodName = fullFlagIdentifier.substring(lastDotIndex + 1)
        } else {
            // This will cause a crash on startup if a flag constant has an invalid format,
            // which is good because it surfaces configuration errors immediately.
            throw IllegalArgumentException("Invalid flag identifier format: $fullFlagIdentifier")
        }
    }
}
