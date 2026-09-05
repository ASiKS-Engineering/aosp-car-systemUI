/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.car.statusicon.ui;

import com.android.systemui.car.statusicon.base.StatusIconViewController;
import com.android.systemui.car.statusicon.bluetooth.BluetoothStatusIconModule;
import com.android.systemui.car.statusicon.connectivity.ConnectivityStatusIconModule;
import com.android.systemui.car.statusicon.location.LocationStatusIconModule;
import com.android.systemui.car.statusicon.mediavolume.MediaVolumeStatusIconModule;
import com.android.systemui.car.statusicon.mobile.MobileStatusIconModule;
import com.android.systemui.car.statusicon.phonecall.PhoneCallStatusIconModule;
import com.android.systemui.car.statusicon.wifi.WifiStatusIconModule;

import dagger.Module;

/**
 * Dagger injection module for {@link StatusIconViewController}
 */
@Module(includes = {
        BluetoothStatusIconModule.class,
        ConnectivityStatusIconModule.class,
        LocationStatusIconModule.class,
        MediaVolumeStatusIconModule.class,
        MobileStatusIconModule.class,
        PhoneCallStatusIconModule.class,
        WifiStatusIconModule.class})
public abstract class StatusIconModule {}
