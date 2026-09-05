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

package com.android.systemui.car.systembar.usernamepanel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.flexibleui.CarSystemBarElementController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.flexibleui.layout.CarSystemBarTextView;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.settings.UserTracker;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.concurrent.Executor;

/**
 * Controls user name TextView for the current logged in user.
 */
public final class UserNameTextViewController extends
        CarSystemBarElementController<CarSystemBarTextView> {
    private final Executor mMainExecutor;
    private final UserTracker mUserTracker;
    private final UserManager mUserManager;
    private final BroadcastDispatcher mBroadcastDispatcher;
    private boolean mUserLifecycleListenerRegistered;

    private final UserTracker.Callback mUserChangedCallback =
            new UserTracker.Callback() {
                @Override
                public void onUserChanged(int newUser, Context userContext) {
                    updateUser(newUser);
                }
            };

    private final BroadcastReceiver mUserUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateUser(mUserTracker.getUserId());
        }
    };

    @AssistedInject
    protected UserNameTextViewController(@Assisted CarSystemBarTextView view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            @Main Executor mainExecutor, UserTracker userTracker, UserManager userManager,
            BroadcastDispatcher broadcastDispatcher) {
        super(view, disableController, stateController);
        mMainExecutor = mainExecutor;
        mUserTracker = userTracker;
        mUserManager = userManager;
        mBroadcastDispatcher = broadcastDispatcher;
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<CarSystemBarTextView,
                    UserNameTextViewController> {
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        registerForUserChangeEvents();
        updateUser(mUserTracker.getUserId());
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        if (mUserLifecycleListenerRegistered) {
            mBroadcastDispatcher.unregisterReceiver(mUserUpdateReceiver);
            mUserTracker.removeCallback(mUserChangedCallback);
            mUserLifecycleListenerRegistered = false;
        }
    }

    private void registerForUserChangeEvents() {
        if (mUserLifecycleListenerRegistered) {
            return;
        }
        mUserLifecycleListenerRegistered = true;
        // Register for user switching
        mUserTracker.addCallback(mUserChangedCallback, mMainExecutor);
        // Also register for user info changing
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_INFO_CHANGED);
        mBroadcastDispatcher.registerReceiver(mUserUpdateReceiver, filter, /* executor= */ null,
                UserHandle.ALL);
    }

    private void updateUser(int userId) {
        UserInfo currentUserInfo = mUserManager.getUserInfo(userId);
        mView.setText(currentUserInfo.name);
    }
}
