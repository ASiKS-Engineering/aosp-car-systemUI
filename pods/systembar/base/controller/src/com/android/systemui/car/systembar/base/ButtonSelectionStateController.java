/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.car.systembar.base;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.window.DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityTaskManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.PanelState;
import com.android.systemui.car.flags.FlagManager;
import com.android.systemui.car.wm.scalableui.ScalableUIUtils;
import com.android.systemui.car.wm.scalableui.panel.TaskPanelInfoRepository;
import com.android.systemui.dagger.SysUISingleton;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CarSystemBarButtons can optionally have selection state that toggles certain visual indications
 * based on whether the active application on screen is associated with it. This is basically a
 * similar concept to a radio button group.
 *
 * This class controls the selection state of CarSystemBarButtons that have opted in to have such
 * selection state-dependent visual indications.
 */
@SysUISingleton
public class ButtonSelectionStateController {
    private static final String TAG = ButtonSelectionStateController.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;

    private final Set<CarSystemBarButton> mRegisteredViews = new HashSet<>();

    protected final Context mContext;
    protected final TaskPanelInfoRepository mTaskPanelInfoRepository;
    private final FlagManager mFlagManager;
    protected ButtonMap<String> mButtonsByCategory = new ButtonMap<>();
    protected ButtonMap<String> mButtonsByPackage = new ButtonMap<>();
    protected ButtonMap<ComponentName> mButtonsByComponentName = new ButtonMap<>();
    protected HashSet<CarSystemBarButton> mSelectedButtons;
    protected HashSet<CarSystemBarButton> mSelectedButtonsForPanelApp;
    protected HashSet<CarSystemBarButton> mSelectedButtonsForPanelVisibility;

    private final TaskPanelInfoRepository.TaskPanelChangeListener mTaskPanelListener =
            (panelId, changedComponentNames) -> panelTaskChanged(Collections.singletonList(panelId),
                    changedComponentNames);

    private final StateManager.PanelStateObserver mPanelStateObserver =
            new StateManager.PanelStateObserver() {
                @Override
                public void onBeforePanelStateChanged(Set<String> changedPanelIds,
                        Map<String, PanelState> panelStates) {
                    if (DEBUG) {
                        StringBuilder panelStatesString = new StringBuilder();
                        changedPanelIds.stream().map(panelStates::get).filter(
                                Objects::nonNull).forEach(
                                    s -> panelStatesString.append(s.toShortString()).append("\n"));
                        Log.d(TAG, "onBeforePanelStateChanged: changedPanelIds="
                                + changedPanelIds + " panelStates=" + panelStatesString);
                    }
                    panelVisibilityChanged(panelStates);
                    // also trigger task change since it depends on panel visibility
                    panelTaskChanged(changedPanelIds, /* changedComponentNames= */ null);
                }

                @Override
                public void onPanelStateChanged(Set<String> changedPanelIds,
                        Map<String, PanelState> panelStates) {
                    // handled opportunistically in before method
                }
            };

    public ButtonSelectionStateController(Context context,
            TaskPanelInfoRepository taskPanelInfoRepository,
            FlagManager flagManager) {
        mContext = context;
        mTaskPanelInfoRepository = taskPanelInfoRepository;
        mSelectedButtons = new HashSet<>();
        mSelectedButtonsForPanelApp = new HashSet<>();
        mSelectedButtonsForPanelVisibility = new HashSet<>();
        mFlagManager = flagManager;
        if (ScalableUIUtils.isScalableUIEnabled(mContext, mFlagManager)
                && mTaskPanelInfoRepository != null) {
            mTaskPanelInfoRepository.addChangeListener(mTaskPanelListener);
            StateManager.getInstance().addPanelStateObserver(mPanelStateObserver);
        }
    }

    /**
     * Iterate through a view looking for CarSystemBarButton and add it to the controller if it
     * opted in to be highlighted when the active application is associated with it.
     *
     * @param v the View that may contain CarFacetButtons
     */
    protected void addAllButtonsWithSelectionState(View v) {
        if (v instanceof CarSystemBarButton) {
            if (((CarSystemBarButton) v).hasSelectionState()) {
                addButtonWithSelectionState((CarSystemBarButton) v);
            }
        } else if (v instanceof ViewGroup viewGroup) {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                addAllButtonsWithSelectionState(viewGroup.getChildAt(i));
            }
        }
    }

    /** Removes a button from the button maps. */
    protected void removeButton(CarSystemBarButton button) {
        mButtonsByCategory.values().forEach(set -> set.remove(button));
        mButtonsByPackage.values().forEach(set -> set.remove(button));
        mButtonsByComponentName.values().forEach(set -> set.remove(button));
        mSelectedButtons.remove(button);
        synchronized (mRegisteredViews) {
            mRegisteredViews.remove(button);
        }
    }

    /**
     * This will unselect the currently selected CarSystemBarButton and determine which one should
     * be selected next. It does this by reading the properties on the CarSystemBarButton and
     * seeing if they are a match with the supplied StackInfo list.
     * The order of selection detection is ComponentName, PackageName then Category
     * They will then be compared with the supplied StackInfo list.
     * The StackInfo is expected to be supplied in order of recency and StackInfo will only be used
     * for consideration if it has the same displayId as the CarSystemBarButton.
     *
     * @param taskInfoList of the currently running application
     * @param validDisplay index of the valid display
     */
    protected void taskChanged(List<RootTaskInfo> taskInfoList, int validDisplay) {
        if (ScalableUIUtils.isScalableUIEnabled(mContext, mFlagManager)) {
            return;
        }
        RootTaskInfo validTaskInfo = null;

        for (RootTaskInfo taskInfo : taskInfoList) {
            // Find the first stack info with a topActivity in the primary display.
            // TODO: We assume that CarFacetButton will launch an app only in the primary display.
            // We need to extend the functionality to handle the multiple display properly.
            if (taskInfo.topActivity != null && taskInfo.displayAreaFeatureId == validDisplay) {
                validTaskInfo = taskInfo;
                break;
            }
        }

        if (validTaskInfo == null) {
            // No stack was found that was on the same display as the buttons thus return
            return;
        }
        int displayId = validTaskInfo.displayId;

        // Clear all registered views
        clearAllSelectedButtons(displayId);

        HashSet<CarSystemBarButton> selectedButtons = findSelectedButtons(validTaskInfo);

        if (selectedButtons != null) {
            selectedButtons.forEach(carSystemBarButton -> {
                if (carSystemBarButton.getDisplayId() == displayId) {
                    carSystemBarButton.setSelected(true);
                    mSelectedButtons.add(carSystemBarButton);
                }
            });
        }
    }

    /**
     * This will unselect the currently selected CarSystemBarButtons and determine which one should
     * be selected next. It does this by reading the properties on the CarSystemBarButton and
     * seeing if they are a match based on panel visibility and task visibility on panels.
     */
    protected void panelTaskChanged(@NonNull Collection<String> changedPanelIds,
            @Nullable List<ComponentName> changedComponentNames) {
        List<ComponentName> changedComponentNamesFinal;
        Set<String> changedPackageNames;
        if (changedComponentNames == null || changedComponentNames.isEmpty()) {
            mSelectedButtonsForPanelApp.clear();
            changedComponentNamesFinal = mButtonsByComponentName.keySet().stream().toList();
            changedPackageNames = mButtonsByPackage.keySet();
        } else {
            changedComponentNamesFinal = changedComponentNames;
            changedPackageNames = changedComponentNamesFinal.stream().map(
                    ComponentName::getPackageName).collect(Collectors.toSet());
            mSelectedButtonsForPanelApp.removeIf(button ->
                    !Collections.disjoint(button.getComponentNames(), changedComponentNamesFinal)
                            || !Collections.disjoint(Arrays.asList(button.getPackages()),
                            changedPackageNames));
        }

        changedComponentNamesFinal.forEach(componentName -> {
            mButtonsByComponentName.getOrDefault(componentName, new HashSet<>()).forEach(button -> {
                if (mTaskPanelInfoRepository.isComponentVisibleOnDisplay(componentName,
                        button.getDisplayId())) {
                    mSelectedButtonsForPanelApp.add(button);
                }
            });
        });

        changedPackageNames.forEach(packageName -> {
            mButtonsByPackage.getOrDefault(packageName, new HashSet<>()).forEach(button -> {
                if (mTaskPanelInfoRepository.isPackageVisibleOnDisplay(packageName,
                        button.getDisplayId())) {
                    mSelectedButtonsForPanelApp.add(button);
                }
            });
        });

        // TODO(b/409398038): handle categories for ScalableUI

        updatePanelButtonsSelection();
    }

    /**
     * Determine which CarSystemBarButtons should be selected based on the current panel state
     */
    protected void panelVisibilityChanged(Map<String, PanelState> panelStates) {
        mSelectedButtonsForPanelVisibility.clear();
        synchronized (mRegisteredViews) {
            mRegisteredViews.forEach(button -> {
                if (button.getPanelNames().length > 0
                        && shouldSelectButtonForPanelStates(button, panelStates)) {
                    mSelectedButtonsForPanelVisibility.add(button);
                }
            });
        }
        updatePanelButtonsSelection();
    }

    /**
     * When adding a button to this controller, check the current panel and task state to determine
     * if the button should be initially selected.
     */
    protected void selectForInitialPanelTaskState(CarSystemBarButton button) {
        if (button.getPanelNames().length > 0) {
            if (shouldSelectButtonForPanelStates(button, /* panelStates= */ null)) {
                mSelectedButtonsForPanelVisibility.add(button);
            } else {
                mSelectedButtonsForPanelVisibility.remove(button);
            }
        }

        boolean shouldSelectForApp = false;
        String[] packages = button.getPackages();
        for (String aPackage : packages) {
            if (mTaskPanelInfoRepository.isPackageVisibleOnDisplay(
                    aPackage, button.getDisplayId())) {
                shouldSelectForApp = true;
                break;
            }
        }
        List<ComponentName> componentNames = button.getComponentNames();
        for (ComponentName componentName : componentNames) {
            if (mTaskPanelInfoRepository.isComponentVisibleOnDisplay(
                    componentName,
                    button.getDisplayId())) {
                shouldSelectForApp = true;
                break;
            }
        }
        // TODO(b/409398038): handle categories for ScalableUI
        if (shouldSelectForApp) {
            mSelectedButtonsForPanelApp.add(button);
        } else {
            mSelectedButtonsForPanelApp.remove(button);
        }

        updatePanelButtonsSelection();
    }

    private boolean shouldSelectButtonForPanelStates(@NonNull CarSystemBarButton button,
            @Nullable Map<String, PanelState> panelStates) {
        if (button.getPanelNames().length == 0) {
            return false;
        }
        for (String panelString : button.getPanelNames()) {
            if (TextUtils.isEmpty(panelString)) {
                // not valid - don't select
                return false;
            }
            boolean invertVisibility = panelString.charAt(0) == '-';
            if (invertVisibility) {
                panelString = panelString.substring(1);
            }
            PanelState state;
            if (panelStates != null) {
                state = panelStates.get(panelString);
            } else {
                state = StateManager.getPanelState(panelString);
            }
            if (!isPanelVisible(state, button.getDisplayId()) ^ invertVisibility) {
                return false;
            }
        }
        return true;
    }

    private boolean isPanelVisible(PanelState state, int displayId) {
        if (state == null) {
            return false;
        }
        return state.getDisplayId() == displayId
                && state.getCurrentVariant() != null
                && state.getCurrentVariant().isVisible();
    }

    protected void updatePanelButtonsSelection() {
        mContext.getMainExecutor().execute(() -> {
            synchronized (mRegisteredViews) {
                mRegisteredViews.forEach(button -> {
                    if (mSelectedButtonsForPanelVisibility.contains(button)
                            || mSelectedButtonsForPanelApp.contains(button)) {
                        button.setSelected(true);
                        mSelectedButtons.add(button);
                    } else {
                        button.setSelected(false);
                        mSelectedButtons.remove(button);
                    }
                });
            }
        });
    }

    protected void clearAllSelectedButtons(int displayId) {
        synchronized (mRegisteredViews) {
            mRegisteredViews.forEach(carSystemBarButton -> {
                if (carSystemBarButton.getDisplayId() == displayId) {
                    carSystemBarButton.setSelected(false);
                }
            });
        }
        mSelectedButtons.clear();
    }

    /**
     * Defaults to Display.DEFAULT_DISPLAY when no parameter is provided for the validDisplay.
     *
     * @param taskInfoList of the currently running application
     */
    protected void taskChanged(List<RootTaskInfo> taskInfoList) {
        taskChanged(taskInfoList, FEATURE_DEFAULT_TASK_CONTAINER);
    }

    /**
     * Add navigation button to this controller if it uses selection state.
     */
    private void addButtonWithSelectionState(CarSystemBarButton carSystemBarButton) {
        synchronized (mRegisteredViews) {
            if (mRegisteredViews.contains(carSystemBarButton)) {
                return;
            }
            String[] categories = carSystemBarButton.getCategories();
            for (String category : categories) {
                mButtonsByCategory.add(category, carSystemBarButton);
            }

            String[] packages = carSystemBarButton.getPackages();
            for (String aPackage : packages) {
                mButtonsByPackage.add(aPackage, carSystemBarButton);
            }
            List<ComponentName> componentNames = carSystemBarButton.getComponentNames();
            for (ComponentName componentName : componentNames) {
                mButtonsByComponentName.add(componentName, carSystemBarButton);
            }

            mRegisteredViews.add(carSystemBarButton);
        }

        if (ScalableUIUtils.isScalableUIEnabled(mContext, mFlagManager)
                    && mTaskPanelInfoRepository != null) {
            selectForInitialPanelTaskState(carSystemBarButton);
        }
    }

    private HashSet<CarSystemBarButton> findSelectedButtons(RootTaskInfo validTaskInfo) {
        ComponentName topActivity = getTopActivity(validTaskInfo);
        if (topActivity == null) return null;

        String packageName = topActivity.getPackageName();

        HashSet<CarSystemBarButton> selectedButtons = mButtonsByComponentName.get(topActivity);
        if (selectedButtons == null) {
            selectedButtons = mButtonsByPackage.get(packageName);
        }
        if (selectedButtons == null) {
            String category = getPackageCategory(packageName);
            if (category != null) {
                selectedButtons = mButtonsByCategory.get(category);
            }
        }

        return selectedButtons;
    }

    @Nullable
    protected ComponentName getTopActivity(RootTaskInfo validTaskInfo) {
        // Window mode being WINDOW_MODE_MULTI_WINDOW implies TaskView might be visible on the
        // display. In such cases, topActivity reported by validTaskInfo will be the one hosted in
        // TaskView and not necessarily the main activity visible on display. Thus we should get
        // rootTaskInfo instead.
        if (validTaskInfo.getWindowingMode() == WINDOWING_MODE_MULTI_WINDOW) {
            try {
                RootTaskInfo rootTaskInfo =
                        ActivityTaskManager.getService().getRootTaskInfoOnDisplay(
                                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_UNDEFINED,
                                validTaskInfo.displayId);
                return SystemBarUtil.INSTANCE.getTaskComponentName(rootTaskInfo);
            } catch (RemoteException e) {
                Log.e(TAG, "findSelectedButtons: Failed getting root task info", e);
            }
        } else {
            return SystemBarUtil.INSTANCE.getTaskComponentName(validTaskInfo);
        }

        return null;
    }

    private String getPackageCategory(String packageName) {
        PackageManager pm = mContext.getPackageManager();
        Set<String> supportedCategories = mButtonsByCategory.keySet();
        for (String category : supportedCategories) {
            Intent intent = new Intent();
            intent.setPackage(packageName);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(category);
            List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);
            if (list.size() > 0) {
                // Cache this package name into ButtonsByPackage map, so we won't have to query
                // all categories next time this package name shows up.
                mButtonsByPackage.put(packageName, mButtonsByCategory.get(category));
                return category;
            }
        }
        return null;
    }

    // simple multi-map
    private static class ButtonMap<K> extends HashMap<K, HashSet<CarSystemBarButton>> {

        public boolean add(K key, CarSystemBarButton value) {
            if (containsKey(key)) {
                return get(key).add(value);
            }
            HashSet<CarSystemBarButton> set = new HashSet<>();
            set.add(value);
            put(key, set);
            return true;
        }
    }
}
