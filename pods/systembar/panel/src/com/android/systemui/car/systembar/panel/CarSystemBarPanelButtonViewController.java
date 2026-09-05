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

package com.android.systemui.car.systembar.panel;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.android.systemui.car.flexibleui.CarSystemBarElementController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;


import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import javax.inject.Provider;

/** Controller for the button view that anchors system bar panels. */
public class CarSystemBarPanelButtonViewController extends
        CarSystemBarElementController<CarSystemBarPanelButtonView> {
    private static final String KEY_IS_SELECTED = "key_is_selected";
    private final Provider<PanelViewController.Factory> mStatusIconPanelFactoryProvider;

    @AssistedInject
    protected CarSystemBarPanelButtonViewController(@Assisted CarSystemBarPanelButtonView view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            Provider<PanelViewController.Factory> statusIconPanelFactoryProvider) {
        super(view, disableController, stateController);
        mStatusIconPanelFactoryProvider = statusIconPanelFactoryProvider;
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<CarSystemBarPanelButtonView,
                    CarSystemBarPanelButtonViewController> {
    }

    @Override
    protected void onInit() {
        PanelViewController.Factory factory = mStatusIconPanelFactoryProvider.get();
        // The View (mView) itself implements PanelContentProvider to provide its own content.
        PanelViewController panelController = factory.create(/* anchorView= */ mView,
                getPanelContentProvider());
        panelController.init();
    }

    @Override
    protected boolean shouldRestoreState() {
        return true;
    }

    @Override
    protected Bundle getState(Bundle bundle) {
        bundle.putBoolean(KEY_IS_SELECTED, mView.isSelected());
        return bundle;
    }

    @Override
    protected void restoreState(Bundle bundle) {
        if (bundle.containsKey(KEY_IS_SELECTED)) {
            boolean selected = bundle.getBoolean(KEY_IS_SELECTED);
            if (selected != mView.isSelected()) {
                mView.callOnClick();
            }
        }
    }

    @NonNull
    protected PanelContentProvider getPanelContentProvider() {
        return (PanelContentProvider) mView;
    }
}
