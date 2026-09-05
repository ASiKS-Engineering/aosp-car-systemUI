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
package com.android.systemui.car.systembar.aaosstudio;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

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
 * A CarSystemBarElementController for handling AAOS Studio interactions.
 */
public class AaosStudioButtonController extends CarSystemBarButtonController  {

    private static final String AAOS_STUDIO_PACKAGE_NAME = "com.android.aaos.studio";

    private final Context mContext;

    @AssistedInject
    public AaosStudioButtonController(@Assisted CarSystemBarButton button,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            UserTracker userTracker, EventDispatcher eventDispatcher,
            ButtonSelectionStateController buttonSelectionStateController) {
        super(button, disableController, stateController, userTracker, eventDispatcher,
                buttonSelectionStateController);
        mContext = button.getContext();
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<CarSystemBarButton,
                    AaosStudioButtonController> {
    }

    @Override
    protected boolean shouldBeVisible() {
        return isPackageInstalled(mContext);
    }

    private boolean isPackageInstalled(Context context) {
        PackageManager packageManager = context.getPackageManager();
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(AAOS_STUDIO_PACKAGE_NAME, 0);
            return packageInfo != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false; // Package not found
        } catch (Exception e) {
            return false;
        }
    }
}
