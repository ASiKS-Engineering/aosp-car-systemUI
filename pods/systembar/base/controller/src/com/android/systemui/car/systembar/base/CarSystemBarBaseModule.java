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

package com.android.systemui.car.systembar.base;

import android.content.Context;
import android.os.Handler;
import android.view.WindowManager;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.CoreStartable;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.dagger.CarSysUIDynamicOverride;
import com.android.systemui.car.flags.FlagManager;
import com.android.systemui.car.flexibleui.CarSystemBarElementController;
import com.android.systemui.car.keyguard.KeyguardSystemBarPresenter;
import com.android.systemui.car.shared.R;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.car.wm.scalableui.panel.TaskPanelInfoRepository;
import com.android.systemui.car.wm.scalableui.systemwindow.SystemUiWindowProvider;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.settings.DisplayTracker;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.phone.AutoHideController;
import com.android.systemui.statusbar.phone.LightBarController;
import com.android.systemui.statusbar.phone.PhoneStatusBarPolicy;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.concurrency.DelayableExecutor;

import dagger.Binds;
import dagger.BindsOptionalOf;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.Multibinds;

import java.util.Map;
import java.util.Optional;

/**
 * Dagger injection module for System Bar Base.
 */
@Module
public abstract class CarSystemBarBaseModule {

    @Binds
    @IntoMap
    @ClassKey(CarSystemBar.class)
    abstract CoreStartable bindCarSystemBarStartable(CarSystemBar systemBarService);

    @Binds
    @IntoSet
    abstract ConfigurationListener provideCarSystemBarConfigListener(
            CarSystemBarController carSystemBarController);

    @BindsOptionalOf
    @CarSysUIDynamicOverride
    abstract ButtonSelectionStateListener optionalButtonSelectionStateListener();

    @SysUISingleton
    @Provides
    static ButtonSelectionStateListener provideButtonSelectionStateListener(@CarSysUIDynamicOverride
            Optional<ButtonSelectionStateListener> overrideButtonSelectionStateListener,
            ButtonSelectionStateController controller) {
        if (overrideButtonSelectionStateListener.isPresent()) {
            return overrideButtonSelectionStateListener.get();
        }
        return new ButtonSelectionStateListener(controller);
    }

    @BindsOptionalOf
    @CarSysUIDynamicOverride
    abstract ButtonSelectionStateController optionalButtonSelectionStateController();

    @SysUISingleton
    @Provides
    static ButtonSelectionStateController provideButtonSelectionStateController(Context context,
            TaskPanelInfoRepository infoRepository,
            @CarSysUIDynamicOverride Optional<ButtonSelectionStateController> controller,
            FlagManager flagManager) {
        if (controller.isPresent()) {
            return controller.get();
        }
        return new ButtonSelectionStateController(context, infoRepository, flagManager);
    }

    @BindsOptionalOf
    @CarSysUIDynamicOverride
    abstract CarSystemBarController optionalCarSystemBarController();

    /**
     * Allows for the replacement of {@link CarSystemBarController} class with a custom subclass.
     * Note that this is not ideal and should be used as a last resort since there are no guarantees
     * that there will not be changes upstream that break the dependencies here (creating additional
     * maintenance burden).
     */
    @SysUISingleton
    @Provides
    static CarSystemBarController provideCarSystemBarController(
            @Main Handler mainHandler,
            @CarSysUIDynamicOverride Optional<CarSystemBarController> carSystemBarController,
            Context context,
            UserTracker userTracker,
            CarSystemBarViewFactory carSystemBarViewFactory,
            SystemBarConfigs systemBarConfigs,
            // TODO(b/156052638): Should not need to inject LightBarController
            LightBarController lightBarController,
            DarkIconDispatcher darkIconDispatcher,
            WindowManager windowManager,
            CarDeviceProvisionedController deviceProvisionedController,
            CommandQueue commandQueue,
            AutoHideController autoHideController,
            ButtonSelectionStateListener buttonSelectionStateListener,
            @Main DelayableExecutor mainExecutor,
            IStatusBarService barService,
            Lazy<KeyguardStateController> keyguardStateControllerLazy,
            Lazy<PhoneStatusBarPolicy> iconPolicyLazy,
            ConfigurationController configurationController,
            CarSystemBarRestartTracker restartTracker,
            DisplayTracker displayTracker,
            SystemUiWindowProvider windowProvider) {

        if (carSystemBarController.isPresent()) {
            return carSystemBarController.get();
        }

        boolean isSecondaryMUMDSystemUI = (CarSystemUIUserUtil.isSecondaryMUMDSystemUI()
                || CarSystemUIUserUtil.isMUPANDSystemUI());
        boolean isSecondaryUserRROsEnabled = context.getResources()
                .getBoolean(R.bool.config_enableSecondaryUserRRO);

        if (isSecondaryMUMDSystemUI && isSecondaryUserRROsEnabled) {
            return new MDSystemBarsControllerImpl(mainHandler, context, userTracker,
                    carSystemBarViewFactory, systemBarConfigs, lightBarController,
                    darkIconDispatcher, windowManager, deviceProvisionedController, commandQueue,
                    autoHideController, buttonSelectionStateListener, mainExecutor, barService,
                    keyguardStateControllerLazy, iconPolicyLazy, configurationController,
                    restartTracker, displayTracker, windowProvider);
        } else {
            return new CarSystemBarControllerImpl(context, userTracker, carSystemBarViewFactory,
                    systemBarConfigs, lightBarController, darkIconDispatcher, windowManager,
                    deviceProvisionedController, commandQueue, autoHideController,
                    buttonSelectionStateListener, mainExecutor, barService,
                    keyguardStateControllerLazy, iconPolicyLazy, configurationController,
                    restartTracker, displayTracker, windowProvider, mainHandler);
        }
    }

    /** Injects KeyguardSystemBarPresenter */
    @SysUISingleton
    @Provides
    static Optional<KeyguardSystemBarPresenter> provideKeyguardSystemBarPresenter(
             CarSystemBarController controller) {
        if (controller instanceof KeyguardSystemBarPresenter) {
            return Optional.of((KeyguardSystemBarPresenter) controller);
        } else {
            return Optional.empty();
        }
    }

    // CarSystemBarElements

    /** Empty set for CarSystemBarElements. */
    @Multibinds
    abstract Map<Class<?>, CarSystemBarElementController.Factory> bindEmptyElementFactoryMap();

    /** Injects CarSystemBarViewFactory */
    @SysUISingleton
    @Binds
    public abstract CarSystemBarViewFactory bindCarSystemBarViewFactory(
            CarSystemBarViewFactoryImpl impl);

    /** Injects CarSystemBarButtonController */
    @Binds
    @IntoMap
    @ClassKey(CarSystemBarButtonController.class)
    public abstract CarSystemBarElementController.Factory bindCarSystemBarButtonControllerFactory(
            CarSystemBarButtonController.Factory factory);

    /** Injects SystemBarConfigs */
    @SysUISingleton
    @Binds
    public abstract SystemBarConfigs bindSystemBarConfigs(SystemBarConfigsImpl impl);
}
