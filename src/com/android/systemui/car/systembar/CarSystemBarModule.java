/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.car.systembar;

import com.android.systemui.car.flexibleui.FlexibleUiModule;
import com.android.systemui.car.hvac.HvacSystemBarButtonModule;
import com.android.systemui.car.notification.NotificationSystemBarButtonModule;
import com.android.systemui.car.qc.datasubscription.DataSubscriptionModule;
import com.android.systemui.car.systembar.aaosstudio.AaosStudioButtonModule;
import com.android.systemui.car.systembar.appgrid.AppGridButtonModule;
import com.android.systemui.car.systembar.assistant.AssistantButtonModule;
import com.android.systemui.car.systembar.base.CarSystemBarBaseModule;
import com.android.systemui.car.systembar.controlcenter.ControlCenterButtonModule;
import com.android.systemui.car.systembar.debugpanel.DebugPanelModule;
import com.android.systemui.car.systembar.dock.DockViewModule;
import com.android.systemui.car.systembar.extension.ExtensionSystemBarModule;
import com.android.systemui.car.systembar.home.HomeButtonModule;
import com.android.systemui.car.systembar.notificationchip.PromotedNotificationChipModule;
import com.android.systemui.car.systembar.panel.PanelModule;
import com.android.systemui.car.systembar.passengerhome.PassengerHomeButtonModule;
import com.android.systemui.car.systembar.privacy.camera.PrivacyChipCameraModule;
import com.android.systemui.car.systembar.privacy.cast.PrivacyChipCastModule;
import com.android.systemui.car.systembar.privacy.mic.PrivacyChipMicModule;
import com.android.systemui.car.systembar.privacy.share.PrivacyChipShareModule;
import com.android.systemui.car.systembar.split.SplitSystemBarModule;
import com.android.systemui.car.systembar.standard.StandardSystemBarModule;
import com.android.systemui.car.systembar.usernamepanel.UserNamePanelModule;
import com.android.systemui.car.systembar.volume.VolumeButtonModule;

import dagger.Module;

/**
 * Dagger injection module for {@link CarSystemBar}.
 *
 * This module includes the non-@Inject classes used as part of the {@link CarSystemBar}, allowing
 * extensions of SystemUI to override and provide their own implementations without replacing the
 * default system bar class.
 */
@Module(includes = {
        AaosStudioButtonModule.class,
        AppGridButtonModule.class,
        AssistantButtonModule.class,
        CarSystemBarBaseModule.class,
        ControlCenterButtonModule.class,
        DebugPanelModule.class,
        DataSubscriptionModule.class,
        DockViewModule.class,
        ExtensionSystemBarModule.class,
        FlexibleUiModule.class,
        HomeButtonModule.class,
        HvacSystemBarButtonModule.class,
        NotificationSystemBarButtonModule.class,
        PanelModule.class,
        PassengerHomeButtonModule.class,
        PrivacyChipMicModule.class,
        PrivacyChipCameraModule.class,
        PrivacyChipCastModule.class,
        PrivacyChipShareModule.class,
        PromotedNotificationChipModule.class,
        SplitSystemBarModule.class,
        StandardSystemBarModule.class,
        UserNamePanelModule.class,
        VolumeButtonModule.class})
public abstract class CarSystemBarModule {}
