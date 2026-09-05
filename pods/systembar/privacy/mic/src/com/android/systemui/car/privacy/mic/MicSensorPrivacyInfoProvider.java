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
package com.android.systemui.car.systembar.privacy.mic;

import static android.hardware.SensorPrivacyManager.Sensors.MICROPHONE;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.SensorPrivacyManager;
import android.permission.PermissionManager;

import com.android.systemui.car.systembar.privacy.base.SensorPrivacyInfoProvider;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.privacy.PrivacyItemController;
import com.android.systemui.privacy.PrivacyType;
import com.android.systemui.privacy.logging.PrivacyLogger;
import com.android.systemui.settings.UserTracker;

import javax.inject.Inject;

/**
 * Implementation of {@link
 * com.android.systemui.car.systembar.privacy.base.SensorPrivacyInfoProvider} for microphone.
 */
@SysUISingleton
public class MicSensorPrivacyInfoProvider extends SensorPrivacyInfoProvider {
    @Inject
    public MicSensorPrivacyInfoProvider(Context context,
            PermissionManager permissionManager,
            PackageManager packageManager,
            SensorPrivacyManager sensorPrivacyManager,
            PrivacyItemController privacyItemController,
            UserTracker userTracker,
            PrivacyLogger privacyLogger) {
        super(context, permissionManager, packageManager, sensorPrivacyManager,
                privacyItemController,
                userTracker, privacyLogger);
    }

    @Override
    protected PrivacyType getProviderPrivacyType() {
        return PrivacyType.TYPE_MICROPHONE;
    }

    @Override
    protected int getChipSensor() {
        return MICROPHONE;
    }
}
