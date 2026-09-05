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


import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.app.WindowConfiguration;
import android.car.app.CarActivityManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.taskview.TaskViewBase;
import com.android.wm.shell.taskview.TaskViewTaskController;
import com.android.wm.shell.taskview.TaskViewTransitions;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public final class RootTaskMediatorTest extends CarSysuiTestCase {
    private RootTaskMediator mMediator;
    private final ShellTaskOrganizer mShellTaskOrganizer = mock(ShellTaskOrganizer.class);
    private final TaskViewTaskController mTaskViewTaskController = mock(
            TaskViewTaskController.class);
    private final TaskViewBase mTaskViewClientPart = mock(TaskViewBase.class);
    private final CarActivityManager mCarActivityManager = mock(CarActivityManager.class);
    private final TaskViewTransitions mTaskViewTransitions = mock(TaskViewTransitions.class);

    private ActivityManager.RunningTaskInfo createTask(int taskId) {
        ActivityManager.RunningTaskInfo taskInfo =
                new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.configuration.windowConfiguration.setWindowingMode(
                WINDOWING_MODE_MULTI_WINDOW);
        taskInfo.parentTaskId = INVALID_TASK_ID;
        taskInfo.token = mock(WindowContainerToken.class);
        taskInfo.isVisible = true;
        return taskInfo;
    }

    @Test
    public void createActivityArray_generatesCorrectArray() {
        int[] expectedActivityTypes = {
                WindowConfiguration.ACTIVITY_TYPE_STANDARD,
                WindowConfiguration.ACTIVITY_TYPE_HOME,
                WindowConfiguration.ACTIVITY_TYPE_RECENTS,
                WindowConfiguration.ACTIVITY_TYPE_ASSISTANT
        };

        int[] actualActivityTypes = RootTaskMediator.createActivityArray(/* embedHomeTask= */
                true, /* embedRecentsTask= */  true, /* embedAssistantTask= */ true);

        assertThat(expectedActivityTypes).isEqualTo(actualActivityTypes);
    }

    @Test
    public void onTaskAppeared_setsRootTask() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ true, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mCarActivityManager,
                mTaskViewTransitions,
                /* windowDecorViewModelOptional= */Optional.empty());
        ActivityManager.RunningTaskInfo taskInfo = createTask(/* taskId= */ 1);
        mMediator.onTaskAppeared(taskInfo, null);

        assertThat(mMediator.getRootTask()).isEqualTo(taskInfo);
    }

    @Test
    public void onTaskAppeared_withIsLaunchRoot_setsLaunchRootTask() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ true, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mCarActivityManager,
                mTaskViewTransitions,
                /* windowDecorViewModelOptional= */Optional.empty());
        ActivityManager.RunningTaskInfo rootTaskInfo = createTask(/* taskId= */ 1);

        mMediator.onTaskAppeared(rootTaskInfo, null);

        assertThat(mMediator.getRootTask()).isEqualTo(rootTaskInfo);
    }

    @Test
    public void onTaskAppeared_withIsLaunchRoot_callsCarActivityManager() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ true, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mCarActivityManager,
                mTaskViewTransitions,
                /* windowDecorViewModelOptional= */Optional.empty());
        ActivityManager.RunningTaskInfo launchRootTask = createTask(/* taskId= */ 1);
        mMediator.onTaskAppeared(launchRootTask, null);

        ActivityManager.RunningTaskInfo taskInfo = createTask(/* taskId= */ 2);
        mMediator.onTaskAppeared(taskInfo, null);

        verify(mCarActivityManager).onTaskAppeared(eq(taskInfo), isNull());
    }

    @Test
    public void onTaskAppeared_rootTaskExists_updatesTaskStack() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ false, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mCarActivityManager,
                mTaskViewTransitions,
                /* windowDecorViewModelOptional= */Optional.empty());
        ActivityManager.RunningTaskInfo rootTask = new ActivityManager.RunningTaskInfo();
        rootTask.taskId = 1;
        mMediator.onTaskAppeared(rootTask, null);

        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = 2;
        mMediator.onTaskAppeared(taskInfo, null);

        assertThat(mMediator.getTaskStack()).contains(taskInfo);
    }

    @Test
    public void onTaskInfoChanged_forRootTask_updatesShellPart() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ false, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mCarActivityManager,
                mTaskViewTransitions,
                /* windowDecorViewModelOptional= */Optional.empty());
        ActivityManager.RunningTaskInfo taskInfo = createTask(/* taskId= */ 1);
        mMediator.onTaskAppeared(taskInfo, null);

        mMediator.onTaskInfoChanged(taskInfo);

        verify(mTaskViewTaskController).onTaskInfoChanged(eq(taskInfo));
    }

    @Test
    public void onTaskInfoChanged_withIsLaunchRoot_callsCarActivityManager() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ true, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mCarActivityManager,
                mTaskViewTransitions,
                /* windowDecorViewModelOptional= */Optional.empty());
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = 1;

        mMediator.onTaskInfoChanged(taskInfo);

        verify(mCarActivityManager).onTaskInfoChanged(taskInfo);
    }

    @Test
    public void onTaskInfoChanged_multipleExisting_taskVisible_movesToFrontOfStack() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ true, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mCarActivityManager,
                mTaskViewTransitions,
                /* windowDecorViewModelOptional= */Optional.empty());
        ActivityManager.RunningTaskInfo rootTask = createTask(/* taskId= */ 99);
        mMediator.onTaskAppeared(rootTask, null);
        ActivityManager.RunningTaskInfo task1 = createTask(/* taskId= */ 1);
        mMediator.onTaskAppeared(task1, null);
        ActivityManager.RunningTaskInfo task2 = createTask(/* taskId= */ 2);
        mMediator.onTaskAppeared(task2, null);

        mMediator.onTaskInfoChanged(task1);

        assertThat(mMediator.getTaskStack()).containsExactly(task1, task2);
        verify(mTaskViewClientPart).onTaskInfoChanged(task1);
    }

    @Test
    public void onTaskVanished_forRootTask_updatesShellPart() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ false, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mCarActivityManager,
                mTaskViewTransitions,
                /* windowDecorViewModelOptional= */Optional.empty());
        ActivityManager.RunningTaskInfo taskInfo = createTask(/* taskId= */ 1);
        mMediator.onTaskAppeared(taskInfo, null);

        mMediator.onTaskVanished(taskInfo);

        verify(mTaskViewTaskController).onTaskVanished(eq(taskInfo));
    }

    @Test
    public void onTaskVanished_withIsLaunchRoot_callsCarActivityManager() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ true, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */ true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mCarActivityManager,
                mTaskViewTransitions,
                /* windowDecorViewModelOptional= */Optional.empty());
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = 1;
        mMediator.onTaskAppeared(taskInfo, null);

        mMediator.onTaskVanished(taskInfo);

        verify(mCarActivityManager).onTaskVanished(taskInfo);
    }

    @Test
    public void onTaskVanished_multipleExistingTasks_removesFromTaskStack() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */true, false, false,
                true, mShellTaskOrganizer, mTaskViewTaskController, mTaskViewClientPart,
                mCarActivityManager, mTaskViewTransitions,
                /* windowDecorViewModelOptional= */Optional.empty());
        ActivityManager.RunningTaskInfo rootTask = createTask(/* taskId= */ 99);
        mMediator.onTaskAppeared(rootTask, null);
        ActivityManager.RunningTaskInfo task1 = createTask(/* taskId= */ 1);
        mMediator.onTaskAppeared(task1, null);
        ActivityManager.RunningTaskInfo task2 = createTask(/* taskId= */ 2);
        mMediator.onTaskAppeared(task2, null);

        mMediator.onTaskVanished(task1);

        assertThat(mMediator.getTaskStack()).containsExactly(task2);
        verify(mTaskViewClientPart).onTaskVanished(task1);
    }

    @Test
    public void release_clearsRootTask() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ false, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */ true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mCarActivityManager,
                mTaskViewTransitions,
                /* windowDecorViewModelOptional= */Optional.empty());
        ActivityManager.RunningTaskInfo rootTask = new ActivityManager.RunningTaskInfo();
        rootTask.taskId = 1;
        mMediator.onTaskAppeared(rootTask, null);

        mMediator.release();

        assertThat(mMediator.getRootTask()).isNull();
    }

    @Test
    public void release_withIsLaunchRoot_clearsLaunchRootTask() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ false, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */ true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mCarActivityManager,
                mTaskViewTransitions,
                /* windowDecorViewModelOptional= */Optional.empty());
        ActivityManager.RunningTaskInfo rootTask = createTask(/* taskId= */ 1);
        mMediator.onTaskAppeared(rootTask, null);
        ActivityManager.RunningTaskInfo task = createTask(/* taskId= */ 2);
        mMediator.onTaskAppeared(task, null);

        mMediator.release();

        assertThat(mMediator.getRootTask()).isNull();
        assertThat(mMediator.getTaskStack()).isEmpty();
    }

    @Test
    public void onBackOnTaskRoot_withMultipleTasks_removesTopTask() {
        // Arrange: Create a mediator with a root task and two child tasks.
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ true, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mCarActivityManager,
                mTaskViewTransitions,
                /* windowDecorViewModelOptional= */Optional.empty());
        ActivityManager.RunningTaskInfo rootTask = createTask(/* taskId= */ 99);
        mMediator.onTaskAppeared(rootTask, null);
        ActivityManager.RunningTaskInfo task1 = createTask(/* taskId= */ 1);
        mMediator.onTaskAppeared(task1, null);
        ActivityManager.RunningTaskInfo task2 = createTask(/* taskId= */ 2);
        mMediator.onTaskAppeared(task2, null);

        // Act: Trigger the back press callback.
        mMediator.onBackOnTaskRoot(rootTask, /* isFromBackPress= */ true,
                /* isOptInOnBackInvoked= */ false, /* hasOpaqueSibling= */ false);

        // Assert: Verify that a transition is started to remove the top task.
        ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mTaskViewTransitions).startInstantTransition(anyInt(), wctCaptor.capture());
        // The test can't inspect the content of WindowContainerTransaction,
        // but this verifies that a transaction is started to handle the back press.
    }

    @Test
    public void onBackOnTaskRoot_withOneTask_doesNothing() {
        // Arrange: Create a mediator with a root task and one child task.
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ true, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mCarActivityManager,
                mTaskViewTransitions,
                /* windowDecorViewModelOptional= */Optional.empty());
        ActivityManager.RunningTaskInfo rootTask = createTask(/* taskId= */ 99);
        mMediator.onTaskAppeared(rootTask, null);
        ActivityManager.RunningTaskInfo task1 = createTask(/* taskId= */ 1);
        mMediator.onTaskAppeared(task1, null);

        // Act: Trigger the back press callback.
        mMediator.onBackOnTaskRoot(rootTask, /* isFromBackPress= */ true,
                /* isOptInOnBackInvoked= */ false, /* hasOpaqueSibling= */ false);

        // Assert: Verify that no transition is started as there is only one task in the stack.
        verify(mTaskViewTransitions, never()).startInstantTransition(anyInt(),
                any(WindowContainerTransaction.class));
    }

    @Test
    public void onBackOnTaskRoot_withEmptyStack_doesNothing() {
        // Arrange: Create a mediator with only a root task.
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ true, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mCarActivityManager,
                mTaskViewTransitions,
                /* windowDecorViewModelOptional= */Optional.empty());
        ActivityManager.RunningTaskInfo rootTask = createTask(/* taskId= */ 99);
        mMediator.onTaskAppeared(rootTask, null);

        // Act: Trigger the back press callback.
        mMediator.onBackOnTaskRoot(rootTask, /* isFromBackPress= */ true,
                /* isOptInOnBackInvoked= */ false, /* hasOpaqueSibling= */ false);

        // Assert: Verify that no transition is started as the task stack is empty.
        verify(mTaskViewTransitions, never()).startInstantTransition(anyInt(),
                any(WindowContainerTransaction.class));
    }
}
