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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.scalableui.loader.xml.parser.SystemBarParser
import com.android.car.scalableui.model.PanelControllerMetadata
import com.android.systemui.CarSysuiTestCase
import com.android.systemui.car.CarSystemUiTest
import com.android.systemui.car.systembar.SystemBarConstants
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer
import com.android.systemui.car.wm.scalableui.systemwindow.HUN_Z_ORDER
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

@CarSystemUiTest
@RunWith(AndroidJUnit4::class)
@SmallTest
class SystemBarConfigurationTest : CarSysuiTestCase() {

    private val testBundle = Bundle()
    private val mockPanelControllerMetadata = mock<PanelControllerMetadata> {
        on { configurations } doReturn testBundle
    }
    private val mockPanelUpdateConsumer = mock<PanelUpdateConsumer> {
        on { getPanelControllerMetadata(NAME) } doReturn mockPanelControllerMetadata
    }

    @Test
    fun constructor_panelUpdateConsumerNotPresent_throwsException() {
        var exception: IllegalStateException? = null
        try {
            SystemBarConfiguration(Optional.empty(), NAME, INDEX, INDEX_OFFSET)
        } catch (e: IllegalStateException) {
            exception = e
        }
        assertThat(exception).isNotNull()
    }

    @Test
    fun constructor_panelControllerMetadataNotPresent_throwsException() {
        mockPanelUpdateConsumer.stub {
            on { getPanelControllerMetadata(NAME) } doReturn null
        }
        var exception: IllegalStateException? = null
        try {
            SystemBarConfiguration(
                Optional.of(mockPanelUpdateConsumer),
                NAME,
                INDEX,
                INDEX_OFFSET
            )
        } catch (e: IllegalStateException) {
            exception = e
        }
        assertThat(exception).isNotNull()
    }

    @Test
    fun getType_isNavigation() {
        testBundle.putString(
            SystemBarParser.TYPE_ATTRIBUTE,
            SystemBarParser.TYPE_NAVIGATION
        )
        val systemBarConfiguration = SystemBarConfiguration(
            Optional.of(mockPanelUpdateConsumer),
            NAME,
            INDEX,
            INDEX_OFFSET
        )
        assertThat(systemBarConfiguration.type)
            .isEqualTo(SystemBarConstants.NAVIGATION_BAR)
    }

    @Test
    fun getType_isStatus() {
        testBundle.putString(
            SystemBarParser.TYPE_ATTRIBUTE,
            SystemBarParser.TYPE_STATUS
        )
        val systemBarConfiguration = SystemBarConfiguration(
            Optional.of(mockPanelUpdateConsumer),
            NAME,
            INDEX,
            INDEX_OFFSET
        )
        assertThat(systemBarConfiguration.type).isEqualTo(SystemBarConstants.STATUS_BAR)
    }

    @Test
    fun getZOrder() {
        val zOrder = 10
        testBundle.putInt(SystemBarParser.BAR_Z_ORDER_ATTRIBUTE, zOrder)
        val systemBarConfiguration = SystemBarConfiguration(
            Optional.of(mockPanelUpdateConsumer),
            NAME,
            INDEX,
            INDEX_OFFSET
        )
        assertThat(systemBarConfiguration.zOrder).isEqualTo(zOrder)
    }

    @Test
    fun isAboveHun_isTrue() {
        testBundle.putInt(
            SystemBarParser.BAR_Z_ORDER_ATTRIBUTE,
            HUN_Z_ORDER + 1
        )
        val systemBarConfiguration = SystemBarConfiguration(
            Optional.of(mockPanelUpdateConsumer),
            NAME,
            INDEX,
            INDEX_OFFSET
        )
        assertThat(systemBarConfiguration.isAboveHun).isTrue()
    }

    @Test
    fun isAboveHun_isFalse() {
        testBundle.putInt(
            SystemBarParser.BAR_Z_ORDER_ATTRIBUTE,
            HUN_Z_ORDER
        )
        val systemBarConfiguration = SystemBarConfiguration(
            Optional.of(mockPanelUpdateConsumer),
            NAME,
            INDEX,
            INDEX_OFFSET
        )
        assertThat(systemBarConfiguration.isAboveHun).isFalse()
    }

    @Test
    fun isHiddenForKeyboard() {
        testBundle.putBoolean(SystemBarParser.HIDE_FOR_KEYBOARD_ATTRIBUTE, true)
        val systemBarConfiguration = SystemBarConfiguration(
            Optional.of(mockPanelUpdateConsumer),
            NAME,
            INDEX,
            INDEX_OFFSET
        )
        assertThat(systemBarConfiguration.isHiddenForKeyboard).isTrue()
    }

    companion object {
        private const val NAME = "test"
        private const val INDEX = 1
        private const val INDEX_OFFSET = 2
    }
}
