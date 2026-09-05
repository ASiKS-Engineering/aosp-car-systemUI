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

package com.android.systemui;

import static android.car.CarOccupantZoneManager.DISPLAY_TYPE_MAIN;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.Display;
import android.view.WindowManager;

import com.android.car.oem.tokens.Token;
import com.android.systemui.application.impl.SystemUIApplicationImpl;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.scene.shared.flag.SceneContainerFlag;

/**
 * Application class for CarSystemUI.
 */
public class CarSystemUIApplication extends SystemUIApplicationImpl {

    private boolean mIsVisibleBackgroundUserSysUI;

    public CarSystemUIApplication() {
        super();
        SceneContainerFlag.isEnabledOnVariant = false;
    }

    @Override
    public void onCreate() {
        mIsVisibleBackgroundUserSysUI = CarSystemUIUserUtil.isSecondaryMUMDSystemUI();
        super.onCreate();
        if (mIsVisibleBackgroundUserSysUI) {
            Car car = Car.createCar(this);
            if (car == null) {
                return;
            }
            CarOccupantZoneManager manager = (CarOccupantZoneManager) car.getCarManager(
                    Car.CAR_OCCUPANT_ZONE_SERVICE);
            if (manager != null) {
                CarOccupantZoneManager.OccupantZoneInfo info = manager.getMyOccupantZone();
                if (info != null) {
                    Display display = manager.getDisplayForOccupant(info, DISPLAY_TYPE_MAIN);
                    if (display != null) {
                        updateDisplay(display.getDisplayId());
                        startSystemUserServicesIfNeeded();
                    }
                }
            }
            car.disconnect();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        getTheme().applyStyle(com.android.systemui.res.R.style.Theme_SystemUI, true);
        getTheme().applyStyle(
                com.android.systemui.car.shared.R.style.CarSystemUIThemeOverlay, true);
        Token.applyOemTokenStyle(this);
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected boolean shouldStartSystemUserServices() {
        if (mIsVisibleBackgroundUserSysUI) {
            // visible background user SystemUI instances should start the same services as the
            // normal system user SystemUI instance.
            return true;
        }
        return super.shouldStartSystemUserServices();
    }

    @Override
    protected boolean shouldStartSecondaryUserServices() {
        if (mIsVisibleBackgroundUserSysUI) {
            // visible background user SystemUI instances should start the same services as the
            // normal system user SystemUI instance - this already includes the secondary user
            // services.
            return false;
        }
        return super.shouldStartSecondaryUserServices();
    }

    @Override
    public void attachBaseContext(Context base) {
        Token.applyOemTokenStyle(base);
        base.getTheme().applyStyle(
                com.android.systemui.car.shared.R.style.CarSystemUIThemeOverlay, true);
        super.attachBaseContext(base);
    }

    /**
     * A wrapper that ensures themes are applied and that any contexts derived from this context are
     * also wrapped.
     */
    private class ThemedContextWrapper extends ContextWrapper {
        ThemedContextWrapper(Context base) {
            super(base);
            // Apply the theme immediately upon wrapping
            applySystemUITheme(base);
        }

        @NonNull
        @Override
        public Context createWindowContext(int type, @Nullable Bundle options) {
            return new ThemedContextWrapper(super.createWindowContext(type, options));
        }

        @NonNull
        @Override
        public Context createWindowContext(@NonNull Display display, int type,
                @Nullable Bundle options) {
            return new ThemedContextWrapper(super.createWindowContext(display, type, options));
        }

        @NonNull
        @Override
        public Context createConfigurationContext(Configuration overrideConfiguration) {
            return new ThemedContextWrapper(
                    super.createConfigurationContext(overrideConfiguration));
        }

        @NonNull
        @Override
        public Context createDisplayContext(Display display) {
            return new ThemedContextWrapper(super.createDisplayContext(display));
        }

        @NonNull
        @Override
        public Context createContextAsUser(UserHandle user, int flags) {
            return new ThemedContextWrapper(super.createContextAsUser(user, flags));
        }
    }

    private Context applySystemUITheme(Context context) {
        context.getTheme().setTo(getTheme());
        context.getTheme().rebase();
        // If OEM tokens need to be applied to every derived context:
        Token.applyOemTokenStyle(context);
        return context;
    }

    @Override
    @NonNull
    public Context createContextAsUser(UserHandle user, @CreatePackageOptions int flags) {
        return new ThemedContextWrapper(super.createContextAsUser(user, flags));
    }

    @Override
    @NonNull
    public Context createWindowContext(@WindowManager.LayoutParams.WindowType int type,
            @Nullable Bundle options) {
        return applySystemUITheme(super.createWindowContext(type, options));
    }

    @Override
    @NonNull
    public Context createWindowContext(@NonNull Display display, int type,
            @Nullable Bundle options) {
        return applySystemUITheme(super.createWindowContext(display, type, options));
    }

    @Override
    public Context createConfigurationContext(Configuration overrideConfiguration) {
        return new ThemedContextWrapper(super.createConfigurationContext(overrideConfiguration));
    }

    @Override
    public Context createDisplayContext(Display display) {
        return new ThemedContextWrapper(super.createDisplayContext(display));
    }
}
