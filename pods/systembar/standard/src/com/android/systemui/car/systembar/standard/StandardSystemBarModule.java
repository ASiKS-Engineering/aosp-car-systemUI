/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.car.systembar.standard;

import com.android.systemui.car.systembar.SystemBarConstants;
import com.android.systemui.car.systembar.base.CarSystemBarViewControllerFactory;
import com.android.systemui.car.systembar.base.CarSystemBarViewControllerImpl;
import com.android.systemui.car.systembar.base.CarSystemBarViewSupplier;
import com.android.systemui.car.systembar.base.CarSystemBarViewSupplierUsingLayout;
import com.android.systemui.car.systembar.base.CarSystemBarWindowSupplier;
import com.android.systemui.car.systembar.base.CarSystemBarWindowSupplierUsingLayout;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;

@Module
public abstract class StandardSystemBarModule {
    /** Injects CarSystemBarViewController for TOP_BAR_NAME */
    @Binds
    @IntoMap
    @StringKey(SystemBarConstants.TOP_BAR_NAME)
    public abstract CarSystemBarViewControllerFactory<?> bindTopCarSystemBarViewFactory(
            CarSystemBarViewControllerImpl.Factory factory);

    /** Injects CarSystemBarViewController for BOTTOM_BAR_NAME */
    @Binds
    @IntoMap
    @StringKey(SystemBarConstants.BOTTOM_BAR_NAME)
    public abstract CarSystemBarViewControllerFactory<?> bindBottomCarSystemBarViewFactory(
            CarSystemBarViewControllerImpl.Factory factory);

    /** Injects CarSystemBarViewController for LEFT_BAR_NAME */
    @Binds
    @IntoMap
    @StringKey(SystemBarConstants.LEFT_BAR_NAME)
    public abstract CarSystemBarViewControllerFactory<?> bindLeftCarSystemBarViewFactory(
            CarSystemBarViewControllerImpl.Factory factory);

    /** Injects CarSystemBarViewController for RIGHT_BAR_NAME */
    @Binds
    @IntoMap
    @StringKey(SystemBarConstants.RIGHT_BAR_NAME)
    public abstract CarSystemBarViewControllerFactory<?> bindRightCarSystemBarViewFactory(
            CarSystemBarViewControllerImpl.Factory factory);

    @Provides
    @IntoMap
    @StringKey(SystemBarConstants.TOP_BAR_NAME)
    static CarSystemBarViewSupplier bindTopCarSystemBarViewSupplier() {
        return new CarSystemBarViewSupplierUsingLayout(
                R.layout.car_top_system_bar,
                R.layout.car_top_system_bar_unprovisioned);
    }

    @Provides
    @IntoMap
    @StringKey(SystemBarConstants.TOP_BAR_NAME)
    static CarSystemBarWindowSupplier bindTopCarSystemBarWindowSupplier() {
        return new CarSystemBarWindowSupplierUsingLayout(
                com.android.systemui.res.R.layout.navigation_bar_window,
                R.id.car_top_bar_window);
    }

    @Provides
    @IntoMap
    @StringKey(SystemBarConstants.LEFT_BAR_NAME)
    static CarSystemBarViewSupplier bindLeftCarSystemBarViewSupplier() {
        return new CarSystemBarViewSupplierUsingLayout(
                R.layout.car_left_system_bar,
                R.layout.car_left_system_bar_unprovisioned);
    }

    @Provides
    @IntoMap
    @StringKey(SystemBarConstants.LEFT_BAR_NAME)
    static CarSystemBarWindowSupplier bindLeftCarSystemBarWindowSupplier() {
        return new CarSystemBarWindowSupplierUsingLayout(
                com.android.systemui.res.R.layout.navigation_bar_window,
                R.id.car_left_bar_window);
    }

    @Provides
    @IntoMap
    @StringKey(SystemBarConstants.RIGHT_BAR_NAME)
    static CarSystemBarViewSupplier bindRightCarSystemBarViewSupplier() {
        return new CarSystemBarViewSupplierUsingLayout(
                R.layout.car_right_system_bar,
                R.layout.car_right_system_bar_unprovisioned);
    }

    @Provides
    @IntoMap
    @StringKey(SystemBarConstants.RIGHT_BAR_NAME)
    static CarSystemBarWindowSupplier bindRightCarSystemBarWindowSupplier() {
        return new CarSystemBarWindowSupplierUsingLayout(
                com.android.systemui.res.R.layout.navigation_bar_window,
                R.id.car_right_bar_window);
    }

    @Provides
    @IntoMap
    @StringKey(SystemBarConstants.BOTTOM_BAR_NAME)
    static CarSystemBarViewSupplier bindBottomCarSystemBarViewSupplier() {
        return new CarSystemBarViewSupplierUsingLayout(
                R.layout.car_bottom_system_bar,
                R.layout.car_bottom_system_bar_unprovisioned);
    }

    @Provides
    @IntoMap
    @StringKey(SystemBarConstants.BOTTOM_BAR_NAME)
    static CarSystemBarWindowSupplier bindBottomCarSystemBarWindowSupplier() {
        return new CarSystemBarWindowSupplierUsingLayout(
                com.android.systemui.res.R.layout.navigation_bar_window,
                R.id.car_bottom_bar_window);
    }
}
