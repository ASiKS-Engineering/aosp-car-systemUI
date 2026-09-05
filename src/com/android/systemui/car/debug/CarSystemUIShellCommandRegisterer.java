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

import android.os.Build;

import com.android.systemui.CoreStartable;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.process.ProcessWrapper;
import com.android.systemui.statusbar.commandline.CommandRegistry;

import java.util.Set;

import javax.inject.Inject;

/**
 * CoreStartable class to register all instances of {@link CarSystemUIShellCommand} as a
 * statusbar shell command. This will only be registered on debug builds for the system user
 * process of SystemUI.
 */
@SysUISingleton
public class CarSystemUIShellCommandRegisterer implements CoreStartable {
    private final CommandRegistry mCommandRegistry;
    private final ProcessWrapper mProcessWrapper;
    private final Set<CarSystemUIShellCommand> mCommands;

    @Inject
    public CarSystemUIShellCommandRegisterer(CommandRegistry commandRegistry,
            ProcessWrapper processWrapper,
            Set<CarSystemUIShellCommand> commands) {
        mCommandRegistry = commandRegistry;
        mProcessWrapper = processWrapper;
        mCommands = commands;
    }

    @Override
    public void start() {
        if (!Build.IS_DEBUGGABLE || !mProcessWrapper.isSystemUser()) {
            // only enable on debug builds for system user
            return;
        }

        mCommands.forEach(command -> mCommandRegistry.registerCommand(command.getCommandName(),
                ()-> command));
    }
}
