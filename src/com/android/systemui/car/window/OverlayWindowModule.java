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

package com.android.systemui.car.window;

import static com.android.systemui.car.keyguard.KeyguardConstants.OVERLAY_TYPE_KEYGUARD;
import static com.android.systemui.car.keyguard.KeyguardConstants.OVERLAY_TYPE_PASSENGER_KEYGUARD;
import static com.android.systemui.car.userswitcher.UserSwitcherConstants.OVERLAY_TYPE_FULLSCREEN_USER_SWITCHER;
import static com.android.systemui.car.userswitcher.UserSwitcherConstants.OVERLAY_TYPE_USER_SWITCHING_DIALOG;

import com.android.systemui.car.hvac.HvacPanelOverlayViewMediator;
import com.android.systemui.car.keyguard.CarKeyguardOverlayViewMediator;
import com.android.systemui.car.keyguard.CarKeyguardViewController;
import com.android.systemui.car.keyguard.passenger.PassengerKeyguardOverlayViewController;
import com.android.systemui.car.keyguard.passenger.PassengerKeyguardOverlayViewMediator;
import com.android.systemui.car.notification.BottomNotificationPanelViewMediator;
import com.android.systemui.car.notification.NotificationPanelViewMediator;
import com.android.systemui.car.notification.TopNotificationPanelViewMediator;
import com.android.systemui.car.systemdialogs.SystemDialogsViewMediator;
import com.android.systemui.car.userswitcher.FullScreenUserSwitcherViewController;
import com.android.systemui.car.userswitcher.FullscreenUserSwitcherViewMediator;
import com.android.systemui.car.userswitcher.UserSwitchTransitionViewController;
import com.android.systemui.car.userswitcher.UserSwitchTransitionViewMediator;
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.StringKey;

/**
 * Dagger injection module for {@link SystemUIOverlayWindowManager}
 */
@Module
public abstract class OverlayWindowModule {

    /** Injects NotificationPanelViewMediator. */
    @Binds
    @IntoMap
    @ClassKey(NotificationPanelViewMediator.class)
    public abstract OverlayViewMediator bindNotificationPanelViewMediator(
            NotificationPanelViewMediator notificationPanelViewMediator);

    /** Injects TopNotificationPanelViewMediator. */
    @Binds
    @IntoMap
    @ClassKey(TopNotificationPanelViewMediator.class)
    public abstract OverlayViewMediator bindTopNotificationPanelViewMediator(
            TopNotificationPanelViewMediator topNotificationPanelViewMediator);

    /** Injects BottomNotificationPanelViewMediator. */
    @Binds
    @IntoMap
    @ClassKey(BottomNotificationPanelViewMediator.class)
    public abstract OverlayViewMediator bindBottomNotificationPanelViewMediator(
            BottomNotificationPanelViewMediator bottomNotificationPanelViewMediator);

    /** Inject into CarKeyguardOverlayViewMediator. */
    @Binds
    @IntoMap
    @ClassKey(CarKeyguardOverlayViewMediator.class)
    public abstract OverlayViewMediator bindCarKeyguardOverlayViewMediator(
            CarKeyguardOverlayViewMediator carKeyguardOverlayViewMediator);

    /** Injects PassengerKeyguardOverlayViewMediator. */
    @Binds
    @IntoMap
    @ClassKey(PassengerKeyguardOverlayViewMediator.class)
    public abstract OverlayViewMediator bindPassengerKeyguardViewMediator(
            PassengerKeyguardOverlayViewMediator overlayViewsMediator);

    /** Injects FullscreenUserSwitcherViewsMediator. */
    @Binds
    @IntoMap
    @ClassKey(FullscreenUserSwitcherViewMediator.class)
    public abstract OverlayViewMediator bindFullscreenUserSwitcherViewsMediator(
            FullscreenUserSwitcherViewMediator overlayViewsMediator);

    /** Injects CarUserSwitchingDialogMediator. */
    @Binds
    @IntoMap
    @ClassKey(UserSwitchTransitionViewMediator.class)
    public abstract OverlayViewMediator bindUserSwitchTransitionViewMediator(
            UserSwitchTransitionViewMediator userSwitchTransitionViewMediator);

    /** Injects HvacPanelOverlayViewMediator. */
    @Binds
    @IntoMap
    @ClassKey(HvacPanelOverlayViewMediator.class)
    public abstract OverlayViewMediator bindHvacPanelOverlayViewMediator(
            HvacPanelOverlayViewMediator overlayViewMediator);

    /** Inject SystemDialogsViewMediator. */
    @Binds
    @IntoMap
    @ClassKey(SystemDialogsViewMediator.class)
    public abstract OverlayViewMediator bindSystemDialogsViewMediator(
            SystemDialogsViewMediator sysui);

    /** Listen to config changes for SystemUIOverlayWindowManager. */
    @Binds
    @IntoSet
    public abstract ConfigurationListener bindSystemUIOverlayWindowManagerConfigChanges(
            SystemUIOverlayWindowManager systemUIOverlayWindowManager);

    /** Injects OverlayVisibilityMediator. */
    @Binds
    public abstract OverlayVisibilityMediator bindOverlayVisibilityMediator(
            OverlayVisibilityMediatorImpl overlayVisibilityMediatorImpl);

    @Binds
    @IntoMap
    @StringKey(OVERLAY_TYPE_FULLSCREEN_USER_SWITCHER)
    abstract OverlayViewController bindFullScreenUserSwitcherViewController(
            FullScreenUserSwitcherViewController controller);

    @Binds
    @IntoMap
    @StringKey(OVERLAY_TYPE_USER_SWITCHING_DIALOG)
    abstract OverlayViewController bindUserSwitchTransitionViewController(
            UserSwitchTransitionViewController controller);

    @Binds
    @IntoMap
    @StringKey(OVERLAY_TYPE_KEYGUARD)
    abstract OverlayViewController bindCarKeyguardViewController(
            CarKeyguardViewController controller);

    @Binds
    @IntoMap
    @StringKey(OVERLAY_TYPE_PASSENGER_KEYGUARD)
    abstract OverlayViewController bindPassengerKeyguardOverlayViewController(
            PassengerKeyguardOverlayViewController controller);
}
