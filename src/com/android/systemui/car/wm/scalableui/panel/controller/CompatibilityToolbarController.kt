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
package com.android.systemui.car.wm.scalableui.panel.controller

import android.annotation.SuppressLint
import android.app.ActivityManager.RunningTaskInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo.RESIZE_MODE_RESIZEABLE
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.input.InputManager
import android.os.Build
import android.os.SystemClock
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.window.WindowContainerTransaction
import com.android.car.scalableui.model.Event
import com.android.car.scalableui.panel.PanelPool
import com.android.systemui.car.shared.R
import com.android.systemui.car.wm.scalableui.EventDispatcher
import com.android.systemui.car.wm.scalableui.getDisplayBounds
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer
import com.android.systemui.car.wm.scalableui.panel.ui.CompatibilityToolbar
import com.android.systemui.car.wm.scalableui.panel.ui.CompatibilityToolbarState
import com.android.systemui.car.wm.scalableui.panel.ui.CompatibilityToolbarUiState
import com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_ENTER_IMMERSIVE_EVENT_ID
import com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_EXIT_IMMERSIVE_EVENT_ID
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.automotive.AutoCaptionBarViewController
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * A controller that creates and manages a [CompatibilityToolbar] for a task window.
 *
 * This class acts as a factory for the task's caption bar (toolbar) and wires up its UI controls
 * to system-level actions. It handles:
 * - **Back Navigation**: Injects a system back key event.
 * - **Closing Task**: Uses [ShellTaskOrganizer] to remove the task.
 * - **Aspect Ratio**: Launches the per-app aspect ratio settings screen.
 * - **Fullscreen Toggle**: Dispatches an event to enter/exit immersive mode.
 *
 * It is instantiated via an [AssistedFactory] for a specific `panelId` and registers itself
 * with a [PanelUpdateConsumer] to receive updates about the panel's state.
 *
 * TODO(b/440110664): polish UI and logic
 *
 * @property context The application context.
 * @property shellTaskOrganizer The shell component for managing task windows.
 * @property eventDispatcher A dispatcher for sending system-wide events.
 * @property panelId The unique identifier of the panel this toolbar is associated with.
 * @see AutoCaptionBarViewController
 * @see CompatibilityToolbar
 * @see PanelUpdateConsumer
 */
@SuppressLint("MissingPermission")
class CompatibilityToolbarController
@AssistedInject
constructor(
    private val context: Context,
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val eventDispatcher: EventDispatcher,
    private val uiState: CompatibilityToolbarUiState,
    @Assisted private val panelId: String,
) : TaskToolbarController, PanelUpdateConsumer.PanelUpdateCallback {

    val displayId: Int =
        PanelPool.getInstance().getPanel(panelId)?.displayId ?: Display.INVALID_DISPLAY

    override fun createView(taskInfo: RunningTaskInfo): View {
        return CompatibilityToolbar(context).apply {
            // Configure aspect ratio button based on task info availability.
            updateButtonVisibility(this, taskInfo)
            setupButtonClickListener(this, taskInfo)
        }
    }

    override fun updateView(captionView: View, taskInfo: RunningTaskInfo) {
        (captionView as? CompatibilityToolbar)?.let { toolbar ->
            updateButtonVisibility(toolbar, taskInfo)
            setupButtonClickListener(toolbar, taskInfo)
        }.run {
            logIfDebuggable("captionView could not be casted to CompatibilityToolbar")
        }
    }

    private fun updateButtonVisibility(
        compatibilityToolbar: CompatibilityToolbar,
        taskInfo: RunningTaskInfo
    ) {
        val taskIsValid = taskInfo.topActivity != null
        val isActivityResizable = taskInfo.topActivityInfo?.resizeMode == RESIZE_MODE_RESIZEABLE
        val aspectRatioVisible = taskIsValid && !isActivityResizable
        compatibilityToolbar.apply {
            aspectRatioButton?.visibility = if (aspectRatioVisible) View.VISIBLE else View.GONE
            displayDensityButton?.visibility =
                if (taskIsValid) View.VISIBLE else View.GONE
        }
    }

    private fun setupButtonClickListener(
        compatibilityToolbar: CompatibilityToolbar,
        taskInfo: RunningTaskInfo
    ) {
        taskInfo.topActivity?.let { topActivityComponent ->
            compatibilityToolbar.aspectRatioButton?.setOnClickListener {
                sendAspectRatioIntent(topActivityComponent, taskInfo.userId)
            }

            compatibilityToolbar.displayDensityButton?.setOnClickListener {
                sendDisplayDensityIntent(
                    topActivityComponent,
                    taskInfo.userId,
                    taskInfo.displayId
                )
            }
        }

        // Configure other buttons using modern lambda syntax.
        compatibilityToolbar.backButton?.setOnClickListener { sendBackEvent(taskInfo.displayId) }
        compatibilityToolbar.closeButton?.setOnClickListener {
            taskInfo.let { info ->
                logIfDebuggable("Close button clicked: $info")
                val wct = WindowContainerTransaction().apply { removeTask(info.token) }
                shellTaskOrganizer.applyTransaction(wct)
            }
        }
        compatibilityToolbar.fullscreenButton?.setOnClickListener {
            getImmersiveEvent()?.let {
                eventDispatcher.executeEvent(
                    it
                )
            }
            shellTaskOrganizer.restartTaskProcessIfVisible(taskInfo.token)
        }
    }

    private fun getImmersiveEvent(): Event? {
        val panelBounds = PanelPool.getInstance().getPanel(panelId)?.bounds ?: Rect()
        uiState.updateToolbarState(panelBounds, context.getDisplayBounds(displayId))
        val eventId = when (uiState.uiState.value) {
            is CompatibilityToolbarState.Immersive -> {
                // If the toolbar is already immersive, the button should disable it (toggle off).
                SYSTEM_EXIT_IMMERSIVE_EVENT_ID
            }

            is CompatibilityToolbarState.Open -> {
                // If the toolbar is open, the button should enable it (toggle on).
                SYSTEM_ENTER_IMMERSIVE_EVENT_ID
            }

            else -> {
                // If the panel is closed, do nothing
                return null
            }
        }
        return Event.Builder(eventId).setPanelId(panelId).build()
    }

    private fun sendAspectRatioIntent(topActivity: ComponentName, userId: Int) {
        val intent =
            Intent(ASPECT_RATIO_SHOW_DIALOG_ACTION).apply {
                `package` = getSettingsPackageName(userId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(ASPECT_RATIO_SHOW_DIALOG_EXTRA_KEY_CMP_NAME, topActivity)
                putExtra(ASPECT_RATIO_SHOW_DIALOG_EXTRA_KEY_UID, userId)
            }
        context.startActivityAsUser(intent, UserHandle.of(userId))
        logIfDebuggable("sendAspectRatioIntent: $intent")
    }

    private fun sendDisplayDensityIntent(topActivity: ComponentName, userId: Int, displayId: Int) {
        val intent =
            Intent(DISPLAY_DENSITY_SHOW_DIALOG_ACTION).apply {
                `package` = getSettingsPackageName(userId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(DISPLAY_DENSITY_SHOW_DIALOG_EXTRA_KEY_CMP_NAME, topActivity)
                putExtra(DISPLAY_DENSITY_SHOW_DIALOG_EXTRA_KEY_UID, userId)
                putExtra(DISPLAY_DENSITY_SHOW_DIALOG_EXTRA_KEY_DISPLAY_ID, displayId)
            }
        context.startActivityAsUser(intent, UserHandle.of(userId))
        logIfDebuggable("sendDisplayDensityIntent: $intent")
    }

    private fun sendBackEvent(displayId: Int) {
        logIfDebuggable("sendBackEvent: $displayId")

        val eventTime = SystemClock.uptimeMillis()
        sendKeyEvent(KeyEvent.ACTION_DOWN, displayId, eventTime - 1)
        sendKeyEvent(KeyEvent.ACTION_UP, displayId, eventTime)
    }

    private fun sendKeyEvent(action: Int, displayId: Int, eventTime: Long) {
        val keyEvent = KeyEvent(
            eventTime,
            eventTime,
            action,
            KeyEvent.KEYCODE_BACK,
            0,
            0,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            0,
            KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_VIRTUAL_HARD_KEY,
            InputDevice.SOURCE_KEYBOARD
        ).apply { this.displayId = displayId }

        context
            .getSystemService(InputManager::class.java)
            ?.injectInputEvent(keyEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)
    }

    private fun getSettingsPackageName(userId: Int): String {
        val settingsIntent = Intent(Settings.ACTION_SETTINGS)
        val flags = PackageManager.MATCH_DIRECT_BOOT_AWARE or
                PackageManager.MATCH_DIRECT_BOOT_UNAWARE or
                PackageManager.MATCH_DEFAULT_ONLY

        val resolveInfo = context.packageManager
            .resolveActivityAsUser(settingsIntent, flags, userId)

        return resolveInfo?.activityInfo?.packageName
            ?: context.resources.getString(R.string.config_defaultSettingsPackage)
    }

    @AssistedFactory
    interface Factory : TaskToolbarController.Factory<CompatibilityToolbarController> {
        override fun create(panelId: String): CompatibilityToolbarController
    }

    companion object {
        private val DEBUG = Build.isDebuggable()
        const val TAG: String = "CompatibilityToolbarCtr"
        const val ASPECT_RATIO_SHOW_DIALOG_ACTION =
            "com.android.car.settings.aspectRatio.action.SHOW_DIALOG"
        const val ASPECT_RATIO_SHOW_DIALOG_EXTRA_KEY_CMP_NAME =
            "com.android.car.settings.aspectRatio.extra.COMPONENT_NAME"
        const val ASPECT_RATIO_SHOW_DIALOG_EXTRA_KEY_UID =
            "com.android.car.settings.aspectRatio.extra.USER_ID"
        const val DISPLAY_DENSITY_SHOW_DIALOG_ACTION =
            "com.android.car.settings.displayDensity.action.SHOW_DIALOG"
        const val DISPLAY_DENSITY_SHOW_DIALOG_EXTRA_KEY_CMP_NAME =
            "com.android.car.settings.displayDensity.extra.COMPONENT_NAME"
        const val DISPLAY_DENSITY_SHOW_DIALOG_EXTRA_KEY_UID =
            "com.android.car.settings.displayDensity.extra.USER_ID"
        const val DISPLAY_DENSITY_SHOW_DIALOG_EXTRA_KEY_DISPLAY_ID =
            "com.android.car.settings.displayDensity.extra.DISPLAY_ID"

        fun logIfDebuggable(msg: String) {
            if (DEBUG) {
                Log.d(TAG, msg)
            }
        }
    }
}
