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

package com.android.systemui.wmshell;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.UserManager;
import android.util.Log;
import android.view.IWindowManager;

import androidx.annotation.NonNull;

import com.android.car.scalableui.loader.xml.parser.HunPanelParser;
import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.PanelState;
import com.android.car.scalableui.panel.PanelUpdatePublisher;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.flags.Flag;
import com.android.systemui.car.flags.FlagManager;
import com.android.systemui.car.minimizedcontrols.MinimizedControlsModule;
import com.android.systemui.car.wm.AutoCaptionPerDisplayInitializer;
import com.android.systemui.car.wm.AutoDisplayCompatWindowDecorViewModel;
import com.android.systemui.car.wm.CarFullscreenTaskMonitorListener;
import com.android.systemui.car.wm.CarWMUserHelper;
import com.android.systemui.car.wm.scalableui.ActionConfigReader;
import com.android.systemui.car.wm.scalableui.AutoTaskStackHelper;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.car.wm.scalableui.PanelAutoTaskStackTransitionHandlerDelegate;
import com.android.systemui.car.wm.scalableui.PanelConfigReader;
import com.android.systemui.car.wm.scalableui.ScalableUIDumpsys;
import com.android.systemui.car.wm.scalableui.ScalableUIUtils;
import com.android.systemui.car.wm.scalableui.ScalableUIWMInitializer;
import com.android.systemui.car.wm.scalableui.panel.DecorPanel;
import com.android.systemui.car.wm.scalableui.panel.SysUIPanel;
import com.android.systemui.car.wm.scalableui.panel.TaskPanel;
import com.android.systemui.car.wm.scalableui.panel.controller.PanelControllerModule;
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelConfigReadStateMonitor;
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer;
import com.android.systemui.car.wm.scalableui.panel.panelupdates.ScalableUIPanelUpdateImpl;
import com.android.systemui.car.wm.scalableui.systemwindow.HunWindow;
import com.android.systemui.car.wm.scalableui.systemwindow.SystemUiWindowProvider;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.wm.DisplaySystemBarsController;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.automotive.AutoCaptionController;
import com.android.wm.shell.automotive.AutoLayoutManager;
import com.android.wm.shell.automotive.AutoShellModule;
import com.android.wm.shell.automotive.AutoTaskRepository;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.dagger.DynamicOverride;
import com.android.wm.shell.dagger.WMShellBaseModule;
import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.fullscreen.FullscreenTaskListener;
import com.android.wm.shell.pip.Pip;
import com.android.wm.shell.recents.RecentTasksController;
import com.android.wm.shell.shared.annotations.ShellBackgroundThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.taskview.TaskViewTransitions;
import com.android.wm.shell.transition.FocusTransitionObserver;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;
import com.android.wm.shell.windowdecor.common.viewhost.DefaultWindowDecorViewHostSupplier;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier;

import dagger.BindsOptionalOf;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

import kotlinx.coroutines.CoroutineScope;

import java.util.Optional;

/** Provides dependencies from {@link com.android.wm.shell} for CarSystemUI. */
@Module(includes = { WMShellBaseModule.class, AutoShellModule.class, PanelControllerModule.class,
        MinimizedControlsModule.class })
public abstract class CarWMShellModule {
    private static final String TAG = CarWMShellModule.class.getSimpleName();

    @WMSingleton
    @Provides
    static DisplaySystemBarsController provideDisplaySystemBarsController(Context context,
            UserManager userManager, CarServiceProvider carServiceProvider,
            IWindowManager wmService, DisplayController displayController,
            DisplayInsetsController displayInsetsController,
            @Main Handler mainHandler, CarWMUserHelper userHelper,
            ShellController shellController, SystemUiWindowProvider windowProvider,
            EventDispatcher dispatcher) {
        return new DisplaySystemBarsController(context, userManager, carServiceProvider, wmService,
                displayController, displayInsetsController, mainHandler, userHelper,
                shellController, windowProvider, dispatcher);
    }

    @WMSingleton
    @Provides
    static Optional<AutoCaptionPerDisplayInitializer> provideAutoCaptionPerDisplayInitializer(
            Context context,
            ShellTaskOrganizer shellTaskOrganizer,
            AutoCaptionController autoCaptionController,
            DisplayController displayController,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            AutoLayoutManager autoLayoutManager) {
        return Optional.of(
                new AutoCaptionPerDisplayInitializer(context, shellTaskOrganizer,
                        autoCaptionController, displayController, rootTaskDisplayAreaOrganizer,
                        autoLayoutManager));
    }

    @BindsOptionalOf
    abstract Pip optionalPip();

    @WMSingleton
    @Provides
    @DynamicOverride
    static FullscreenTaskListener provideFullScreenTaskListener(Context context,
            CarServiceProvider carServiceProvider,
            ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer,
            SyncTransactionQueue syncQueue,
            Optional<RecentTasksController> recentTasksOptional,
            Optional<WindowDecorViewModel> windowDecorViewModelOptional,
            TaskViewTransitions taskViewTransitions,
            AutoTaskRepository taskRepository) {
        return new CarFullscreenTaskMonitorListener(context,
                carServiceProvider,
                shellInit,
                shellTaskOrganizer,
                syncQueue,
                recentTasksOptional,
                windowDecorViewModelOptional,
                taskViewTransitions,
                taskRepository);
    }

    @WMSingleton
    @Provides
    static WindowDecorViewHostSupplier<WindowDecorViewHost> provideWindowDecorViewHostSupplier(
            @ShellMainThread @NonNull CoroutineScope mainScope) {
        return new DefaultWindowDecorViewHostSupplier(mainScope);
    }

    @WMSingleton
    @Provides
    static WindowDecorViewModel provideWindowDecorViewModel(
            Context context,
            @ShellMainThread Handler handler,
            Transitions transitions,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellBackgroundThread ShellExecutor bgExecutor,
            ShellInit shellInit,
            ShellTaskOrganizer taskOrganizer,
            DisplayController displayController,
            DisplayInsetsController displayInsetsController,
            SyncTransactionQueue syncQueue,
            FocusTransitionObserver focusTransitionObserver,
            WindowDecorViewHostSupplier<WindowDecorViewHost> windowDecorViewHostSupplier,
            CarServiceProvider carServiceProvider
    ) {
        return new AutoDisplayCompatWindowDecorViewModel(
                context,
                handler,
                transitions,
                mainExecutor,
                bgExecutor,
                shellInit,
                taskOrganizer,
                displayController,
                displayInsetsController,
                syncQueue,
                focusTransitionObserver,
                windowDecorViewHostSupplier,
                carServiceProvider);
    }

    @WMSingleton
    @Provides
    static Optional<PanelConfigReader> providesPanelConfigReader(
            Context context,
            TaskPanel.Factory taskPanelFactory,
            DecorPanel.Factory decorPanelFactory,
            SysUIPanel.Factory sysUiPanelFactory,
            PanelConfigReadStateMonitor panelConfigMonitor,
            FlagManager flagManager
    ) {
        if (ScalableUIUtils.isScalableUIEnabled(context, flagManager)) {
            return Optional.of(new PanelConfigReader(
                    context,
                    taskPanelFactory,
                    decorPanelFactory,
                    sysUiPanelFactory,
                    panelConfigMonitor,
                    flagManager));
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static Optional<ActionConfigReader> providesActionConfigReader(Context context,
            FlagManager flagManager) {
        if (ScalableUIUtils.isScalableUIEnabled(context, flagManager)) {
            return Optional.of(new ActionConfigReader(context, flagManager));
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static Optional<ScalableUIWMInitializer> provideScalableUIInitializer(ShellInit shellInit,
            Context context,
            Optional<ActionConfigReader> actionConfigReaderOptional,
            Optional<PanelConfigReader> panelConfigReaderOptional,
            Lazy<PanelAutoTaskStackTransitionHandlerDelegate> delegate,
            ScalableUIDumpsys scalableUIDumpsys,
            FlagManager flagManager,
            AutoTaskStackHelper autoTaskStackHelper,
            ShellController shellController) {
        if (ScalableUIUtils.isScalableUIEnabled(context, flagManager)
                && panelConfigReaderOptional.isPresent()) {
            return Optional.of(
                    new ScalableUIWMInitializer(context, shellInit,
                            actionConfigReaderOptional.get(),
                            panelConfigReaderOptional.get(), delegate.get(), scalableUIDumpsys,
                            autoTaskStackHelper, flagManager, shellController));
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static FlagManager provideFlagManager(Context context) {
        return new FlagManager(context);
    }

    @WMSingleton
    @Provides
    static Optional<ScalableUIPanelUpdateImpl> provideScalableUIPanelUpdateImpl(Context context,
            FlagManager flagManager) {
        if (ScalableUIUtils.isScalableUIEnabled(context, flagManager) && flagManager.isEnabled(
                Flag.EnableExtPanelUpdates)) {
            return Optional.of(new ScalableUIPanelUpdateImpl());
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static Optional<PanelUpdatePublisher> providePanelUpdatePublisher(
            Optional<ScalableUIPanelUpdateImpl> scalableUIPanelUpdateOptional) {
        if (scalableUIPanelUpdateOptional.isPresent()) {
            return Optional.of(scalableUIPanelUpdateOptional.get());
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static Optional<PanelUpdateConsumer> providePanelUpdateConsumer(
            Optional<ScalableUIPanelUpdateImpl> scalableUIPanelUpdateOptional) {
        if (scalableUIPanelUpdateOptional.isPresent()) {
            return Optional.of(scalableUIPanelUpdateOptional.get());
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static Optional<HunWindow> provideHunWindow(Context context, DisplayManager displayManager,
            Optional<PanelUpdateConsumer> consumer, EventDispatcher dispatcher) {
        if (consumer.isPresent()) {
            PanelState panelState = StateManager.getPanelState(HunPanelParser.HUN_PANEL_ID);
            if (panelState == null) {
                Log.w(TAG, "HunWindow not initialized because PanelState for HUN_PANEL_ID "
                        + "is null.");
                return Optional.empty();
            }
            try {
                return Optional.of(
                        new HunWindow(context, displayManager, consumer.get(), dispatcher,
                                panelState.getDisplayId()));
            } catch (IllegalStateException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
