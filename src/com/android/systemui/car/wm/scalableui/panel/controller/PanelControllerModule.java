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

package com.android.systemui.car.wm.scalableui.panel.controller;

import android.content.Context;
import android.view.View;

import com.android.car.scalableui.panel.DecorPanelController;
import com.android.car.scalableui.panel.TaskPanelController;
import com.android.systemui.car.minimizedcontrols.MinimizedControlsPanelModule;
import com.android.systemui.car.wm.scalableui.view.AppStyledViewController;
import com.android.systemui.car.wm.scalableui.view.AppStyledViewScrim;
import com.android.systemui.car.wm.scalableui.view.GripBarViewController;
import com.android.systemui.car.wm.scalableui.view.HorizontalGripBar;
import com.android.systemui.car.wm.scalableui.view.PanelOverlay;
import com.android.systemui.car.wm.scalableui.view.PanelOverlayController;
import com.android.systemui.car.wm.scalableui.view.VerticalGripBar;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

/**
 * Module to inject instance related to panel controllers.
 */
@Module(includes = { MinimizedControlsPanelModule.class })
public abstract class PanelControllerModule {
    /** Binds MapsPanelController.Factory. */
    @Binds
    @IntoMap
    @ClassKey(MapsPanelController.class)
    public abstract TaskPanelController.Factory bindMapsPanelControllerFactory(
            MapsPanelController.Factory factory);

    /** Binds BaseTaskPanelController.Factory. */
    @Binds
    @IntoMap
    @ClassKey(BaseTaskPanelController.class)
    public abstract TaskPanelController.Factory bindsBaseTaskPanelControllerFactory(
            BaseTaskPanelController.Factory factory);

    /** Binds SetupPanelController.Factory. */
    @Binds
    @IntoMap
    @ClassKey(SetupPanelController.class)
    public abstract TaskPanelController.Factory bindsSetupPanelControllerFactory(
            SetupPanelController.Factory factory);

    /** Binds GripBarViewController.Factory. */
    @Binds
    @IntoMap
    @ClassKey(GripBarViewController.class)
    public abstract DecorPanelController.Factory<?> bindGripBarControllerFactory(
            GripBarViewController.Factory factory);

    /** Binds AppStyledViewController.Factory. */
    @Binds
    @IntoMap
    @ClassKey(AppStyledViewController.class)
    public abstract DecorPanelController.Factory<?> bindAppStyledViewControllerFactory(
            AppStyledViewController.Factory factory);

    /** Binds PanelOverlayController.Factory. */
    @Binds
    @IntoMap
    @ClassKey(PanelOverlayController.class)
    public abstract DecorPanelController.Factory<?> bindPanelOverlayControllerFactory(
            PanelOverlayController.Factory factory);


    /** Binds TaskToolBarController.Factory. */
    @Binds
    @IntoMap
    @ClassKey(CompatibilityToolbarController.class)
    public abstract TaskToolbarController.Factory bindTCompatibilityToolBarControllerFactory(
            CompatibilityToolbarController.Factory factory
    );

    /** Binds {@link HorizontalGripBar} as a decor panel view. */
    @Provides
    @IntoMap
    @ClassKey(HorizontalGripBar.class)
    @DecorPanelViewMap
    static View bindHorizontalGripBarView(Context context) {
        return new HorizontalGripBar(context);
    }

    /** Binds {@link VerticalGripBar} as a decor panel view. */
    @Provides
    @IntoMap
    @ClassKey(VerticalGripBar.class)
    @DecorPanelViewMap
    static View bindVerticalGripBarView(Context context) {
        return new VerticalGripBar(context);
    }

    /** Binds {@link AppStyledViewScrim} as a decor panel view. */
    @Provides
    @IntoMap
    @ClassKey(AppStyledViewScrim.class)
    @DecorPanelViewMap
    static View bindAppStyledScrimView(Context context) {
        return new AppStyledViewScrim(context);
    }


    /** Binds {@link PanelOverlay} as a decor panel view. */
    @Provides
    @IntoMap
    @ClassKey(PanelOverlay.class)
    @DecorPanelViewMap
    static View bindPanelOverlayView(Context context) {
        return new PanelOverlay(context);
    }

}
