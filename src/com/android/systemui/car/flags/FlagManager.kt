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

import android.content.Context
import android.content.res.Resources
import android.util.Log
import javax.inject.Inject

/**
 * Manages the state of various feature flags within the application.
 *
 * This class provides a centralized mechanism to check the enablement status of features
 * by combining Trunk Stable flag definitions with resource configurations.
 * A feature is considered enabled only if both its Trunk Stable flag and
 * its corresponding boolean resource in `R.bool` are set to true.
 *
 * @param context The Android [Context] used to access application resources (e.g., `R.bool` values).
 */

/**
 * A singleton manager to abstract away feature flag logic.
 *
 * It provides a single source of truth for checking if a feature is enabled at RUNTIME.
 * Its companion object also provides COMPILE-TIME constants for use in annotations.
 */

class FlagManager @Inject constructor(
    private val context: Context,
) {
    /**
     * Companion object to hold compile-time constants for flag identifiers.
     * These constants are the SINGLE SOURCE OF TRUTH. They are used for both
     * compile-time annotations (`@FlaggedApi`) and to initialize the `Feature` enum.
     */
    private val tag = "FlagManager"
    private val flagCache = mutableMapOf<Flag, Boolean>()

    /**
     * Checks if a given feature is enabled at RUNTIME.
     *
     * @param feature The feature to check, from the `Feature` enum.
     * @param context A Context object, required to access resources for the fallback method.
     * @return `true` if the feature is enabled, `false` otherwise.
     */
    fun isEnabled(feature: Flag): Boolean {
        flagCache[feature]?.let { return it }

        val result = try {
            val flagClass = Class.forName(feature.flagClassName)
            val method = flagClass.getMethod(feature.flagMethodName)
            (method.invoke(null) as? Boolean) ?: false
        } catch (e: Exception) {
            logFlagCheck(
                "Modern flag '${feature.flagClassName}.${feature.flagMethodName}'" +
                        " not found. Falling back to resource."
            )
            try {
                context.resources.getBoolean(feature.resourceId)
            } catch (resNotFound: Resources.NotFoundException) {
                logFlagCheck("Resource ID for '${feature.name}' not found. Defaulting to 'false'.")
                false
            }
        }

        logFlagCheck("Flag '${feature.name}' resolved to: $result")
        flagCache[feature] = result
        return result
    }

    fun clearCache() {
        flagCache.clear()
    }

    private fun logFlagCheck(message: String) {
        Log.d(tag, message)
    }

    override fun toString(): String {
        val formatted = Flag.entries.joinToString(
            separator = " , ",
            prefix = "[ ",
            postfix = " ]"
        ) { "${it.flagMethodName} = ${isEnabled(it)}" }
        return formatted
    }

    fun findFlag(flagName: String): Flag? {
        return Flag.entries.find { it.flagMethodName.endsWith(flagName, ignoreCase = true) }
    }
}
