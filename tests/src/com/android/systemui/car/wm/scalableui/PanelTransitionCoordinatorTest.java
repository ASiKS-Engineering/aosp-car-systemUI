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

import static com.android.systemui.car.wm.scalableui.PanelTransitionCoordinator.DECOR_TRANSACTION;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_TASK_OPEN_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_TASK_PANEL_EMPTY_EVENT_ID;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.WindowConfiguration;
import android.content.ComponentName;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.testing.TestableLooper;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.Event;
import com.android.car.scalableui.model.PanelTransaction;
import com.android.car.scalableui.model.Transition;
import com.android.car.scalableui.model.Variant;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.ShellSyncExecutor;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.flags.FlagManager;
import com.android.systemui.car.wm.CarWMUserHelper;
import com.android.systemui.car.wm.scalableui.panel.DecorPanel;
import com.android.systemui.car.wm.scalableui.panel.PanelUtils;
import com.android.systemui.car.wm.scalableui.panel.TaskPanel;
import com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants;
import com.android.wm.shell.automotive.AutoLayoutManager;
import com.android.wm.shell.automotive.AutoSurfaceTransaction;
import com.android.wm.shell.automotive.AutoSurfaceTransactionFactory;
import com.android.wm.shell.automotive.AutoTaskStackController;
import com.android.wm.shell.automotive.AutoTaskStackState;
import com.android.wm.shell.automotive.AutoTaskStackTransaction;
import com.android.wm.shell.automotive.RootTaskStack;
import com.android.wm.shell.automotive.TaskStackStateChange;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.transition.Transitions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper
@SmallTest
public class PanelTransitionCoordinatorTest extends CarSysuiTestCase {

    private static final String TEST_PANEL_1 = "test_panel_1";
    private static final String TEST_PANEL_2 = "test_panel_2";
    private static final int TEST_TASK_ID_1 = 1;
    private static final int TEST_TASK_ID_2 = 2;

    private PanelTransitionCoordinator mPanelTransitionCoordinator;
    private ShellExecutor mMainExecutor;

    @Mock
    private Transitions.TransitionFinishCallback mFinishCallback;
    @Mock
    private SurfaceControl.Transaction mFinishTransaction;
    @Mock
    private TransitionInfo mInfo;
    @Mock
    private AutoTaskStackController mAutoTaskStackController;
    @Mock
    private PanelUtils mPanelUtils;
    @Mock
    private AutoSurfaceTransactionFactory mAutoSurfaceTransactionFactory;
    @Mock
    private AutoSurfaceTransaction mAutoSurfaceTransaction;
    @Mock
    private AutoLayoutManager mAutoLayoutManager;
    @Mock
    private FlagManager mFlagManager;
    @Mock
    private AutoTaskStackTransaction mAutoTaskStackTransaction;
    @Mock
    private TaskPanel mTaskPanel;
    @Mock
    private CarWMUserHelper mCarWMUserHelper;

    private MockitoSession mSession;

    @Before
    public void setUp() {
        mSession = ExtendedMockito.mockitoSession()
                .initMocks(this)
                .mockStatic(StateManager.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
        mMainExecutor = new ShellSyncExecutor();
        mPanelTransitionCoordinator = new PanelTransitionCoordinator(
                mAutoTaskStackController, mAutoSurfaceTransactionFactory, mPanelUtils,
                mAutoLayoutManager, mMainExecutor, mFlagManager, mCarWMUserHelper);
        when(mAutoSurfaceTransactionFactory.createTransaction(anyString())).thenReturn(
                mAutoSurfaceTransaction);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void testStartTransition_addsPendingTransaction() {
        IBinder binder = new Binder();
        Animator animator = new ValueAnimator();
        when(mAutoTaskStackController.startTransition(any())).thenReturn(binder);
        PanelTransaction panelTransaction = new PanelTransaction.Builder()
                .addAnimator("testPanel", animator).setHasWindowChanges(true).build();

        mPanelTransitionCoordinator.startTransition(panelTransaction);

        PanelTransaction pendingTransaction =
                mPanelTransitionCoordinator.getPendingPanelTransaction(binder);
        assertThat(pendingTransaction).isNotNull();
        assertThat(pendingTransaction.getAnimators().size()).isEqualTo(1);
    }

    @Test
    public void testStartTransition_noWindowChanges_updatesPanelSurface() {
        // This test covers the scenario where a transaction does not involve window changes.
        // In this case, the panel surfaces should be updated directly without going through
        // the shell transition machinery. This path is wrapped by the Trace calls that were
        // added.
        PanelTransaction panelTransaction = new PanelTransaction.Builder()
                .setHasWindowChanges(false)
                .build();

        mPanelTransitionCoordinator.startTransition(panelTransaction);

        // Verify that a surface transaction is created and applied, which is the expected
        // behavior for a transaction with no window changes.
        verify(mAutoSurfaceTransactionFactory).createTransaction(DECOR_TRANSACTION);
        verify(mAutoSurfaceTransaction).apply();
    }

    @Test
    public void testPlayPendingAnimations_noTransaction_returnsFalse() {
        IBinder binder = new Binder();
        AtomicBoolean animationStarted = new AtomicBoolean(false);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            animationStarted.set(mPanelTransitionCoordinator.playPendingAnimations(binder,
                    mFinishCallback, mFinishTransaction, Collections.emptyList()));
        });

        assertThat(animationStarted.get()).isFalse();
    }

    @Test
    public void testPlayPendingAnimations() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1); // Latch for waiting
        IBinder binder = new Binder();
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(1000L);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                latch.countDown();
            }
        });
        PanelTransaction panelTransaction = new PanelTransaction.Builder()
                .addAnimator("testPanel", animator).build();
        mPanelTransitionCoordinator.createAutoTaskStackTransaction(binder, panelTransaction);

        AtomicBoolean animationStarted = new AtomicBoolean(false);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            animationStarted.set(mPanelTransitionCoordinator.playPendingAnimations(binder,
                    mFinishCallback, mFinishTransaction, Collections.emptyList()));
        });

        assertThat(animationStarted.get()).isTrue();
        assertThat(latch.await(/* timeout= */ 10, TimeUnit.SECONDS)).isTrue();
        assertThat(latch.getCount()).isEqualTo(0);
        assertThat(mPanelTransitionCoordinator.isAnimationRunning()).isFalse();
        // There may be a slight delay between the Animator receiving onAnimationEnd and the
        // AnimatorSet receiving onAnimationEnd.
        verify(mFinishCallback, timeout(1000)).onTransitionFinished(null);
    }

    @Test
    public void testStopRunningAnimationsIfNeed_differentTransition_stopAnimation()
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1); // Latch for waiting
        IBinder binder = new Binder();
        IBinder binder2 = new Binder();
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(5000L);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                latch.countDown();
            }
        });
        PanelTransaction panelTransaction = new PanelTransaction.Builder()
                .addAnimator("testPanel", animator).build();
        mPanelTransitionCoordinator.createAutoTaskStackTransaction(binder, panelTransaction);

        // Run the animation on the main looper
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPanelTransitionCoordinator.playPendingAnimations(binder, mFinishCallback,
                    mFinishTransaction, Collections.emptyList());
        });

        mPanelTransitionCoordinator.stopOtherAnimations(binder2);
        // onAnimationEnd should still be called when cancelled - wait for a small amount of time
        // and expect animation end callback to execute
        assertThat(latch.await(/* timeout= */ 1, TimeUnit.SECONDS)).isTrue();
        assertThat(latch.getCount()).isEqualTo(0);
        // There may be a slight delay between the Animator receiving onAnimationEnd and the
        // AnimatorSet receiving onAnimationEnd.
        verify(mFinishCallback, timeout(1000)).onTransitionFinished(null);
    }

    @Test
    public void testStartTransition_nullTransition_runsAnimationDirectly()
            throws InterruptedException {
        // This test covers the case where a transaction has window changes, but the shell
        // does not create a transition for it, so it must be animated directly.
        when(mAutoTaskStackController.startTransition(any())).thenReturn(null);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean startCallbackCalled = new AtomicBoolean(false);
        AtomicBoolean endCallbackCalled = new AtomicBoolean(false);

        Runnable startCallback = () -> startCallbackCalled.set(true);
        Runnable endCallback = () -> {
            endCallbackCalled.set(true);
            latch.countDown();
        };

        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(100L);

        PanelTransaction panelTransaction = new PanelTransaction.Builder()
                .addAnimator("testPanel", animator)
                .setAnimationStartCallbackRunnable(startCallback)
                .setAnimationEndCallbackRunnable(endCallback)
                .setHasWindowChanges(true)
                .build();

        mPanelTransitionCoordinator.startTransition(panelTransaction);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPanelTransitionCoordinator.startTransition(panelTransaction);
        });

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(startCallbackCalled.get()).isTrue();
        assertThat(endCallbackCalled.get()).isTrue();
    }

    @Test
    public void calculateFocusedTaskStack_taskOpenEventNoTransition_focusesPanel() {
        Event event = new Event.Builder(SYSTEM_TASK_OPEN_EVENT_ID).setPanelId(TEST_PANEL_1).build();
        setupTaskPanel(TEST_PANEL_1, TEST_TASK_ID_1, /* isVisible= */ true);
        when(mPanelUtils.getTaskPanel(any())).thenReturn(mTaskPanel);
        PanelTransaction panelTransaction = new PanelTransaction.Builder()
                .setTransactionEvents(Collections.singletonList(event))
                .build();

        mPanelTransitionCoordinator.calculateFocusedTaskStack(panelTransaction,
                mAutoTaskStackTransaction);

        verify(mAutoTaskStackTransaction).setFocusedTaskStack(TEST_TASK_ID_1);
    }

    @Test
    public void calculateFocusedTaskStack_taskOpenEventPanelBecomingVisible_focusesPanel() {
        Event event = new Event.Builder(SYSTEM_TASK_OPEN_EVENT_ID).setPanelId(TEST_PANEL_1).build();

        setupTaskPanel(TEST_PANEL_1, TEST_TASK_ID_1, /* isVisible= */ false);
        when(mPanelUtils.getTaskPanel(any())).thenReturn(mTaskPanel);
        Transition transition = mock(Transition.class);
        Variant variant = mock(Variant.class);
        when(transition.getToVariant()).thenReturn(variant);
        when(variant.isVisible()).thenReturn(true);
        PanelTransaction panelTransaction = new PanelTransaction.Builder()
                .addPanelTransaction(TEST_PANEL_1, transition)
                .setTransactionEvents(Collections.singletonList(event))
                .build();

        mPanelTransitionCoordinator.calculateFocusedTaskStack(panelTransaction,
                mAutoTaskStackTransaction);

        verify(mAutoTaskStackTransaction).setFocusedTaskStack(TEST_TASK_ID_1);
    }

    private void setupTaskPanel(String panelId, int taskId, boolean isVisible) {
        when(mTaskPanel.getPanelId()).thenReturn(panelId);

        RootTaskStack rootTaskStack = mock(RootTaskStack.class);
        if (taskId == -1) {
            when(mTaskPanel.getRootStack()).thenReturn(null);

        } else {
            when(rootTaskStack.getId()).thenReturn(taskId);
            when(mTaskPanel.getRootStack()).thenReturn(rootTaskStack);

        }

        when(mTaskPanel.isVisible()).thenReturn(isVisible);
        when(mTaskPanel.canFocusOnTransition()).thenReturn(true);
    }

    @Test
    public void testResetUnpreparedDecorPanel_resetsWhenDecorIsNullAndToVariantIsVisible() {
        DecorPanel decorPanel = mock(DecorPanel.class);
        when(decorPanel.getPanelId()).thenReturn(TEST_PANEL_1);
        when(decorPanel.getAutoDecor()).thenReturn(null);
        when(mPanelUtils.getDecorPanel(any())).thenReturn(decorPanel);

        Transition transition = mock(Transition.class);
        Variant toVariant = mock(Variant.class);
        when(toVariant.isVisible()).thenReturn(true);
        when(transition.getToVariant()).thenReturn(toVariant);

        PanelTransaction transaction = new PanelTransaction.Builder()
                .addPanelTransaction(TEST_PANEL_1, transition)
                .build();

        mPanelTransitionCoordinator.resetUnpreparedDecorPanel(transaction);

        verify(decorPanel).reset();
    }

    @Test
    public void testMergeAnimation_mergesAnimationCorrectly() {
        IBinder binder = new Binder();
        IBinder mergeBinder = new Binder();
        // Setup a running animation
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(1000L);
        Transition mockTransition = mock(Transition.class);
        Variant mockVariant = mock(Variant.class);
        when(mockTransition.getToVariant()).thenReturn(mockVariant);
        when(mockVariant.getIdName()).thenReturn("test_variant");
        PanelTransaction runningTransaction = new PanelTransaction.Builder()
                .addAnimator(TEST_PANEL_1, animator)
                .addPanelTransaction(TEST_PANEL_1, mockTransition)
                .build();
        mPanelTransitionCoordinator.createAutoTaskStackTransaction(binder, runningTransaction);

        // Start the animation
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPanelTransitionCoordinator.playPendingAnimations(binder,
                    mFinishCallback, mFinishTransaction, Collections.emptyList());
        });

        // Setup merge transaction
        PanelTransaction mergeTransaction = new PanelTransaction.Builder()
                .addPanelTransaction(TEST_PANEL_1, mock(Transition.class))
                .build();
        mPanelTransitionCoordinator.createAutoTaskStackTransaction(mergeBinder, mergeTransaction);

        // Perform merge
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPanelTransitionCoordinator.mergeAnimation(mergeBinder, binder);
        });

        assertThat(runningTransaction.shouldMergePanelAnimation(TEST_PANEL_1)).isTrue();
        assertThat(mPanelTransitionCoordinator.isAnimationRunning()).isFalse();
    }

    @Test
    public void testReconcileAutoTaskStackState_detectsConflicts_startsTransition() {
        // Setup conflict: TaskPanel visible, but AutoTaskStackState says invisible
        setupTaskPanel(TEST_PANEL_1, TEST_TASK_ID_1, /* isVisible= */ true);
        when(mTaskPanel.getLayer()).thenReturn(1);
        when(mTaskPanel.getBounds()).thenReturn(new Rect(0, 0, 100, 100));
        when(mPanelUtils.getTaskPanel(any())).thenReturn(mTaskPanel);

        AutoTaskStackState changedState = mock(AutoTaskStackState.class);
        when(changedState.isAboveBarrier()).thenReturn(false);
        when(changedState.getLayer()).thenReturn(1);
        when(changedState.getBounds()).thenReturn(new Rect(0, 0, 100, 100));
        List<TaskStackStateChange> changedTaskStacks = Collections.singletonList(
                new TaskStackStateChange(TEST_TASK_ID_1, changedState));

        // Mock StateManager to capture events and return a transaction
        PanelTransaction resultingTransaction = new PanelTransaction.Builder().build();
        ExtendedMockito.doReturn(resultingTransaction).when(
                () -> StateManager.handleEvents(any(), anyBoolean()));

        mPanelTransitionCoordinator.reconcileAutoTaskStackState(new Binder(), changedTaskStacks,
                mInfo);

        // Verify StateManager was called with events
        ExtendedMockito.verify(() -> StateManager.handleEvents(any(), anyBoolean()));
    }

    @Test
    public void testReconcileAutoTaskStackState_detectsPanelsBecomingEmpty_startsTransition() {
        setupTaskPanel(TEST_PANEL_1, TEST_TASK_ID_1, /* isVisible= */ true);
        when(mTaskPanel.getLayer()).thenReturn(1);
        when(mTaskPanel.getBounds()).thenReturn(new Rect(0, 0, 100, 100));
        when(mTaskPanel.hasRestart()).thenReturn(false);
        when(mTaskPanel.isRootTaskEmpty()).thenReturn(true);
        when(mPanelUtils.getTaskPanel(any())).thenReturn(mTaskPanel);

        AutoTaskStackState changedState = mock(AutoTaskStackState.class);
        when(changedState.isAboveBarrier()).thenReturn(false);
        when(changedState.getLayer()).thenReturn(1);
        when(changedState.getBounds()).thenReturn(new Rect(0, 0, 100, 100));
        List<TaskStackStateChange> changedTaskStacks = Collections.singletonList(
                new TaskStackStateChange(TEST_TASK_ID_1, changedState));

        PanelTransaction resultingTransaction = new PanelTransaction.Builder().build();
        ExtendedMockito.doReturn(resultingTransaction).when(
                () -> StateManager.handleEvents(any(), anyBoolean()));

        mPanelTransitionCoordinator.reconcileAutoTaskStackState(new Binder(), changedTaskStacks,
                mInfo);

        ArgumentCaptor<List<Event>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        ExtendedMockito.verify(
                () -> StateManager.handleEvents(eventsCaptor.capture(), anyBoolean()));
        List<Event> events = eventsCaptor.getValue();
        assertThat(events).isNotEmpty();
        assertThat(events.stream().anyMatch(
                event -> event.getId().equals(SYSTEM_TASK_PANEL_EMPTY_EVENT_ID))).isTrue();
    }

    @Test
    public void testReconcileAutoTaskStackState_requestedTransitionNotApplied_detectsConflict() {
        IBinder binder = new Binder();
        setupTaskPanel(TEST_PANEL_1, TEST_TASK_ID_1, /* isVisible= */ false);
        when(mPanelUtils.getTaskPanel(any())).thenReturn(mTaskPanel);

        // Requested state: visible = true
        Transition requestedTransition = mock(Transition.class);
        Variant toVariant = mock(Variant.class);
        when(toVariant.isVisible()).thenReturn(true);
        when(toVariant.getLayer()).thenReturn(1);
        when(toVariant.getBounds()).thenReturn(new Rect(0, 0, 100, 100));
        when(requestedTransition.getToVariant()).thenReturn(toVariant);

        PanelTransaction transaction = new PanelTransaction.Builder()
                .addPanelTransaction(TEST_PANEL_1, requestedTransition)
                .build();
        mPanelTransitionCoordinator.createAutoTaskStackTransaction(binder, transaction);

        // Current state: visible = false
        AutoTaskStackState currentState = mock(AutoTaskStackState.class);
        when(currentState.isAboveBarrier()).thenReturn(false);
        when(currentState.getLayer()).thenReturn(1);
        when(currentState.getBounds()).thenReturn(new Rect(0, 0, 100, 100));
        when(mAutoTaskStackController.getTaskStackStateMap()).thenReturn(
                Collections.singletonMap(TEST_TASK_ID_1, currentState));

        PanelTransaction resultingTransaction = new PanelTransaction.Builder().build();
        ExtendedMockito.doReturn(resultingTransaction).when(
                () -> StateManager.handleEvents(any(), anyBoolean()));

        // reconcile with NO changes reported by WM for TEST_PANEL_1
        mPanelTransitionCoordinator.reconcileAutoTaskStackState(binder, Collections.emptyList(),
                mInfo);

        ExtendedMockito.verify(() -> StateManager.handleEvents(any(), anyBoolean()));
    }

    @Test
    public void testReconcileAutoTaskStackState_unexpectedHomeTransition_detectsConflict() {
        IBinder binder = new Binder();
        // Setup a transaction without a home event
        PanelTransaction transaction = new PanelTransaction.Builder().build();
        mPanelTransitionCoordinator.createAutoTaskStackTransaction(binder, transaction);

        // Setup TransitionInfo with a home transition
        TransitionInfo.Change homeChange = mock(TransitionInfo.Change.class);
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.topActivityType = WindowConfiguration.ACTIVITY_TYPE_HOME;
        taskInfo.configuration.windowConfiguration.setActivityType(
                WindowConfiguration.ACTIVITY_TYPE_HOME);
        taskInfo.userId = 10;
        taskInfo.baseActivity = new ComponentName("com.test.launcher",
                "com.test.launcher.Launcher");
        when(homeChange.getTaskInfo()).thenReturn(taskInfo);
        when(homeChange.getMode()).thenReturn(WindowManager.TRANSIT_OPEN);
        when(mInfo.getChanges()).thenReturn(Collections.singletonList(homeChange));

        when(mCarWMUserHelper.getDisplayIdsForUser(10)).thenReturn(Collections.singletonList(0));

        // Current state for any panel to trigger reconcile logic
        setupTaskPanel(TEST_PANEL_1, TEST_TASK_ID_1, /* isVisible= */ true);
        when(mTaskPanel.getLayer()).thenReturn(1);
        when(mTaskPanel.getBounds()).thenReturn(new Rect(0, 0, 100, 100));
        when(mPanelUtils.getTaskPanel(any())).thenReturn(mTaskPanel);
        AutoTaskStackState changedState = mock(AutoTaskStackState.class);
        when(changedState.isAboveBarrier()).thenReturn(false); // trigger conflict
        when(changedState.getLayer()).thenReturn(1);
        when(changedState.getBounds()).thenReturn(new Rect(0, 0, 100, 100));
        List<TaskStackStateChange> changedTaskStacks = Collections.singletonList(
                new TaskStackStateChange(TEST_TASK_ID_1, changedState));

        ExtendedMockito.doReturn(transaction).when(
                () -> StateManager.handleEvents(any(), anyBoolean()));

        mPanelTransitionCoordinator.reconcileAutoTaskStackState(binder, changedTaskStacks,
                mInfo);

        ArgumentCaptor<List<Event>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        ExtendedMockito.verify(
                () -> StateManager.handleEvents(eventsCaptor.capture(), anyBoolean()));
        List<Event> events = eventsCaptor.getValue();
        assertThat(events.stream().anyMatch(
                event -> event.getId().equals(
                        SystemEventConstants.SYSTEM_HOME_EVENT_ID))).isTrue();
    }
}
