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

package com.android.systemui.car.hvac;

import static com.android.systemui.car.hvac.HvacConstants.HVAC_SYSTEM_BAR_NAMES;
import static com.android.systemui.car.hvac.HvacConstants.OVERLAY_TYPE_HVAC_PANEL;

import android.content.Context;

import com.android.systemui.car.shared.R;
import com.android.systemui.car.window.OverlayViewController;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;

import java.util.Arrays;
import java.util.List;

import javax.inject.Named;

/** Dagger module for HVAC UI. */
@Module
public abstract class HvacUiModule {

    /** Provides the list of system bar names that the HVAC panel should register with. */
    @Provides
    @Named(HVAC_SYSTEM_BAR_NAMES)
    static List<String> provideHvacSystemBarNames(Context context) {
        return Arrays.asList(context.getResources().getStringArray(
                R.array.config_registerHvacDragCloseListener));
    }

    @Binds
    @IntoMap
    @StringKey(OVERLAY_TYPE_HVAC_PANEL)
    abstract OverlayViewController bindHvacPanelOverlayViewController(
            HvacPanelOverlayViewController controller);
}
