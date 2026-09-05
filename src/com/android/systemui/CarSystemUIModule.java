/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui;

import static com.android.systemui.Dependency.ALLOW_NOTIFICATION_LONG_PRESS_NAME;
import static com.android.systemui.Dependency.LEAK_REPORT_EMAIL_NAME;

import android.content.Context;
import android.hardware.SensorPrivacyManager;
import android.view.InsetsFrameProvider;
import android.window.DisplayAreaOrganizer;

import com.android.car.datasubscription.DataSubscriptionMessageCreator;
import com.android.car.ui.utils.CarUxRestrictionsUtil;
import com.android.keyguard.KeyguardViewController;
import com.android.systemui.accessibility.AccessibilityModule;
import com.android.systemui.accessibility.data.repository.AccessibilityRepositoryModule;
import com.android.systemui.biometrics.dagger.BiometricsModule;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarDeviceProvisionedControllerImpl;
import com.android.systemui.car.decor.CarPolicyModule;
import com.android.systemui.car.decor.CarPrivacyChipBarType;
import com.android.systemui.car.decor.CarPrivacyChipDecorProviderFactory;
import com.android.systemui.car.decor.CarPrivacyChipViewController;
import com.android.systemui.car.displayconfig.ExternalDisplayController;
import com.android.systemui.car.flags.FlagManager;
import com.android.systemui.car.hvac.HvacControllerModule;
import com.android.systemui.car.hvac.HvacUiModule;
import com.android.systemui.car.keyguard.CarKeyguardViewController;
import com.android.systemui.car.notification.NotificationModule;
import com.android.systemui.car.notification.NotificationShadeWindowControllerImpl;
import com.android.systemui.car.shared.R;
import com.android.systemui.car.statusbar.DozeServiceHost;
import com.android.systemui.car.systembar.base.SystemBarConfigs;
import com.android.systemui.car.users.CarMultiUserUtilsModule;
import com.android.systemui.car.volume.CarVolumeModule;
import com.android.systemui.car.wm.activity.window.ActivityWindowModule;
import com.android.systemui.communal.posturing.dagger.NoopPosturingModule;
import com.android.systemui.dagger.GlobalRootComponent;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.decor.PrivacyDotDecorProviderFactory;
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent;
import com.android.systemui.dock.DockManager;
import com.android.systemui.dock.DockManagerImpl;
import com.android.systemui.doze.DozeHost;
import com.android.systemui.dreams.suppression.dagger.NoOpActivityRecognitionModule;
import com.android.systemui.inputmethod.ImeSwitcherMenuModule;
import com.android.systemui.lowlight.dagger.NoopAmbientLightModeMonitorModule;
import com.android.systemui.media.muteawait.MediaMuteAwaitConnectionCli;
import com.android.systemui.media.nearby.NearbyMediaDevicesManager;
import com.android.systemui.minmode.MinModeManager;
import com.android.systemui.minmode.MinModeManagerImpl;
import com.android.systemui.navigationbar.NoopNavigationBarControllerModule;
import com.android.systemui.navigationbar.gestural.GestureModule;
import com.android.systemui.plugins.qs.QSFactory;
import com.android.systemui.power.dagger.PowerModule;
import com.android.systemui.qs.dagger.QSModule;
import com.android.systemui.qs.tileimpl.QSFactoryImpl;
import com.android.systemui.recents.Recents;
import com.android.systemui.recents.RecentsImplementation;
import com.android.systemui.recents.RecentsModule;
import com.android.systemui.screenshot.ReferenceScreenshotModule;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.shade.ShadeEmptyImplModule;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.NotificationLockscreenUserManager;
import com.android.systemui.statusbar.NotificationLockscreenUserManagerImpl;
import com.android.systemui.statusbar.NotificationShadeWindowController;
import com.android.systemui.statusbar.events.PrivacyDotViewController;
import com.android.systemui.statusbar.notification.headsup.HeadsUpEmptyImplModule;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.statusbar.policy.IndividualSensorPrivacyController;
import com.android.systemui.statusbar.policy.IndividualSensorPrivacyControllerImpl;
import com.android.systemui.statusbar.policy.SensorPrivacyController;
import com.android.systemui.statusbar.policy.SensorPrivacyControllerImpl;
import com.android.systemui.statusbar.policy.dagger.SmartRepliesInflationModule;
import com.android.systemui.unfold.SysUIUnfoldStartableModule;
import com.android.systemui.wallpapers.dagger.NoopWallpaperModule;
import com.android.systemui.window.dagger.WindowRootViewBlurNotSupportedModule;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import java.util.Optional;
import java.util.concurrent.Executor;

import javax.inject.Named;
import javax.inject.Provider;

@Module(
        includes = {
                AccessibilityModule.class,
                AccessibilityRepositoryModule.class,
                ActivityWindowModule.class,
                BiometricsModule.class,
                CarMultiUserUtilsModule.class,
                CarPolicyModule.class,
                CarVolumeModule.class,
                ImeSwitcherMenuModule.class,
                ExternalDisplayController.StartableModule.class,
                GestureModule.class,
                HeadsUpEmptyImplModule.class,
                HvacControllerModule.class,
                HvacUiModule.class,
                MediaMuteAwaitConnectionCli.StartableModule.class,
                NearbyMediaDevicesManager.StartableModule.class,
                NoOpActivityRecognitionModule.class,
                NoopNavigationBarControllerModule.class,
                NoopPosturingModule.class,
                NoopAmbientLightModeMonitorModule.class,
                NoopWallpaperModule.class,
                NotificationModule.class,
                PowerModule.class,
                QSModule.class,
                RecentsModule.class,
                ReferenceScreenshotModule.class,
                ScreenDecorationsModule.class,
                ShadeEmptyImplModule.class,
                SmartRepliesInflationModule.class,
                SysUIUnfoldStartableModule.class,
                WindowRootViewBlurNotSupportedModule.class
        },
        subcomponents = {
                CarSysUIDisplaySubcomponent.class
        }
)
abstract class CarSystemUIModule {

    @Binds
    abstract SystemUIDisplaySubcomponent.Factory displaySubComponentFactory(
            CarSysUIDisplaySubcomponent.Factory factory);

    @SysUISingleton
    @Provides
    @Named(ALLOW_NOTIFICATION_LONG_PRESS_NAME)
    static boolean provideAllowNotificationLongPress() {
        return false;
    }

    @SysUISingleton
    @Provides
    @Named(LEAK_REPORT_EMAIL_NAME)
    static String provideLeakReportEmail() {
        return "buganizer-system+181579@google.com";
    }

    @Provides
    @SysUISingleton
    static Recents provideRecents(Context context, RecentsImplementation recentsImplementation,
            CommandQueue commandQueue) {
        return new Recents(context, recentsImplementation, commandQueue);
    }

    @Provides
    @SysUISingleton
    static DisplayAreaOrganizer provideDisplayAreaOrganizer(@Main Executor executor) {
        return new DisplayAreaOrganizer(executor);
    }

    @Binds
    abstract NotificationLockscreenUserManager bindNotificationLockscreenUserManager(
            NotificationLockscreenUserManagerImpl notificationLockscreenUserManager);

    @Provides
    @SysUISingleton
    static SensorPrivacyController provideSensorPrivacyController(
            SensorPrivacyManager sensorPrivacyManager) {
        SensorPrivacyController spC = new SensorPrivacyControllerImpl(sensorPrivacyManager);
        spC.init();
        return spC;
    }

    @Provides
    @SysUISingleton
    static IndividualSensorPrivacyController provideIndividualSensorPrivacyController(
            SensorPrivacyManager sensorPrivacyManager, UserTracker userTracker) {
        IndividualSensorPrivacyController spC = new IndividualSensorPrivacyControllerImpl(
                sensorPrivacyManager, userTracker);
        spC.init();
        return spC;
    }

    @Binds
    @SysUISingleton
    public abstract QSFactory bindQSFactory(QSFactoryImpl qsFactoryImpl);

    @Binds
    abstract DockManager bindDockManager(DockManagerImpl dockManager);

    @Binds
    abstract GlobalRootComponent bindGlobalRootComponent(
            CarGlobalRootComponent globalRootComponent);

    @Binds
    abstract KeyguardViewController bindKeyguardViewController(
            CarKeyguardViewController carKeyguardViewController);

    @Binds
    abstract NotificationShadeWindowController bindNotificationShadeController(
            NotificationShadeWindowControllerImpl notificationPanelViewController);

    @Provides
    static DeviceProvisionedController bindDeviceProvisionedController(
            CarDeviceProvisionedControllerImpl deviceProvisionedController) {
        deviceProvisionedController.init();
        return deviceProvisionedController;
    }

    @Binds
    abstract CarDeviceProvisionedController bindCarDeviceProvisionedController(
            CarDeviceProvisionedControllerImpl deviceProvisionedController);

    @Binds
    abstract DozeHost bindDozeHost(DozeServiceHost dozeServiceHost);

    @Binds
    abstract PrivacyDotViewController providePrivacyDotViewController(
            CarPrivacyChipViewController carPrivacyChipViewController);

    @Binds
    abstract PrivacyDotDecorProviderFactory providePrivacyDotDecorProviderFactory(
            CarPrivacyChipDecorProviderFactory carPrivacyDotDecorProviderFactory);

    @Provides
    static DataSubscriptionMessageCreator bindDataSubscriptionMessageCreator(
            Context context) {
        return new DataSubscriptionMessageCreator(context);
    }

    @Provides
    @SysUISingleton
    static Optional<MinModeManager> provideMinModeManager(
            Provider<MinModeManagerImpl> minModeManagerProvider) {
        if (Flags.enableMinmode()) {
            return Optional.of(minModeManagerProvider.get());
        } else {
            return Optional.empty();
        }
    }

    @Provides
    @SysUISingleton
    static FlagManager provideFlagManager(Context context) {
        return new FlagManager(context);
    }

    @Provides
    @SysUISingleton
    static CarUxRestrictionsUtil provideCarUxRestrictionsUtil(Context context) {
        return CarUxRestrictionsUtil.getInstance(context);
    }

    @Provides
    @CarPrivacyChipBarType
    static int provideCarPrivacyChipBarType(Context context, SystemBarConfigs systemBarConfigs) {
        InsetsFrameProvider provider = systemBarConfigs.getInsetsFrameProviderByName(
                context.getResources().getString(R.string.config_privacyIndicatorLocation));
        return provider != null ? provider.getType() : -1;
    }
}
