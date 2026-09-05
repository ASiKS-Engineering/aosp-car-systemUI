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
package com.android.systemui.wm

import android.car.settings.CarSettings
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings
import android.util.ArraySet
import android.util.Log
import android.view.WindowInsets
import android.view.WindowInsets.Type.InsetsType
import com.android.systemui.car.Flags.packageLevelSystemBarVisibility
import com.android.systemui.car.shared.R
import com.android.systemui.car.systembar.SystemBarConstants.SYSTEM_BAR_PERSISTENCY_CONFIG_BARPOLICY
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Data class to hold the calculated visibility types for system bars.
 *
 * @property showTypes A bitmask of [InsetsType] that should be shown.
 * @property hideTypes A bitmask of [InsetsType] that should be hidden.
 */
data class BarVisibility(val showTypes: Int, val hideTypes: Int)

/**
 * Manages system bar visibility policies based on global settings.
 *
 * This class parses the `SYSTEM_BAR_VISIBILITY_OVERRIDE` setting to determine which applications
 * can force immersive mode for the status and navigation bars.
 *
 * The setting value is a colon-separated list of name-value pairs. For example:
 * - `immersive.full=*` (force immersive mode for all packages)
 * - `immersive.status=com.package1,-com.package2` (hide status bar for com.package1 but not for
 *   com.package2)
 * - `immersive.navigation=*,+com.package1` (hide nav bar for all, but allow com.package1 to
 *   control its visibility)
 *
 * This class is designed to be instantiated and managed by a dependency injection framework.
 */
class BarControlPolicy() {
    var settingValue: String = EMPTY_POLICY
    var immersiveStatusFilter: Filter? = null
    var immersiveNavigationFilter: Filter? = null

    /**
     * Registers a content observer to listen for updates to the visibility override setting.
     *
     * @param listener A callback to be invoked when the filter policy is updated.
     */
    fun registerSystemBarVisibilityOverrideObserver(
        context: Context,
        handler: Handler,
        listener: () -> Unit
    ) {
        val contentResolver = context.contentResolver
        val uri = Settings.Global.getUriFor(CarSettings.Global.SYSTEM_BAR_VISIBILITY_OVERRIDE)

        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                if (reloadFromSetting(context)) {
                    listener()
                }
            }
        }
        contentResolver.registerContentObserver(uri, false, observer, UserHandle.USER_ALL)
    }

    /**
     * Calculates the intended visibility of system bars for a given package.
     *
     * This method is intended for use when the `packageLevelSystemBarVisibility` flag is disabled.
     *
     * @param packageName The name of the package to check against the policy.
     * @return A [BarVisibility] object detailing which bar types to show and hide.
     */
    fun getBarVisibilities(packageName: String?): BarVisibility {
        check(!packageLevelSystemBarVisibility()) {
            "'package_level_system_bar_visibility' is enabled"
        }

        val showTypesStatus =
            if (!matchesStatusFilter(packageName)) WindowInsets.Type.statusBars() else 0
        val showTypesNav =
            if (!matchesNavigationFilter(packageName)) WindowInsets.Type.navigationBars() else 0
        val showTypes = showTypesStatus or showTypesNav

        val hideTypesStatus =
            if (matchesStatusFilter(packageName)) WindowInsets.Type.statusBars() else 0
        val hideTypesNav =
            if (matchesNavigationFilter(packageName)) WindowInsets.Type.navigationBars() else 0
        val hideTypes = hideTypesStatus or hideTypesNav

        return BarVisibility(showTypes, hideTypes)
    }

    /**
     * Calculates the intended visibility of system bars for a given package, considering the
     * application's requested visibility.
     *
     * This method is intended for use when the `packageLevelSystemBarVisibility` flag is enabled.
     *
     * @param packageName The name of the package to check against the policy.
     * @param requestedVisibleTypes A bitmask of [InsetsType] that the application has requested
     * to be visible.
     * @return A [BarVisibility] object detailing which bar types to show and hide.
     */
    fun getBarVisibilities(
        packageName: String?,
        @InsetsType requestedVisibleTypes: Int
    ): BarVisibility {
        check(packageLevelSystemBarVisibility()) {
            "'package_level_system_bar_visibility' is disabled"
        }

        val (showStatus, hideStatus) = getVisibilityForBar(
            WindowInsets.Type.statusBars(),
            immersiveStatusFilter,
            packageName,
            requestedVisibleTypes
        )

        val (showNav, hideNav) = getVisibilityForBar(
            WindowInsets.Type.navigationBars(),
            immersiveNavigationFilter,
            packageName,
            requestedVisibleTypes
        )

        return BarVisibility(showTypes = showStatus or showNav, hideTypes = hideStatus or hideNav)
    }

    /**
     * Determines the visibility for a single system bar type based on policy and app request.
     *
     * @return A Pair where the first value is the `showType` and the second is the `hideType`
     *         for the given bar.
     */
    private fun getVisibilityForBar(
        @InsetsType barType: Int,
        filter: Filter?,
        packageName: String?,
        @InsetsType requestedVisibleTypes: Int
    ): Pair<Int, Int> {
        val isControlAllowed = filter?.isControlAllowed(packageName) ?: false

        // Determine if the bar should be shown.
        val shouldShow = if (isControlAllowed) {
            // App has control, so respect its visibility request.
            (requestedVisibleTypes and barType) != 0
        } else {
            // App does not have control, so apply the override policy.
            // A match in the filter implies the bar should be hidden.
            !(filter?.matches(packageName) ?: false)
        }

        return if (shouldShow) {
            Pair(barType, 0)
        } else {
            Pair(0, barType)
        }
    }

    private fun matchesStatusFilter(packageName: String?): Boolean =
        immersiveStatusFilter?.matches(packageName) ?: false

    private fun matchesNavigationFilter(packageName: String?): Boolean =
        immersiveNavigationFilter?.matches(packageName) ?: false

    /** Loads values from the setting and updates the filters. Returns true if changed. */
    fun reloadFromSetting(context: Context): Boolean {
        if (DEBUG) Log.d(TAG, "reloadFromSetting()" + context)
        val value: String = try {
            var tempValue = Settings.Global.getStringForUser(
                context.contentResolver,
                CarSettings.Global.SYSTEM_BAR_VISIBILITY_OVERRIDE,
                UserHandle.USER_CURRENT
            )

            if (tempValue.isNullOrBlank()) {
                val useBarPolicy: Boolean =
                    (context.resources.getInteger(R.integer.config_systemBarPersistency)
                            == SYSTEM_BAR_PERSISTENCY_CONFIG_BARPOLICY)

                if (useBarPolicy and packageLevelSystemBarVisibility()) {
                    tempValue = context.getString(R.string.system_bar_visibility_override_backup)
                }
            }
            if (DEBUG) Log.d(TAG, "return value $tempValue")
            tempValue ?: EMPTY_POLICY
        } catch (t: Throwable) {
            Log.w(TAG, "Error loading policy control", t)
            EMPTY_POLICY
        }

        if (DEBUG) Log.d(TAG, "Set policy: $value")

        if (settingValue == value) {
            return false
        } else {
            setFilters(value)
            settingValue = value
            return true
        }
    }

    private fun setFilters(value: String) {
        if (DEBUG) Log.d(TAG, "setFilters: $value")
        immersiveStatusFilter = null
        immersiveNavigationFilter = null

        value.split(":")
            .mapNotNull { nameValuePair ->
                nameValuePair.split('=', limit = 2).takeIf { it.size == 2 }?.let { parts ->
                    // If valid, create a Pair of the name and the parsed Filter
                    parts[0] to Filter.parse(parts[1])
                }
            }.forEach { (name, filter) ->
                when (name) {
                    NAME_IMMERSIVE_FULL -> {
                        immersiveStatusFilter = filter
                        immersiveNavigationFilter = filter
                    }

                    NAME_IMMERSIVE_STATUS -> immersiveStatusFilter = filter
                    NAME_IMMERSIVE_NAVIGATION -> immersiveNavigationFilter = filter
                }
            }
        if (DEBUG) {
            Log.d(TAG, "immersiveStatusFilter: $immersiveStatusFilter")
            Log.d(TAG, "immersiveNavigationFilter: $immersiveNavigationFilter")
        }
    }

    /** Used in testing to reset BarControlPolicy. */
    fun reset() {
        settingValue = EMPTY_POLICY
        immersiveStatusFilter = null
        immersiveNavigationFilter = null
    }

    companion object {
        private const val TAG = "BarControlPolicy"
        private val DEBUG = Build.isDebuggable()

        private const val NAME_IMMERSIVE_FULL = "immersive.full"
        private const val NAME_IMMERSIVE_STATUS = "immersive.status"
        private const val NAME_IMMERSIVE_NAVIGATION = "immersive.navigation"
        private const val EMPTY_POLICY = ""
    }

    /**
     * An inner class that represents the filter policy for a system bar.
     * It parses a comma-separated string to determine which packages are included, excluded,
     * or allowed to control visibility.
     */
    class Filter(
        private val toInclude: Set<String>,
        private val toExclude: Set<String>,
        private val allowControl: Set<String>?
    ) {
        fun matches(packageName: String?): Boolean {
            if (packageName == null) return false
            return !isExcluded(packageName) && isIncluded(packageName)
        }

        fun isControlAllowed(packageName: String?): Boolean {
            if (packageName == null || allowControl == null || !packageLevelSystemBarVisibility()) {
                return false
            }
            return allowControl.contains(ALL) || allowControl.contains(packageName)
        }

        private fun isExcluded(packageName: String): Boolean =
            toExclude.contains(ALL) || toExclude.contains(packageName)

        private fun isIncluded(packageName: String): Boolean =
            toInclude.contains(ALL) || toInclude.contains(packageName)

        override fun toString(): String {
            val writer = PrintWriter(StringWriter())
            writer.print("Filter[")
            dumpSet("toInclude", toInclude, writer)
            writer.print(',')
            dumpSet("toExclude", toExclude, writer)
            if (packageLevelSystemBarVisibility() && allowControl != null) {
                writer.print(',')
                dumpSet("allowControl", allowControl, writer)
            }
            writer.print(']')
            return writer.toString()
        }

        private fun dumpSet(name: String, set: Set<String>, pw: PrintWriter) {
            pw.print("$name=(${set.joinToString(",")})")
        }

        companion object {
            private const val ALL = "*"

            fun parse(value: String?): Filter {
                val toInclude = ArraySet<String>()
                val toExclude = ArraySet<String>()
                val allowControl =
                    if (packageLevelSystemBarVisibility()) ArraySet<String>() else null

                value?.split(",")?.forEach { token ->
                    val trimmed = token.trim()
                    when {
                        trimmed.startsWith("-") && trimmed.length > 1 ->
                            toExclude.add(trimmed.substring(1))

                        allowControl != null && trimmed.startsWith("+") && trimmed.length > 1 ->
                            allowControl.add(trimmed.substring(1))

                        trimmed.isNotEmpty() ->
                            toInclude.add(trimmed)
                    }
                }
                return Filter(toInclude, toExclude, allowControl)
            }
        }
    }
}
