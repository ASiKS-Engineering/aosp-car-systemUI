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

package com.android.systemui.car.debug;

import com.android.systemui.CoreStartable;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.Multibinds;

import java.util.Set;

/**
 * Dagger injection module for debug constructs of CarSystemUi.
 */
@Module
public abstract class CarSystemUIDebugModule {
    /** Inject CarSystemUIShellCommandRegisterer as a CoreStartable */
    @Binds
    @IntoMap
    @ClassKey(CarSystemUIShellCommandRegisterer.class)
    public abstract CoreStartable bindCarSystemUIShellCommandRegisterer(
            CarSystemUIShellCommandRegisterer registerer);


    /** Empty set for CarSystemBarShellCommands. */
    @Multibinds
    abstract Set<CarSystemUIShellCommand> bindEmptyShellCommandSet();

    /** Inject ScalableUIEventDispatcherCommand as a CarSystemUIShellCommand */
    @Binds
    @IntoSet
    public abstract CarSystemUIShellCommand bindEventDispatcherShellCommand(
            ScalableUIEventDispatcherCommand command);

    /** Inject ScalableUIPanelStateDumpCommand as a CarSystemUIShellCommand */
    @Binds
    @IntoSet
    public abstract CarSystemUIShellCommand bindPanelStateDumpCommand(
            ScalableUIPanelStateDumpCommand command);

    /** Inject ScalableUIPanelDumpCommand as a CarSystemUIShellCommand */
    @Binds
    @IntoSet
    public abstract CarSystemUIShellCommand bindScalableUIPanelDumpCommand(
            ScalableUIPanelDumpCommand command);

    /** Inject ScalableUIPanelDumpCommand as a CarSystemUIShellCommand */
    @Binds
    @IntoSet
    public abstract CarSystemUIShellCommand bindFlagDumpCommand(
            FlagDumpCommand command);
}
