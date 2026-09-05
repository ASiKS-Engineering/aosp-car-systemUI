/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.car.wm;

import android.app.ActivityManager;
import android.content.Context;
import android.util.ArraySet;
import android.util.Log;
import android.view.SurfaceControl;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import com.android.systemui.car.CarServiceProvider;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.automotive.AutoTaskRepository;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.fullscreen.FullscreenTaskListener;
import com.android.wm.shell.recents.RecentTasksController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.taskview.TaskViewTransitions;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;

import java.util.Optional;

/**
 * The Car version of {@link FullscreenTaskListener}, which reports Task lifecycle to CarService
 * only when the {@link CarSystemUIProxyImpl} should be registered.
 *
 * Please note that this reports FULLSCREEN + MULTI_WINDOW tasks to the CarActivityService but
 * excludes the tasks that are associated with a taskview.
 *
 * Listeners can also be added to receive task changes for FULLSCREEN + MULTI_WINDOW tasks.
 *
 * <p>When {@link CarSystemUIProxyImpl#shouldRegisterCarSystemUIProxy(Context)} returns true, the
 * task organizer is registered by the system ui alone and hence SystemUI is responsible to act as
 * a task monitor for the car service.
 *
 * <p>On legacy system where a task organizer is registered by system ui and car launcher both,
 * this listener will not forward task lifecycle to car service as this would end up sending
 * multiple task events to the car service.
 */
public class CarFullscreenTaskMonitorListener extends FullscreenTaskListener {
    static final String TAG = CarFullscreenTaskMonitorListener.class.getSimpleName();
    static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private final ShellTaskOrganizer mShellTaskOrganizer;
    // TODO(b/395767437): Add task listener for fullscreen and multi window mode in task repository
    private final AutoTaskRepository mTaskRepository;
    @GuardedBy("mLock")
    private final ArraySet<OnTaskChangeListener> mTaskListeners = new ArraySet<>();
    private final Object mLock = new Object();

    private final Optional<WindowDecorViewModel> mWindowDecorViewModelOptional;
    private final ShellTaskOrganizer.TaskListener mMultiWindowTaskListener =
            new ShellTaskOrganizer.TaskListener() {
                @Override
                public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo,
                        SurfaceControl leash) {
                    mTaskRepository.onTaskAppeared(taskInfo, leash);
                    synchronized (mLock) {
                        for (OnTaskChangeListener listener : mTaskListeners) {
                            listener.onTaskAppeared(taskInfo);
                        }
                    }
                    if (mWindowDecorViewModelOptional.isPresent()) {
                        SurfaceControl.Transaction t = new SurfaceControl.Transaction();
                        mWindowDecorViewModelOptional.get().onTaskOpening(taskInfo, leash, t, t);
                        t.apply();
                    }
                }

                @Override
                public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
                    mTaskRepository.onTaskChanged(taskInfo);
                    synchronized (mLock) {
                        for (OnTaskChangeListener listener : mTaskListeners) {
                            listener.onTaskInfoChanged(taskInfo);
                        }
                    }

                    if (mWindowDecorViewModelOptional.isPresent()) {
                        mWindowDecorViewModelOptional.get().onTaskInfoChanged(taskInfo);
                    }
                }

                @Override
                public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
                    mTaskRepository.onTaskVanished(taskInfo);
                    synchronized (mLock) {
                        for (OnTaskChangeListener listener : mTaskListeners) {
                            listener.onTaskVanished(taskInfo);
                        }
                    }

                    if (mWindowDecorViewModelOptional.isPresent()) {
                        mWindowDecorViewModelOptional.get().destroyWindowDecoration(taskInfo);
                    }
                }
            };

    public CarFullscreenTaskMonitorListener(
            Context context,
            CarServiceProvider carServiceProvider,
            ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer,
            SyncTransactionQueue syncQueue,
            Optional<RecentTasksController> recentTasksOptional,
            Optional<WindowDecorViewModel> windowDecorViewModelOptional,
            TaskViewTransitions taskViewTransitions,
            AutoTaskRepository taskRepository) {
        super(shellInit, shellTaskOrganizer, syncQueue, recentTasksOptional,
                windowDecorViewModelOptional, Optional.empty(), Optional.empty());
        mShellTaskOrganizer = shellTaskOrganizer;
        mTaskRepository = taskRepository;

        shellInit.addInitCallback(
                () -> mShellTaskOrganizer.addListenerForType(mMultiWindowTaskListener,
                        ShellTaskOrganizer.TASK_LISTENER_TYPE_MULTI_WINDOW),
                this);
        mWindowDecorViewModelOptional = windowDecorViewModelOptional;
    }

    @Override
    public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl leash) {
        super.onTaskAppeared(taskInfo, leash);
        mTaskRepository.onTaskAppeared(taskInfo, leash);
        synchronized (mLock) {
            for (OnTaskChangeListener listener : mTaskListeners) {
                listener.onTaskAppeared(taskInfo);
            }
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
        super.onTaskInfoChanged(taskInfo);
        mTaskRepository.onTaskChanged(taskInfo);
        synchronized (mLock) {
            for (OnTaskChangeListener listener : mTaskListeners) {
                listener.onTaskInfoChanged(taskInfo);
            }
        }
    }

    @Override
    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        super.onTaskVanished(taskInfo);
        mTaskRepository.onTaskVanished(taskInfo);
        synchronized (mLock) {
            for (OnTaskChangeListener listener : mTaskListeners) {
                listener.onTaskVanished(taskInfo);
            }
        }
        if (mWindowDecorViewModelOptional.isPresent()) {
            mWindowDecorViewModelOptional.get().destroyWindowDecoration(taskInfo);
        }
    }

    /**
     * Adds a listener for tasks.
     */
    public void addTaskListener(@NonNull OnTaskChangeListener listener) {
        synchronized (mLock) {
            mTaskListeners.add(listener);
        }
    }

    /**
     * Remove a listener for tasks.
     */
    public boolean removeTaskListener(@NonNull OnTaskChangeListener listener) {
        synchronized (mLock) {
            return mTaskListeners.remove(listener);
        }
    }

    /**
     * Limited scope interface to give information about task changes.
     */
    public interface OnTaskChangeListener {
        /**
         * Gives the information of the task that just appeared
         */
        void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo);

        /**
         * Gives the information of the task that just changed
         */
        void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo);

        /**
         * Gives the information of the task that just vanished
         */
        void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo);
    }
}
