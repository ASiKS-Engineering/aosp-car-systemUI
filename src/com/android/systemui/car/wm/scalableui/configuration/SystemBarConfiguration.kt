/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.car.wm.scalableui.configuration

import android.os.Bundle
import com.android.car.scalableui.loader.xml.parser.SystemBarParser.BAR_Z_ORDER_ATTRIBUTE
import com.android.car.scalableui.loader.xml.parser.SystemBarParser.HIDE_FOR_KEYBOARD_ATTRIBUTE
import com.android.car.scalableui.loader.xml.parser.SystemBarParser.TYPE_ATTRIBUTE
import com.android.car.scalableui.loader.xml.parser.SystemBarParser.TYPE_NAVIGATION
import com.android.systemui.car.systembar.SystemBarConstants.NAVIGATION_BAR
import com.android.systemui.car.systembar.SystemBarConstants.STATUS_BAR
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer
import com.android.systemui.car.wm.scalableui.systemwindow.HUN_Z_ORDER
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.Optional

/**
 * An object that stores configuration values for system bars.
 */
data class SystemBarConfiguration(
    /** a [Bundle] that contains all of the configuration values */
    val configuration: Bundle,
    /** System bar name/id **/
    val name: String,
    /** System bar {@link InsetsFrameProvider}'s index **/
    val index: Int,
    /** WindowInsets.Type.mandatorySystemGestures {@link InsetsFrameProvider}'s index **/
    val mandatorySystemGestureIndexOffset: Int
) {

    @AssistedInject
    constructor(
        consumer: Optional<PanelUpdateConsumer>,
        @Assisted name: String,
        @Assisted("index") index: Int,
        @Assisted("indexOffset") mandatorySystemGestureIndexOffset: Int
    ) : this(getMetadata(consumer, name), name, index, mandatorySystemGestureIndexOffset)

    val type: Int
        /**
         * @return System bar type name
         */
        get() = when (configuration.getString(TYPE_ATTRIBUTE)) {
            TYPE_NAVIGATION -> {
                NAVIGATION_BAR
            }
            else -> {
                STATUS_BAR
            }
        }

    val zOrder: Int
        /**
         * @return the relative Z-order of the SystemBar
         */
        get() = configuration.getInt(BAR_Z_ORDER_ATTRIBUTE)

    val isAboveHun: Boolean
        /**
         * @return `true` if SystemBar should be displayed above HUN
         */
        get() = HUN_Z_ORDER < zOrder

    val isHiddenForKeyboard: Boolean
        /**
         * @return `true` if this system bar should be hidden when keyboard is visible.
         */
        get() = configuration.getBoolean(HIDE_FOR_KEYBOARD_ATTRIBUTE)

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted id: String,
            @Assisted("index") index: Int,
            @Assisted("indexOffset") mandatorySystemGestureIndexOffset: Int
        ): SystemBarConfiguration
    }

    companion object {
        private fun getMetadata(consumer: Optional<PanelUpdateConsumer>, id: String): Bundle {
            if (consumer.isEmpty) {
                throw IllegalStateException("PanelUpdateConsumer must be present")
            }
            val metadata = consumer.get().getPanelControllerMetadata(id)
            checkNotNull(metadata) { "PanelControllerMetadata must be present" }
            return metadata.configurations
        }
    }
}
