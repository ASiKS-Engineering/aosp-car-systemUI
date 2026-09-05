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

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.TaskInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.Event;
import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.car.scalableui.model.PanelState;
import com.android.car.scalableui.model.Transition;
import com.android.car.scalableui.model.Variant;
import com.android.car.scalableui.panel.PanelPool;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.wm.shell.dagger.WMSingleton;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import javax.inject.Inject;

/**
 * This utility class provides helper methods for {@link TaskPanel}.
 */
@WMSingleton
public class PanelUtils {
    private static final String TAG = PanelUtils.class.getSimpleName();
    private final Context mContext;
    private final UserManager mUserManager;

    @Inject
    public PanelUtils(Context context) {
        mContext = context;
        mUserManager = mContext.getSystemService(UserManager.class);
    }

    /**
     * Checks if any panel in the pool handles the given root task ID.
     *
     * @param rootTaskId The root task ID to check.
     * @return True if a panel with the given root task ID exists in the pool, false otherwise.
     */
    public boolean handles(int rootTaskId) {
        return getTaskPanel(panel -> panel.getRootTaskId() == rootTaskId) != null;
    }

    /**
     * Retrieves a {@link TaskPanel} that satisfies the given {@link Predicate}.
     *
     * @param predicate The predicate to test against potential {@link TaskPanel} instances.
     * @return The matching {@link TaskPanel}, or null if none is found.
     */
    @Nullable
    public TaskPanel getTaskPanel(Predicate<TaskPanel> predicate) {
        return (TaskPanel) PanelPool.getInstance().getPanel(
                p -> (p instanceof TaskPanel tp) && predicate.test(tp));
    }

    /**
     * Retrieves a {@link DecorPanel} that satisfies the given {@link Predicate}.
     *
     * @param predicate The predicate to test against potential {@link DecorPanel} instances.
     * @return The matching {@link DecorPanel}, or null if none is found.
     */
    @Nullable
    public DecorPanel getDecorPanel(Predicate<DecorPanel> predicate) {
        return (DecorPanel) PanelPool.getInstance().getPanel(
                p -> (p instanceof DecorPanel decorPanel) && predicate.test(decorPanel));
    }

    /**
     * Retrieves a {@link SysUIPanel} that satisfies the given {@link Predicate}.
     *
     * @param predicate The predicate to test against potential {@link SysUIPanel} instances.
     * @return The matching {@link SysUIPanel}, or null if none is found.
     */
    @Nullable
    public SysUIPanel getSysUiPanel(Predicate<SysUIPanel> predicate) {
        return (SysUIPanel) PanelPool.getInstance().getPanel(
                p -> (p instanceof SysUIPanel sysUiPanel) && predicate.test(sysUiPanel));
    }

    /**
     * Retrieve the current variant set on the PanelState for a particular panel id.
     */
    @Nullable
    public Variant getCurrentVariant(String panelId) {
        PanelState panelState = StateManager.getPanelState(panelId);
        if (panelState == null) {
            return null;
        }
        return panelState.getCurrentVariant();
    }

    /**
     * Checks if the user is unlocked.
     */
    public boolean isUserUnlocked() {
        int userId = CarSystemUIUserUtil.isSecondaryMUMDSystemUI()
                ? mContext.getUserId()
                : ActivityManager.getCurrentUser();

        return mUserManager != null && mUserManager.isUserUnlocked(userId);
    }

    /**
     * Helper method to safely extract the ComponentName from a TaskInfo.
     * It checks topActivity, realActivity, baseActivity, and finally the baseIntent
     * in that order to find a valid component.
     *
     * @param taskInfo The TaskInfo object.
     * @return The ComponentName associated with the task, or null if it cannot be determined.
     */
    @Nullable
    public ComponentName getTaskComponentName(@Nullable TaskInfo taskInfo) {
        if (taskInfo == null) {
            return null;
        }

        // 1. Try topActivity
        if (taskInfo.topActivity != null) {
            return taskInfo.topActivity;
        }

        // 2. Try realActivity
        if (taskInfo.realActivity != null) {
            return taskInfo.realActivity;
        }

        // 3. Try baseActivity (the original attempt)
        if (taskInfo.baseActivity != null) {
            return taskInfo.baseActivity;
        }

        // 4. Try getting the component from the baseIntent
        ComponentName component = taskInfo.baseIntent.getComponent();
        if (component != null) {
            return component;
        }

        // If none of the above worked, return null
        Log.w(TAG, "Could not determine component for taskId: " + taskInfo.taskId);
        return null;
    }

    /**
     * Helper method to safely extract the package name from a TaskInfo.
     * See {@link #getTaskComponentName} for ordering of retrieving component. If not present,
     * attempt to fall back to baseIntent package.
     *
     * @param taskInfo The TaskInfo object.
     * @return The package name associated with the task, or null if it cannot be determined.
     */
    @Nullable
    public String getTaskPackageName(@Nullable TaskInfo taskInfo) {
        if (taskInfo == null) {
            return null;
        }

        ComponentName taskComponentName = getTaskComponentName(taskInfo);
        if (taskComponentName != null) {
            return taskComponentName.getPackageName();
        }

        // If component is null, the package might be set explicitly on the intent
        String intentPackage = taskInfo.baseIntent.getPackage();
        if (intentPackage != null) {
            return intentPackage;
        }

        // If none of the above worked, return null
        Log.w(TAG, "Could not determine package name for taskId: " + taskInfo.taskId);
        return null;
    }

    /**
     * Parses persistent activity {@link ComponentName}s from package names specified in the
     * configuration.
     */
    @NonNull
    public Set<ComponentName> parsePersistentActivitiesFromPackages(
            @NonNull PanelControllerMetadata panelControllerMetadata, @NonNull String configName) {
        Set<ComponentName> set = new HashSet<>();
        if (!panelControllerMetadata.hasConfiguration(configName)) {
            return set;
        }
        List<String> list = panelControllerMetadata.getListConfiguration(configName);
        if (list == null) {
            String value = panelControllerMetadata.getStringConfiguration(configName);
            set.addAll(getComponentNamesFromPackage(value));
        } else {
            for (String item : list) {
                set.addAll(getComponentNamesFromPackage(item));
            }
        }
        return set;
    }

    @NonNull
    private Set<ComponentName> getComponentNamesFromPackage(@Nullable String packageName) {
        Set<ComponentName> set = new HashSet<>();
        if (packageName == null) {
            return set;
        }
        PackageManager pm = mContext.getPackageManager();
        try {
            // User may not be unlocked when parsing package info - use MATCH_DIRECT_BOOT_AWARE
            // and MATCH_DIRECT_BOOT_UNAWARE to retrieve activities regardless of user state.
            PackageInfo packageInfo = pm.getPackageInfoAsUser(packageName,
                    PackageManager.GET_ACTIVITIES | PackageManager.MATCH_DIRECT_BOOT_AWARE
                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                    ActivityManager.getCurrentUser());
            if (packageInfo != null && packageInfo.activities != null) {
                for (ActivityInfo ai : packageInfo.activities) {
                    set.add(ai.getComponentName());
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Fail to find package Info for " + packageName + ", e=" + e);
        }
        return set;
    }

    /**
     * Retrieves the {@link Transition} for a given {@link Event} applied to a specific panelId
     * without triggering any changes to the state.
     */
    @Nullable
    public Transition peekPanelTransitionForEvent(@NonNull Event event, @NonNull String panelId) {
        PanelState state = StateManager.getPanelState(panelId);
        if (state == null) {
            Log.e(TAG, "panel state is null");
            return null;
        }
        return state.getTransition(event);
    }
}
