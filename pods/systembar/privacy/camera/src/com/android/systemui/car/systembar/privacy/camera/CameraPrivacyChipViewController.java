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

package com.android.systemui.car.systembar.privacy.camera;

import static android.hardware.SensorPrivacyManager.Sensors.CAMERA;

import android.content.Context;
import android.hardware.SensorPrivacyManager;

import androidx.annotation.IdRes;
import androidx.annotation.VisibleForTesting;

import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.flexibleui.CarSystemBarElementController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.systembar.panel.PanelViewController;
import com.android.systemui.car.systembar.privacy.base.PrivacyChip;
import com.android.systemui.car.systembar.privacy.base.PrivacyChipViewController;
import com.android.systemui.privacy.PrivacyItemController;
import com.android.systemui.privacy.PrivacyType;
import com.android.systemui.settings.UserTracker;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import javax.inject.Provider;

/** Controls a Camera Privacy Chip view in system icons. */
public class CameraPrivacyChipViewController extends PrivacyChipViewController {

    @AssistedInject
    public CameraPrivacyChipViewController(@Assisted PrivacyChip view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            Context context,
            PrivacyItemController privacyItemController,
            SensorPrivacyManager sensorPrivacyManager,
            UserTracker userTracker,
            CarDeviceProvisionedController carDeviceProvisionedController,
            Provider<PanelViewController.Factory> panelControllerFactoryProvider) {
        super(view, disableController, stateController, context, privacyItemController,
                sensorPrivacyManager, userTracker, carDeviceProvisionedController,
                panelControllerFactoryProvider);
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<PrivacyChip,
                    CameraPrivacyChipViewController> {
    }

    @Override
    protected @SensorPrivacyManager.Sensors.Sensor int getChipSensor() {
        return CAMERA;
    }

    @Override
    protected PrivacyType getChipPrivacyType() {
        return PrivacyType.TYPE_CAMERA;
    }

    @Override
    protected @IdRes int getChipResourceId() {
        return R.id.camera_privacy_chip;
    }

    @Override
    protected int getPanelLayoutRes() {
        return R.layout.qc_camera_panel;
    }

    @VisibleForTesting
    @Override
    protected void onViewDetached() {
        super.onViewDetached();
    }

    @VisibleForTesting
    @Override
    protected void onViewAttached() {
        super.onViewAttached();
    }

    @VisibleForTesting
    @Override
    protected boolean isSensorEnabled() {
        return super.isSensorEnabled();
    }
}
