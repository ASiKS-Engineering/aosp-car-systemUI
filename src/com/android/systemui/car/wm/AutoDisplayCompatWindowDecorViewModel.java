/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.systemui.car.wm;

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.systemui.car.Flags.displayCompatibilityCaptionBar;
import static com.android.systemui.car.displaycompat.CarDisplayCompatUtils.getPackageName;
import static com.android.systemui.car.displaycompat.CarDisplayCompatUtils.requiresDisplayCompat;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.car.content.pm.CarPackageManager;
import android.content.Context;
import android.os.Handler;
import android.view.SurfaceControl;

import com.android.systemui.car.CarServiceProvider;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.shared.annotations.ShellBackgroundThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.FocusTransitionObserver;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.windowdecor.CarWindowDecorViewModel;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier;

/**
 * Implementation of {@link CarWindowDecorViewModel} that adds
 * {@link com.android.wm.shell.windowdecor.CarWindowDecoration} to the tasks that require
 * display compatibility.
 * TODO(b/409134330): Remove when new auto decor solution is merged and approved.
 */
public class AutoDisplayCompatWindowDecorViewModel extends CarWindowDecorViewModel {
    @Nullable
    private CarPackageManager mCarPackageManager;

    public AutoDisplayCompatWindowDecorViewModel(Context context,
            @NonNull @ShellMainThread Handler handler,
            @NonNull Transitions transitions,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellBackgroundThread ShellExecutor bgExecutor,
            ShellInit shellInit,
            ShellTaskOrganizer taskOrganizer,
            DisplayController displayController,
            DisplayInsetsController displayInsetsController,
            SyncTransactionQueue syncQueue,
            FocusTransitionObserver focusTransitionObserver,
            WindowDecorViewHostSupplier<WindowDecorViewHost> windowDecorViewHostSupplier,
            CarServiceProvider carServiceProvider) {
        super(context, handler, transitions, mainExecutor, bgExecutor, shellInit, taskOrganizer,
                displayController, displayInsetsController, syncQueue, focusTransitionObserver,
                windowDecorViewHostSupplier);
        if (displayCompatibilityCaptionBar()) {
            carServiceProvider.addListener(
                    car -> mCarPackageManager = car.getCarManager(CarPackageManager.class));
        }
    }

    @Override
    public boolean onTaskOpening(ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl taskSurface, SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        if (!displayCompatibilityCaptionBar()) {
            return false;
        }
        return super.onTaskOpening(taskInfo, taskSurface, startT, finishT);
    }

    @Override
    public void onTaskChanging(
            ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl taskSurface,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        if (!displayCompatibilityCaptionBar()) {
            return;
        }
        super.onTaskChanging(taskInfo, taskSurface, startT, finishT);
    }

    @Override
    protected boolean shouldShowWindowDecor(ActivityManager.RunningTaskInfo taskInfo) {
        return displayCompatibilityCaptionBar()
                && taskInfo != null
                && requiresDisplayCompat(
                getPackageName(taskInfo), taskInfo.userId, mCarPackageManager)
                && taskInfo.displayId == DEFAULT_DISPLAY;
    }
}
