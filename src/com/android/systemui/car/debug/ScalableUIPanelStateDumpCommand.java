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

import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.PanelState;
import com.android.car.scalableui.panel.Panel;
import com.android.systemui.dagger.SysUISingleton;

import java.io.PrintWriter;
import java.util.List;

import javax.inject.Inject;

/**
 * Shell command to dump {@link PanelState} saved in {@link StateManager},
 *
 * <p> {@link Panel}: Represents the actual visual states of a window or surface with its
 * configurations.
 * {@link PanelState}: Represents the calculated states maintained by StateManager.
 * The {@link PanelState} contains desired values and will sync to {@link Panel} eventually.
 *
 * <p> Usage: adb shell cmd statusbar carsysui-dump-panelstates
 * Usage: adb shell cmd statusbar carsysui-dump-panelstates app_panel
 * Usage: adb shell cmd statusbar carsysui-dump-panelstates app_panel suw_panel
 */
@SysUISingleton
public class ScalableUIPanelStateDumpCommand extends CarSystemUIShellCommand {

    @Inject
    public ScalableUIPanelStateDumpCommand() {
    }

    @Override
    public String getCommandName() {
        return "carsysui-dump-panelstates";
    }

    @Override
    public void execute(PrintWriter pw, List<String> args) {
        if (args == null || args.isEmpty()) {
            pw.println("Current Panel States:");
            StateManager.dumpPanelStates(pw);
        } else {
            args.forEach(panelId -> {
                PanelState panelState = StateManager.getPanelState(panelId);
                if (panelState == null) {
                    pw.println("Panel no found for " + panelId);
                } else {
                    pw.println(panelState.toShortString());
                }
            });
        }

    }

    @Override
    public void help(PrintWriter pw) {
        pw.println("Usage: adb shell cmd statusbar " + getCommandName() + " [panelId]+");
    }
}
