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

package com.android.systemui.car.wm.taskview;

import static android.view.WindowManager.TRANSIT_TO_FRONT;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.WindowConfiguration;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;
import android.util.Slog;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.car.wm.CarSystemUIProxyImpl;
import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.taskview.TaskViewTransitions;
import com.android.wm.shell.transition.Transitions;

import dagger.Lazy;

import java.util.List;

import javax.inject.Inject;

/**
 * This class handles the extra transitions work pertaining to shell transitions. This class only
 * works when shell transitions are enabled.
 */
@WMSingleton
public final class RemoteCarTaskViewTransitions implements Transitions.TransitionHandler {
    // TODO(b/359584498): Add unit tests for this class.
    private static final String TAG = "CarTaskViewTransit";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    private final Transitions mTransitions;
    private final Context mContext;
    private final Lazy<CarSystemUIProxyImpl> mCarSystemUIProxy;
    private final TaskViewTransitions mTaskViewTransitions;

    private IBinder mLastReorderedTransitionInHandleRequest;

    @Inject
    public RemoteCarTaskViewTransitions(Transitions transitions,
            Lazy<CarSystemUIProxyImpl> carSystemUIProxy,
            Context context,
            TaskViewTransitions taskViewTransitions) {
        mTransitions = transitions;
        mContext = context;
        mCarSystemUIProxy = carSystemUIProxy;
        mTaskViewTransitions = taskViewTransitions;

        if (Transitions.ENABLE_SHELL_TRANSITIONS) {
            mTransitions.addHandler(this);
        } else {
            Slog.e(TAG,
                    "Not initializing RemoteCarTaskViewTransitions, as shell transitions are "
                            + "disabled");
        }
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        if (request.getTriggerTask() == null) {
            return null;
        }

        WindowContainerTransaction wct = null;
        // TODO(b/333923667): Plumb some API and get the host activity from CarSystemUiProxyImpl
        //  on a per taskview basis and remove the ACTIVITY_TYPE_HOME check.
        if (isHome(request.getTriggerTask())
                && TransitionUtil.isOpeningType(request.getType())) {
            wct = reorderEmbeddedTasksToTop(
                    request.getTriggerTask().displayId, /* includeOtherTasksAboveHome= */false);
            mLastReorderedTransitionInHandleRequest = transition;
        }

        // TODO(b/333923667): Think of moving this to CarUiPortraitSystemUI instead.
        if (mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_CAR_SPLITSCREEN_MULTITASKING)) {
            if (isHome(request.getTriggerTask())
                    && TransitionUtil.isOpeningType(request.getType())
                    && isInFullScreenMode(request.getTriggerTask())) {
                Slog.e(TAG, "A non-home task (" + request.getTriggerTask().taskId + ") "
                        + "shouldn't appear in fullscreen mode on automotive split-screen.");
                // TODO(b/333923667): Need a recovery. Either reparent this task to the relevant
                //  root task + bring home to top or just terminate this task.
            }
        }
        return wct;
    }

    private static boolean isHome(ActivityManager.RunningTaskInfo taskInfo) {
        return taskInfo.getActivityType() == WindowConfiguration.ACTIVITY_TYPE_HOME;
    }

    private static boolean isInFullScreenMode(ActivityManager.RunningTaskInfo taskInfo) {
        return taskInfo.getWindowingMode() == WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
    }

    private static boolean isInMultiWindowMode(ActivityManager.RunningTaskInfo taskInfo) {
        return taskInfo.getWindowingMode() == WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
    }

    private WindowContainerTransaction reorderEmbeddedTasksToTop(int endDisplayId,
            boolean includeOtherTasksAboveHome) {
        WindowContainerTransaction wct = new WindowContainerTransaction();
        boolean reorderedEmbeddedTasks = false;
        for (int i = mCarSystemUIProxy.get().getAllTaskViews().size() - 1; i >= 0; i--) {
            // TODO(b/359586295): Handle restarting of tasks if required.
            ActivityManager.RunningTaskInfo task =
                    mCarSystemUIProxy.get().getAllTaskViews().valueAt(i).getTaskInfo();
            if (task == null) continue;
            if (task.displayId != endDisplayId) continue;
            if (DBG) {
                Slog.d(TAG, "Adding transition work to bring the embedded " + task.topActivity
                        + " to top");
            }
            wct.reorder(task.token, /* onTop= */true);
            reorderedEmbeddedTasks = true;
        }
        if (reorderedEmbeddedTasks) {
            return includeOtherTasksAboveHome ? reorderOtherTasks(wct, endDisplayId) : wct;
        }
        return null;
    }

    private WindowContainerTransaction reorderOtherTasks(WindowContainerTransaction wct,
            int displayId) {
        // TODO(b/376380746): Remove using ActivityTaskManager to get the tasks once the task
        //  repository has been implemented in shell
        List<ActivityManager.RunningTaskInfo> tasks = ActivityTaskManager.getInstance().getTasks(
                Integer.MAX_VALUE);
        boolean aboveHomeTask = false;
        // Iterate in bottom to top manner
        for (int i = tasks.size() - 1; i >= 0; i--) {
            ActivityManager.RunningTaskInfo task = tasks.get(i);
            if (task.getDisplayId() != displayId) continue;
            // Skip embedded tasks which are running in multi window mode
            if (mTaskViewTransitions.isTaskViewTask(task) && isInMultiWindowMode(task)) continue;
            if (isHome(task)) {
                aboveHomeTask = true;
                continue;
            }
            if (!aboveHomeTask) continue;
            // Only the tasks which are after the home task and not running in windowing mode
            // multi window are left
            if (DBG) {
                Slog.d(TAG, "Adding transition work to bring the other task " + task.topActivity
                        + " after home to top");
            }
            wct.reorder(task.token, /* onTop= */true);
        }
        return wct;
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        if (mLastReorderedTransitionInHandleRequest != transition) {
            // This is to handle the case where when some activity on top of home goes away by
            // pressing back, a handleRequest is not sent for the home due to which the home
            // comes to the top and embedded tasks become invisible. Only do this when home is
            // coming to the top due to opening type transition. Note that a new transition will
            // be sent out for each home activity if the TransitionInfo.Change contains multiple
            // home activities.
            for (TransitionInfo.Change chg : info.getChanges()) {
                if (chg.getTaskInfo() != null && isHome(chg.getTaskInfo())
                        && TransitionUtil.isOpeningType(chg.getMode())) {
                    WindowContainerTransaction wct = reorderEmbeddedTasksToTop(
                            chg.getEndDisplayId(), /* includeOtherTasksAboveHome= */true);
                    if (wct != null) {
                        mTransitions.startTransition(TRANSIT_TO_FRONT, wct, /* handler= */null);
                    }
                }
            }
        }
        mLastReorderedTransitionInHandleRequest = null;
        return false;
    }
}
