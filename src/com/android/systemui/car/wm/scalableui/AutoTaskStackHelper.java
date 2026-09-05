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

import static com.android.car.scalableui.model.TaskBehavior.TASK_PROPERTY_UNTRIMMABLE;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.PanelState;
import com.android.systemui.car.shared.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.dagger.WMSingleton;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

@WMSingleton
public final class AutoTaskStackHelper {
    private static final String TAG = AutoTaskStackHelper.class.getSimpleName();
    private static final String DELIMITER = ";";
    private final ShellTaskOrganizer mShellTaskOrganizer;
    private final Context mContext;
    private final Set<ComponentName> mNonTrimmableComponentSet;
    private final Map<String, ComponentName> mDefaultComponentsMap;

    @Inject
    public AutoTaskStackHelper(Context context, ShellTaskOrganizer shellTaskOrganizer) {
        mShellTaskOrganizer = shellTaskOrganizer;
        mContext = context;
        mNonTrimmableComponentSet = new HashSet<>();
        mDefaultComponentsMap = new HashMap<>();
        initUntrimmableTaskSet();
        initDefaultTaskMap();
    }

    /**
     * Reloads the untrimmable task set and default task map.
     */
    public void reloadTaskConfigs() {
        initUntrimmableTaskSet();
        initDefaultTaskMap();
    }

    /**
     * Checks if a given running task is trimmable.
     *
     * <p> A task is trimmable if it's configured in config_untrimmable_activities
     */
    private boolean isTrimmable(@NonNull ActivityManager.RunningTaskInfo task) {
        return !mNonTrimmableComponentSet.contains(task.baseActivity);
    }

    /**
     * Sets a task as trimmable or not - by default this will be true for tasks.
     */
    private void setTaskTrimmable(@NonNull ActivityManager.RunningTaskInfo task,
            boolean trimmable) {
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setTaskTrimmableFromRecents(task.token, trimmable);
        mShellTaskOrganizer.applyTransaction(wct);
    }

    /**
     * Retrieves the default {@link ComponentName} associated with a given ID.
     *
     * <p> The relationship is defined in config_default_activities.
     */
    @Nullable
    public ComponentName getDefaultIntent(String id) {
        return mDefaultComponentsMap.get(id);
    }

    /**
     * Initializes the default task map from the config_default_activities array.
     *
     * <p> The format of the array is as follows:
     * 1. panel_id;componentname
     * 2. panel_id;com.example.app/.activity
     */
    private void initDefaultTaskMap() {
        mDefaultComponentsMap.clear();
        String[] configStrings = mContext.getResources().getStringArray(
                R.array.config_default_activities);
        for (int i = configStrings.length - 1; i >= 0; i--) {
            String[] parts = configStrings[i].split(DELIMITER);
            if (parts.length == 2) {
                String key = parts[0].trim(); // Trim whitespace
                String value = parts[1].trim(); // Trim whitespace
                mDefaultComponentsMap.put(key, ComponentName.unflattenFromString(value));
            } else {
                // Handle cases where the split doesn't result in two parts (e.g., malformed input)
                Log.e(TAG, "Skipping malformed pair: " + configStrings[i]);
                // You could choose to throw an exception here, or just continue.
            }
        }
    }

    /**
     * Initializes the {@code mNonTrimmableComponentSet} tasks from the
     * config_untrimmable_activities string array resource.
     */
    private void initUntrimmableTaskSet() {
        mNonTrimmableComponentSet.clear();
        String[] componentNameStrings = mContext.getResources().getStringArray(
                R.array.config_untrimmable_activities);
        for (int i = componentNameStrings.length - 1; i >= 0; i--) {
            mNonTrimmableComponentSet.add(
                    ComponentName.unflattenFromString(componentNameStrings[i]));
        }
    }

    /**
     * Sets the given task as untrimmable if it is not already trimmable.
     *
     * <p>This method checks if the provided {@link ActivityManager.RunningTaskInfo}
     * is considered according to config_untrimmable_activities. If the task is *not*
     * trimmable, it explicitly sets the task's trimmable state to `false`.
     */
    public void setTaskUntrimmableIfNeeded(@NonNull String panelId,
            @NonNull ActivityManager.RunningTaskInfo taskInfo) {
        if (!isTrimmable(taskInfo)) {
            setTaskTrimmable(taskInfo, /* trimmable= */ false);
            return;
        }
        PanelState panelState = StateManager.getPanelState(panelId);
        if (panelState == null || panelState.getTaskBehavior() == null) {
            return;
        }
        String untrimmablePolicy = panelState.getTaskBehavior().getTaskProperties();
        if (TASK_PROPERTY_UNTRIMMABLE.equals(untrimmablePolicy)) {
            setTaskTrimmable(taskInfo, /* trimmable= */ false);
        }
    }
}
