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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.window.TransitionInfo.FLAG_MOVED_TO_TOP;

import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_HOME_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_ON_ANIMATION_END_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_TASK_CLOSE_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_TASK_OPEN_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_TASK_PANEL_EMPTY_EVENT_ID;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.internal.dep.Trace;
import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.Event;
import com.android.car.scalableui.model.PanelTransaction;
import com.android.car.scalableui.model.Transition;
import com.android.car.scalableui.model.Variant;
import com.android.car.scalableui.panel.Panel;
import com.android.car.scalableui.panel.PanelPool;
import com.android.systemui.car.flags.Flag;
import com.android.systemui.car.flags.FlagManager;
import com.android.systemui.car.wm.CarWMUserHelper;
import com.android.systemui.car.wm.scalableui.panel.DecorPanel;
import com.android.systemui.car.wm.scalableui.panel.PanelUtils;
import com.android.systemui.car.wm.scalableui.panel.SysUIPanel;
import com.android.systemui.car.wm.scalableui.panel.TaskPanel;
import com.android.wm.shell.automotive.AutoLayoutManager;
import com.android.wm.shell.automotive.AutoSurfaceTransaction;
import com.android.wm.shell.automotive.AutoSurfaceTransactionFactory;
import com.android.wm.shell.automotive.AutoTaskStackController;
import com.android.wm.shell.automotive.AutoTaskStackState;
import com.android.wm.shell.automotive.AutoTaskStackTransaction;
import com.android.wm.shell.automotive.TaskStackStateChange;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.transition.Transitions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

/**
 * Manages the state transitions of the UI panels.
 * This class is responsible for creating AutoTaskStackTransaction and queuing up panel animations
 * based on event triggers and then applying visual updates to panels based on their current state.
 */
@WMSingleton
public class PanelTransitionCoordinator {
    private static final String TAG = PanelTransitionCoordinator.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    @VisibleForTesting
    protected static final String DECOR_TRANSACTION = "DECOR_TRANSACTION";
    private static final String PANEL_TRANSACTION = "PANEL_TRANSACTION";
    // Amount of time (in ms) that historically applied events should be stored for before
    // potentially clearing
    private static final long HISTORICAL_EVENTS_RETENTION_THRESHOLD = 5000;

    private final AutoTaskStackController mAutoTaskStackController;
    @GuardedBy("mPendingPanelTransactions")
    private final HashMap<IBinder, PanelTransaction> mPendingPanelTransactions = new HashMap<>();
    // Map of timestamp (elapsedRealtime) to list of events that occurred at that time.
    @GuardedBy("mHistoricallyAppliedEvents")
    private final ConcurrentSkipListMap<Long, List<Event>> mHistoricallyAppliedEvents =
            new ConcurrentSkipListMap<>();
    @NonNull
    private final FlagManager mFlagManager;
    private AnimatorSet mRunningAnimatorSet = null;
    private final AutoSurfaceTransactionFactory mAutoSurfaceTransactionFactory;
    private final PanelUtils mPanelUtils;
    private IBinder mActiveTransition;
    private final AutoLayoutManager mAutoLayoutManager;
    private final ShellExecutor mMainExecutor;
    private final CarWMUserHelper mUserHelper;

    @Inject
    public PanelTransitionCoordinator(AutoTaskStackController autoTaskStackController,
            AutoSurfaceTransactionFactory autoSurfaceTransactionFactory,
            PanelUtils panelUtils,
            AutoLayoutManager autoLayoutManager,
            @ShellMainThread ShellExecutor mainExecutor,
            FlagManager flagManager,
            CarWMUserHelper userHelper) {
        mAutoTaskStackController = autoTaskStackController;
        mAutoSurfaceTransactionFactory = autoSurfaceTransactionFactory;
        mPanelUtils = panelUtils;
        mAutoLayoutManager = autoLayoutManager;
        mMainExecutor = mainExecutor;
        mFlagManager = flagManager;
        mUserHelper = userHelper;
    }

    /**
     * See {@link #startTransition(PanelTransaction, Set)}
     */
    public void startTransition(@NonNull PanelTransaction transaction) {
        startTransition(transaction, Collections.emptySet());
    }

    /**
     * Starts a panel transition using the provided {@link PanelTransaction} that causes window
     * state change.
     *
     * @param transaction The {@link PanelTransaction} object containing the details of the
     *                    transition.
     * @param panelIdsToForceCurrentState a set of panelIds to force the current panel state upon
     *                                    in the current transition in the event they are not
     *                                    already included in the transaction.
     */
    public void startTransition(@NonNull PanelTransaction transaction, @NonNull Set<String>
            panelIdsToForceCurrentState) {
        Trace.beginSection(
                TAG + "#startTransition, windowChanges:" + transaction.hasWindowChanges());
        if (transaction.hasWindowChanges()) {
            mMainExecutor.execute(() -> {
                synchronized (mPendingPanelTransactions) {
                    IBinder transition = mAutoTaskStackController.startTransition(
                            createAutoTaskStackTransaction(transaction,
                                    panelIdsToForceCurrentState));
                    if (transition != null) {
                        mPendingPanelTransactions.put(transition, transaction);
                        resetUnpreparedDecorPanel(transaction);
                    } else {
                        // This transaction does not result in shell transitions, so it needs to be
                        // animated directly.
                        startDirectAnimation(transaction);
                    }
                }
            });
        } else {
            // If the transaction does not involve window changes, execute it directly. Posting
            // to the shell main thread could introduce unnecessary latency and visual lag.
            updatePanelSurface(transaction);
        }
        Trace.endSection();
    }

    private void startDirectAnimation(PanelTransaction transaction) {
        if (transaction.getAnimators().isEmpty()) {
            return;
        }
        endAnimationsWithoutTransition();

        mRunningAnimatorSet = new AnimatorSet();
        mActiveTransition = null;
        List<Animator> animators = transaction.getAnimators().stream()
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());

        Runnable onAnimationEnd = () -> {
            if (transaction.getAnimationEndCallbackRunnable() != null) {
                transaction.getAnimationEndCallbackRunnable().run();
            }
            mRunningAnimatorSet = null;
            mActiveTransition = null;
        };

        startAnimationSet(mRunningAnimatorSet, transaction,
                getAnimatorsToRun(transaction), onAnimationEnd);
    }

    private void startAnimationSet(
            AnimatorSet animatorSet,
            PanelTransaction transaction,
            List<Animator> animators,
            Runnable onAnimationEnd) {
        animatorSet.playTogether(animators);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                if (transaction.getAnimationStartCallbackRunnable() != null) {
                    transaction.getAnimationStartCallbackRunnable().run();
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                onAnimationEnd.run();
            }
        });
        animatorSet.start();
    }

    /**
     * Resets {@link DecorPanel} with no {@link AutoDecor} initialized.
     *
     * <p>This method iterates through each panel state defined in the provided
     * {@code PanelTransaction}. For each entry, it attempts to find the corresponding
     * {@code DecorPanel} using its ID. If a {@code DecorPanel} is found,and it does not have an
     * associated {@link AutoDecor} and its target variant in the
     * transaction
     * is set to be visible, then the {@code DecorPanel} will be reset to its
     * default state.
     */
    public void resetUnpreparedDecorPanel(PanelTransaction transaction) {
        for (Map.Entry<String, Transition> entry : transaction.getPanelTransactionStates()) {
            DecorPanel decorPanel = mPanelUtils.getDecorPanel(
                    p -> p.getPanelId().equals(entry.getKey()));
            if (decorPanel == null) {
                continue;
            }
            Variant toVariant = entry.getValue().getToVariant();
            if (decorPanel.getAutoDecor() == null && toVariant.isVisible()) {
                decorPanel.reset();
            }
        }
    }

    private void updatePanelSurface(PanelTransaction panelTransaction) {
        logIfDebuggable("updatePanelSurface: " + panelTransaction);
        AutoSurfaceTransaction autoSurfaceTransaction =
                mAutoSurfaceTransactionFactory.createTransaction(DECOR_TRANSACTION);
        SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
        for (Map.Entry<String, Transition> entry : panelTransaction.getPanelTransactionStates()) {
            Panel panel = PanelPool.getInstance().getPanel(
                    p -> p.getPanelId().equals(entry.getKey()));
            if (panel == null) {
                logIfDebuggable("Panel is null for " + entry.getKey());
                continue;
            }
            Transition transition = entry.getValue();
            Variant toVariant = transition.getToVariant();
            if (panel instanceof SysUIPanel sysUiPanel) {
                sysUiPanel.update(autoSurfaceTransaction, tx, toVariant);
            } else {
                Log.e(TAG, "Invalid panel " + panel);
            }
        }
        for (String unchangedPanelId : panelTransaction.getLockededPanelIdSet()) {
            Panel panel = PanelPool.getInstance().getPanel(
                    p -> p.getPanelId().equals(unchangedPanelId));
            if (panel instanceof SysUIPanel sysUiPanel) {
                sysUiPanel.update(autoSurfaceTransaction, tx);
            }
        }
        autoSurfaceTransaction.apply();
    }

    /**
     * Handle special cases within the task state provided by startAnimation.
     */
    void reconcileAutoTaskStackState(@NonNull IBinder transition,
            @NonNull List<TaskStackStateChange> changedTaskStacks,
            @Nullable TransitionInfo transitionInfo) {
        boolean shouldForceEvents = false;
        Map<String, Boolean> conflictingPanelStates = getConflictingPanelStates(changedTaskStacks,
                transition);
        List<Event> reconciliationEvents = new ArrayList<>(
                getAndOrderConflictEvents(conflictingPanelStates, changedTaskStacks,
                        transition, transitionInfo));

        // If it is believed that this transition will cause a TaskPanel to become empty,
        // preemptively trigger a new transition for the task panel empty event.
        // These are applied after the conflict events in the case a conflict close leads to an
        // empty panel.
        List<String> panelsBecomingEmpty = getPanelsBecomingEmptyIds(changedTaskStacks,
                conflictingPanelStates);
        reconciliationEvents.addAll(getTriggerTaskPanelEmptyEvents(panelsBecomingEmpty));
        List<Event> postAppliedEvents = getPostAppliedEvents(transition);
        if (!postAppliedEvents.isEmpty()) {
            PanelTransaction transaction = getPendingPanelTransaction(transition);
            if (transaction != null) {
                // Add our current transaction events to the start of the reconciliationEvents such
                // that the from variants of the transitions are corrected to what the state
                // actually is after this transition.
                reconciliationEvents.addAll(0, transaction.getTransactionEvents());
            }
            reconciliationEvents.addAll(postAppliedEvents);
            shouldForceEvents = true;
        }

        if (!conflictingPanelStates.isEmpty() || !reconciliationEvents.isEmpty()) {
            logIfDebuggable("Reconciling: conflictingPanelStates=" + conflictingPanelStates
                    + ", reconciliationEvents=" + reconciliationEvents);
            PanelTransaction transaction = StateManager.handleEvents(reconciliationEvents,
                    shouldForceEvents);
            startTransition(transaction, conflictingPanelStates.keySet());
        }
    }

    /**
     * Return a map of panelId -> childTaskVisible (according to WM) for which the panel state
     * is not in or going to be in this correct state during this transition.
     */
    private Map<String, Boolean> getConflictingPanelStates(
            @NonNull List<TaskStackStateChange> changedTaskStacks,
            @NonNull IBinder transition) {
        PanelTransaction transaction = null;
        // Map of conflicting panelId to if the child task is visible
        Map<String, Boolean> conflictingPanelStates = new HashMap<>();
        synchronized (mPendingPanelTransactions) {
            transaction = mPendingPanelTransactions.get(transition);
        }

        Set<String> panelsAlreadyChecked = new HashSet<>();
        // To ensure consistent states between ScalableUI and the WM, it's necessary to check both
        // directions of TaskStackChanges <-> PendingTransactions to ensure both the requested
        // transactions were applied and the WM didn't get any additional changes not requested.
        // First, check all TaskStack changes against the expected state from ScalableUI
        for (TaskStackStateChange change : changedTaskStacks) {
            int autoTaskStackId = change.getTaskId();
            TaskPanel tp = mPanelUtils.getTaskPanel(taskPanel ->
                    taskPanel.getRootStack() != null
                            && taskPanel.getRootStack().getId() == autoTaskStackId);
            if (tp == null) {
                logIfDebuggable("No panel found for auto task stack " + autoTaskStackId);
                continue;
            }
            panelsAlreadyChecked.add(tp.getPanelId());

            AutoTaskStackState changedState = change.getState();
            Transition panelTransition = transaction != null
                    ? transaction.getPanelTransactionState(tp.getPanelId())
                    : null;

            if (hasConflictingState(changedState, tp, panelTransition)) {
                conflictingPanelStates.put(tp.getPanelId(), changedState.isAboveBarrier());
            }
        }

        if (transaction == null) {
            return conflictingPanelStates;
        }
        // Second, check all panel transitions in the transaction against current TaskStack state
        // to ensure everything was applied.
        for (Map.Entry<String, Transition> entry : transaction.getPanelTransactionStates()) {
            String panelId = entry.getKey();
            if (panelsAlreadyChecked.contains(panelId)) {
                // Already resolved - don't need to check again
                continue;
            }
            TaskPanel tp = mPanelUtils.getTaskPanel(p -> p.getPanelId().equals(panelId));
            if (tp == null || tp.getRootStack() == null) {
                continue;
            }
            AutoTaskStackState currentState = mAutoTaskStackController.getTaskStackStateMap().get(
                    tp.getRootStack().getId());
            if (currentState == null) {
                continue;
            }
            if (hasConflictingState(currentState, tp, entry.getValue())) {
                conflictingPanelStates.put(tp.getPanelId(), currentState.isAboveBarrier());
            }
        }

        return conflictingPanelStates;
    }

    /**
     * Retrieve and order a set of events to try to help resolve conflicting panel states. These
     * events will be ordered in ascending z-order by visibility, meaning the close events will
     * always trigger first and the last event in each set will be the top window in the z-order.
     *
     * <p> This is a medium-term workaround to resolve the transition conflicts.
     *
     * <p>Transition conflicts arise when multiple intents occur rapidly, leading to
     * {@code handleRequest} only processing the initial intent. Subsequent intents are handled
     * directly by the Window Manager without invoking the {@code handleRequest} callback. Due to
     * missing task info, window state corrections are limited to scenarios where the launch root
     * task has changed. This change is interpreted as either a task open or close event, determined
     * by the visibility change.
     * TODO(b/397527431) : handle transition conflicts correctly after b/388067743.
     */
    private List<Event> getAndOrderConflictEvents(
            @NonNull Map<String, Boolean> conflictingPanelStates,
            @NonNull List<TaskStackStateChange> changes,
            @NonNull IBinder transition,
            @Nullable TransitionInfo transitionInfo) {
        if (conflictingPanelStates.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> orderedCloseConflictPanelIds = new LinkedHashSet<>();
        LinkedHashSet<String> orderedOpenConflictPanelIds = new LinkedHashSet<>();
        // Changes are sorted by z-order top to bottom - iterate from last to first to apply the
        // top z-order last.
        for (int i = changes.size() - 1; i >= 0; i--) {
            TaskStackStateChange change = changes.get(i);
            TaskPanel taskPanel = mPanelUtils.getTaskPanel(
                    tp -> tp.getRootTaskId() == change.getTaskId());
            if (taskPanel == null) {
                continue;
            }
            String panelId = taskPanel.getPanelId();
            if (!conflictingPanelStates.containsKey(panelId)) {
                continue;
            }
            boolean isChildTaskVisible = conflictingPanelStates.get(panelId);
            if (isChildTaskVisible && change.getState().isAboveBarrier()) {
                orderedOpenConflictPanelIds.addLast(panelId);
            } else if (!isChildTaskVisible && !change.getState().isAboveBarrier()) {
                orderedCloseConflictPanelIds.addLast(panelId);
            }
        }
        // If a previous transition was merged into this one, some of the changes may not be
        // included in this transition. Triggers these first with our best-guess of a proper
        // resolution.
        Map<String, Boolean> unhandledConflictingPanelStates =
                conflictingPanelStates.entrySet().stream()
                        .filter(entry -> !orderedOpenConflictPanelIds.contains(entry.getKey()))
                        .filter(entry -> !orderedCloseConflictPanelIds.contains(entry.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        LinkedHashSet<String> unhandledCloseConflictPanelIds = new LinkedHashSet<>();
        LinkedHashSet<String> unhandledOpenConflictPanelIds = new LinkedHashSet<>();
        for (String unhandledConflictPanel : unhandledConflictingPanelStates.keySet()) {
            if (unhandledConflictingPanelStates.get(unhandledConflictPanel)) {
                unhandledOpenConflictPanelIds.addLast(unhandledConflictPanel);
            } else {
                unhandledCloseConflictPanelIds.addLast(unhandledConflictPanel);
            }
        }
        List<Event> conflictEvents = new ArrayList<>();
        // Order by:
        // - Unhandled close events
        // - Unhandled open events
        // - Ordered close events
        // - Home events
        // - Ordered open events
        unhandledCloseConflictPanelIds.forEach(panelId -> {
            conflictEvents.add(new Event.Builder(SYSTEM_TASK_CLOSE_EVENT_ID)
                    .setPanelId(panelId)
                    .build());
        });
        unhandledOpenConflictPanelIds.forEach(panelId -> {
            conflictEvents.add(new Event.Builder(SYSTEM_TASK_OPEN_EVENT_ID)
                    .setPanelId(panelId)
                    .build());
        });
        orderedCloseConflictPanelIds.forEach(panelId -> {
            conflictEvents.add(new Event.Builder(SYSTEM_TASK_CLOSE_EVENT_ID)
                    .setPanelId(panelId)
                    .build());
        });
        if (transitionInfo != null) {
            Event homeEvent = getHomeConflictEvent(transition, transitionInfo);
            if (homeEvent != null) {
                conflictEvents.add(homeEvent);
            }
        }
        orderedOpenConflictPanelIds.forEach(panelId -> {
            conflictEvents.add(new Event.Builder(SYSTEM_TASK_OPEN_EVENT_ID)
                    .setPanelId(panelId)
                    .build());
        });
        return conflictEvents;
    }

    /**
     * Detect if a home event is part of the current transition and was not part of the pending
     * transaction. If so, create a home event to be sent as part of the conflict resolution.
     */
    @Nullable
    private Event getHomeConflictEvent(@NonNull IBinder transition,
            @NonNull TransitionInfo transitionInfo) {
        PanelTransaction transaction;
        synchronized (mPendingPanelTransactions) {
            transaction = mPendingPanelTransactions.get(transition);
        }
        if (transaction == null) {
            return null;
        }
        if (transaction.getTransactionEvents().stream().anyMatch(
                e -> TextUtils.equals(e.getId(), SYSTEM_HOME_EVENT_ID))) {
            return null;
        }

        TransitionInfo.Change homeChange = transitionInfo.getChanges().stream().filter(
                this::isHomeOpenTransitionChange).findFirst().orElse(null);
        if (homeChange == null || homeChange.getTaskInfo() == null) {
            return null;
        }
        ComponentName component = mPanelUtils.getTaskComponentName(homeChange.getTaskInfo());
        String packageString = component != null ? component.getPackageName() : null;
        return new Event.Builder(SYSTEM_HOME_EVENT_ID)
                .setPackageName(packageString)
                .addApplicableDisplays(
                        mUserHelper.getDisplayIdsForUser(homeChange.getTaskInfo().userId))
                .build();
    }

    private boolean isHomeOpenTransitionChange(TransitionInfo.Change change) {
        if (change.getTaskInfo() == null) {
            return false;
        }
        if (change.getTaskInfo().getActivityType() != ACTIVITY_TYPE_HOME) {
            return false;
        }
        if (TransitionUtil.isOpeningMode(change.getMode())) {
            return true;
        }
        return (change.getMode() == TRANSIT_CHANGE) && ((change.getFlags() & FLAG_MOVED_TO_TOP)
                != 0);
    }

    /**
     * Returns a list of empty panel events for each panel specified in the supplied parameter.
     */
    private List<Event> getTriggerTaskPanelEmptyEvents(
            @NonNull List<String> emptyPanelKeys) {
        List<Event> emptyEvents = new ArrayList<>();
        emptyPanelKeys.forEach(panelId -> {
            emptyEvents.add(new Event.Builder(
                    SYSTEM_TASK_PANEL_EMPTY_EVENT_ID)
                    .setPanelId(panelId).build());
        });
        return emptyEvents;
    }

    /**
     * Returns a list of panel ids for which the panel will become empty after applying all the
     * changes in this transition.
     * Note that a panel will not be included if it has indicated that it will handle its own task
     * restart.
     */
    private List<String> getPanelsBecomingEmptyIds(@NonNull List<TaskStackStateChange> changes,
            @NonNull Map<String, Boolean> conflictingPanelStates) {
        // Use LinkedList for more efficient addFirst calls.
        List<String> taskPanelIds = new LinkedList<>();

        // First, check changes from WM belonging to this transition for empty panels
        for (int i = changes.size() - 1; i >= 0; i--) {
            TaskStackStateChange change = changes.get(i);

            TaskPanel taskPanel = mPanelUtils.getTaskPanel(
                    tp -> tp.getRootTaskId() == change.getTaskId() && !tp.hasRestart());
            if (taskPanel == null) {
                continue;
            }
            if (taskPanel.isRootTaskEmpty()) {
                taskPanelIds.add(taskPanel.getPanelId());
            }
        }

        // Second, check the conflicting panels to see if there are any panels where no change was
        // applied because the panel had an open in handleRequest but the Task already finished by
        // startAnimation.
        for (Map.Entry<String, Boolean> entry : conflictingPanelStates.entrySet()) {
            if (taskPanelIds.contains(entry.getKey())) {
                // Already handled above
                continue;
            }
            TaskPanel taskPanel = mPanelUtils.getTaskPanel(
                    tp -> tp.getPanelId().equals(entry.getKey()) && !tp.hasRestart());
            if (taskPanel == null) {
                continue;
            }
            if (taskPanel.isRootTaskEmpty()) {
                // Because these changes are less-known in order, assume they came earlier than
                // the known ones.
                taskPanelIds.addFirst(taskPanel.getPanelId());
            }
        }
        return taskPanelIds;
    }

    /**
     * Returns true if the AutoTaskStackState has a different state than the requested panel
     * transition (if provided) or the current TaskPanel state if the panel is not known as part
     * of the transition.
     */
    private boolean hasConflictingState(@NonNull AutoTaskStackState changedState,
            @NonNull TaskPanel tp, @Nullable Transition panelTransition) {
        Variant toVariant = panelTransition != null ? panelTransition.getToVariant() : null;
        boolean isVisible = toVariant != null ? toVariant.isVisible() : tp.isVisible();
        int layer = toVariant != null ? toVariant.getLayer() : tp.getLayer();
        Rect bounds = toVariant != null ? toVariant.getBounds() : tp.getBounds();
        boolean hasConflict = changedState.isAboveBarrier() != isVisible
                || changedState.getLayer() != layer
                || !changedState.getBounds().equals(bounds);
        if (hasConflict) {
            String debugString = "Transition conflict found on panel "
                    + tp.getPanelId()
                    + " | changedState: isAboveBarrier="
                    + changedState.isAboveBarrier() + " layer="
                    + changedState.getLayer() + " bounds="
                    + changedState.getBounds()
                    + " | panelState: isVisible=" + isVisible
                    + " layer=" + layer + " bounds=" + bounds;
            Log.e(TAG, debugString);
        }
        return hasConflict;
    }

    /**
     * Get a list of events that were supposed to be applied after this transition but due to
     * differing transition priority were actually applied first. These events can be re-applied
     * after this transition to correct the final state.
     */
    private List<Event> getPostAppliedEvents(IBinder transition) {
        List<Event> postAppliedEvents = new ArrayList<>();
        PanelTransaction transaction = getPendingPanelTransaction(transition);
        if (transaction == null) {
            return postAppliedEvents;
        }
        long currTimestamp = transaction.getBuildTime();

        synchronized (mHistoricallyAppliedEvents) {
            ConcurrentNavigableMap<Long, List<Event>> rangeView =
                    mHistoricallyAppliedEvents.subMap(currTimestamp, false,
                            SystemClock.elapsedRealtime(), true);

            for (List<Event> events : rangeView.values()) {
                postAppliedEvents.addAll(events);
            }

            // clean up old entries
            long clearanceThreshold = currTimestamp - HISTORICAL_EVENTS_RETENTION_THRESHOLD;
            mHistoricallyAppliedEvents.headMap(clearanceThreshold).clear();

            // add current events
            mHistoricallyAppliedEvents.put(currTimestamp, transaction.getTransactionEvents());
        }
        logIfDebuggable("Post applied events " + postAppliedEvents);
        return postAppliedEvents;
    }

    /**
     * Create a AutoTaskStackTransaction for a given PanelTransaction and set the appropriate
     * pending animators.
     */
    AutoTaskStackTransaction createAutoTaskStackTransaction(IBinder transition,
            PanelTransaction panelTransaction) {
        AutoTaskStackTransaction autoTaskStackTransaction = createAutoTaskStackTransaction(
                panelTransaction);

        synchronized (mPendingPanelTransactions) {
            mPendingPanelTransactions.put(transition, panelTransaction);
        }
        return autoTaskStackTransaction;
    }

    /**
     * See {@link #playPendingAnimations}
     */
    @ShellMainThread
    boolean playPendingAnimations(IBinder transition) {
        return playPendingAnimations(transition, /* finishCallback= */ null,
                /* finishTransaction= */ null, /* info */ null);
    }

    /**
     * Plays the animation in the pending list.
     *
     * @return true if any animations were started
     */
    @ShellMainThread
    boolean playPendingAnimations(IBinder transition,
            @Nullable Transitions.TransitionFinishCallback finishCallback,
            @Nullable SurfaceControl.Transaction finishTransaction,
            @Nullable List<TaskStackStateChange> changes) {
        PanelTransaction panelTransaction;
        synchronized (mPendingPanelTransactions) {
            panelTransaction = mPendingPanelTransactions.get(transition);
        }
        if (panelTransaction == null || panelTransaction.getAnimators().isEmpty()) {
            logIfDebuggable("No animations for transition " + transition);
            calculateFinishTransaction(finishTransaction, changes, panelTransaction);
            return false;
        }
        logIfDebuggable("playPendingAnimations: " + panelTransaction.getAnimators().size());
        Trace.beginSection(TAG + "#playPendingAnimations");

        // TODO(b/409121871): resolve potential glitch after stopping previous animation.
        stopOtherAnimations(transition);

        mRunningAnimatorSet = new AnimatorSet();
        mActiveTransition = transition;

        Runnable onAnimationEnd = () -> {
            mayFinishTransaction(finishCallback, panelTransaction, transition,
                    finishTransaction, changes);
        };

        startAnimationSet(mRunningAnimatorSet, panelTransaction,
                getAnimatorsToRun(panelTransaction), onAnimationEnd);
        Trace.endSection();
        return true;
    }

    @NonNull
    private List<Animator> getAnimatorsToRun(PanelTransaction panelTransaction) {
        long totalDuration = 0;
        List<Animator> animationToRun = new ArrayList<>();
        for (Map.Entry<String, Animator> entry : panelTransaction.getAnimators()) {
            Animator animator = entry.getValue();
            logIfDebuggable(
                    entry.getKey() + " duration for animator " + animator.getTotalDuration());
            totalDuration = Math.max(totalDuration, animator.getTotalDuration());
            animationToRun.add(animator);
        }

        logIfDebuggable("total duration " + totalDuration);
        animationToRun.add(createSurfaceAnimator(totalDuration, panelTransaction.getAnimators()));
        return animationToRun;
    }

    private void mayFinishTransaction(Transitions.TransitionFinishCallback finishCallback,
            PanelTransaction panelTransaction, IBinder transition,
            @Nullable SurfaceControl.Transaction finishTransaction,
            @Nullable List<TaskStackStateChange> changes) {
        logIfDebuggable("Animation set finished " + finishCallback);

        // Enforce the surface state for panels.
        AutoSurfaceTransaction autoSurfaceTransaction =
                mAutoSurfaceTransactionFactory.createTransaction(PANEL_TRANSACTION);
        for (Map.Entry<String, Transition> entry :
                panelTransaction.getPanelTransactionStates()) {
            SysUIPanel sysUiPanel = mPanelUtils.getSysUiPanel(
                    dp -> dp.getPanelId().equals(entry.getKey()));
            if (sysUiPanel == null) {
                continue;
            }
            if (panelTransaction.shouldMergePanelAnimation(entry.getKey())) {
                // update to the current state of the panel
                sysUiPanel.update(autoSurfaceTransaction, /* variant= */ null,
                        /* updateChildren= */ true);
            } else {
                Variant toVariant = entry.getValue().getToVariant();
                sysUiPanel.update(autoSurfaceTransaction, toVariant,
                        /* updateChildren= */ true);
            }
        }
        calculateFinishTransaction(finishTransaction, changes, panelTransaction);
        autoSurfaceTransaction.apply();

        synchronized (mPendingPanelTransactions) {
            mPendingPanelTransactions.remove(transition);
            mActiveTransition = null;
            mRunningAnimatorSet = null;
        }
        if (finishCallback != null) {
            logIfDebuggable("Finish the transition");
            finishCallback.onTransitionFinished(/* wct= */ null);
        }
        if (panelTransaction.getAnimationEndCallbackRunnable() != null) {
            panelTransaction.getAnimationEndCallbackRunnable().run();
        }
        Trace.endSection();

        for (Map.Entry<String, Animator> entry : panelTransaction.getAnimators()) {
            Transition trans = panelTransaction.getPanelTransactionState(entry.getKey());
            if (trans == null) {
                continue;
            }
            dispatchAnimationEndEvent(entry.getKey(), trans.getToVariant().getIdName());
        }
    }

    private void dispatchAnimationEndEvent(String panelId, String variantId) {
        logIfDebuggable("dispatching animation end event for panel " + panelId
                + " with variant " + variantId);
        PanelTransaction transaction = StateManager.handleEvent(new Event.Builder(
                SYSTEM_ON_ANIMATION_END_EVENT_ID)
                .setPanelId(panelId)
                .setToVariantId(variantId)
                .build());
        if (transaction != null) {
            startTransition(transaction);
        }
    }

    @ShellMainThread
    void mergeAnimation(@NonNull IBinder transition, @NonNull IBinder mergeTarget) {
        if (!isAnimationRunning() || mergeTarget != mActiveTransition) {
            return;
        }
        PanelTransaction transactionTransaction = getPendingPanelTransaction(transition);
        PanelTransaction mergeTransaction = getPendingPanelTransaction(mergeTarget);
        if (transactionTransaction == null || mergeTransaction == null
                || mRunningAnimatorSet == null) {
            stopRunningAnimation(mergeTarget);
            return;
        }
        mRunningAnimatorSet.pause();
        for (Map.Entry<String, Transition> entry :
                transactionTransaction.getPanelTransactionStates()) {
            Animator animator = mergeTransaction.getAnimators().stream()
                    .filter(mergeEntry -> mergeEntry.getKey().equals(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
            if (animator != null) {
                // merged animations should freeze their state and should no longer update - remove
                // all listeners.
                animator.removeAllListeners();
                if (animator instanceof ValueAnimator valueAnimator) {
                    valueAnimator.removeAllUpdateListeners();
                }
            }
            mergeTransaction.addPanelIdToAnimationMerge(entry.getKey());
        }
        mRunningAnimatorSet.cancel();
    }

    /**
     * Immediately ends any currently running animation set. This method is used when a new
     * animation needs to start and any existing one should be abruptly finished, for example,
     * when starting a direct animation without a shell transition token.
     */
    private void endAnimationsWithoutTransition() {
        logIfDebuggable("endAnimationsWithoutTransition");
        if (isAnimationRunning()) {
            logIfDebuggable("endAnimationsWithoutTransition: Ending running animatorSet "
                    + mRunningAnimatorSet.getCurrentPlayTime()
                    + ", active transition = " + mActiveTransition);
            mRunningAnimatorSet.end();
            // mRunningAnimatorSet and mActiveTransition will be nulled out
            // in the onAnimationEnd listener
        }
    }

    /**
     * Stops a running transition if the provided token is the currently running animation.
     *
     * @param transition The {@link IBinder} token for the incoming transition request. Used to
     *                   check if the currently running animation is for the same transition.
     * @return true if an animation was stopped
     */
    boolean stopRunningAnimation(@NonNull IBinder transition) {
        logIfDebuggable("stopRunningAnimation " + transition);
        if (isAnimationRunning() && transition == mActiveTransition) {
            logIfDebuggable("stopRunningAnimation: has running animatorSet "
                    + mRunningAnimatorSet.getCurrentPlayTime() + ", transition = "
                    + transition);
            mRunningAnimatorSet.end();
            return true;
        }
        return false;
    }

    /**
     * Stops any currently running animation if it belongs to a transition different from the
     * provided one.If an animation is running and its associated transition does not match the
     * incoming{@code transition} token, the animation set is immediately advanced to its end
     * state.
     *
     * @param transition The {@link IBinder} token for the incoming transition request. Used to
     *                   check if the currently running animation is for a different transition.
     * @return true if an animation was stopped
     */
    boolean stopOtherAnimations(@NonNull IBinder transition) {
        logIfDebuggable("stopOtherAnimations " + transition);
        if (isAnimationRunning() && transition != mActiveTransition) {
            logIfDebuggable("stopOtherAnimations: has running animatorSet "
                    + mRunningAnimatorSet.getCurrentPlayTime() + ", incoming transition = "
                    + transition + ", active transition = " + mActiveTransition);
            mRunningAnimatorSet.end();
            return true;
        }
        return false;
    }

    void calculateStartTransaction(@NonNull SurfaceControl.Transaction transaction,
            @NonNull List<TaskStackStateChange> changes) {
        calculateTransaction(transaction, changes, /* useCurrentState= */ panelId -> true);
    }

    void calculateFinishTransaction(@Nullable SurfaceControl.Transaction transaction,
            @Nullable List<TaskStackStateChange> changes,
            @Nullable PanelTransaction panelTransaction) {
        if (transaction == null || changes == null) {
            return;
        }

        // If PanelTransaction not supplied, assume false to use final variant for all panels
        Predicate<String> useCurrentState =
                panelTransaction != null ? panelTransaction::shouldMergePanelAnimation
                        : panelId -> false;
        calculateTransaction(transaction, changes, useCurrentState);
    }

    private void calculateTransaction(SurfaceControl.Transaction transaction,
            List<TaskStackStateChange> changes, Predicate<String> useCurrentState) {
        for (TaskStackStateChange change : changes) {
            Variant variant = null;
            TaskPanel taskPanel = mPanelUtils.getTaskPanel(
                    tp -> tp.getRootTaskId() == change.getTaskId());
            if (taskPanel == null) {
                logIfDebuggable("Task panel not found for taskstackchange"
                        + change.getTaskId());
                continue;
            }
            SurfaceControl leash = taskPanel.getLeash();
            if (leash == null) {
                logIfDebuggable("No leash for TaskPanel " + taskPanel);
                continue;
            }
            taskPanel.setLeash(leash);
            if (!useCurrentState.test(taskPanel.getPanelId())) {
                // Use the PanelState variant - it is up to date even before animation
                variant = mPanelUtils.getCurrentVariant(taskPanel.getPanelId());
                if (variant == null) {
                    Log.e(TAG, "Current Variant for panelState is null " + taskPanel.getPanelId());
                    continue;
                }
            }
            if (DEBUG) {
                Log.d(TAG, taskPanel.getPanelId()
                        + (useCurrentState.test(taskPanel.getPanelId())
                        ? "currentState" : "toVariant"));
            }
            taskPanel.update(transaction, variant);
        }
    }

    private static void logIfDebuggable(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }

    @VisibleForTesting
    boolean isAnimationRunning() {
        return mRunningAnimatorSet != null && mRunningAnimatorSet.isRunning();
    }

    @VisibleForTesting
    PanelTransaction getPendingPanelTransaction(IBinder transition) {
        synchronized (mPendingPanelTransactions) {
            return mPendingPanelTransactions.get(transition);
        }
    }

    private AutoTaskStackTransaction createAutoTaskStackTransaction(
            PanelTransaction panelTransaction) {
        return createAutoTaskStackTransaction(panelTransaction, Collections.emptySet());
    }

    private AutoTaskStackTransaction createAutoTaskStackTransaction(
            PanelTransaction panelTransaction, @NonNull Set<String> panelIdsToForceCurrentState) {
        AutoTaskStackTransaction autoTaskStackTransaction = new AutoTaskStackTransaction();
        for (Map.Entry<String, Transition> entry :
                panelTransaction.getPanelTransactionStates()) {
            Transition transition = entry.getValue();
            Variant toVariant = transition.getToVariant();
            applyAutoTaskStackState(autoTaskStackTransaction, entry.getKey(), toVariant);
        }

        for (String panelId : panelIdsToForceCurrentState) {
            if (panelTransaction.getPanelTransactionState(panelId) != null) {
                // panel is already changing
                continue;
            }
            logIfDebuggable("Forcing current panel state on " + panelId);
            applyAutoTaskStackState(autoTaskStackTransaction, panelId, /* toVariant= */ null);
        }

        calculateFocusedTaskStack(panelTransaction, autoTaskStackTransaction);

        return autoTaskStackTransaction;
    }

    private void applyAutoTaskStackState(@NonNull AutoTaskStackTransaction autoTaskStackTransaction,
            @NonNull String panelId, @Nullable Variant toVariant) {
        TaskPanel taskPanel = mPanelUtils.getTaskPanel(
                p -> p.getRootStack() != null && p.getPanelId().equals(panelId));
        if (taskPanel == null) {
            return;
        }

        Rect bounds;
        boolean isVisible;
        int layer;
        if (toVariant != null) {
            bounds = toVariant.getBounds();
            isVisible = toVariant.isVisible();
            layer = toVariant.getLayer();
        } else {
            Variant currentVariant = mPanelUtils.getCurrentVariant(panelId);
            if (currentVariant == null) {
                return;
            }
            bounds = currentVariant.getBounds();
            isVisible = currentVariant.isVisible();
            layer = currentVariant.getLayer();
        }

        AutoTaskStackState autoTaskStackState = new AutoTaskStackState(bounds, isVisible, layer);
        autoTaskStackTransaction.setTaskStackState(taskPanel.getRootStack().getId(),
                autoTaskStackState);
        if (mFlagManager.isEnabled(Flag.DisplayCompatibilityAutoDecorSafeRegion)) {
            // TODO (b/431223025): Add warnings about using caption and safe region separately
            Rect safeBounds =
                    toVariant != null ? toVariant.getSafeBounds() : taskPanel.getSafeBounds();
            autoTaskStackTransaction.setSafeRegionBounds(taskPanel.getRootStack().getId(),
                    safeBounds);
        }

        if (isVisible && taskPanel.isRootTaskEmpty()
                && mPanelUtils.isUserUnlocked()) {
            taskPanel.setBaseIntent(autoTaskStackTransaction);
            logIfDebuggable("Set base intent for " + taskPanel.getPanelId());
        }
    }

    /**
     * Determine focus using the following criteria (in order):
     * 1. If the trigger is a task being opened on a visible panel, focus that panel
     * 2. If one or more panels are becoming visible, focus the highest z-layer panel permitted
     * 3. If the current focused panel is becoming invisible, focus the highest z-layer panel
     * permitted that is still visible.
     */
    @VisibleForTesting
    void calculateFocusedTaskStack(PanelTransaction panelTransaction,
            AutoTaskStackTransaction autoTaskStackTransaction) {
        // 1. If the trigger is a task being opened on a visible panel, focus that panel
        for (Event event : panelTransaction.getTransactionEvents().reversed()) {
            if (!TextUtils.equals(event.getId(), SYSTEM_TASK_OPEN_EVENT_ID)) {
                continue;
            }
            String panelId = event.getPanelId();
            if (panelId != null) {
                TaskPanel taskPanel = mPanelUtils.getTaskPanel(
                        p -> p.getPanelId().equals(panelId) && p.getRootStack() != null);
                if (taskPanel != null) {
                    // ensure the panel is or will become visible
                    Transition toState = panelTransaction.getPanelTransactionState(
                            taskPanel.getPanelId());
                    boolean isVisible;
                    if (toState != null) {
                        isVisible = toState.getToVariant().isVisible();
                    } else {
                        Variant variant = mPanelUtils.getCurrentVariant(taskPanel.getPanelId());
                        if (variant != null) {
                            isVisible = variant.isVisible();
                        } else {
                            isVisible = taskPanel.isVisible();
                        }
                    }
                    if (isVisible) {
                        logIfDebuggable("Focusing TaskPanel=" + taskPanel.getPanelId()
                                + " for task launch");
                        autoTaskStackTransaction.setFocusedTaskStack(
                                taskPanel.getRootStack().getId());
                        return;
                    }
                }
            }
        }

        // If there are no panel changes, don't choose a focus and let the system handle it. If a
        // focus is manually set for this case, it may override an explicit user focus touch.
        if (panelTransaction.getPanelTransactionStates().isEmpty()) {
            logIfDebuggable("No panel transactions - don't override focus");
            return;
        }
        TaskPanel rootTaskToFocus = null;
        int rootTaskToFocusLayer = Integer.MIN_VALUE;
        // Set to true if the focus is for the purpose of a panel opening. This takes priority over
        // other aspects so once it's set to true for a selected panel, only panels who this is also
        // true for will be considered
        boolean isFocusingForPanelOpen = false;
        // Set to true if the currently focused panel is going from visible to invisible such that
        // a new focus must be found.
        boolean isCurrentFocusedPanelBecomingInvisible = false;

        for (Map.Entry<String, Transition> entry :
                panelTransaction.getPanelTransactionStates()) {
            Transition transition = entry.getValue();
            Variant toVariant = transition.getToVariant();
            TaskPanel taskPanel = mPanelUtils.getTaskPanel(
                    p -> p.getRootStack() != null && p.getPanelId().equals(entry.getKey()));
            if (taskPanel == null) {
                continue;
            }

            // To become the focus candidate, the panel must:
            //   - Be visible after the transition and focusable on transition.
            //   - Being not visible before transition (i.e. opening) takes priority over panels
            //     that are already open. Therefore, when iterating if a candidate is selected for
            //     opening, all future candidates must also be opening.
            //   - Have a higher layer than the current candidate.
            if (toVariant.isVisible() && toVariant.canFocusOnTransition()
                    && ((!isFocusingForPanelOpen && !taskPanel.isVisible())
                    || ((!isFocusingForPanelOpen || !taskPanel.isVisible())
                    && toVariant.getLayer() > rootTaskToFocusLayer))) {
                rootTaskToFocusLayer = toVariant.getLayer();
                rootTaskToFocus = taskPanel;
                isFocusingForPanelOpen = !taskPanel.isVisible();
            } else if (taskPanel.isVisible() && !toVariant.isVisible()
                    && taskPanel.getRootStack().getRootTaskInfo().isFocused) {
                isCurrentFocusedPanelBecomingInvisible = true;
            }
        }

        // If the current focus is going away and a new focus hasn't been found for a panel opening,
        // look at all unchanged panels to see if one of those should take focus.
        if (isCurrentFocusedPanelBecomingInvisible && !isFocusingForPanelOpen) {
            for (String unchangedPanelId : panelTransaction.getLockededPanelIdSet()) {
                Variant currentVariant = mPanelUtils.getCurrentVariant(unchangedPanelId);
                TaskPanel taskPanel = mPanelUtils.getTaskPanel(
                        p -> p.getPanelId().equals(unchangedPanelId));
                if (taskPanel != null && currentVariant != null && currentVariant.isVisible()
                        && currentVariant.canFocusOnTransition()
                        && currentVariant.getLayer() > rootTaskToFocusLayer) {
                    rootTaskToFocusLayer = currentVariant.getLayer();
                    rootTaskToFocus = taskPanel;
                }
            }
        }

        // If a task has been found, it should be focused only if the task is selected for a panel
        // open or if the current focus is becoming invisible (so another focus must be found).
        if (rootTaskToFocus != null
                && (isCurrentFocusedPanelBecomingInvisible || isFocusingForPanelOpen)) {
            String reason =
                    isFocusingForPanelOpen ? " for panel open" : " as highest focusable layer";
            logIfDebuggable(
                    "Focusing TaskPanel=" + rootTaskToFocus.getPanelId() + reason);
            autoTaskStackTransaction.setFocusedTaskStack(rootTaskToFocus.getRootStack().getId());
        } else {
            TaskPanel currentFocusedTaskPanel = mPanelUtils.getTaskPanel(
                    p -> p.getRootStack() != null && p.getRootStack().getRootTaskInfo().isFocused);
            if (currentFocusedTaskPanel == null) {
                return;
            }
            Variant currentVariant = mPanelUtils.getCurrentVariant(
                    currentFocusedTaskPanel.getPanelId());
            if (currentVariant != null && currentVariant.isVisible()) {
                logIfDebuggable(
                        "Maintaining focus on TaskPanel=" + currentFocusedTaskPanel.getPanelId());
                autoTaskStackTransaction.setFocusedTaskStack(
                        currentFocusedTaskPanel.getRootStack().getId());
            }
        }
    }

    private ValueAnimator createSurfaceAnimator(long duration,
            @NonNull Set<Map.Entry<String, Animator>> animators) {
        ValueAnimator surfaceAnimator = ValueAnimator.ofFloat(0, 1f);
        surfaceAnimator.setDuration(duration);
        surfaceAnimator.addUpdateListener(animation -> {
            onSurfaceAnimatorProgress(animators, String.valueOf(animation.getAnimatedFraction()));
        });
        surfaceAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(@NonNull Animator animation) {
                onSurfaceAnimatorProgress(animators, "Cancelled");
            }
        });
        return surfaceAnimator;
    }

    private void onSurfaceAnimatorProgress(@NonNull Set<Map.Entry<String, Animator>> animators,
            @NonNull String progress) {
        Trace.beginSection(TAG + "#updatePanelSurface");
        logIfDebuggable("Surface animation progress " + progress);
        AutoSurfaceTransaction autoSurfaceTransaction =
                mAutoSurfaceTransactionFactory.createTransaction(DECOR_TRANSACTION);

        SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
        for (Map.Entry<String, Animator> entry : animators) {
            String id = entry.getKey();
            Panel panel = PanelPool.getInstance().getPanel(p -> id.equals(p.getPanelId()));
            if (panel instanceof SysUIPanel sysUiPanel) {
                sysUiPanel.update(autoSurfaceTransaction, tx);
            }
        }
        //TODO(b/404959846): migrate to autoSurfaceTransaction here once api is added.
        tx.apply();
        autoSurfaceTransaction.apply();
        Trace.endSection();
    }
}
