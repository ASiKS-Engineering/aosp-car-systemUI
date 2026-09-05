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

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.hardware.input.InputManager;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.VisibleForTesting;

import com.android.systemui.car.flexibleui.CarSystemBarElementController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.systembar.base.ButtonSelectionStateController;
import com.android.systemui.car.systembar.base.CarSystemBarButton;
import com.android.systemui.car.systembar.base.CarSystemBarButtonController;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

/**
 * A CarSystemBarElementController for handling AppGrid button interactions.
 */
public class AppGridButtonController extends CarSystemBarButtonController {

    private final AppGridButton mAppGridButton;
    private final InputManager mInputManager;
    private final ComponentName mRecentsComponentName;
    private final boolean mIsRecentsEntryPointEnabled;
    @VisibleForTesting
    TaskStackChangeListener mTaskStackChangeListener;
    private boolean mIsRecentsActive;

    @AssistedInject
    public AppGridButtonController(@Assisted CarSystemBarButton appGridButton,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            UserTracker userTracker, EventDispatcher eventDispatcher,
            ButtonSelectionStateController buttonSelectionStateController) {
        super(appGridButton, disableController, stateController, userTracker, eventDispatcher,
                buttonSelectionStateController);

        mAppGridButton = (AppGridButton) appGridButton;
        Context context = mAppGridButton.getContext();
        mInputManager = context.getSystemService(InputManager.class);
        mRecentsComponentName = ComponentName.unflattenFromString(
                context.getString(com.android.internal.R.string.config_recentsComponentName));
        mIsRecentsEntryPointEnabled = context.getResources()
                .getBoolean(R.bool.config_enableRecentsEntryPoint);
    }

    @Override
    @CallSuper
    protected void onInit() {
        super.onInit();

        mTaskStackChangeListener =
                new TaskStackChangeListener() {
                    @Override
                    public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo) {
                        if (mRecentsComponentName == null) {
                            return;
                        }
                        ComponentName topComponent =
                                taskInfo.topActivity != null
                                        ? taskInfo.topActivity
                                        : taskInfo.baseIntent.getComponent();
                        if (topComponent != null
                                && mRecentsComponentName
                                        .getClassName()
                                        .equals(topComponent.getClassName())) {
                            mIsRecentsActive = true;
                        } else {
                            mIsRecentsActive = false;
                        }
                        mAppGridButton.setIsRecentsActive(mIsRecentsActive);
                    }
                };
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        registerListeners();
    }

    private void registerListeners() {
        if (mTaskStackChangeListener != null) {
            TaskStackChangeListeners.getInstance()
                    .registerTaskStackListener(mTaskStackChangeListener);
        }

        View.OnClickListener defaultClickListener = mAppGridButton.getDefaultButtonClickListener();
        mAppGridButton.setOnClickListener(v -> {
            if (mIsRecentsActive) {
                toggleRecents();
                return;
            }
            if (defaultClickListener != null) {
                defaultClickListener.onClick(v);
            }
        });

        mAppGridButton.setOnLongClickListener(v -> {
            if (mIsRecentsActive) {
                return false;
            }
            return toggleRecents();
        });
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        if (mTaskStackChangeListener != null) {
            TaskStackChangeListeners.getInstance().unregisterTaskStackListener(
                    mTaskStackChangeListener);
        }
        mAppGridButton.setOnClickListener(null);
        mAppGridButton.setOnLongClickListener(null);
    }

    protected boolean toggleRecents() {
        return mIsRecentsEntryPointEnabled && mInputManager.injectInputEvent(
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_APP_SWITCH),
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<CarSystemBarButton,
                    AppGridButtonController> {
    }
}
