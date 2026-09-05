/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.car.systembar.appgrid;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.hardware.input.InputManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableResources;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.systembar.base.ButtonSelectionStateController;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@SmallTest
public class AppGridButtonControllerTest extends CarSysuiTestCase {
    private static final String RECENTS_ACTIVITY_NAME =
            "com.android.car.carlauncher/.recents.CarRecentsActivity";
    private static final String DIALER_ACTIVITY_NAME = "com.android.car.dialer/.ui.TelecomActivity";

    private AppGridButtonController mAppGridButtonController;
    private TaskStackChangeListener mTaskStackChangeListener;
    private TestableResources mTestableResources;

    @Mock
    private AppGridButton mAppGridButton;
    @Mock
    private CarSystemBarElementStatusBarDisableController mDisableController;
    @Mock
    private CarSystemBarElementStateController mStateController;
    @Mock
    private UserTracker mUserTracker;
    @Mock
    private EventDispatcher mEventDispatcher;
    @Mock
    private ButtonSelectionStateController mButtonSelectionStateController;

    @Mock
    private InputManager mInputManager;
    @Mock
    private ActivityManager.RunningTaskInfo mRecentsRunningTaskInfo;
    @Mock
    private ActivityManager.RunningTaskInfo mDialerRunningTaskInfo;
    @Mock
    private ActivityManager.RunningTaskInfo mNoTopComponentRunningTaskInfo;
    @Mock
    private Intent mDialerBaseIntent;
    @Mock
    private Intent mBaseIntentWithNoComponent;
    @Mock
    private View.OnClickListener mOnClickListener;

    @Captor
    ArgumentCaptor<View.OnLongClickListener> mOnLongClickListenerCaptor;
    @Captor
    ArgumentCaptor<View.OnClickListener> mOnClickListenerCaptor;
    @Captor
    ArgumentCaptor<TaskStackChangeListener> mTaskStackChangeListenerCaptor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTestableResources = mContext.getOrCreateTestableResources();
        mTestableResources.addOverride(R.bool.config_enableRecentsEntryPoint, true);
        mContext = spy(mContext);
        when(mInputManager.injectInputEvent(any(InputEvent.class), anyInt())).thenReturn(true);
        doReturn(mInputManager).when(mContext).getSystemService(InputManager.class);
        mTestableResources.addOverride(com.android.internal.R.string.config_recentsComponentName,
                RECENTS_ACTIVITY_NAME);
        mRecentsRunningTaskInfo.topActivity = ComponentName.unflattenFromString(
                RECENTS_ACTIVITY_NAME);
        mDialerRunningTaskInfo.topActivity = ComponentName.unflattenFromString(
                DIALER_ACTIVITY_NAME);
        mNoTopComponentRunningTaskInfo.baseIntent = mBaseIntentWithNoComponent;
        when(mDialerBaseIntent.getComponent())
                .thenReturn(ComponentName.unflattenFromString(DIALER_ACTIVITY_NAME));

        when(mAppGridButton.getContext()).thenReturn(mContext);
        when(mAppGridButton.getDefaultButtonClickListener()).thenReturn(mOnClickListener);

        mAppGridButtonController = new AppGridButtonController(mAppGridButton, mDisableController,
                mStateController, mUserTracker, mEventDispatcher, mButtonSelectionStateController);

        mAppGridButtonController.onInit();
        mAppGridButtonController.onViewAttached();

        // Capture listeners set during init
        verify(mAppGridButton).setOnClickListener(mOnClickListenerCaptor.capture());
        verify(mAppGridButton).setOnLongClickListener(mOnLongClickListenerCaptor.capture());

        // Capture TaskStackChangeListener
        TaskStackChangeListeners taskStackChangeListeners = TaskStackChangeListeners.getInstance();
        taskStackChangeListeners.unregisterTaskStackListener(mTaskStackChangeListener);
        mAppGridButtonController.onViewDetached(); // Detach to reset

        org.mockito.Mockito.clearInvocations(mAppGridButton);

        mAppGridButtonController = new AppGridButtonController(mAppGridButton, mDisableController,
                mStateController, mUserTracker, mEventDispatcher, mButtonSelectionStateController);
        mAppGridButtonController.onInit();
        mAppGridButtonController.onViewAttached();

        verify(mAppGridButton).setOnClickListener(mOnClickListenerCaptor.capture());
        verify(mAppGridButton).setOnLongClickListener(mOnLongClickListenerCaptor.capture());
    }

    @After
    public void tearDown() {
        if (mAppGridButtonController != null) {
            mAppGridButtonController.onViewDetached();
        }
    }


    @Test
    public void recents_movedToFront_recentsActive() {
        TaskStackChangeListener listener = mAppGridButtonController.mTaskStackChangeListener;
        listener.onTaskMovedToFront(mDialerRunningTaskInfo);
        listener.onTaskMovedToFront(mRecentsRunningTaskInfo);

        verify(mAppGridButton).setIsRecentsActive(true);
    }

    @Test
    public void dialer_movedToFront_recentsNotActive() {
        TaskStackChangeListener listener = mAppGridButtonController.mTaskStackChangeListener;
        listener.onTaskMovedToFront(mRecentsRunningTaskInfo);
        listener.onTaskMovedToFront(mDialerRunningTaskInfo);

        verify(mAppGridButton).setIsRecentsActive(false);
    }

    @Test
    public void noTopActivityDialerTask_movedToFront_recentsNotActive() {
        mDialerRunningTaskInfo.topActivity = null;
        mDialerRunningTaskInfo.baseIntent = mDialerBaseIntent;

        TaskStackChangeListener listener = mAppGridButtonController.mTaskStackChangeListener;
        listener.onTaskMovedToFront(mRecentsRunningTaskInfo);
        listener.onTaskMovedToFront(mDialerRunningTaskInfo);

        verify(mAppGridButton).setIsRecentsActive(false);
    }

    @Test
    public void noTopActivityAndNoBaseIntentTask_movedToFront_recentsNotActive() {
        TaskStackChangeListener listener = mAppGridButtonController.mTaskStackChangeListener;
        listener.onTaskMovedToFront(mRecentsRunningTaskInfo);
        listener.onTaskMovedToFront(mNoTopComponentRunningTaskInfo);

        verify(mAppGridButton).setIsRecentsActive(false);
    }

    @Test
    public void onLongClick_recentsNotActive_returnsTrueAndInjectsEvent() {
        // Dialer on front -> Recents not active
        TaskStackChangeListener listener = mAppGridButtonController.mTaskStackChangeListener;
        listener.onTaskMovedToFront(mDialerRunningTaskInfo);

        boolean result = mOnLongClickListenerCaptor.getValue().onLongClick(mAppGridButton);

        assertThat(result).isTrue();
        verify(mInputManager, times(1))
                .injectInputEvent(argThat(this::isRecentsKeyEvent), anyInt());
    }

    @Test
    public void onLongClick_configSetToFalse_returnsFalse() {
        mTestableResources.addOverride(R.bool.config_enableRecentsEntryPoint, false);
        AppGridButtonController controller = new AppGridButtonController(mAppGridButton,
                mDisableController, mStateController, mUserTracker, mEventDispatcher,
                mButtonSelectionStateController);
        controller.onInit();
        controller.onViewAttached();
        verify(mAppGridButton, times(2))
                .setOnLongClickListener(mOnLongClickListenerCaptor.capture());

        TaskStackChangeListener listener = mAppGridButtonController.mTaskStackChangeListener;
        listener.onTaskMovedToFront(mDialerRunningTaskInfo);

        boolean result = mOnLongClickListenerCaptor.getValue().onLongClick(mAppGridButton);

        assertThat(result).isFalse();
        verify(mInputManager, never()).injectInputEvent(argThat(this::isRecentsKeyEvent), anyInt());
    }

    @Test
    public void onLongClick_recentsActive_returnsFalse() {
        TaskStackChangeListener listener = mAppGridButtonController.mTaskStackChangeListener;
        listener.onTaskMovedToFront(mRecentsRunningTaskInfo);

        boolean result = mOnLongClickListenerCaptor.getValue().onLongClick(mAppGridButton);

        assertThat(result).isFalse();
        verify(mInputManager, never()).injectInputEvent(argThat(this::isRecentsKeyEvent), anyInt());
    }

    @Test
    public void onClick_recentsNotActive_callsDefaultClickListener() {
        TaskStackChangeListener listener = mAppGridButtonController.mTaskStackChangeListener;
        listener.onTaskMovedToFront(mDialerRunningTaskInfo);

        mOnClickListenerCaptor.getValue().onClick(mAppGridButton);

        verify(mOnClickListener, times(1)).onClick(mAppGridButton);
        verify(mInputManager, never()).injectInputEvent(argThat(this::isRecentsKeyEvent), anyInt());
    }

    @Test
    public void onClick_recentsActive_keyEventSentAndDefaultNotCalled() {
        TaskStackChangeListener listener = mAppGridButtonController.mTaskStackChangeListener;
        listener.onTaskMovedToFront(mRecentsRunningTaskInfo);

        mOnClickListenerCaptor.getValue().onClick(mAppGridButton);

        verify(mOnClickListener, never()).onClick(any());
        verify(mInputManager, times(1))
                .injectInputEvent(argThat(this::isRecentsKeyEvent), anyInt());
    }

    private boolean isRecentsKeyEvent(InputEvent event) {
        return event instanceof KeyEvent
                && ((KeyEvent) event).getKeyCode() == KeyEvent.KEYCODE_APP_SWITCH;
    }
}
