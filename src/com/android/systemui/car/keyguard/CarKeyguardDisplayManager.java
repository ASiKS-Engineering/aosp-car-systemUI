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

package com.android.systemui.car.keyguard;

import android.content.Context;

import com.android.keyguard.ConnectedDisplayKeyguardPresentationFactory;
import com.android.keyguard.KeyguardDisplayManager;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Application;
import com.android.systemui.display.data.repository.DisplayRepository;
import com.android.systemui.navigationbar.NavigationBarController;
import com.android.systemui.settings.DisplayTracker;
import com.android.systemui.shade.data.repository.ShadeDisplaysRepository;
import com.android.systemui.statusbar.policy.KeyguardStateController;

import dagger.Lazy;

import java.util.concurrent.Executor;

import javax.inject.Provider;

import kotlinx.coroutines.CoroutineScope;

/**
 * Implementation of the {@link KeyguardDisplayManager} that provides different display tracker
 * implementations depending on the system.
 *
 * For the driver SystemUI instance on a MUMD system, the default DisplayTrackerImpl is provided
 * in place of the MUMD display tracker so that when the driver is locked, the
 * KeyguardDisplayManager can be aware of all displays on the system, not just the driver displays.
 * In all other cases, the default display tracker provided by dagger will be used.
 */
@SysUISingleton
public class CarKeyguardDisplayManager extends KeyguardDisplayManager {
    public CarKeyguardDisplayManager(Context context,
            Lazy<NavigationBarController> navigationBarControllerLazy,
            DisplayTracker displayTracker, Executor mainExecutor, Executor uiBgExecutor,
            KeyguardDisplayManager.DeviceStateHelper deviceStateHelper,
            KeyguardStateController keyguardStateController,
            ConnectedDisplayKeyguardPresentationFactory connectedDisplayKeyguardPresentationFactory,
            Provider<ShadeDisplaysRepository> shadeDisplaysRepositoryProvider,
            @Application CoroutineScope appScope, boolean isWallpaperPresentationEnabled,
            DisplayRepository displayRepository) {
        super(context, navigationBarControllerLazy, displayTracker, mainExecutor, uiBgExecutor,
                deviceStateHelper, keyguardStateController,
                connectedDisplayKeyguardPresentationFactory, shadeDisplaysRepositoryProvider,
                appScope, isWallpaperPresentationEnabled, displayRepository);
    }
}
