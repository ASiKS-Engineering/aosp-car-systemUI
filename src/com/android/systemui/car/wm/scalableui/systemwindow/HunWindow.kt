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
package com.android.systemui.car.wm.scalableui.systemwindow

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.view.ViewGroup
import android.view.WindowManager
import com.android.car.scalableui.loader.xml.parser.HunPanelParser
import com.android.systemui.car.wm.scalableui.EventDispatcher
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer
import kotlin.math.min

/**
 * An implementation of [SystemUiWindow] for Heads-Up Notifications (Huns).
 */
class HunWindow(
    context: Context,
    displayManager: DisplayManager,
    consumer: PanelUpdateConsumer,
    eventDispatcher: EventDispatcher,
    displayId: Int
) : SystemUiWindowBase(
    context,
    displayManager,
    consumer,
    eventDispatcher,
    HunPanelParser.HUN_PANEL_ID,
    displayId
) {

    override fun getLayoutParams(): WindowManager.LayoutParams? {
        val bounds = panelUpdateConsumer.getBounds(id) ?: return null
        val metrics = displayMetrics ?: return null

        val lp = WindowManager.LayoutParams(
            bounds.width(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val leftMargin = bounds.left
        val rightMargin = metrics.widthPixels - bounds.right
        val marginHorizontalPx = min(leftMargin, rightMargin)
        lp.horizontalMargin = marginHorizontalPx.toFloat() / metrics.widthPixels
        lp.title = WINDOW_TITLE
        lp.gravity = panelUpdateConsumer.getGravity(id)
        return lp
    }

    /** Returns the gravity for the Hun window. */
    val gravity: Int
        get() = panelUpdateConsumer.getGravity(id)

    companion object {
        const val WINDOW_TITLE = "HeadsUpNotification"
    }
}
