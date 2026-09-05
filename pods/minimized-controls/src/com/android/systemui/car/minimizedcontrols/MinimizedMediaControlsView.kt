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

package com.android.systemui.car.minimizedcontrols

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout

/**
 * A custom view to display minimized media controls.
 */
class MinimizedMediaControlsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        LayoutInflater.from(context).inflate(R.layout.minimized_media_controls_view, this, true)

        findViewById<android.widget.ImageView>(R.id.media_widget_app_icon)?.let { icon ->
            icon.outlineProvider = OvalOutlineProvider
            icon.clipToOutline = true
        }
    }

    private object OvalOutlineProvider : android.view.ViewOutlineProvider() {
        override fun getOutline(
            view: android.view.View,
            outline: android.graphics.Outline
        ) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }
}
