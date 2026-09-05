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

import com.android.car.scalableui.model.PanelState
import com.android.car.scalableui.panel.Panel
import com.android.car.scalableui.panel.PanelPool
import com.android.systemui.dagger.SysUISingleton
import java.io.PrintWriter
import javax.inject.Inject

/**
 * Shell command to dump [Panel] saved in [PanelPool]
 *
 * [Panel]: Represents the actual visual state of a window or surface with its configurations.
 * [PanelState]: Represents the calculated states maintained by StateManager.
 * The [PanelState] contains desired values and will sync to [Panel] eventually.
 *
 * Usage: adb shell cmd statusbar carsysui-dump-panel
 * Usage: adb shell cmd statusbar carsysui-dump-panel app_panel
 * Usage: adb shell cmd statusbar carsysui-dump-panel app_panel suw_panel
 */
@SysUISingleton
class ScalableUIPanelDumpCommand @Inject constructor() : CarSystemUIShellCommand() {
    override fun getCommandName(): String {
        return "carsysui-dump-panel"
    }

    override fun execute(pw: PrintWriter, args: List<String>) {
        if (args.isEmpty()) {
            PanelPool.getInstance().forEach { panel -> panel.dump(pw) }
        } else {
            args.forEach { str ->
                val panel: Panel? = PanelPool.getInstance().getPanel(str)
                if (panel == null) {
                    pw.println("Panel $str not found")
                } else {
                    panel.dump(pw)
                }
            }
        }
    }

    override fun help(pw: PrintWriter) {
        pw.println("Usage: adb shell cmd statusbar $commandName + [panelId]+")
    }
}
