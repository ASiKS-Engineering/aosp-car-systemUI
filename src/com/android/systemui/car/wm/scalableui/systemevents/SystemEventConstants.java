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
package com.android.systemui.car.wm.scalableui.systemevents;

public class SystemEventConstants {
    /** Event IDs */
    public static final String EMPTY_EVENT_ID = "empty_event";
    public static final String SYSTEM_HOME_EVENT_ID = "_System_OnHomeEvent";
    public static final String SYSTEM_TASK_OPEN_EVENT_ID = "_System_TaskOpenEvent";
    public static final String SYSTEM_TASK_CLOSE_EVENT_ID = "_System_TaskCloseEvent";
    public static final String SYSTEM_TASK_PANEL_EMPTY_EVENT_ID = "_System_TaskPanelEmptyEvent";
    public static final String SYSTEM_ENTER_SUW_EVENT_ID = "_System_EnterSuwEvent";
    public static final String SYSTEM_EXIT_SUW_EVENT_ID = "_System_ExitSuwEvent";
    public static final String SYSTEM_BEFORE_USER_SWITCH_EVENT_ID = "_System_BeforeUserSwitch";
    public static final String SYSTEM_USER_SWITCH_COMPLETE_EVENT_ID = "_System_UserSwitchComplete";
    public static final String SYSTEM_KEYGUARD_SHOWN_EVENT_ID = "_System_KeyguardShown";
    public static final String SYSTEM_KEYGUARD_HIDDEN_EVENT_ID = "_System_KeyguardHidden";
    /**
     * UserAuthenticated event ID is fired when all of the following are true:
     * - Display is on
     * - User storage is unlocked
     * - Keyguard is not showing
     * The event will include the userSwitch token with "true" or "false" depending on if this
     * user is newly being switched to or not.
     * Note: It's not recommended to consume both the KEYGUARD_HIDDEN_EVENT and this event in
     * the defined panel transitions since they may be executed close together.
     */
    public static final String SYSTEM_USER_AUTHENTICATED_EVENT_ID = "_System_UserAuthenticated";
    public static final String SYSTEM_USER_SWITCH_ON_AUTHENTICATED_TOKEN_ID = "userSwitch";
    public static final String SYSTEM_ON_ANIMATION_END_EVENT_ID = "_System_OnAnimationEndEvent";
    public static final String SYSTEM_ENTER_IMMERSIVE_EVENT_ID = "_System_EnterImmersiveMode";
    public static final String SYSTEM_EXIT_IMMERSIVE_EVENT_ID = "_System_ExitImmersiveMode";
    public static final String SYSTEM_UXR_STATE_CHANGED_EVENT_ID = "_System_UxrStateChanged";
    public static final String SYSTEM_UXR_RESTRICTED_TOKEN_ID = "restricted";
    public static final String SYSTEM_SHOW_PANEL_EVENT_ID = "_System_Show_Panel";
    public static final String SYSTEM_HIDE_PANEL_EVENT_ID = "_System_Hide_Panel";

    /**
     * Represents the variant at the end of the animation.
     */
    public static final String PANEL_DRAG_DIRECTION_ID = "direction";
}
