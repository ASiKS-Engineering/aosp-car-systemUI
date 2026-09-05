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

package com.android.systemui.car.systembar.extension

import com.android.systemui.car.systembar.base.CarSystemBarViewControllerFactory
import com.android.systemui.car.systembar.base.CarSystemBarViewControllerImpl
import com.android.systemui.car.systembar.base.CarSystemBarViewSupplier
import com.android.systemui.car.systembar.base.CarSystemBarViewSupplierUsingLayout
import com.android.systemui.car.systembar.base.CarSystemBarWindowSupplier
import com.android.systemui.car.systembar.base.CarSystemBarWindowSupplierUsingLayout
import com.android.systemui.car.systembar.standard.R
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

/** Dagger module for the system bars controlled by Extension Panel updates. */
@Module
abstract class ExtensionSystemBarModule {

    @Binds
    @IntoMap
    @StringKey("nav")
    abstract fun bindNavCarSystemBarViewFactory(
        factory: CarSystemBarViewControllerImpl.Factory
    ): CarSystemBarViewControllerFactory<*>

    @Binds
    @IntoMap
    @StringKey("status")
    abstract fun bindStatusCarSystemBarViewFactory(
        factory: CarSystemBarViewControllerImpl.Factory
    ): CarSystemBarViewControllerFactory<*>

    companion object {
        @Provides
        @IntoMap
        @StringKey("nav")
        fun provideNavCarSystemBarViewSupplier(): CarSystemBarViewSupplier {
            return CarSystemBarViewSupplierUsingLayout(
                R.layout.car_bottom_system_bar,
                R.layout.car_bottom_system_bar_unprovisioned
            )
        }

        @Provides
        @IntoMap
        @StringKey("nav")
        fun provideNavCarSystemBarWindowSupplier(): CarSystemBarWindowSupplier {
            return CarSystemBarWindowSupplierUsingLayout(
                com.android.systemui.res.R.layout.navigation_bar_window,
                R.id.car_bottom_bar_window
            )
        }

        @Provides
        @IntoMap
        @StringKey("status")
        fun provideStatusCarSystemBarViewSupplier(): CarSystemBarViewSupplier {
            return CarSystemBarViewSupplierUsingLayout(
                R.layout.car_top_system_bar,
                R.layout.car_top_system_bar_unprovisioned
            )
        }

        @Provides
        @IntoMap
        @StringKey("status")
        fun provideStatusCarSystemBarWindowSupplier(): CarSystemBarWindowSupplier {
            return CarSystemBarWindowSupplierUsingLayout(
                com.android.systemui.res.R.layout.navigation_bar_window,
                R.id.car_top_bar_window
            )
        }
    }
}
