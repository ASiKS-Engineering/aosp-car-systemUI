/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.systemui.car.hvac;

import android.view.View;

import com.android.systemui.car.flexibleui.CarSystemBarElementController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.systembar.base.ButtonSelectionStateController;
import com.android.systemui.car.systembar.base.CarSystemBarButton;
import com.android.systemui.car.systembar.base.CarSystemBarButtonController;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.settings.UserTracker;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

/**
 * A CarSystemBarElementController for handling notification button interactions.
 */
public class HvacButtonController extends CarSystemBarButtonController {

    private final HvacPanelOverlayViewController mHvacPanelOverlayViewController;

    @AssistedInject
    public HvacButtonController(@Assisted CarSystemBarButton hvacButton,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            HvacPanelOverlayViewController hvacPanelOverlayViewController,
            UserTracker userTracker, EventDispatcher eventDispatcher,
            ButtonSelectionStateController buttonSelectionStateController) {
        super(hvacButton, disableController, stateController, userTracker, eventDispatcher,
                buttonSelectionStateController);

        mHvacPanelOverlayViewController = hvacPanelOverlayViewController;
        mHvacPanelOverlayViewController.registerViewStateListener(hvacButton);
        hvacButton.setOnClickListener(this::onHvacClick);
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<CarSystemBarButton,
                    HvacButtonController> {
    }

    private void onHvacClick(View v) {
        mHvacPanelOverlayViewController.toggle();
    }
}
