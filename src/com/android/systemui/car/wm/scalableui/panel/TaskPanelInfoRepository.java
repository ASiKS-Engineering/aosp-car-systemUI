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

package com.android.systemui.car.wm.scalableui.panel;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.scalableui.model.Variant;
import com.android.systemui.dagger.qualifiers.UiBackground;
import com.android.wm.shell.dagger.WMSingleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

@WMSingleton
public class TaskPanelInfoRepository {
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final Map<String, LinkedHashMap<Integer, ActivityManager.RunningTaskInfo>>
            mPanelTaskMap = new HashMap<>();

    // Special map to keep track of the last running task on a panel that is now empty for the
    // purpose of restart
    // TODO(b/440364117): remove once task ordering is consistent
    @GuardedBy("mLock")
    private final Map<String, ActivityManager.RunningTaskInfo>
            mLastVanishedTaskInfo = new HashMap<>();

    @GuardedBy("mLock")
    private final Set<TaskPanelChangeListener> mListeners = new ArraySet<>();
    private final Executor mUiBackgroundExecutor;
    private final PanelUtils mPanelUtils;


    @Inject
    public TaskPanelInfoRepository(@UiBackground Executor executor, PanelUtils panelUtils) {
        mUiBackgroundExecutor = executor;
        mPanelUtils = panelUtils;
    }

    /**
     * Add a change listener for all panels.
     */
    public void addChangeListener(TaskPanelChangeListener listener) {
        synchronized (mLock) {
            mListeners.add(listener);
        }
    }

    /**
     * Remove a change listener for all panels.
     */
    public void removeChangeListener(TaskPanelChangeListener listener) {
        synchronized (mLock) {
            mListeners.remove(listener);
        }
    }

    /**
     * Query if a specific package is currently visible on any panel.
     */
    public boolean isPackageVisible(String packageName) {
        synchronized (mLock) {
            for (String panelId : mPanelTaskMap.keySet()) {
                ActivityManager.RunningTaskInfo taskInfo = getTopVisibleTaskOnPanel(panelId);
                if (taskInfo == null) {
                    continue;
                }
                if (taskInfo.topActivity != null
                        && Objects.equals(taskInfo.topActivity.getPackageName(), packageName)) {
                    return isPanelVisible(panelId);
                }
            }
        }
        return false;
    }

    /**
     * Query if a specific package is currently visible on a specific display.
     */
    public boolean isPackageVisibleOnDisplay(String packageName, int displayId) {
        synchronized (mLock) {
            for (String panelId : mPanelTaskMap.keySet()) {
                ActivityManager.RunningTaskInfo taskInfo = getTopVisibleTaskOnPanel(panelId);
                if (taskInfo == null) {
                    continue;
                }
                if (taskInfo.topActivity != null
                        && Objects.equals(taskInfo.topActivity.getPackageName(), packageName)
                        && taskInfo.displayId == displayId) {
                    return isPanelVisible(panelId);
                }
            }
        }
        return false;
    }

    /**
     * Query if a specific component is currently visible.
     */
    public boolean isComponentVisible(ComponentName componentName) {
        synchronized (mLock) {
            for (String panelId : mPanelTaskMap.keySet()) {
                ActivityManager.RunningTaskInfo taskInfo = getTopVisibleTaskOnPanel(panelId);
                if (taskInfo == null) {
                    continue;
                }
                if (Objects.equals(taskInfo.topActivity, componentName)) {
                    return isPanelVisible(panelId);
                }
            }
        }
        return false;
    }

    /**
     * Query if a specific component is currently visible on a specific display.
     */
    public boolean isComponentVisibleOnDisplay(ComponentName componentName, int displayId) {
        synchronized (mLock) {
            for (String panelId : mPanelTaskMap.keySet()) {
                ActivityManager.RunningTaskInfo taskInfo = getTopVisibleTaskOnPanel(panelId);
                if (taskInfo == null) {
                    continue;
                }
                if (Objects.equals(taskInfo.topActivity, componentName)
                        && taskInfo.displayId == displayId) {
                    return isPanelVisible(panelId);
                }
            }
        }
        return false;
    }

    /**
     * Query if a specific panel is currently visible.
     */
    private boolean isPanelVisible(String panelId) {
        Variant currentVariant = mPanelUtils.getCurrentVariant(panelId);
        if (currentVariant == null) {
            return false;
        }
        return currentVariant.isVisible();
    }

    void onTaskAppearedOnPanel(String panelId, ActivityManager.RunningTaskInfo taskInfo) {
        synchronized (mLock) {
            if (!mPanelTaskMap.containsKey(panelId)) {
                mPanelTaskMap.put(panelId, new LinkedHashMap<>());
            }
            mPanelTaskMap.get(panelId).put(taskInfo.taskId, taskInfo);
            List<ComponentName> changedComponents = getChangedComponents(panelId);
            notifyTaskPanelChangeListeners(panelId, changedComponents);

            if (!mPanelTaskMap.get(panelId).isEmpty()) {
                mLastVanishedTaskInfo.remove(panelId);
            }
        }
    }

    void onTaskChangedOnPanel(String panelId, ActivityManager.RunningTaskInfo taskInfo) {
        synchronized (mLock) {
            if (!mPanelTaskMap.containsKey(panelId)) {
                return;
            }
            ActivityManager.RunningTaskInfo oldTask = mPanelTaskMap.get(panelId).remove(
                    taskInfo.taskId);
            mPanelTaskMap.get(panelId).put(taskInfo.taskId, taskInfo);
            if (oldTask == null
                    || !Objects.equals(oldTask.topActivity, taskInfo.topActivity)
                    || isTaskVisible(oldTask) != isTaskVisible(taskInfo)) {
                List<ComponentName> changedComponents = getChangedComponents(panelId);

                notifyTaskPanelChangeListeners(panelId, changedComponents);
            }

            if (!mPanelTaskMap.get(panelId).isEmpty()) {
                mLastVanishedTaskInfo.remove(panelId);
            }
        }
    }

    void onTaskVanishedOnPanel(String panelId, ActivityManager.RunningTaskInfo taskInfo) {
        synchronized (mLock) {
            if (!mPanelTaskMap.containsKey(panelId)) {
                return;
            }
            ActivityManager.RunningTaskInfo removed = mPanelTaskMap.get(panelId).remove(
                    taskInfo.taskId);
            if (removed == null) {
                return;
            }

            List<ComponentName> changedComponents = getChangedComponents(panelId);
            notifyTaskPanelChangeListeners(panelId, changedComponents);

            if (mPanelTaskMap.get(panelId).isEmpty()) {
                mLastVanishedTaskInfo.put(panelId, removed);
            }
        }
    }

    /**
     * Notify if the top task on any panel has changed.
     */
    private void notifyTaskPanelChangeListeners(String panelId,
            List<ComponentName> changedComponentName) {
        synchronized (mLock) {
            mListeners.forEach(listener -> mUiBackgroundExecutor.execute(
                    () -> listener.onTopTaskOnPanelChanged(panelId, changedComponentName)));
        }
    }

    @Nullable
    private ActivityManager.RunningTaskInfo getTopVisibleTaskOnPanel(String panelId) {
        synchronized (mLock) {
            LinkedHashMap<Integer, ActivityManager.RunningTaskInfo> map = mPanelTaskMap.get(
                    panelId);
            if (map == null || map.isEmpty()) {
                return null;
            }
            for (Map.Entry<Integer, ActivityManager.RunningTaskInfo> entry :
                    map.reversed().entrySet()) {
                if (entry.getValue() != null && isTaskVisible(entry.getValue())) {
                    return entry.getValue();
                }
            }
            return null;
        }
    }

    private boolean isTaskVisible(ActivityManager.RunningTaskInfo task) {
        return task.isVisible && task.isRunning && !task.isSleeping;
    }

    private List<ComponentName> getChangedComponents(String panelId) {
        synchronized (mLock) {
            List<ComponentName> changedComponents = new ArrayList<>();
            for (ActivityManager.RunningTaskInfo info : mPanelTaskMap.get(panelId).values()) {
                if (info.topActivity != null) {
                    changedComponents.add(info.topActivity);
                }
            }
            return changedComponents;
        }
    }

    // TODO(b/440364117): remove once task ordering is consistent
    @Nullable
    ActivityManager.RunningTaskInfo getLastTopTaskOnPanel(@NonNull String panelId) {
        synchronized (mLock) {
            if (mPanelTaskMap.containsKey(panelId) && !mPanelTaskMap.get(panelId).isEmpty()) {
                return mPanelTaskMap.get(panelId).lastEntry().getValue();
            }
            return mLastVanishedTaskInfo.get(panelId);
        }
    }

    public interface TaskPanelChangeListener {
        /**
         * Notify the top task on a panel has changed.
         * @param panelId the id of the panel that has a new top task
         * @param changedComponentNames a list of components who's visibility may have changed
         */
        void onTopTaskOnPanelChanged(String panelId, List<ComponentName> changedComponentNames);
    }
}
