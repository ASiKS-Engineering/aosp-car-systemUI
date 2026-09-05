/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.car.flexibleui;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.StatusBarManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.Display;

import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.statusbar.CommandQueue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class CarSystemBarElementStatusBarDisableControllerTest extends CarSysuiTestCase {
    private CarSystemBarElementStatusBarDisableController mController;
    @Mock
    private CommandQueue mCommandQueue;
    @Mock
    private ActivityManager mActivityManager;
    @Mock
    private CarSystemBarElementStatusBarDisableController.Listener mMockListener;
    @Mock
    private CarSystemBarElementStatusBarDisableController.Listener mMockListener2;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext.addMockSystemService(ActivityManager.class, mActivityManager);
        mController = new CarSystemBarElementStatusBarDisableController(mContext, mCommandQueue);
    }

    @Test
    public void onInit_doesNotRegisterCommandQueueCallback() {
        verify(mCommandQueue, never()).addCallback(any());
    }

    @Test
    public void onListenerAdded_firstListener_registersCommandQueueCallback() {
        mController.addListener(mMockListener, Display.DEFAULT_DISPLAY,
                StatusBarManager.DISABLE_HOME, StatusBarManager.DISABLE2_NONE, false);

        verify(mCommandQueue).addCallback(any());
    }

    @Test
    public void onListenerAdded_secondListener_doesNotRegisterCommandQueueCallback() {
        mController.addListener(mMockListener, Display.DEFAULT_DISPLAY,
                StatusBarManager.DISABLE_HOME, StatusBarManager.DISABLE2_NONE, false);
        mController.addListener(mMockListener2, Display.DEFAULT_DISPLAY,
                StatusBarManager.DISABLE_NONE, StatusBarManager.DISABLE2_NONE, true);

        verify(mCommandQueue, times(1)).addCallback(any());
    }

    @Test
    public void onListenerRemoved_notLastListener_doesNotUnregisterCommandQueueCallback() {
        mController.addListener(mMockListener, Display.DEFAULT_DISPLAY,
                StatusBarManager.DISABLE_HOME, StatusBarManager.DISABLE2_NONE, false);
        mController.addListener(mMockListener2, Display.DEFAULT_DISPLAY,
                StatusBarManager.DISABLE_NONE, StatusBarManager.DISABLE2_NONE, true);

        mController.removeListener(mMockListener);

        verify(mCommandQueue, never()).removeCallback(any());
    }

    @Test
    public void onListenerRemoved_lastListener_unregistersCommandQueueCallback() {
        mController.addListener(mMockListener, Display.DEFAULT_DISPLAY,
                StatusBarManager.DISABLE_HOME, StatusBarManager.DISABLE2_NONE, false);
        mController.addListener(mMockListener2, Display.DEFAULT_DISPLAY,
                StatusBarManager.DISABLE_NONE, StatusBarManager.DISABLE2_NONE, true);

        mController.removeListener(mMockListener);
        mController.removeListener(mMockListener2);

        verify(mCommandQueue).removeCallback(any());
    }

    @Test
    public void onListenerAddedRemovedAndAdded_callbackRegisteredUnregisteredAndReregistered() {
        // Verifies the complete lifecycle of the CommandQueue callback registration.
        // The callback should be added only when the first listener is added, removed when the
        // last listener is removed, and re-added if a new listener is added after all previous
        // ones were removed.

        // 1. Add the first listener.
        // Expect that the CommandQueue callback is registered.
        mController.addListener(mMockListener, Display.DEFAULT_DISPLAY,
                StatusBarManager.DISABLE_HOME, StatusBarManager.DISABLE2_NONE, false);
        verify(mCommandQueue).addCallback(any());

        // 2. Remove the only listener.
        // Expect that the CommandQueue callback is unregistered.
        mController.removeListener(mMockListener);
        verify(mCommandQueue).removeCallback(any());

        // 3. Add a listener again.
        // Expect that the CommandQueue callback is registered again.
        mController.addListener(mMockListener2, Display.DEFAULT_DISPLAY,
                StatusBarManager.DISABLE_NONE, StatusBarManager.DISABLE2_NONE, true);
        verify(mCommandQueue, times(2)).addCallback(any());
    }

    @Test
    public void onStatusBarDisableChanged_doesNotAffectListener_listenerNotNotified() {
        mController.addListener(mMockListener, Display.DEFAULT_DISPLAY,
                StatusBarManager.DISABLE_HOME, StatusBarManager.DISABLE2_NONE, false);
        when(mActivityManager.getLockTaskModeState()).thenReturn(
                ActivityManager.LOCK_TASK_MODE_LOCKED);

        CommandQueue.Callbacks callbacks = getCommandQueueCallback();
        callbacks.disable(Display.DEFAULT_DISPLAY, StatusBarManager.DISABLE_BACK,
                StatusBarManager.DISABLE2_NONE, false);

        verify(mMockListener, never()).onStatusBarDisabledStateChanged(anyBoolean());
    }

    @Test
    public void onStatusBarDisableChanged_disablesListener_listenerNotified() {
        mController.addListener(mMockListener, Display.DEFAULT_DISPLAY,
                StatusBarManager.DISABLE_HOME, StatusBarManager.DISABLE2_NONE, false);
        when(mActivityManager.getLockTaskModeState()).thenReturn(
                ActivityManager.LOCK_TASK_MODE_LOCKED);

        CommandQueue.Callbacks callbacks = getCommandQueueCallback();
        callbacks.disable(Display.DEFAULT_DISPLAY,
                StatusBarManager.DISABLE_BACK | StatusBarManager.DISABLE_HOME,
                StatusBarManager.DISABLE2_NONE, false);

        verify(mMockListener).onStatusBarDisabledStateChanged(true);
    }

    @Test
    public void onStatusBarDisableChanged_enablesListener_listenerNotified() {
        mController.addListener(mMockListener, Display.DEFAULT_DISPLAY,
                StatusBarManager.DISABLE_HOME, StatusBarManager.DISABLE2_NONE, false);
        when(mActivityManager.getLockTaskModeState()).thenReturn(
                ActivityManager.LOCK_TASK_MODE_LOCKED);
        CommandQueue.Callbacks callbacks = getCommandQueueCallback();
        callbacks.disable(Display.DEFAULT_DISPLAY,
                StatusBarManager.DISABLE_BACK | StatusBarManager.DISABLE_HOME,
                StatusBarManager.DISABLE2_NONE, false);
        clearInvocations(mMockListener);

        callbacks.disable(Display.DEFAULT_DISPLAY,
                StatusBarManager.DISABLE_NONE,
                StatusBarManager.DISABLE2_NONE, false);

        verify(mMockListener).onStatusBarDisabledStateChanged(false);
    }

    private CommandQueue.Callbacks getCommandQueueCallback() {
        ArgumentCaptor<CommandQueue.Callbacks> captor = ArgumentCaptor.forClass(
                CommandQueue.Callbacks.class);
        verify(mCommandQueue).addCallback(captor.capture());
        return captor.getValue();
    }
}
