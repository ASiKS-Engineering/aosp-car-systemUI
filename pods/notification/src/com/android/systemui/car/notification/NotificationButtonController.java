/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.systemui.car.notification;

import android.view.View;

import com.android.systemui.car.flexibleui.CarSystemBarElementController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.systembar.base.ButtonSelectionStateController;
import com.android.systemui.car.systembar.base.CarSystemBarButton;
import com.android.systemui.car.systembar.base.CarSystemBarButtonController;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.settings.UserTracker;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

/**
 * A CarSystemBarElementController for handling notification button interactions.
 */
public class NotificationButtonController extends CarSystemBarButtonController {

    private final NotificationPanelViewController mNotificationPanelViewController;

    @AssistedInject
    public NotificationButtonController(@Assisted CarSystemBarButton notificationsButton,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            NotificationPanelViewController notificationPanelViewController,
            UserTracker userTracker, EventDispatcher eventDispatcher,
            ButtonSelectionStateController buttonSelectionStateController) {
        super(notificationsButton, disableController, stateController, userTracker,
                eventDispatcher, buttonSelectionStateController);

        mNotificationPanelViewController = notificationPanelViewController;
        mNotificationPanelViewController.registerViewStateListener(notificationsButton);
        mNotificationPanelViewController.setOnUnseenCountUpdateListener(unseenNotificationCount -> {
            toggleNotificationUnseenIndicator(unseenNotificationCount > 0);
        });
        notificationsButton.setOnClickListener(this::onNotificationsClick);
    }

    /**
     * Toggles the notification unseen indicator on/off.
     *
     * @param hasUnseen true if the unseen notification count is great than 0.
     */
    private void toggleNotificationUnseenIndicator(boolean hasUnseen) {
        mView.setUnseen(hasUnseen);
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<CarSystemBarButton,
                    NotificationButtonController> {
    }

    private void onNotificationsClick(View v) {
        if (mView.getDisabled()) {
            mView.runOnClickWhileDisabled();
            return;
        }
        mNotificationPanelViewController.toggle();
    }
}
