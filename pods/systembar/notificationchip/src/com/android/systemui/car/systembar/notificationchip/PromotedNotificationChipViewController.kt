/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.car.systembar.notificationchip

import android.content.Context
import android.os.Build
import android.util.Log
import com.android.car.notification.CarNotificationListener
import com.android.car.notification.PromotedNotificationModel
import com.android.car.notification.PromotedNotificationsRepository
import com.android.systemui.car.flags.Flag
import com.android.systemui.car.flags.FlagManager
import com.android.systemui.car.flexibleui.CarSystemBarElementController
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController
import com.android.systemui.car.notification.NotificationPanelViewController
import com.android.systemui.car.window.OverlayViewController
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.graphics.ImageLoader
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * View controller for PromotedNotificationChipView. This controller is responsible for observing
 * changes in promoted notifications and updating the chip view accordingly.
 */
class PromotedNotificationChipViewController
@AssistedInject
constructor(
    @Assisted view: PromotedNotificationChipView,
    disableController: CarSystemBarElementStatusBarDisableController,
    stateController: CarSystemBarElementStateController,
    private val context: Context,
    @Application private val scope: CoroutineScope,
    private val imageLoader: ImageLoader,
    private val carNotificationListener: CarNotificationListener,
    private val flagManager: FlagManager,
    private val notificationPanelViewController: NotificationPanelViewController
) : CarSystemBarElementController<PromotedNotificationChipView>(
    view,
    disableController,
    stateController
) {

    private val mutex = Mutex()
    private var job: Job? = null
    private var currentPromotedNotification: PromotedNotificationModel? = null
    private val isPanelExpanded = MutableStateFlow(false)

    private val overlayViewStateListener = object : OverlayViewController.OverlayViewStateListener {
        override fun onVisibilityChanged(isVisible: Boolean) {
            isPanelExpanded.value = isVisible
        }
    }

    @AssistedFactory
    interface Factory :
        CarSystemBarElementController.Factory<PromotedNotificationChipView,
                PromotedNotificationChipViewController>

    public override fun onViewAttached() {
        super.onViewAttached()
        if (!flagManager.isEnabled(Flag.PromotedNotifications)) {
            return
        }

        mView.setOnClickListener {
            // TODO(b/481110013): re-show the HUN on click instead of showing the entire panel
            currentPromotedNotification?.let {
                if (it.isHeadsUp) {
                    return@setOnClickListener
                }

                carNotificationListener.showHunImmediately(it.key)
            }
        }

        notificationPanelViewController.registerViewStateListener(overlayViewStateListener)
        isPanelExpanded.value = notificationPanelViewController.isPanelExpanded

        job?.cancel()
        job = scope.launch {
            combine(
                PromotedNotificationsRepository.getInstance().promotedNotifications,
                isPanelExpanded,
                ::Pair
            ).collect { (promotedNotifications, isPanelExpanded) ->
                maybeShowPromotedChip(promotedNotifications, isPanelExpanded)
            }
        }
    }

    public override fun onViewDetached() {
        super.onViewDetached()

        notificationPanelViewController.removePanelViewStateListener(overlayViewStateListener)

        job?.cancel()
        job = null
    }

    // TODO (b/485656033): Hide the promoted chip when the NC shade is open and the
    //  promoted notification exists inside
    // TODO (b/486207496): Hide the promoted chip when the HUN is triggered
    // TODO (b/486231805): Hide the promoted chip with the app under automation is opened
    private suspend fun maybeShowPromotedChip(
        promotedNotifications: List<PromotedNotificationModel>,
        isPanelExpanded: Boolean
    ) {
        val entry = getMostRecentPromotedNotification(promotedNotifications)
        mutex.withLock {
            entry ?: run {
                if (DEBUG) {
                    Log.d(TAG, "No promoted notifications")
                }
                currentPromotedNotification = null
                mView.animateOut()
                return
            }

            if (isPanelExpanded) {
                if (DEBUG) {
                    Log.d(TAG, "Notification shade open, hiding chip")
                }
                currentPromotedNotification = null
                mView.animateOut()
                return
            }

            currentPromotedNotification?.let {
                if (it == entry) {
                    return
                }
            }
            val isIconSame = currentPromotedNotification?.areIconsEqual(entry) ?: false
            currentPromotedNotification = entry
            if (DEBUG) {
                Log.d(TAG, "Changed promoted notification $currentPromotedNotification")
            }

            if (!isIconSame) {
                val icon = entry.icon
                icon?.also {
                    val drawable = imageLoader.loadDrawable(icon, context)
                    mView.updateNotificationIcon(drawable)
                } ?: run {
                    mView.updateNotificationIcon(null)
                }
            }

            mView.setPromotedInfo(entry.shortCriticalText, entry.appName)
            mView.animateIn()
        }
    }

    private fun getMostRecentPromotedNotification(
        promotedNotifications: List<PromotedNotificationModel>
    ): PromotedNotificationModel? {
        return promotedNotifications
            .filter { !it.isHeadsUp }
            .maxByOrNull { it.postTime }
    }

    private companion object {
        val DEBUG = Build.IS_ENG || Build.IS_USERDEBUG
        val TAG = PromotedNotificationChipViewController::class.java.simpleName
    }
}
