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

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

/**
 * A CarSystemBarElementController for handling hvac button interactions.
 */
public class TemperatureControlViewController extends
            CarSystemBarElementController<TemperatureControlView> {

    private final HvacPanelOverlayViewController mHvacPanelOverlayViewController;
    private final HvacController mHvacController;

    @AssistedInject
    public TemperatureControlViewController(@Assisted TemperatureControlView hvacButton,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            HvacPanelOverlayViewController hvacPanelOverlayViewController,
            HvacController hvacController) {
        super(hvacButton, disableController, stateController);

        mHvacPanelOverlayViewController = hvacPanelOverlayViewController;
        hvacButton.setTemperatureTextClickListener(this::onHvacClick);
        mHvacController = hvacController;
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<TemperatureControlView,
                    TemperatureControlViewController> {
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        mHvacController.registerHvacViews(mView);
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        mHvacController.unregisterViews(mView);
    }

    private void onHvacClick(View v) {
        mHvacPanelOverlayViewController.toggle();
    }
}
