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
package com.android.systemui.car.debug

import com.android.systemui.car.flags.FlagManager
import com.android.systemui.dagger.SysUISingleton
import java.io.PrintWriter
import javax.inject.Inject

/**
 * Shell command to dump [Flag] managed by [FlagManager]
 *
 * Usage: adb shell cmd statusbar carsysui-dump-flag
 * Usage: adb shell cmd statusbar carsysui-dump-flag scalableUi
 * Usage: adb shell cmd statusbar carsysui-dump-flag scalableUi enableAnimationEndEvent
 */
@SysUISingleton
class FlagDumpCommand
@Inject constructor(val flagManager: FlagManager) : CarSystemUIShellCommand() {
    override fun getCommandName(): String {
        return "carsysui-dump-flag"
    }

    override fun execute(pw: PrintWriter, args: List<String>) {
        if (args.isEmpty()) {
            pw.println(flagManager)
        } else {
            args.forEach { str ->
                val flag = flagManager.findFlag(str)
                flag?.let {
                    pw.println("${it.flagMethodName} is ${flagManager.isEnabled(it)}")
                } ?: pw.println("Unknown flag: $str")
            }
        }
    }

    override fun help(pw: PrintWriter) {
        pw.println("Usage: adb shell cmd statusbar $commandName + [flag]+")
    }
}
