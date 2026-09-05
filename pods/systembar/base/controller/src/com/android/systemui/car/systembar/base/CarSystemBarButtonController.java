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
package com.android.systemui.car.systembar.base;

import androidx.annotation.CallSuper;

import com.android.systemui.car.flexibleui.CarSystemBarElementController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.settings.UserTracker;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

/**
 * A generic CarSystemBarElementController for handling CarSystemBarButton button interactions.
 */
public class CarSystemBarButtonController
        extends CarSystemBarElementController<CarSystemBarButton> {

    private final UserTracker mUserTracker;
    private final EventDispatcher mEventDispatcher;
    private final ButtonSelectionStateController mButtonSelectionStateController;

    @AssistedInject
    public CarSystemBarButtonController(@Assisted CarSystemBarButton barButton,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            UserTracker userTracker, EventDispatcher eventDispatcher,
            ButtonSelectionStateController buttonSelectionStateController) {
        super(barButton, disableController, stateController);

        mUserTracker = userTracker;
        mEventDispatcher = eventDispatcher;
        mButtonSelectionStateController = buttonSelectionStateController;
    }

    @Override
    @CallSuper
    protected void onInit() {
        mView.setUserTracker(mUserTracker);
        mView.setEventDispatcher(mEventDispatcher);
    }

    protected UserTracker getUserTracker() {
        return mUserTracker;
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        mButtonSelectionStateController.addAllButtonsWithSelectionState(mView);
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        mButtonSelectionStateController.removeButton(mView);
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<CarSystemBarButton,
                    CarSystemBarButtonController> {
    }
}
