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

import com.android.systemui.statusbar.commandline.Command;

/**
 * Base class for a shell command to be registered for CarSystemUI debugging.
 */
public abstract class CarSystemUIShellCommand implements Command {
    /**
     * The command string to be registered as `adb shell cmd statusbar [commandName]`.
     */
    public abstract String getCommandName();
}
