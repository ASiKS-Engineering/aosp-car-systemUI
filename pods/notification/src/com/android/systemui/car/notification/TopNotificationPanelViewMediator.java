/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.systemui.car.notification.NotificationConstants.DRAG_OPEN_NOTIFICATION_BAR_NAMES;
import static com.android.systemui.car.notification.NotificationConstants.DRAG_CLOSE_NOTIFICATION_BAR_NAMES;

import android.content.Context;

import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.systembar.base.CarSystemBarController;
import com.android.systemui.car.window.OverlayPanelViewController;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.ConfigurationController;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Implementation of NotificationPanelViewMediator that sets the notification panel to be opened
 * from the top navigation bar.
 */
@SysUISingleton
public class TopNotificationPanelViewMediator extends NotificationPanelViewMediator {

    @Inject
    public TopNotificationPanelViewMediator(
            Context context,
            CarSystemBarController carSystemBarController,
            NotificationPanelViewController notificationPanelViewController,
            PowerManagerHelper powerManagerHelper,
            BroadcastDispatcher broadcastDispatcher,
            UserTracker userTracker,
            ConfigurationController configurationController,
            @Named(DRAG_OPEN_NOTIFICATION_BAR_NAMES) List<String> dragOpenBarNames,
            @Named(DRAG_CLOSE_NOTIFICATION_BAR_NAMES) List<String> dragCloseBarNames) {
        super(context,
                carSystemBarController,
                notificationPanelViewController,
                powerManagerHelper,
                broadcastDispatcher,
                userTracker,
                configurationController,
                dragOpenBarNames,
                dragCloseBarNames);
        notificationPanelViewController.setOverlayDirection(
                OverlayPanelViewController.OVERLAY_FROM_TOP_BAR);
    }
}
