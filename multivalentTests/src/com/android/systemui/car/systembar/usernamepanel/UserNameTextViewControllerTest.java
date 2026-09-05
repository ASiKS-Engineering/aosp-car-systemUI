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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.os.UserManager;
import android.testing.TestableLooper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.flexibleui.layout.CarSystemBarTextView;
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
public class UserNameTextViewControllerTest extends CarSysuiTestCase {
    private static final String USER_1_NAME = "User 1";
    private static final String USER_2_NAME = "User 2";
    private final UserInfo mUserInfo1 =
            new UserInfo(/* id= */ 0, USER_1_NAME, /* flags= */ 0);
    private final UserInfo mUserInfo2 =
            new UserInfo(/* id= */ 1, USER_2_NAME, /* flags= */ 0);

    @Mock
    CarSystemBarElementStatusBarDisableController mDisableController;
    @Mock
    CarSystemBarElementStateController mStateController;
    @Mock
    private Executor mExecutor;
    @Mock
    private UserTracker mUserTracker;
    @Mock
    private UserManager mUserManager;
    @Mock
    private BroadcastDispatcher mBroadcastDispatcher;

    private CarSystemBarTextView mView;
    private UserNameTextViewController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mUserManager.getUserInfo(mUserInfo1.id)).thenReturn(mUserInfo1);
        when(mUserManager.getUserInfo(mUserInfo2.id)).thenReturn(mUserInfo2);
        when(mUserTracker.getUserId()).thenReturn(mUserInfo1.id);

        mView = new CarSystemBarTextView(mContext);
        mController = new UserNameTextViewController(mView, mDisableController,
                mStateController, mExecutor, mUserTracker, mUserManager, mBroadcastDispatcher);
    }

    @Test
    public void onViewAttached_registersListeners() {
        mController.onViewAttached();

        verify(mUserTracker).addCallback(any(), any());
        verify(mBroadcastDispatcher).registerReceiver(any(), any(), any(), any());
    }

    @Test
    public void onViewAttached_updatesUser() {
        mController.onViewAttached();

        assertThat(mView.getText().toString()).isEqualTo(USER_1_NAME);
    }

    @Test
    public void onViewDetached_unregistersListeners() {
        mController.onViewAttached();
        mController.onViewDetached();

        verify(mUserTracker).removeCallback(any());
        verify(mBroadcastDispatcher).unregisterReceiver(any());
    }

    @Test
    public void onUserSwitched_updatesUser() {
        ArgumentCaptor<UserTracker.Callback> captor = ArgumentCaptor.forClass(
                UserTracker.Callback.class);
        mController.onViewAttached();
        verify(mUserTracker).addCallback(captor.capture(), any());
        assertThat(captor.getValue()).isNotNull();

        captor.getValue().onUserChanged(mUserInfo2.id, mContext);

        assertThat(mView.getText().toString()).isEqualTo(USER_2_NAME);
    }

    @Test
    public void onUserNameChanged_updatesUser() {
        ArgumentCaptor<BroadcastReceiver> captor = ArgumentCaptor.forClass(BroadcastReceiver.class);
        mController.onViewAttached();
        verify(mBroadcastDispatcher).registerReceiver(captor.capture(), any(), any(), any());
        assertThat(captor.getValue()).isNotNull();

        when(mUserTracker.getUserId()).thenReturn(mUserInfo2.id);
        captor.getValue().onReceive(getContext(),
                new Intent(Intent.ACTION_USER_INFO_CHANGED));

        assertThat(mView.getText().toString()).isEqualTo(USER_2_NAME);
    }
}
