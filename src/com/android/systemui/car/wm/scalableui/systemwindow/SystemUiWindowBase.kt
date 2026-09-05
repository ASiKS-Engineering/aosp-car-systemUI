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
import android.graphics.Insets
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowManager
import com.android.car.scalableui.model.Corner
import com.android.car.scalableui.model.Event
import com.android.systemui.car.wm.scalableui.EventDispatcher
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer
import com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_HIDE_PANEL_EVENT_ID
import com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_SHOW_PANEL_EVENT_ID

/**
 * A base class for [SystemUiWindow] implementations, providing common functionality.
 */
abstract class SystemUiWindowBase(
    private val context: Context,
    private val displayManager: DisplayManager,
    protected val panelUpdateConsumer: PanelUpdateConsumer,
    protected val eventDispatcher: EventDispatcher,
    protected var id: String,
    private val displayId: Int
) : SystemUiWindow {
    protected var display: Display? = null
    protected var displayContext: Context? = null
    protected var windowManager: WindowManager? = null
    protected var displayMetrics: DisplayMetrics? = null
    protected var _rootView: View? = null
    private val panelUpdateCallback: PanelUpdateConsumer.PanelUpdateCallback

    init {
        initializeDisplay()
        panelUpdateCallback = object : PanelUpdateConsumer.PanelUpdateCallback {
            override fun onBoundsChange(panelId: String, bounds: Rect) {
                _rootView?.let {
                    windowManager?.updateViewLayout(it, getLayoutParams())
                }
            }

            override fun onInsetsChange(panelId: String, insets: Insets) {
                _rootView?.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            }

            override fun onAlphaChange(panelId: String, alpha: Float) {
                _rootView?.alpha = alpha
            }

            override fun onVisibilityChange(panelId: String, visible: Boolean) {
                _rootView?.visibility = if (visible) View.VISIBLE else View.GONE
            }

            override fun onGravityChange(panelId: String, gravity: Int) {
                _rootView?.let {
                    windowManager?.updateViewLayout(it, getLayoutParams())
                }
            }

            override fun onCornerRadiusChange(panelId: String, radius: Corner?) {
                _rootView?.let {
                    radius?.let { cornerRadius ->
                        it.background?.let { background ->
                            if (background is GradientDrawable) {
                                background.cornerRadii = floatArrayOf(
                                    cornerRadius.topLeftRadius.toFloat(),
                                    cornerRadius.topLeftRadius.toFloat(),
                                    cornerRadius.topRightRadius.toFloat(),
                                    cornerRadius.topRightRadius.toFloat(),
                                    cornerRadius.bottomRightRadius.toFloat(),
                                    cornerRadius.bottomRightRadius.toFloat(),
                                    cornerRadius.bottomLeftRadius.toFloat(),
                                    cornerRadius.bottomLeftRadius.toFloat(),
                                )
                                it.background = background
                            }
                        }
                    }
                }
            }
        }
    }

    private fun initializeDisplay() {
        if (displayId == Display.INVALID_DISPLAY) {
            Log.w(TAG, "Invalid display ID (-1) for $id")
            return
        }

        val localDisplay = displayManager.getDisplay(displayId) ?: run {
            Log.e(TAG, "Cannot find display for id: $displayId for $id")
            return
        }

        display = localDisplay
        val localDisplayContext = context.createDisplayContext(localDisplay)
        displayContext = localDisplayContext
        windowManager = displayContext?.getSystemService(WindowManager::class.java) ?: run {
            Log.e(TAG, "Cannot obtain WindowManager for $id on display $displayId")
            return
        }
        displayMetrics = localDisplayContext.resources.displayMetrics ?: run {
            Log.w(TAG, "Cannot find displayMetrics for id: $displayId for $id")
            return
        }
    }

    override fun getName(): String = id

    override fun getBounds(): Rect? {
        return panelUpdateConsumer.getBounds(id)
    }

    override fun setRootView(view: View, layoutParams: WindowManager.LayoutParams?) {
        if (_rootView != null) {
            removeRootView()
        }
        this._rootView = view
        windowManager?.addView(this._rootView, layoutParams)
        panelUpdateConsumer.registerCallback(id, panelUpdateCallback)
    }

    override fun removeRootView() {
        _rootView?.let {
            windowManager?.removeView(it)
            _rootView = null
        }
        panelUpdateConsumer.unregisterCallback(id, panelUpdateCallback)
    }

    override fun removeRootViewImmediate() {
        _rootView?.let {
            windowManager?.removeViewImmediate(it)
            _rootView = null
        }
        panelUpdateConsumer.unregisterCallback(id, panelUpdateCallback)
    }

    override fun isVisible(): Boolean {
        return panelUpdateConsumer.isVisible(id) ?: false
    }

    override fun hide() {
        val event = Event.Builder(SYSTEM_HIDE_PANEL_EVENT_ID).setPanelId(id).build()
        eventDispatcher.executeEvent(event)
    }

    override fun show() {
        val event = Event.Builder(SYSTEM_SHOW_PANEL_EVENT_ID).setPanelId(id).build()
        eventDispatcher.executeEvent(event)
    }

    override fun getHeight(): Int {
        return panelUpdateConsumer.getBounds(id)?.height() ?: 0
    }

    override fun getWidth(): Int {
        return panelUpdateConsumer.getBounds(id)?.width() ?: 0
    }

    override fun getAlpha(): Float {
        return panelUpdateConsumer.getAlpha(id) ?: 1f
    }

    override fun getInsets(): Insets? {
        return panelUpdateConsumer.getInsets(id)
    }

    override fun getCornerRadius(): Corner {
        return panelUpdateConsumer.getCornerRadius(id)
    }

    override fun getDisplayId() = displayId

    override fun addCallback(callback: SystemUiWindow.WindowUpdateCallback) {
        panelUpdateConsumer.registerCallback(id, callback)
    }

    override fun removeCallback(callback: SystemUiWindow.WindowUpdateCallback) {
        panelUpdateConsumer.unregisterCallback(id, callback)
    }

    companion object {
        private val TAG = SystemUiWindowBase::class.simpleName
    }
}
