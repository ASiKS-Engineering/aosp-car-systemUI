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

import com.android.systemui.car.wm.AutoCaptionPerDisplayInitializer;
import com.android.systemui.car.wm.CarSystemUIProxyImpl;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.car.wm.scalableui.ScalableUIWMInitializer;
import com.android.systemui.car.wm.scalableui.configuration.SystemUiConfigurationProvider;
import com.android.systemui.car.wm.scalableui.panel.TaskPanelInfoRepository;
import com.android.systemui.car.wm.scalableui.systemwindow.SystemUiWindowProvider;
import com.android.systemui.car.wm.taskview.RemoteCarTaskViewTransitions;
import com.android.systemui.wm.DisplaySystemBarsController;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.automotive.AutoDecorManager;
import com.android.wm.shell.automotive.AutoLayoutManager;
import com.android.wm.shell.automotive.AutoTaskStackController;
import com.android.wm.shell.dagger.WMComponent;
import com.android.wm.shell.dagger.WMSingleton;

import dagger.Subcomponent;

import java.util.Optional;

/**
 * Dagger Subcomponent for WindowManager.
 */
@WMSingleton
@Subcomponent(modules = {CarWMShellModule.class})
public interface CarWMComponent extends WMComponent {

    /**
     * Builder for a SysUIComponent.
     */
    @Subcomponent.Builder
    interface Builder extends WMComponent.Builder {
        CarWMComponent build();
    }

    @WMSingleton
    RootTaskDisplayAreaOrganizer getRootTaskDisplayAreaOrganizer();

    @WMSingleton
    DisplaySystemBarsController getDisplaySystemBarsController();

    /**
     * Returns the initializer used to initialize AutoCaption per display.
     */
    @WMSingleton
    Optional<AutoCaptionPerDisplayInitializer> getAutoCaptionPerDisplayInitializer();

    /**
     * Returns the implementation of car system ui proxy which will be used by other apps to
     * interact with the car system ui.
     */
    @WMSingleton
    CarSystemUIProxyImpl getCarSystemUIProxy();

    /**
     * Returns the RemoteCarTaskViewTransitions used for transitions stuff related to task views.
     */
    @WMSingleton
    RemoteCarTaskViewTransitions getRemoteCarTaskViewTransitions();



    /**
     * Provides the {@link AutoTaskStackController} used to implement custom
     * windowing behavior.
     */
    @WMSingleton
    AutoTaskStackController getAutoTaskStackController();

    /**
     * Optional {@link ScalableUIWMInitializer} component for initializing scalable ui
     */
    @WMSingleton
    Optional<ScalableUIWMInitializer> getScalableUIWMInitializer();

    /** Provides the {@link TaskPanelInfoRepository} used to dispatch ScalableUI task info. */
    @WMSingleton
    TaskPanelInfoRepository getTaskPanelInfoRepository();

    /** Provides the {@link EventDispatcher} used to dispatch ScalableUI events. */
    @WMSingleton
    EventDispatcher getScalableUIEventDispatcher();

    /** Provides the {@link AutoDecorManager} used to manage {@link AutoDecor}. */
    @WMSingleton
    AutoDecorManager getAutoDecorManager();

    /** Provides the {@link AutoLayoutManager} used to set ScalableUI Insets. */
    @WMSingleton
    AutoLayoutManager getAutoLayoutManager();

    /** Provides the {@link SystemUiWindowProvider} */
    @WMSingleton
    SystemUiWindowProvider getSystemUiWindowProvider();

    /** Provides the {@link SystemUiConfigurationProvider} */
    @WMSingleton
    SystemUiConfigurationProvider getSystemUiConfigurationProvider();
}
