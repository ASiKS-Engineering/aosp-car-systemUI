/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_ASSISTANT;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;

import android.app.ActivityManager;
import android.car.app.CarActivityManager;
import android.util.Log;
import android.view.SurfaceControl;
import android.window.TaskCreationParams;
import android.window.WindowContainerTransaction;

import com.android.internal.annotations.VisibleForTesting;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.taskview.TaskViewBase;
import com.android.wm.shell.taskview.TaskViewTaskController;
import com.android.wm.shell.taskview.TaskViewTransitions;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * A mediator to {@link RemoteCarTaskViewServerImpl} that encapsulates the root task related logic.
 */
public final class RootTaskMediator implements ShellTaskOrganizer.TaskListener {
    private static final String TAG = RootTaskMediator.class.getSimpleName();

    private final int mDisplayId;
    private final boolean mIsLaunchRoot;
    private final int[] mActivityTypes;
    private final ShellTaskOrganizer mShellTaskOrganizer;
    private final TaskViewTaskController mTaskViewTaskShellPart;
    private final TaskViewBase mTaskViewClientPart;
    private final CarActivityManager mCarActivityManager;
    private final LinkedHashMap<Integer, TaskRecord> mTaskStack = new LinkedHashMap<>();
    private final TaskViewTransitions mTransitions;
    private final Optional<WindowDecorViewModel> mWindowDecorViewModelOptional;

    private static class TaskRecord {
        private ActivityManager.RunningTaskInfo mTaskInfo;
        private SurfaceControl mLeash;

        private TaskRecord(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
            mTaskInfo = taskInfo;
            mLeash = leash;
        }
    }

    private ActivityManager.RunningTaskInfo mRootTask;

    public RootTaskMediator(
            int displayId,
            boolean isLaunchRoot,
            boolean embedHomeTask,
            boolean embedRecentsTask,
            boolean embedAssistantTask,
            ShellTaskOrganizer shellTaskOrganizer,
            TaskViewTaskController taskViewTaskShellPart,
            TaskViewBase taskViewClientPart,
            CarActivityManager carActivityManager,
            TaskViewTransitions transitions,
            Optional<WindowDecorViewModel> windowDecorViewModelOptional) {
        mDisplayId = displayId;
        mIsLaunchRoot = isLaunchRoot;
        mActivityTypes = createActivityArray(embedHomeTask, embedRecentsTask, embedAssistantTask);
        mShellTaskOrganizer = shellTaskOrganizer;
        mTaskViewTaskShellPart = taskViewTaskShellPart;
        mTaskViewClientPart = taskViewClientPart;
        mCarActivityManager = carActivityManager;
        mTransitions = transitions;
        mWindowDecorViewModelOptional = windowDecorViewModelOptional;

        TaskCreationParams params = new TaskCreationParams.Builder()
                .setDisplayId(displayId)
                .setWindowingMode(WINDOWING_MODE_MULTI_WINDOW)
                .build();
        mShellTaskOrganizer.createTask(params, this);
    }

    @VisibleForTesting
    static int[] createActivityArray(boolean embedHomeTask, boolean embedRecentsTask,
            boolean embedAssistantTask) {
        int size = 1; // 1 for ACTIVITY_TYPE_STANDARD
        if (embedHomeTask) {
            size++;
        }
        if (embedRecentsTask) {
            size++;
        }
        if (embedAssistantTask) {
            size++;
        }
        int[] activityTypeArray = new int[size];
        int index = 0;
        activityTypeArray[index] = ACTIVITY_TYPE_STANDARD;
        index++;
        if (embedHomeTask) {
            activityTypeArray[index] = ACTIVITY_TYPE_HOME;
            index++;
        }
        if (embedRecentsTask) {
            activityTypeArray[index] = ACTIVITY_TYPE_RECENTS;
            index++;
        }
        if (embedAssistantTask) {
            activityTypeArray[index] = ACTIVITY_TYPE_ASSISTANT;
        }
        return activityTypeArray;
    }

    int getDisplayId() {
        return mDisplayId;
    }

    void release() {
        clearRootTask();
    }

    @Override
    public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
        ShellTaskOrganizer.TaskListener.super.onTaskAppeared(taskInfo, leash);

        // The first call to onTaskAppeared() is always for the root-task.
        if (mRootTask == null && !taskInfo.hasParentTask()) {
            mRootTask = taskInfo;
            WindowContainerTransaction wct = null;
            if (mIsLaunchRoot) {
                // Prepare the wct for the launch root task
                wct = new WindowContainerTransaction();
                wct.setLaunchRoot(taskInfo.token,
                                new int[]{WINDOWING_MODE_UNDEFINED},
                                mActivityTypes)
                        .reorder(taskInfo.token, true);
            }

            // Attach the root task with the taskview shell part.
            // Do not trigger onTaskAppeared on shell part directly as it is no longer the
            // correct entry point for a new task in the task view.
            // Shell part will eventually trigger onTaskAppeared on the client as well.
            mTransitions.startRootTask(mTaskViewTaskShellPart, taskInfo, leash, wct);
            return;
        }

        // For all the children tasks, just update the client part and no notification/update is
        // sent to the shell part
        mTaskViewClientPart.onTaskAppeared(taskInfo, leash);
        mTaskStack.put(taskInfo.taskId, new TaskRecord(taskInfo, leash));

        if (mIsLaunchRoot) {
            mCarActivityManager.onTaskAppeared(taskInfo, leash);
        }

        // Show WindowDecor for display compat apps
        if (mWindowDecorViewModelOptional.isPresent()) {
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            mWindowDecorViewModelOptional.get().onTaskOpening(taskInfo, leash, t, t);
            t.apply();
        }
    }

    @Override
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        ShellTaskOrganizer.TaskListener.super.onTaskInfoChanged(taskInfo);
        if (mRootTask != null
                && mRootTask.taskId == taskInfo.taskId) {
            mTaskViewTaskShellPart.onTaskInfoChanged(taskInfo);
            return;
        }

        // For all the children tasks, just update the client part and no notification/update is
        // sent to the shell part
        mTaskViewClientPart.onTaskInfoChanged(taskInfo);
        if (mIsLaunchRoot) {
            mCarActivityManager.onTaskInfoChanged(taskInfo);
        }
        if (taskInfo.isVisible && mTaskStack.containsKey(taskInfo.taskId)) {
            // Remove the task and insert again so that it jumps to the end of
            // the queue.
            TaskRecord task = mTaskStack.remove(taskInfo.taskId);
            task.mTaskInfo = taskInfo;
            mTaskStack.put(taskInfo.taskId, task);
        }
        if (mWindowDecorViewModelOptional.isPresent()) {
            mWindowDecorViewModelOptional.get().onTaskInfoChanged(taskInfo);
        }
    }

    @Override
    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        ShellTaskOrganizer.TaskListener.super.onTaskVanished(taskInfo);
        if (mRootTask != null
                && mRootTask.taskId == taskInfo.taskId) {
            mTaskViewTaskShellPart.onTaskVanished(taskInfo);
            return;
        }

        // For all the children tasks, just update the client part and no notification/update is
        // sent to the shell part
        mTaskViewClientPart.onTaskVanished(taskInfo);
        if (mIsLaunchRoot) {
            mCarActivityManager.onTaskVanished(taskInfo);
        }
        mTaskStack.remove(taskInfo.taskId);
        if (mWindowDecorViewModelOptional.isPresent()) {
            mWindowDecorViewModelOptional.get().onTaskVanished(taskInfo);
            mWindowDecorViewModelOptional.get().destroyWindowDecoration(taskInfo);
        }
    }

    @Override
    public void onBackOnTaskRoot(ActivityManager.RunningTaskInfo taskInfo,
            boolean isFromBackPress, boolean isOptInOnBackInvoked,
            boolean hasOpaqueSibling) {
        ShellTaskOrganizer.TaskListener.super.onBackOnTaskRoot(taskInfo,
                isFromBackPress, isOptInOnBackInvoked, hasOpaqueSibling);
        if (mTaskStack.size() == 1) {
            Log.i(TAG, "Cannot remove last task from root task, display=" + mDisplayId);
            return;
        }
        if (mTaskStack.size() == 0) {
            Log.i(TAG, "Root task is empty, do nothing, display=" + mDisplayId);
            return;
        }

        ActivityManager.RunningTaskInfo topTask = getTopTaskInLaunchRootTask();
        WindowContainerTransaction wct = new WindowContainerTransaction();
        // removeTask() will trigger onTaskVanished which will remove the task locally
        // from mLaunchRootStack
        wct.removeTask(topTask.token);

        mTransitions.startInstantTransition(TRANSIT_CLOSE, wct);
    }

    @Override
    public void attachChildSurfaceToTask(int taskId, SurfaceControl.Builder b) {
        if (mRootTask != null
                && mRootTask.taskId == taskId) {
            mTaskViewTaskShellPart.attachChildSurfaceToTask(taskId, b);
            return;
        }
        SurfaceControl taskSurface = findTaskSurface(taskId);
        if (taskSurface != null) {
            b.setParent(taskSurface);
        }
    }

    @Override
    public void reparentChildSurfaceToTask(int taskId, SurfaceControl sc,
            SurfaceControl.Transaction t) {
        if (mRootTask != null
                && mRootTask.taskId == taskId) {
            mTaskViewTaskShellPart.reparentChildSurfaceToTask(taskId, sc, t);
            return;
        }
        SurfaceControl taskSurface = findTaskSurface(taskId);
        if (taskSurface != null) {
            t.reparent(sc, taskSurface);
        }
    }

    private SurfaceControl findTaskSurface(int taskId) {
        TaskRecord task = mTaskStack.get(taskId);
        if (task == null) {
            Log.e(TAG, "There is no surface for taskId=" + taskId);
            return null;
        }
        return task.mLeash;
    }

    private void clearRootTask() {
        if (mRootTask == null) {
            Log.w(TAG, "Unable to clear launch root task because it is not created.");
            return;
        }
        if (mIsLaunchRoot) {
            WindowContainerTransaction wct = new WindowContainerTransaction();
            wct.setLaunchRoot(mRootTask.token, null, null);
            mTransitions.startInstantTransition(TRANSIT_CHANGE, wct);
        }
        // Should run on shell's executor
        mShellTaskOrganizer.deleteTask(mRootTask.token);
        mShellTaskOrganizer.removeListener(this);
        mTaskStack.clear();
        mRootTask = null;
    }

    private ActivityManager.RunningTaskInfo getTopTaskInLaunchRootTask() {
        if (mTaskStack.isEmpty()) {
            return null;
        }
        ActivityManager.RunningTaskInfo topTask = null;
        Iterator<TaskRecord> iterator = mTaskStack.values().iterator();
        while (iterator.hasNext()) {
            topTask = iterator.next().mTaskInfo;
        }
        return topTask;
    }

    public boolean isLaunchRoot() {
        return mIsLaunchRoot;
    }

    @VisibleForTesting
    ActivityManager.RunningTaskInfo getRootTask() {
        return mRootTask;
    }

    @VisibleForTesting
    List<ActivityManager.RunningTaskInfo> getTaskStack() {
        List<ActivityManager.RunningTaskInfo> tasks = new ArrayList<>(mTaskStack.size());
        Iterator<TaskRecord> iterator = mTaskStack.values().iterator();
        while (iterator.hasNext()) {
            tasks.add(iterator.next().mTaskInfo);
        }
        return tasks;
    }

    @Override
    public String toString() {
        Iterator<TaskRecord> iterator = mTaskStack.values().iterator();
        StringBuilder stringBuilder = new StringBuilder("{" + "displayId=" + mDisplayId + " ,");
        stringBuilder.append("taskStack=[\n");
        ActivityManager.RunningTaskInfo topTask;
        while (iterator.hasNext()) {
            topTask = iterator.next().mTaskInfo;
            stringBuilder.append(" {" + " taskId=").append(topTask.taskId).append(",").append(
                    " baseActivity=").append(topTask.baseActivity).append("}\n");
        }
        stringBuilder.append("]\n}\n");
        return stringBuilder.toString();
    }
}
