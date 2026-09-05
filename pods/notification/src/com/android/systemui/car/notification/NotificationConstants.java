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

package com.android.systemui.car.notification;

/** Constants related to Notifications. */
public class NotificationConstants {
    /**
     * Qualifier name for the list of system bar names that can drag open notifications.
     */
    public static final String DRAG_OPEN_NOTIFICATION_BAR_NAMES = "DragOpenNotificationBarNames";

    /**
     * Dagger {@link Named} for a list of strings that listen to notification drag close listener.
     */
    public static final String DRAG_CLOSE_NOTIFICATION_BAR_NAMES = "DragCloseNotificationBarNames";

    /** Overlay type key for the Notification panel. */
    public static final String OVERLAY_TYPE_NOTIFICATION_PANEL = "notification_panel";
}
