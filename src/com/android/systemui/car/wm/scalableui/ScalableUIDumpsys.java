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
package com.android.systemui.car.wm.scalableui;

import androidx.annotation.NonNull;

import com.android.car.scalableui.manager.StateManager;
import com.android.systemui.Dumpable;
import com.android.systemui.dump.DumpManager;
import com.android.wm.shell.dagger.WMSingleton;

import java.io.PrintWriter;

import javax.inject.Inject;

/**
 * Helper class to include relevant ScalableUI information into the dumpsys output.
 */
@WMSingleton
public class ScalableUIDumpsys implements Dumpable {
    private static final String TAG = "ScalableUIState";

    private final DumpManager mDumpManager;
    private boolean mInited = false;

    @Inject
    public ScalableUIDumpsys(DumpManager dumpManager) {
        mDumpManager = dumpManager;
    }

    /**
     * Register as a dumpable (if not already registered)
     */
    public void init() {
        if (mInited) {
            return;
        }
        mInited = true;
        mDumpManager.registerCriticalDumpable(TAG, this);
    }

    @Override
    public void dump(@NonNull PrintWriter pw, @NonNull String[] args) {
        StateManager.dumpPanelStates(pw);
    }
}
