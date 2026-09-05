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

package com.android.systemui.car.keyguard;

import android.app.IActivityTaskManager;
import android.app.trust.TrustManager;
import android.content.Context;
import android.os.PowerManager;

import com.android.internal.jank.InteractionJankMonitor;
import com.android.internal.logging.UiEventLogger;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.ConnectedDisplayKeyguardPresentationFactory;
import com.android.keyguard.KeyguardDisplayManager;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardViewController;
import com.android.keyguard.ViewMediatorCallback;
import com.android.keyguard.dagger.KeyguardStatusBarViewComponent;
import com.android.keyguard.mediator.ScreenOnCoordinator;
import com.android.systemui.CoreStartable;
import com.android.systemui.animation.ActivityTransitionAnimator;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.keyguard.passenger.PassengerKeyguardLoadingDialog;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.classifier.FalsingCollector;
import com.android.systemui.classifier.FalsingModule;
import com.android.systemui.communal.domain.interactor.CommunalSceneInteractor;
import com.android.systemui.communal.domain.interactor.CommunalSettingsInteractor;
import com.android.systemui.communal.ui.viewmodel.CommunalTransitionViewModel;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Application;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dagger.qualifiers.UiBackground;
import com.android.systemui.display.data.repository.DisplayRepository;
import com.android.systemui.dreams.DreamOverlayStateController;
import com.android.systemui.dreams.ui.viewmodel.DreamViewModel;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.flags.FeatureFlags;
import com.android.systemui.flags.SystemPropertiesHelper;
import com.android.systemui.keyguard.DismissCallbackRegistry;
import com.android.systemui.keyguard.KeyguardUnlockAnimationController;
import com.android.systemui.keyguard.KeyguardViewMediator;
import com.android.systemui.keyguard.WindowManagerLockscreenVisibilityManager;
import com.android.systemui.keyguard.WindowManagerOcclusionManager;
import com.android.systemui.keyguard.dagger.GlanceableHubTransitionModule;
import com.android.systemui.keyguard.dagger.KeyguardConnectedDisplaysModule;
import com.android.systemui.keyguard.dagger.KeyguardFaceAuthNotSupportedModule;
import com.android.systemui.keyguard.dagger.PrimaryBouncerTransitionModule;
import com.android.systemui.keyguard.data.repository.KeyguardRepositoryModule;
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor;
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionBootInteractor;
import com.android.systemui.keyguard.domain.interactor.StartKeyguardTransitionModule;
import com.android.systemui.log.SessionTracker;
import com.android.systemui.navigationbar.NavigationBarController;
import com.android.systemui.navigationbar.NavigationModeController;
import com.android.systemui.process.ProcessWrapper;
import com.android.systemui.scene.domain.interactor.SceneInteractor;
import com.android.systemui.settings.DisplayTracker;
import com.android.systemui.settings.DisplayTrackerImpl;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.shade.ShadeController;
import com.android.systemui.shade.data.repository.ShadeDisplaysRepository;
import com.android.systemui.statusbar.NotificationShadeDepthController;
import com.android.systemui.statusbar.NotificationShadeWindowController;
import com.android.systemui.statusbar.SysuiStatusBarStateController;
import com.android.systemui.statusbar.phone.DozeParameters;
import com.android.systemui.statusbar.phone.ScreenOffAnimationController;
import com.android.systemui.statusbar.phone.ScrimController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.statusbar.policy.UserSwitcherController;
import com.android.systemui.user.domain.interactor.SelectedUserInteractor;
import com.android.systemui.util.DeviceConfigProxy;
import com.android.systemui.util.kotlin.JavaAdapter;
import com.android.systemui.util.settings.SecureSettings;
import com.android.systemui.util.settings.SystemSettings;
import com.android.systemui.util.time.SystemClock;
import com.android.systemui.wallpapers.WallpaperPresentationEnabled;
import com.android.systemui.wallpapers.data.repository.WallpaperRepository;
import com.android.wm.shell.keyguard.KeyguardTransitions;

import dagger.Binds;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

import kotlinx.coroutines.CoroutineScope;

import java.util.concurrent.Executor;

import javax.inject.Provider;

/**
 * Dagger Module providing keyguard.
 */
@Module(subcomponents = {
        KeyguardStatusBarViewComponent.class
        },
        includes = {
                FalsingModule.class,
                GlanceableHubTransitionModule.class,
                KeyguardFaceAuthNotSupportedModule.class,
                KeyguardRepositoryModule.class,
                PrimaryBouncerTransitionModule.class,
                StartKeyguardTransitionModule.class,
                KeyguardConnectedDisplaysModule.class,
        })
public interface CarKeyguardModule {

    /**
     * Provides our instance of CarKeyguardViewMediator
     */
    @Provides
    @SysUISingleton
    static KeyguardViewMediator newKeyguardViewMediator(
            Context context,
            UiEventLogger uiEventLogger,
            SessionTracker sessionTracker,
            UserTracker userTracker,
            FalsingCollector falsingCollector,
            LockPatternUtils lockPatternUtils,
            BroadcastDispatcher broadcastDispatcher,
            Lazy<KeyguardViewController> statusBarKeyguardViewManagerLazy,
            DismissCallbackRegistry dismissCallbackRegistry,
            KeyguardUpdateMonitor updateMonitor,
            DumpManager dumpManager,
            PowerManager powerManager,
            TrustManager trustManager,
            UserSwitcherController userSwitcherController,
            @UiBackground Executor uiBgExecutor,
            DeviceConfigProxy deviceConfig,
            NavigationModeController navigationModeController,
            CarKeyguardDisplayManager keyguardDisplayManager,
            DozeParameters dozeParameters,
            SysuiStatusBarStateController statusBarStateController,
            KeyguardStateController keyguardStateController,
            Lazy<KeyguardUnlockAnimationController> keyguardUnlockAnimationController,
            ScreenOffAnimationController screenOffAnimationController,
            Lazy<NotificationShadeDepthController> notificationShadeDepthController,
            ScreenOnCoordinator screenOnCoordinator,
            KeyguardTransitions keyguardTransitions,
            InteractionJankMonitor interactionJankMonitor,
            DreamOverlayStateController dreamOverlayStateController,
            JavaAdapter javaAdapter,
            WallpaperRepository wallpaperRepository,
            Lazy<ShadeController> shadeController,
            Lazy<NotificationShadeWindowController> notificationShadeWindowController,
            Lazy<ActivityTransitionAnimator> activityTransitionAnimator,
            Lazy<ScrimController> scrimControllerLazy,
            IActivityTaskManager activityTaskManagerService,
            IStatusBarService statusBarService,
            FeatureFlags featureFlags,
            SecureSettings secureSettings,
            SystemSettings systemSettings,
            SystemClock systemClock,
            ProcessWrapper processWrapper,
            @Application CoroutineScope applicationScope,
            Lazy<DreamViewModel> dreamViewModel,
            Lazy<CommunalTransitionViewModel> communalTransitionViewModel,
            SystemPropertiesHelper systemPropertiesHelper,
            Lazy<WindowManagerLockscreenVisibilityManager> wmLockscreenVisibilityManager,
            SelectedUserInteractor selectedUserInteractor,
            KeyguardInteractor keyguardInteractor,
            KeyguardTransitionBootInteractor transitionBootInteractor,
            Lazy<CommunalSceneInteractor> communalSceneInteractor,
            Lazy<CommunalSettingsInteractor> communalSettingsInteractor,
            WindowManagerOcclusionManager wmOcclusionManager,
            Lazy<SceneInteractor> sceneInteractor) {
        return new CarKeyguardViewMediator(
                context,
                uiEventLogger,
                sessionTracker,
                userTracker,
                falsingCollector,
                lockPatternUtils,
                broadcastDispatcher,
                statusBarKeyguardViewManagerLazy,
                dismissCallbackRegistry,
                updateMonitor,
                dumpManager,
                uiBgExecutor,
                powerManager,
                trustManager,
                userSwitcherController,
                deviceConfig,
                navigationModeController,
                keyguardDisplayManager,
                dozeParameters,
                statusBarStateController,
                keyguardStateController,
                keyguardUnlockAnimationController,
                screenOffAnimationController,
                notificationShadeDepthController,
                screenOnCoordinator,
                keyguardTransitions,
                interactionJankMonitor,
                dreamOverlayStateController,
                javaAdapter,
                wallpaperRepository,
                shadeController,
                notificationShadeWindowController,
                activityTransitionAnimator,
                scrimControllerLazy,
                activityTaskManagerService,
                statusBarService,
                featureFlags,
                secureSettings,
                systemSettings,
                systemClock,
                processWrapper,
                applicationScope,
                dreamViewModel,
                communalTransitionViewModel,
                systemPropertiesHelper,
                wmLockscreenVisibilityManager,
                selectedUserInteractor,
                keyguardInteractor,
                transitionBootInteractor,
                communalSceneInteractor,
                communalSettingsInteractor,
                wmOcclusionManager,
                sceneInteractor);
    }

    /** */
    @Provides
    static ViewMediatorCallback providesViewMediatorCallback(KeyguardViewMediator viewMediator) {
        return viewMediator.getViewMediatorCallback();
    }

    /** Provide car keyguard display manager instance. */
    @Provides
    @SysUISingleton
    static CarKeyguardDisplayManager provideCarKeyguardDisplayManager(Context context,
            Lazy<NavigationBarController> navigationBarControllerLazy,
            DisplayTracker defaultDisplayTracker,
            Lazy<DisplayTrackerImpl> displayTrackerImpl,
            @Main Executor mainExecutor,
            @UiBackground Executor uiBgExecutor,
            KeyguardDisplayManager.DeviceStateHelper deviceStateHelper,
            KeyguardStateController keyguardStateController,
            ConnectedDisplayKeyguardPresentationFactory
                    connectedDisplayKeyguardPresentationFactory,
            Provider<ShadeDisplaysRepository> shadeDisplaysRepositoryProvider,
            @Application CoroutineScope appScope,
            @WallpaperPresentationEnabled boolean isWallpaperPresentationEnabled,
            DisplayRepository displayRepository) {
        DisplayTracker finalDisplayTracker =
                CarSystemUIUserUtil.isDriverMUMDSystemUI() ? displayTrackerImpl.get()
                        : defaultDisplayTracker;
        return new CarKeyguardDisplayManager(context, navigationBarControllerLazy,
                finalDisplayTracker, mainExecutor, uiBgExecutor, deviceStateHelper,
                keyguardStateController, connectedDisplayKeyguardPresentationFactory,
                shadeDisplaysRepositoryProvider, appScope, isWallpaperPresentationEnabled,
                displayRepository);
    }

    /** Binds {@link KeyguardUpdateMonitor} as a {@link CoreStartable}. */
    @Binds
    @IntoMap
    @ClassKey(KeyguardUpdateMonitor.class)
    CoreStartable bindsKeyguardUpdateMonitor(KeyguardUpdateMonitor keyguardUpdateMonitor);

    /** Binds {@link PassengerKeyguardLoadingDialog} as a {@link CoreStartable}. */
    @Binds
    @IntoMap
    @ClassKey(PassengerKeyguardLoadingDialog.class)
    CoreStartable bindsPassengerKeyguardLoadingDialog(PassengerKeyguardLoadingDialog dialog);
}
