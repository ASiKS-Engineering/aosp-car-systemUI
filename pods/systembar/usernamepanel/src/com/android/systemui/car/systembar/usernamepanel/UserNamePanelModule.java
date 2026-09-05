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

package com.android.systemui.car.systembar.usernamepanel;

import com.android.systemui.car.flexibleui.CarSystemBarElementController;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

@Module
public abstract class UserNamePanelModule {
    /** Injects UserNamePanelViewController */
    @Binds
    @IntoMap
    @ClassKey(UserNamePanelViewController.class)
    public abstract CarSystemBarElementController.Factory bindUserNamePanelViewController(
            UserNamePanelViewController.Factory factory);

    /** Injects UserNameTextViewController */
    @Binds
    @IntoMap
    @ClassKey(UserNameTextViewController.class)
    public abstract CarSystemBarElementController.Factory bindUserNameTextViewController(
            UserNameTextViewController.Factory factory);

    /** Injects UserNameImageViewController */
    @Binds
    @IntoMap
    @ClassKey(UserNameImageViewController.class)
    public abstract CarSystemBarElementController.Factory bindUserNameImageViewController(
            UserNameImageViewController.Factory factory);
}
