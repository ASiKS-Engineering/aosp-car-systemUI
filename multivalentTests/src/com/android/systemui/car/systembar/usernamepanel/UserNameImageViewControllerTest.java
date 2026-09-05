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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.pm.UserInfo;
import android.graphics.drawable.Drawable;
import android.testing.TestableLooper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.flexibleui.layout.CarSystemBarImageView;
import com.android.systemui.car.users.CarProfileIconUpdater;
import com.android.systemui.car.userswitcher.UserIconProvider;
import com.android.systemui.settings.UserTracker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper
@SmallTest
public class UserNameImageViewControllerTest extends CarSysuiTestCase {
    private final UserInfo mUserInfo1 =
            new UserInfo(/* id= */ 0, /* name= */ "User 1", /* flags= */ 0);
    private final UserInfo mUserInfo2 =
            new UserInfo(/* id= */ 1, /* name= */ "User 2", /* flags= */ 0);

    @Mock
    CarSystemBarElementStatusBarDisableController mDisableController;
    @Mock
    CarSystemBarElementStateController mStateController;
    @Mock
    private Executor mExecutor;
    @Mock
    private UserTracker mUserTracker;
    @Mock
    private CarProfileIconUpdater mIconUpdater;
    @Mock
    private UserIconProvider mUserIconProvider;
    @Mock
    private Drawable mTestDrawable1;
    @Mock
    private Drawable mTestDrawable2;

    private CarSystemBarImageView mView;
    private UserNameImageViewController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mUserTracker.getUserId()).thenReturn(mUserInfo1.id);
        when(mUserIconProvider.getRoundedUserIcon(anyInt())).thenReturn(mTestDrawable1);

        mView = new CarSystemBarImageView(mContext);
        mController = new UserNameImageViewController(mView, mDisableController,
                mStateController, mContext, mExecutor, mUserTracker, mIconUpdater,
                mUserIconProvider);
    }

    @Test
    public void onViewAttached_registersListeners() {
        mController.onViewAttached();

        verify(mUserTracker).addCallback(any(), any());
        verify(mIconUpdater).addCallback(any());
    }

    @Test
    public void onViewAttached_updatesUser() {
        mController.onViewAttached();

        assertThat(mView.getDrawable()).isEqualTo(mTestDrawable1);
    }

    @Test
    public void onViewDetached_unregistersListeners() {
        mController.onViewAttached();
        mController.onViewDetached();

        verify(mUserTracker).removeCallback(any());
        verify(mIconUpdater).removeCallback(any());
    }

    @Test
    public void onUserSwitched_updatesUser() {
        ArgumentCaptor<UserTracker.Callback> captor = ArgumentCaptor.forClass(
                UserTracker.Callback.class);
        mController.onViewAttached();
        verify(mUserTracker).addCallback(captor.capture(), any());
        assertThat(captor.getValue()).isNotNull();

        when(mUserTracker.getUserId()).thenReturn(mUserInfo2.id);
        when(mUserIconProvider.getRoundedUserIcon(anyInt())).thenReturn(mTestDrawable2);
        captor.getValue().onUserChanged(mUserInfo2.id, mContext);

        assertThat(mView.getDrawable()).isEqualTo(mTestDrawable2);
    }

    @Test
    public void onUserIconChanged_updatesUser() {
        ArgumentCaptor<CarProfileIconUpdater.Callback> captor = ArgumentCaptor.forClass(
                CarProfileIconUpdater.Callback.class);
        mController.onViewAttached();
        verify(mIconUpdater).addCallback(captor.capture());
        assertThat(captor.getValue()).isNotNull();

        when(mUserTracker.getUserId()).thenReturn(mUserInfo2.id);
        when(mUserIconProvider.getRoundedUserIcon(anyInt())).thenReturn(mTestDrawable2);
        captor.getValue().onUserIconUpdated(mUserInfo2.id);

        assertThat(mView.getDrawable()).isEqualTo(mTestDrawable2);
    }
}
