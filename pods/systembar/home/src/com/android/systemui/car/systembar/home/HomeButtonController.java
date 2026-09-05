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
package com.android.systemui.car.systembar.home;

import android.view.View;

import com.android.systemui.car.flexibleui.CarSystemBarElementController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.systembar.base.ButtonSelectionStateController;
import com.android.systemui.car.systembar.base.CarSystemBarButton;
import com.android.systemui.car.systembar.base.CarSystemBarButtonController;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.settings.UserTracker;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

/**
 * A CarSystemBarElementController for handling Home button interactions.
 */
public class HomeButtonController extends CarSystemBarButtonController  {

    @AssistedInject
    public HomeButtonController(@Assisted CarSystemBarButton homeButton,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            UserTracker userTracker, EventDispatcher eventDispatcher,
            ButtonSelectionStateController buttonSelectionStateController) {
        super(homeButton, disableController, stateController, userTracker, eventDispatcher,
                buttonSelectionStateController);

        homeButton.setVisibility(
                CarSystemUIUserUtil.isSecondaryMUMDSystemUI() ? View.GONE : View.VISIBLE);
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<CarSystemBarButton,
                    HomeButtonController> {
    }

    @Override
    protected boolean shouldBeVisible() {
        return !CarSystemUIUserUtil.isSecondaryMUMDSystemUI();
    }
}
