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

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.android.systemui.car.flexibleui.CarSystemBarElementController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.flexibleui.layout.CarSystemBarImageView;
import com.android.systemui.car.users.CarProfileIconUpdater;
import com.android.systemui.car.userswitcher.UserIconProvider;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.settings.UserTracker;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.concurrent.Executor;

/**
 * Controls user name ImageView for the current logged in user.
 */
public final class UserNameImageViewController extends
        CarSystemBarElementController<CarSystemBarImageView> {
    private final Context mContext;
    private final Executor mMainExecutor;
    private final UserTracker mUserTracker;
    private final CarProfileIconUpdater mCarProfileIconUpdater;
    private final UserIconProvider mUserIconProvider;
    private boolean mUserLifecycleListenerRegistered;

    private final UserTracker.Callback mUserChangedCallback =
            new UserTracker.Callback() {
                @Override
                public void onUserChanged(int newUser, Context userContext) {
                    updateUser(newUser);
                }
            };

    private final CarProfileIconUpdater.Callback mUserIconUpdateCallback = this::updateUser;

    @AssistedInject
    protected UserNameImageViewController(@Assisted CarSystemBarImageView view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController, Context context,
            @Main Executor mainExecutor, UserTracker userTracker,
            CarProfileIconUpdater carProfileIconUpdater, UserIconProvider userIconProvider) {
        super(view, disableController, stateController);
        mContext = context;
        mMainExecutor = mainExecutor;
        mUserTracker = userTracker;
        mCarProfileIconUpdater = carProfileIconUpdater;
        mUserIconProvider = userIconProvider;
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<CarSystemBarImageView,
                    UserNameImageViewController> {
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
            mCarProfileIconUpdater.removeCallback(mUserIconUpdateCallback);
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
        // Also register for user icon changing
        mCarProfileIconUpdater.addCallback(mUserIconUpdateCallback);
    }

    private void updateUser(int userId) {
        Drawable roundedUserIcon = mUserIconProvider.getRoundedUserIcon(userId);
        mView.setImageDrawable(roundedUserIcon);
    }
}
