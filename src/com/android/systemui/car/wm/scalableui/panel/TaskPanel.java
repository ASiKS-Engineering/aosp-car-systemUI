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

import static android.car.app.CarActivityManager.LAUNCH_BEHAVIOR_DEFAULT;
import static android.car.app.CarActivityManager.LAUNCH_BEHAVIOR_REMAIN_IN_SOURCE_ROOT_TASK;
import static android.car.app.CarActivityManager.LAUNCH_BEHAVIOR_REPARENT_TO_SOURCE_ROOT_TASK;
import static android.view.WindowInsets.Type.systemOverlays;

import static com.android.car.scalableui.model.Restart.RESTART_POLICY_DEFAULT;
import static com.android.car.scalableui.model.Restart.RESTART_POLICY_LAST;
import static com.android.car.scalableui.model.TaskBehavior.NEW_TASK_LAUNCH_POLICY_DEFAULT;
import static com.android.car.scalableui.model.TaskBehavior.NEW_TASK_LAUNCH_POLICY_REMAIN_IN_SOURCE;
import static com.android.car.scalableui.model.TaskBehavior.NEW_TASK_LAUNCH_POLICY_REPARENT_TO_SOURCE;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_TASK_PANEL_EMPTY_EVENT_ID;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.car.app.CarActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.View;
import android.window.WindowContainerToken;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.internal.dep.Trace;
import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.Corner;
import com.android.car.scalableui.model.Decor;
import com.android.car.scalableui.model.Event;
import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.car.scalableui.model.PanelState;
import com.android.car.scalableui.model.Restart;
import com.android.car.scalableui.model.Role;
import com.android.car.scalableui.model.Variant;
import com.android.car.scalableui.panel.Panel;
import com.android.car.scalableui.panel.PanelUpdatePublisher;
import com.android.car.scalableui.panel.TaskPanelController;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.flags.Flag;
import com.android.systemui.car.flags.FlagManager;
import com.android.systemui.car.wm.AutoCaptionBarViewControllerImpl;
import com.android.systemui.car.wm.scalableui.AutoTaskStackHelper;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.car.wm.scalableui.panel.controller.PanelControllerInitializer;
import com.android.systemui.car.wm.scalableui.panel.controller.TaskToolbarController;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.automotive.AutoCaptionController;
import com.android.wm.shell.automotive.AutoDecor;
import com.android.wm.shell.automotive.AutoDecorManager;
import com.android.wm.shell.automotive.AutoLayoutManager;
import com.android.wm.shell.automotive.AutoSurfaceTransaction;
import com.android.wm.shell.automotive.AutoSurfaceTransactionFactory;
import com.android.wm.shell.automotive.AutoTaskStackController;
import com.android.wm.shell.automotive.AutoTaskStackState;
import com.android.wm.shell.automotive.AutoTaskStackTransaction;
import com.android.wm.shell.automotive.RootTaskStack;
import com.android.wm.shell.automotive.RootTaskStackListener;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.annotations.ExternalMainThread;
import com.android.wm.shell.shared.annotations.ShellBackgroundThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A {@link RootTaskStack} based implementation of a {@link Panel}.
 */
@SuppressLint("MissingPermission")
public final class TaskPanel extends SysUIPanel {
    private static final String TAG = TaskPanel.class.getSimpleName();
    private static final long INITIAL_RETRY_DELAY_MS = 1000;
    private static final long CHECK_RESTART_SUCCESS_DELAY_MS = 500;
    private int mCurrentRetryCount = 0;

    // TODO(b/440364117): remove once task ordering is consistent
    private boolean mWasRootTaskPreviouslyNonEmpty = false;

    private static final boolean DEBUG = Build.isDebuggable();

    @NonNull
    private final AutoTaskStackController mAutoTaskStackController;
    @NonNull
    private final CarServiceProvider mCarServiceProvider;
    @NonNull
    private final Set<ComponentName> mPersistedActivities;
    @NonNull
    private final AutoTaskStackHelper mAutoTaskStackHelper;
    @NonNull
    private final AutoCaptionController mAutoCaptionController;
    @NonNull
    private final AutoCaptionBarViewControllerImpl mAutoCaptionBarViewControllerImpl;
    @NonNull
    private final TaskPanelInfoRepository mTaskPanelInfoRepository;
    @NonNull
    private final EventDispatcher mEventDispatcher;
    @NonNull
    private final PanelControllerInitializer mPanelControllerInitializer;
    @NonNull
    private final PanelUtils mPanelUtils;
    @NonNull
    private final AutoDecorManager mAutoDecorManager;
    @NonNull
    private final Context mContext;
    @Nullable
    private CarActivityManager mCarActivityManager;
    @Nullable
    private SurfaceControl mLeash;
    private boolean mIsLaunchRoot;
    @NonNull
    private Rect mSafeBounds = new Rect();
    @NonNull
    private Rect mTaskToolbarBounds = new Rect();
    @Nullable
    private RootTaskStack mRootTaskStack;
    @NonNull
    @VisibleForTesting
    final Map<String, AutoDecor> mExistingAutoDecors;
    @Nullable
    private String mTopTaskPackageName;
    @Nullable
    private TaskPanelController mTaskPanelController;
    @NonNull
    private final AutoLayoutManager mAutoLayoutManager;
    @NonNull
    private final AutoSurfaceTransactionFactory mAutoSurfaceTransactionFactory;
    @NonNull
    private final FlagManager mFlagManager;
    @NonNull
    private Rect mRelativeToolbarBounds = new Rect();

    /** Keeps track of the last known insets of the panel. */
    @Nullable
    private Rect[] mCurrentInsets;

    @AssistedInject
    public TaskPanel(AutoTaskStackController autoTaskStackController,
            @NonNull Context context,
            CarServiceProvider carServiceProvider,
            AutoTaskStackHelper autoTaskStackHelper,
            ShellTaskOrganizer shellTaskOrganizer,
            AutoCaptionController autoCaptionController,
            PanelUtils panelUtils,
            TaskPanelInfoRepository taskPanelInfoRepository,
            AutoDecorManager autoDecorManager,
            EventDispatcher dispatcher,
            PanelControllerInitializer panelControllerInitializer,
            AutoLayoutManager autoLayoutManager,
            @ExternalMainThread ShellExecutor mainExecutor,
            @ShellMainThread ShellExecutor shellMainExecutor,
            @ShellBackgroundThread ShellExecutor shellBgExecutor,
            AutoSurfaceTransactionFactory autoSurfaceTransactionFactory,
            Optional<PanelUpdatePublisher> panelUpdatePublisherOptional,
            FlagManager flagManager,
            @Assisted String id) {
        super(context, id, panelUpdatePublisherOptional, mainExecutor, shellMainExecutor,
                shellBgExecutor);
        mAutoTaskStackController = autoTaskStackController;
        mCarServiceProvider = carServiceProvider;
        mAutoTaskStackHelper = autoTaskStackHelper;
        mTaskPanelInfoRepository = taskPanelInfoRepository;
        mEventDispatcher = dispatcher;
        mFlagManager = flagManager;
        mPersistedActivities = new ArraySet<>();
        mPanelUtils = panelUtils;
        mAutoCaptionController = autoCaptionController;
        mAutoCaptionBarViewControllerImpl =
                new AutoCaptionBarViewControllerImpl(context, shellTaskOrganizer);
        mAutoDecorManager = autoDecorManager;
        mContext = context;
        mPanelControllerInitializer = panelControllerInitializer;
        mAutoLayoutManager = autoLayoutManager;
        mAutoSurfaceTransactionFactory = autoSurfaceTransactionFactory;
        mExistingAutoDecors = new HashMap<>();
    }

    /**
     * Initializes the panel with the RootTask. This must be called after the state has been
     * set.
     */
    @Override
    public void init() {
        super.init();
        mCarServiceProvider.addListener(
                car -> {
                    logIfDebuggable("On car connected:" + this);
                    mCarActivityManager = car.getCarManager(CarActivityManager.class);
                    trySetPersistentActivity();
                    trySetRootTaskLaunchBehavior();
                });

        mAutoTaskStackController.createRootTaskStack(getDisplayId(), getPanelId(),
                new RootTaskStackListener() {
                    @Override
                    public void onRootTaskStackAppeared(@NonNull RootTaskStack rootTaskStack) {
                        logIfDebuggable(getPanelId() + ", onRootTaskStackAppeared "
                                + rootTaskStack);
                        mRootTaskStack = rootTaskStack;
                        trySetPersistentActivity();
                        trySetRootTaskLaunchBehavior();
                        if (mIsLaunchRoot) {
                            mAutoTaskStackController.setDefaultRootTaskStackOnDisplay(
                                    getDisplayId(),
                                    getRootTaskId());
                        }

                        if (mFlagManager.isEnabled(Flag.DisplayCompatV2)) {
                            setupTaskToolbar(mRootTaskStack,
                                    getRelativeBounds(getTaskToolbarBounds(), getBounds()));
                        } else {
                            setupToolbarRegion();
                        }
                        setLeash(mRootTaskStack.getLeash());

                        if (mPanelUtils.isUserUnlocked()) {
                            reset();
                        }
                    }

                    @Override
                    public void onRootTaskStackInfoChanged(@NonNull RootTaskStack rootTaskStack) {
                        mRootTaskStack = rootTaskStack;
                        // TODO(b/440364117): move to onTaskVanished once ordering is consistent
                        if (isRootTaskEmpty() && mWasRootTaskPreviouslyNonEmpty) {
                            ActivityManager.RunningTaskInfo lastTask =
                                    mTaskPanelInfoRepository.getLastTopTaskOnPanel(getPanelId());
                            if (lastTask != null) {
                                mWasRootTaskPreviouslyNonEmpty = false;
                                logIfDebuggable("onRootTaskStackInfoChanged - " + getPanelId());
                                handlePanelEmpty(lastTask);
                            }
                        } else {
                            mWasRootTaskPreviouslyNonEmpty = !isRootTaskEmpty();
                        }
                    }

                    @Override
                    public void onRootTaskStackDestroyed(@NonNull RootTaskStack rootTaskStack) {
                        logIfDebuggable(getPanelId() + ", onRootTaskStackDestroyed "
                                + rootTaskStack);
                        mAutoCaptionController.removeCaptionRegion(rootTaskStack);
                        mRootTaskStack = null;
                    }

                    @Override
                    public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo,
                            SurfaceControl leash) {

                        logIfDebuggable("onTaskAppeared: " + taskInfo.taskId);
                        mTopTaskPackageName = mPanelUtils.getTaskPackageName(taskInfo);
                        if (mTopTaskPackageName == null) {
                            Log.e(TAG, "onTaskAppeared: Failed to get package name for task "
                                    + taskInfo.taskId);
                            return;
                        }
                        if (mCurrentRetryCount > 0) {
                            logIfDebuggable("onTaskAppeared: Resetting retry count.");
                            mCurrentRetryCount = 0;
                        }
                        mAutoTaskStackHelper.setTaskUntrimmableIfNeeded(getPanelId(), taskInfo);
                        mTaskPanelInfoRepository.onTaskAppearedOnPanel(getPanelId(), taskInfo);
                    }

                    @Override
                    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
                        mTaskPanelInfoRepository.onTaskChangedOnPanel(getPanelId(), taskInfo);
                    }

                    @Override
                    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
                        logIfDebuggable(
                                "onTaskVanished: " + taskInfo.taskId + " isRootTaskEmpty(): "
                                        + isRootTaskEmpty());
                        mTaskPanelInfoRepository.onTaskVanishedOnPanel(getPanelId(), taskInfo);
                    }

                    @Override
                    public void moveRootTaskToBack(ActivityManager.RunningTaskInfo taskInfo) {
                        // TODO(b/464035997): Remove this once we have a Sink root task in
                        //  car-wm-shell that will be a placeholder that contains all the
                        //  intermediate/transient app-tasks.
                        logIfDebuggable("moveRootTaskToBack: " + taskInfo.taskId);
                        reportTaskPanelEmpty(taskInfo);
                    }
                });
    }

    @VisibleForTesting
    void handlePanelEmpty(ActivityManager.RunningTaskInfo taskInfo) {
        if (hasRestart()) {
            scheduleRestartAttempt(taskInfo);
        }
        // Don't report a task panel empty event yet as there could be a trampoline launch in
        // progress which would end up in a race with a transition resulting from task panel empty
        // The task panel empty event will be calculated during startAnimation
    }

    private void reportTaskPanelEmpty(ActivityManager.RunningTaskInfo taskInfo) {
        logIfDebuggable(
                "reportTaskPanelEmpty for TaskPanel" + getPanelId() + ",  forTask " + taskInfo);
        mEventDispatcher.executeEvent(new Event.Builder(
                SYSTEM_TASK_PANEL_EMPTY_EVENT_ID)
                .setPanelId(getPanelId())
                .build());
    }

    @VisibleForTesting
    void scheduleRestartAttempt(ActivityManager.RunningTaskInfo taskInfo) {
        PanelState panelState = getPanelState();
        Restart restart = panelState.getRestart();
        if (mCurrentRetryCount < restart.getMaxRetry()) {
            long delay = (long) (INITIAL_RETRY_DELAY_MS * Math.pow(2, mCurrentRetryCount));
            logIfDebuggable("scheduleRestartAttempt: Attempt " + (mCurrentRetryCount + 1)
                    + " of " + restart.getMaxRetry() + " with delay " + delay + "ms.");
            getShellMainExecutor().executeDelayed(() -> {
                if (restart.getPolicy().equals(RESTART_POLICY_DEFAULT)) {
                    logIfDebuggable("scheduleRestartAttempt: Restarting with DEFAULT policy.");
                    mContext.startActivityAsUser(getDefaultIntent(), UserHandle.CURRENT);
                } else if (restart.getPolicy().equals(RESTART_POLICY_LAST)) {
                    logIfDebuggable("scheduleRestartAttempt: Restarting with LAST policy.");
                    mContext.startActivityAsUser(taskInfo.baseIntent, UserHandle.CURRENT);
                }
                getShellMainExecutor().executeDelayed(() -> {
                    if (isRootTaskEmpty()) {
                        logIfDebuggable(
                                "scheduleRestartAttempt: Restart failed, trying again.");
                        mCurrentRetryCount++;
                        scheduleRestartAttempt(taskInfo);
                    } else {
                        logIfDebuggable("scheduleRestartAttempt: Restart successful.");
                    }
                }, CHECK_RESTART_SUCCESS_DELAY_MS);
            }, delay);
        } else {
            logIfDebuggable(
                    "scheduleRestartAttempt: Max retries reached, sending empty event.");
            mEventDispatcher.executeEvent(new Event.Builder(
                    SYSTEM_TASK_PANEL_EMPTY_EVENT_ID)
                    .setPanelId(getPanelId()).build());
        }
    }

    /**
     * Whether or not this task panel has {@link Restart} set and enabled on it. The restart should
     * be inactive if the panel is invisible.
     *
     * @return true if the panel has task restart enabled
     */
    public boolean hasRestart() {
        PanelState panelState = getPanelState();
        if (panelState == null) {
            return false;
        }
        boolean isVisible = panelState.getCurrentVariant() != null
                ? panelState.getCurrentVariant().isVisible()
                : isVisible();
        return panelState.getRestart() != null && isVisible;
    }

    @Override
    public void destroy() {
        if (mRootTaskStack != null) {
            mAutoTaskStackController.destroyTaskStack(mRootTaskStack.getId());
        }
        if (mTaskPanelController != null) {
            mTaskPanelController.destroy();
        }
        super.destroy();
    }

    @Override
    public void reset() {
        super.reset();
        if (getRootStack() == null) {
            Log.e(TAG, "Cannot reset when root stack is null for panel" + getPanelId());
            return;
        }
        AutoTaskStackTransaction autoTaskStackTransaction = new AutoTaskStackTransaction();
        Variant currentVariant = mPanelUtils.getCurrentVariant(getPanelId());
        boolean isVisible = currentVariant != null ? currentVariant.isVisible() : isVisible();
        AutoTaskStackState autoTaskStackState = new AutoTaskStackState(
                currentVariant != null ? currentVariant.getBounds() : getBounds(),
                isVisible,
                currentVariant != null ? currentVariant.getLayer() : getLayer());
        autoTaskStackTransaction.setTaskStackState(getRootStack().getId(), autoTaskStackState);
        if (mFlagManager.isEnabled(Flag.DisplayCompatibilityAutoDecorSafeRegion)) {
            autoTaskStackTransaction.setSafeRegionBounds(getRootStack().getId(), getSafeBounds());
        }
        if (mFlagManager.isEnabled(Flag.DisplayCompatV2)) {
            setupTaskToolbar(mRootTaskStack,
                    getRelativeBounds(getTaskToolbarBounds(), getBounds()));
        }
        if (isVisible) {
            setBaseIntent(autoTaskStackTransaction);
        }
        getShellMainExecutor().execute(
                () -> mAutoTaskStackController.startTransition(autoTaskStackTransaction));

        AutoSurfaceTransaction autoSurfaceTransaction = mAutoSurfaceTransactionFactory
                .createTransaction(RESET_TRANSACTION + getPanelId());
        SurfaceControl.Transaction tx = new SurfaceControl.Transaction();

        update(autoSurfaceTransaction, tx, currentVariant, /* updateChildren= */ true);

        tx.apply();
        autoSurfaceTransaction.apply();
    }

    @ShellMainThread
    @VisibleForTesting
    void updateDecors(@NonNull AutoSurfaceTransaction autoSurfaceTransaction,
            @Nullable Variant variant, Map<String, View> decorViewMap) {
        logIfDebuggable("Update " + getPanelId() + " decors, with variant" + variant);
        if (!mFlagManager.isEnabled(Flag.EnableDecor)) {
            return;
        }

        if (getRootStack() == null) {
            return;
        }

        Map<String, Decor> decors = variant == null
                ? getCurrentDecors()
                : variant.getDecors();

        logIfDebuggable("Update " + getPanelId() + " decors, with decors" + decors);

        decors.forEach((id, decor) -> {
            logIfDebuggable("Create decor " + id);
            View view = decorViewMap.get(id);
            if (view != null) {
                AutoDecor autoDecor = mExistingAutoDecors.getOrDefault(id,
                        mAutoDecorManager.createAutoDecor(view,
                                decor.getLayer(), getSafeBounds(), decor.getId()));
                if (!mExistingAutoDecors.containsKey(id)) {
                    mAutoDecorManager.attachAutoDecorToTask(autoDecor, getRootTaskId());
                    mExistingAutoDecors.put(id, autoDecor);
                }

                updateAutoDecor(autoDecor, decor, autoSurfaceTransaction);
            }
        });

        // Remove the AutoDecor that is no longer there.
        Set<Map.Entry<String, AutoDecor>> decorToRemove =
                mExistingAutoDecors.entrySet().stream()
                        .filter(entry -> !decors.containsKey(entry.getKey()))
                        .peek(entry -> {
                            logIfDebuggable("Remove decor" + entry.getKey());
                            mAutoDecorManager.removeAutoDecor(entry.getValue());
                        })
                        .collect(Collectors.toSet());
        if (!decorToRemove.isEmpty()) {
            decorToRemove.forEach(entry -> mExistingAutoDecors.remove(entry.getKey()));
        }
    }

    @NonNull
    private Map<String, Decor> getCurrentDecors() {
        Variant currentVariant = mPanelUtils.getCurrentVariant(getPanelId());
        return currentVariant == null ? new HashMap<>() : currentVariant.getDecors();
    }

    @VisibleForTesting
    void updateAutoDecor(AutoDecor autoDecor, Decor decor,
            AutoSurfaceTransaction autoSurfaceTransaction) {
        Rect bounds = new Rect(0, 0, getBounds().width(), getBounds().height());
        autoSurfaceTransaction.setBounds(autoDecor, bounds);
        autoSurfaceTransaction.setVisibility(autoDecor, true);
        autoSurfaceTransaction.setZOrder(autoDecor, decor.getLayer());
        if (com.android.graphics.surfaceflinger.flags.Flags.setClientDrawnCornerRadii()) {
            autoSurfaceTransaction.setCornerRadius(autoDecor,
                    getCornerRadius().getTopLeftRadius(), getCornerRadius().getTopRightRadius(),
                    getCornerRadius().getBottomLeftRadius(),
                    getCornerRadius().getBottomRightRadius());
        } else {
            // Per-corner radius is not supported, applying a uniform radius to all corners.
            // Note: we could use any Corner#getRadius*(), as they will be the same.
            autoSurfaceTransaction.setCornerRadius(autoDecor, getCornerRadius().getTopLeftRadius());
        }
        autoSurfaceTransaction.setCrop(autoDecor, bounds);
    }

    @Override
    @ExternalMainThread
    public void refreshTheme() {
        Map<String, View> decorViewMap = getDecorViewMap(/* variant= */ null);
        super.refreshTheme();
        getShellMainExecutor().execute(() -> {
            logThreadIfDebuggable(getPanelId() + ", refreshTheme", Thread.currentThread());
            mExistingAutoDecors.forEach((id, autoDecor) -> {
                mAutoDecorManager.removeAutoDecor(autoDecor);
            });
            mExistingAutoDecors.clear();

            AutoSurfaceTransaction autoSurfaceTransaction = mAutoSurfaceTransactionFactory
                    .createTransaction(REFRESH_TRANSACTION + getPanelId());
            updateDecors(autoSurfaceTransaction, null, decorViewMap);
            autoSurfaceTransaction.apply();
        });
    }

    @ExternalMainThread
    @VisibleForTesting
    Map<String, View> getDecorViewMap(@Nullable Variant variant) {
        Variant currentVariant = variant == null
                ? mPanelUtils.getCurrentVariant(getPanelId())
                : variant;
        Map<String, View> maps = new HashMap<>();
        if (currentVariant != null) {
            currentVariant.getDecors().forEach((id, decor) -> {
                maps.put(id, decor.getView(mContext));
            });
        }
        return maps;
    }

    /**
     * Sets the base intent for the provided AutoTaskStackTransaction.
     *
     * This method configures and sends a PendingIntent based on the default intent
     * of this component, targeting the root task of the current root stack.
     * It will return early if no default intent or root task info is available.
     *
     * @param autoTaskStackTransaction The transaction to which the base intent will be applied.
     */
    public void setBaseIntent(AutoTaskStackTransaction autoTaskStackTransaction) {
        if (getDefaultIntent() == null || getRootStack().getRootTaskInfo() == null) {
            return;
        }
        Trace.beginSection(TAG + "#setBaseIntent");
        Intent defaultIntent = getDefaultIntent();
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS);
        options.setLaunchRootTask(getRootStack().getRootTaskInfo().token);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                getContext().createContextAsUser(UserHandle.CURRENT, 0), 0, defaultIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        autoTaskStackTransaction.sendPendingIntent(pendingIntent, defaultIntent,
                options.toBundle());
        logIfDebuggable("setBaseIntent:" + this);
        Trace.endSection();
    }

    @Nullable
    public RootTaskStack getRootStack() {
        return mRootTaskStack;
    }

    /**
     * Returns the task ID of the root task associated with this panel.
     */
    public int getRootTaskId() {
        return mRootTaskStack == null ? -1 : mRootTaskStack.getRootTaskInfo().taskId;
    }

    /**
     * Returns the token of the root task associated with this panel.
     */
    @Nullable
    public WindowContainerToken getRootTaskToken() {
        if (mRootTaskStack == null) {
            return null;
        }
        return mRootTaskStack.getRootTaskInfo().token;
    }

    /**
     * Returns the default intent associated with this {@link TaskPanel}.
     *
     * <p>The default intent will be send right after the TaskPanel is ready.
     */
    @Nullable
    public Intent getDefaultIntent() {
        if (mTaskPanelController != null) {
            return mTaskPanelController.getDefaultComponent();
        }
        ComponentName componentName = mAutoTaskStackHelper.getDefaultIntent(getPanelId());
        if (componentName == null) {
            return null;
        }
        Intent defaultIntent = new Intent(Intent.ACTION_MAIN);
        defaultIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        defaultIntent.setComponent(componentName);
        return defaultIntent;
    }

    @Nullable
    public SurfaceControl getLeash() {
        return mLeash;
    }

    @Nullable
    public String getTopTaskPackageName() {
        return mTopTaskPackageName;
    }

    public void setLeash(@Nullable SurfaceControl leash) {
        mLeash = leash;
    }

    /**
     * Return whether this panel is the launch root panel.
     */
    public boolean isLaunchRoot() {
        return mIsLaunchRoot;
    }

    @NonNull
    @Override
    public Rect getSafeBounds() {
        return mSafeBounds;
    }

    @Override
    public void setSafeBounds(@NonNull Rect safeBounds) {
        if (safeBounds.isEmpty() && !getBounds().isEmpty()) {
            throw new IllegalArgumentException(
                    "Tried setting incorrect safe bounds: " + safeBounds + "on panel: "
                            + getPanelId());
        }
        mSafeBounds = safeBounds;
        setupToolbarRegion();
    }

    @NonNull
    @Override
    public Rect getTaskToolbarBounds() {
        return mTaskToolbarBounds;
    }

    @Override
    public void setTaskToolbarBounds(@NonNull Rect bounds) {
        if (!mFlagManager.isEnabled(Flag.DisplayCompatV2)) {
            logIfDebuggable("Task toolbar is disabled, not setting bounds");
            return;
        }
        if (bounds.isEmpty() && !getBounds().isEmpty()) {
            Log.e(TAG,
                    "Tried setting incorrect task tool bar bounds: " + bounds + "on panel: "
                            + getPanelId());
        }
        mTaskToolbarBounds = bounds;
        mRelativeToolbarBounds = getRelativeBounds(bounds, getBounds());
        logIfDebuggable("mRelativeToolbarBounds: " + mRelativeToolbarBounds);
        setupTaskToolbar(mRootTaskStack, mRelativeToolbarBounds);
    }

    private Rect getRelativeBounds(@NonNull Rect bounds, @NonNull Rect parentBounds) {
        int left = bounds.left - parentBounds.left;
        int right = bounds.width() + left;
        int top = bounds.top - parentBounds.top;
        int bottom = top + bounds.height();
        return new Rect(left, top, right, bottom);
    }

    @Override
    public void setRole(@Nullable Role role) {
        if (getRole() == role) return;
        super.setRole(role);

        if (getRole() == null) return;

        if (getRole().isDefault()) {
            mIsLaunchRoot = true;
        } else {
            ComponentName[] persistedActivities = getRole().getPersistedActivities();
            if (persistedActivities != null) {
                mPersistedActivities.clear();
                mPersistedActivities.addAll(Arrays.asList(persistedActivities));
            }
        }
        trySetPersistentActivity();
    }

    /**
     * Calculates the four rectangular areas representing the insets of this {@link TaskPanel}.
     *
     * <p>This method uses the inset values and bounds of this {@link TaskPanel} (or an optionally
     * provided {@link Variant}) to define four distinct {@link Rect} objects. Each rectangle
     * corresponds to the screen area effectively occupied by the left, top, right, or bottom
     * inset, relative to the panel's bounds.
     *
     * @param variant An optional {@link Variant} to use for calculating insets. If null, the
     *                current panel's insets and bounds are used.
     * @return An array of {@link Rect} objects of size 4, ordered as follows:
     * <ul>
     * <li>Index 0: Rectangle representing the left inset area.</li>
     * <li>Index 1: Rectangle representing the top inset area.</li>
     * <li>Index 2: Rectangle representing the right inset area.</li>
     * <li>Index 3: Rectangle representing the bottom inset area.</li>
     * </ul>
     */
    public Rect[] getInsetRects(@Nullable Variant variant) {
        Rect insets = variant == null ? getInsets().toRect() : variant.getInsets().toRect();
        Rect bounds = variant == null ? getBounds() : variant.getBounds();

        Rect[] insetSides = new Rect[4];
        insetSides[0] = insets.left != 0
            ? new Rect(bounds.left, bounds.top, bounds.left + insets.left, bounds.bottom)
            : null;
        insetSides[1] = insets.top != 0
            ? new Rect(bounds.left, bounds.top, bounds.right, bounds.top + insets.top)
            : null;
        insetSides[2] = insets.right != 0
            ? new Rect(bounds.right - insets.right, bounds.top, bounds.right, bounds.bottom)
            : null;
        insetSides[3] = insets.bottom != 0
            ? new Rect(bounds.left, bounds.bottom - insets.bottom, bounds.right, bounds.bottom)
            : null;
        return insetSides;
    }

    @Override
    protected void updateInternal(
            @Nullable AutoSurfaceTransaction autoSurfaceTransaction,
            @Nullable SurfaceControl.Transaction tx,
            @Nullable Variant variant,
            boolean updateChildren) {
        Trace.beginSection(TAG + "#update");
        if (getRootStack() == null) {
            Log.e(TAG, "RootStack is null for " + getPanelId());
            return;
        }
        if (autoSurfaceTransaction == null && tx == null) {
            // Both parameters being null should not be possible if the caller is properly
            // using the update methods rather than directly calling internal method.
            throw new IllegalArgumentException(
                    "AutoSurfaceTransaction and SurfaceControl.Transaction cannot both be null");
        }
        super.updateInternal(autoSurfaceTransaction, tx, variant, updateChildren);
        logIfDebuggable(
                "update TaskPanel:" + getPanelId() + ", updateChildren =" + updateChildren + ", "
                        + "variant" + variant);
        int taskId = getRootTaskId();
        Rect bounds = variant == null ? getBounds() : variant.getBounds();
        if (autoSurfaceTransaction != null) {
            autoSurfaceTransaction.setTaskSurfaceCrop(taskId,
                    new Rect(0, 0, bounds.width(), bounds.height()));
            autoSurfaceTransaction.setTaskSurfacePosition(taskId, bounds.left,
                    bounds.top);
            autoSurfaceTransaction.setTaskSurfaceAlpha(taskId,
                    variant == null ? getAlpha() : variant.getAlpha());
            autoSurfaceTransaction.setTaskSurfaceVisibility(taskId,
                    variant == null ? isVisible() : variant.isVisible());
            autoSurfaceTransaction.setTaskSurfaceTransientLayer(taskId,
                    variant == null ? getLayer() : variant.getLayer());
            Corner radius = variant == null ? getCornerRadius() : variant.getCornerRadius();
            if (com.android.graphics.surfaceflinger.flags.Flags.setClientDrawnCornerRadii()) {
                autoSurfaceTransaction.setTaskSurfaceCornerRadius(taskId,
                        radius.getTopLeftRadius(), radius.getTopRightRadius(),
                        radius.getBottomLeftRadius(),
                        radius.getBottomRightRadius());
            } else {
                // Per-corner radius is not supported, applying a uniform radius to all corners.
                // Note: we could use any Corner#getRadius*(), as they will be the same.
                autoSurfaceTransaction.setTaskSurfaceCornerRadius(taskId,
                        radius.getTopLeftRadius());
            }
        }

        if (tx != null && getLeash() != null) {
            //TODO(b/404959846): move following to AutoSurfaceTransaction
            tx.setVisibility(getLeash(), variant == null ? isVisible() : variant.isVisible());
            tx.setAlpha(getLeash(), variant == null ? getAlpha() : variant.getAlpha());
            tx.setLayer(getLeash(), variant == null ? getLayer() : variant.getLayer());
            if (autoSurfaceTransaction == null) {
                tx.setCrop(getLeash(), new Rect(0, 0, bounds.width(), bounds.height()));
                tx.setPosition(getLeash(), bounds.left, bounds.top);
                Corner radius = variant == null ? getCornerRadius() : variant.getCornerRadius();
                if (com.android.graphics.surfaceflinger.flags.Flags.setClientDrawnCornerRadii()) {
                    tx.setCornerRadius(getLeash(),
                            radius.getTopLeftRadius(), radius.getTopRightRadius(),
                            radius.getBottomLeftRadius(),
                            radius.getBottomRightRadius());
                } else {
                    // Per-corner radius is not supported, applying a uniform radius to all corners.
                    // Note: we could use any Corner#getRadius*(), as they will be the same.
                    tx.setCornerRadius(getLeash(), radius.getTopLeftRadius());
                }
            }
        } else {
            Log.e(TAG, "leash is " + getLeash() + ", tx is " + tx);
        }

        Rect[] panelInsets = getInsetRects(variant);
        addOrUpdateInsets(panelInsets);

        if (updateChildren) {
            // autoSurfaceTransaction being null should not be possible if the caller is properly
            // using the update methods rather than directly calling internal method.
            Objects.requireNonNull(autoSurfaceTransaction,
                    "AutoSurfaceTransaction must be supplied to update child decors");
            getMainExecutor().execute(() -> {
                Map<String, View> decorViewMap = getDecorViewMap(variant);
                getShellMainExecutor().execute(
                        () -> updateDecors(autoSurfaceTransaction, variant, decorViewMap));
            });

        }
        Trace.endSection();
    }

    private void addOrUpdateInsets(Rect[] panelInsets) {
        if (Arrays.equals(mCurrentInsets, panelInsets)) {
            return;
        }
        mCurrentInsets = panelInsets;

        // TODO b/466431797: Revisit the threading mode for car-wm-shell + ScalableUI
        // Execute AutoLayoutManager transactions on ShellBgThread, we may not block the
        // SysUI/WM MainThread as it's not part of the same surface transaction.
        getShellBgExecutor().execute(() -> {
            RootTaskStack rootTaskStack = getRootStack();
            if (rootTaskStack == null) {
                Log.w(TAG, "Skip updating insets, RootTaskStack is null for " + getPanelId());
                return;
            }

            IntStream.range(0, panelInsets.length).forEach(index -> {
                Rect insets = panelInsets[index] == null ? new Rect() : panelInsets[index];
                mAutoLayoutManager.addOrUpdateInsets(rootTaskStack, index,
                        systemOverlays(), insets);
            });
        });
    }

    @Override
    public void setPanelControllerMetadata(
            @Nullable PanelControllerMetadata panelControllerMetadata) {
        if (Objects.equals(getPanelControllerMetadata(), panelControllerMetadata)) {
            logIfDebuggable(getPanelId() + ": PanelControllerMetadata unchanged.");
            return;
        }
        super.setPanelControllerMetadata(panelControllerMetadata);
        mTaskPanelController = mPanelControllerInitializer.createTaskPanelController(
                getPanelId(), panelControllerMetadata);
        if (mTaskPanelController != null) {
            mTaskPanelController.registerTaskPanelHandler(this::trySetPersistentActivity);
        }
    }

    private ArraySet<ComponentName> convertToComponentNames(String[] componentStrings) {
        ArraySet<ComponentName> componentNames = new ArraySet<>(componentStrings.length);
        for (int i = componentStrings.length - 1; i >= 0; i--) {
            componentNames.add(ComponentName.unflattenFromString(componentStrings[i]));
        }
        return componentNames;
    }

    @SuppressLint("MissingPermission")
    private void trySetPersistentActivity() {
        if (mCarActivityManager == null || mRootTaskStack == null) {
            logIfDebuggable("mCarActivityManager or mRootTaskStack is null, [" + getPanelId() + ","
                    + mCarActivityManager + ", " + mRootTaskStack + "]");
            return;
        }

        if (getRole() != null && (getRole().getPersistedActivities() == null
                || getRole().getPersistedActivities().length == 0)) {
            logIfDebuggable("Persistent Activities is empty, [" + getPanelId() + "]");
            return;
        }

        if (mIsLaunchRoot) {
            logIfDebuggable("mIsLaunchRoot is true, [" + getPanelId() + "]");
            return;
        }

        if (mTaskPanelController != null
                && !mTaskPanelController.getPersistentActivities().isEmpty()) {
            mCarActivityManager.setPersistentActivitiesOnRootTask(
                    mTaskPanelController.getPersistentActivities().stream().toList(),
                    mRootTaskStack.getRootTaskInfo().token.asBinder());
        } else {
            mCarActivityManager.setPersistentActivitiesOnRootTask(
                    mPersistedActivities.stream().toList(),
                    mRootTaskStack.getRootTaskInfo().token.asBinder());
        }
    }

    @VisibleForTesting
    @SuppressLint("MissingPermission")
    void trySetRootTaskLaunchBehavior() {
        if (mCarActivityManager == null || mRootTaskStack == null) {
            logIfDebuggable("mCarActivityManager or mRootTaskStack is null, [" + getPanelId() + ","
                    + mCarActivityManager + ", " + mRootTaskStack + "]");
            return;
        }

        if (getPanelState() == null || getPanelState().getTaskBehavior() == null) {
            logIfDebuggable("PanelState or PanelState TaskBehavior is null, ["
                    + getPanelId() + "]");
            return;
        }

        String launchPolicy = getPanelState().getTaskBehavior().getNewTaskLaunchPolicy();
        int launchBehavior = -1;
        switch (launchPolicy) {
            case NEW_TASK_LAUNCH_POLICY_DEFAULT -> launchBehavior = LAUNCH_BEHAVIOR_DEFAULT;
            case NEW_TASK_LAUNCH_POLICY_REMAIN_IN_SOURCE ->
                    launchBehavior = LAUNCH_BEHAVIOR_REMAIN_IN_SOURCE_ROOT_TASK;
            case NEW_TASK_LAUNCH_POLICY_REPARENT_TO_SOURCE ->
                    launchBehavior = LAUNCH_BEHAVIOR_REPARENT_TO_SOURCE_ROOT_TASK;
        }
        if (launchBehavior != -1) {
            mCarActivityManager.setLaunchBehaviorForRootTask(
                    mRootTaskStack.getRootTaskInfo().getToken().asBinder(),
                    launchBehavior);
        }
    }

    /**
     * Checks if the root task stack exists and is currently empty (contains no activities).
     *
     * @return True if mRootTaskStack is not null and the root task has zero activities, false
     * otherwise.
     */
    @VisibleForTesting
    public boolean isRootTaskEmpty() {
        return mRootTaskStack != null
                && mRootTaskStack.getRootTaskInfo().numActivities == 0;
    }

    @VisibleForTesting
    PanelState getPanelState() {
        return StateManager.getPanelState(getPanelId());
    }

    private void setupToolbarRegion() {
        if (!mFlagManager.isEnabled(Flag.DisplayCompatibilityAutoDecorSafeRegion)) {
            return;
        }
        logIfDebuggable("setupToolbarRegion: " + getPanelId());
        if (mFlagManager.isEnabled(Flag.DisplayCompatV2)) {
            logVerbose("Skip setting up the toolbar");
            return;
        }
        if (mRootTaskStack == null) {
            return;
        }

        if (mSafeBounds.isEmpty()) {
            // TODO(b/409067170): update AutoCaptionController API to be able to set these values
            //  independently
            logVerbose(
                    "Invalid Safe Bounds, not setting toolbar region for panel: " + getPanelId());
            return;
        }
        if (getBounds().isEmpty()) {
            logVerbose("Null or invalid panel bounds, not setting safe region for panel: "
                    + getPanelId());
            return;
        }
        if (mSafeBounds.equals(getBounds())) {
            logVerbose(
                    "SafeBounds equivalent to panel bounds, not setting toolbar region for panel: "
                            + getPanelId());
            return;
        }

        Rect toolbarBounds = calculateToolbarBounds(getBounds(), getSafeBounds());
        if (toolbarBounds.isEmpty()) {
            logVerbose("Toolbar with bounds: " + toolbarBounds + " cannot be added to panel: "
                    + getPanelId());
            return;
        }
        toolbarBounds.offset(-getBounds().left, -getBounds().top);

        logVerbose("Setting up toolbar region with following values: "
                + "rootTaskStack = " + mRootTaskStack
                + ", toolbar bounds = " + toolbarBounds
                + ", panel bounds = " + getBounds());

        mAutoCaptionController.setCaptionRegion(mRootTaskStack,
                toolbarBounds, mAutoCaptionBarViewControllerImpl);
    }

    private void setupTaskToolbar(RootTaskStack rootTaskStack, Rect taskToolbarBounds) {
        TaskToolbarController taskToolBarController =
                mPanelControllerInitializer.createTaskToolBarController(
                        getPanelControllerMetadata(), getPanelId());

        if (rootTaskStack == null || taskToolBarController == null) {
            Log.e(TAG, "rootTaskStack or taskToolBarController is null, not setting toolbar"
                    + rootTaskStack + ", " + taskToolBarController);
            return;
        }
        logIfDebuggable("setCaptionRegion" + rootTaskStack
                + ", taskToolbarBounds " + taskToolbarBounds
                + ", taskToolBarController" + taskToolBarController
        );

        mAutoCaptionController.setCaptionRegion(rootTaskStack, taskToolbarBounds,
                taskToolBarController);
    }

    @NonNull
    private Rect calculateToolbarBounds(@NonNull Rect panelBounds, @NonNull Rect safeBounds) {
        // TODO(b/409067170): remove this when AutoCaptionController API is able to handle safe
        //  region and toolbar separately
        if (panelBounds.top < safeBounds.top) {
            return new Rect(safeBounds.left, panelBounds.top, safeBounds.right, safeBounds.top);
        }
        if (panelBounds.bottom > safeBounds.bottom) {
            return new Rect(safeBounds.left, safeBounds.bottom, safeBounds.right,
                    panelBounds.bottom);
        }
        if (panelBounds.left < safeBounds.left) {
            return new Rect(panelBounds.left, safeBounds.top, safeBounds.left, safeBounds.bottom);
        }
        if (panelBounds.right > safeBounds.right) {
            return new Rect(safeBounds.right, safeBounds.top, panelBounds.right, safeBounds.bottom);
        }
        return new Rect();
    }

    private void logVerbose(String message) {
        if (DEBUG) {
            Log.v(TAG, message);
        }
    }

    @VisibleForTesting
    void setRootTaskStack(RootTaskStack rootTaskStack) {
        mRootTaskStack = rootTaskStack;
    }

    @Override
    public void dump(@NonNull PrintWriter pw) {
        pw.println(this);
        pw.println(mTaskPanelController);
    }

    @Override
    public String toString() {

        String decorString = mExistingAutoDecors.isEmpty()
                ? "Empty"
                : mExistingAutoDecors.entrySet()
                        .stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining("\n , "));

        return "TaskPanel{"
                + "mId='" + getPanelId()
                + "\n, isRooTaskEmpty=" + isRootTaskEmpty()
                + "\n, mBounds=" + getBounds()
                + "\n, mAlpha=" + getAlpha()
                + "\n, mIsVisible=" + isVisible()
                + "\n, rootTaskId=" + getRootTaskId()
                + "\n, mRole=" + getRole()
                + "\n, mLayer=" + getLayer()
                + "\n, mLeash=" + mLeash
                + "\n, mRootTaskStack=" + mRootTaskStack
                + "\n, mCornerRadius=" + getCornerRadius()
                + "\n, mIsLaunchRoot=" + mIsLaunchRoot
                + "\n, mDisplayId=" + getDisplayId()
                + "\n, mDecors=" + decorString
                + '}';
    }

    /**
     * Checks if the activity with given {@link ComponentName} should show in current panel.
     */
    public boolean handles(@Nullable ComponentName componentName) {
        if (mTaskPanelController != null) {
            return mTaskPanelController.handles(componentName);
        }
        return componentName != null && mPersistedActivities.contains(componentName);
    }

    @AssistedFactory
    public interface Factory {
        /** Create instance of TaskPanel with specified id */
        TaskPanel create(String id);
    }
}
