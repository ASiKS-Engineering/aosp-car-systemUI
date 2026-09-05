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

import com.android.car.scalableui.model.Event;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.dagger.SysUISingleton;

import java.io.PrintWriter;
import java.util.List;

import javax.inject.Inject;

/**
 * Shell command to inject ScalableUI events into the system.
 * Usage: adb shell cmd statusbar carsysui-dispatch-event [eventId] [tokens]
 */
@SysUISingleton
public class ScalableUIEventDispatcherCommand extends CarSystemUIShellCommand {
    private final EventDispatcher mEventDispatcher;

    @Inject
    public ScalableUIEventDispatcherCommand(EventDispatcher eventDispatcher) {
        mEventDispatcher = eventDispatcher;
    }

    @Override
    public String getCommandName() {
        return "carsysui-dispatch-event";
    }

    @Override
    public void execute(PrintWriter pw, List<String> args) {
        if (args == null || args.isEmpty()) {
            pw.println("Must specify eventId");
            return;
        }

        String eventId = args.get(0);
        Event.Builder event = new Event.Builder(eventId);
        if (args.size() > 1) {
            event.addTokensFromString(args.get(1));
        }

        mEventDispatcher.executeEvent(event.build());
    }

    @Override
    public void help(PrintWriter pw) {
        pw.println("Usage: adb shell cmd statusbar " + getCommandName()
                + " [eventId] [tokens]");
    }
}
