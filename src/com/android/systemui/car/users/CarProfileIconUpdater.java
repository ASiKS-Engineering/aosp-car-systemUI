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

package com.android.systemui.car.users;

import android.annotation.UserIdInt;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.os.UserManager;
import android.util.ArraySet;

import androidx.annotation.GuardedBy;

import com.android.systemui.CoreStartable;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.userswitcher.UserIconProvider;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.settings.UserTracker;

import java.util.Set;
import java.util.concurrent.Executor;

import javax.inject.Inject;

/**
 * CoreStartable service to keep the user icon updated and allow other components to listen to
 * these updates.
 */
@SysUISingleton
public class CarProfileIconUpdater implements CoreStartable {
    private final Context mContext;
    private final Executor mMainExecutor;
    private final UserTracker mUserTracker;
    private final UserManager mUserManager;
    private final BroadcastDispatcher mBroadcastDispatcher;
    private final UserIconProvider mUserIconProvider;
    @GuardedBy("mCallbacks")
    private final Set<Callback> mCallbacks = new ArraySet<>();

    private boolean mUserLifecycleListenerRegistered;
    private String mLastUserName;

    private final UserTracker.Callback mUserChangedCallback =
            new UserTracker.Callback() {
                @Override
                public void onUserChanged(int newUser, Context userContext) {
                    mBroadcastDispatcher.unregisterReceiver(mUserUpdateReceiver);
                    registerForUserInfoChange();
                }
            };

    private final BroadcastReceiver mUserUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateUserIcon(mUserTracker.getUserId());
        }
    };

    @Inject
    public CarProfileIconUpdater(Context context, @Main Executor mainExecutor,
            UserTracker userTracker, UserManager userManager,
            BroadcastDispatcher broadcastDispatcher, UserIconProvider userIconProvider) {
        mContext = context;
        mMainExecutor = mainExecutor;
        mUserTracker = userTracker;
        mUserManager = userManager;
        mBroadcastDispatcher = broadcastDispatcher;
        mUserIconProvider = userIconProvider;
    }

    @Override
    public void start() {
        registerForUserChangeEvents();
    }

    /** Add a callback to listen to user icon updates */
    public void addCallback(Callback callback) {
        synchronized (mCallbacks) {
            mCallbacks.add(callback);
        }
    }

    /** Remove callback for user icon updates */
    public void removeCallback(Callback callback) {
        synchronized (mCallbacks) {
            mCallbacks.remove(callback);
        }
    }

    protected void updateUserIcon(@UserIdInt int userId) {
        UserInfo currentUserInfo = mUserManager.getUserInfo(userId);

        // Update user icon with the first letter of the user name
        if (mLastUserName == null || !mLastUserName.equals(currentUserInfo.name)) {
            mLastUserName = currentUserInfo.name;
            mUserIconProvider.setRoundedUserIcon(userId);
            notifyCallbacks(userId);
        }
    }

    protected void notifyCallbacks(@UserIdInt int userId) {
        synchronized (mCallbacks) {
            for (Callback callback : mCallbacks) {
                callback.onUserIconUpdated(userId);
            }
        }
    }

    private void registerForUserChangeEvents() {
        if (mUserLifecycleListenerRegistered) {
            return;
        }
        mUserLifecycleListenerRegistered = true;
        mUserTracker.addCallback(mUserChangedCallback, mMainExecutor);
        registerForUserInfoChange();
    }

    private void registerForUserInfoChange() {
        mLastUserName = mUserManager.getUserInfo(mUserTracker.getUserId()).name;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_INFO_CHANGED);
        mBroadcastDispatcher.registerReceiver(mUserUpdateReceiver, filter, /* executor= */ null,
                mUserTracker.getUserHandle());
    }

    public interface Callback {
        /** Called when the user icon is updated for a specific userId. */
        void onUserIconUpdated(int userId);
    }
}
