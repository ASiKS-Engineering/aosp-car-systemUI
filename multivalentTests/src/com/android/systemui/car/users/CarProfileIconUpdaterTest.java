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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.testing.TestableLooper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.userswitcher.UserIconProvider;
import com.android.systemui.settings.UserTracker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper
@SmallTest
public class CarProfileIconUpdaterTest extends CarSysuiTestCase {
    private final UserInfo mUserInfo1 =
            new UserInfo(/* id= */ 0, /* name= */ "User 1", /* flags= */ 0);
    private final UserInfo mUserInfo2 =
            new UserInfo(/* id= */ 1, /* name= */ "User 2", /* flags= */ 0);

    @Mock
    private Executor mExecutor;
    @Mock
    private UserTracker mUserTracker;
    @Mock
    private UserManager mUserManager;
    @Mock
    private BroadcastDispatcher mBroadcastDispatcher;
    @Mock
    private UserIconProvider mUserIconProvider;
    @Mock
    private CarProfileIconUpdater.Callback mTestCallback;

    private CarProfileIconUpdater mIconUpdater;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mUserManager.getUserInfo(mUserInfo1.id)).thenReturn(mUserInfo1);
        when(mUserManager.getUserInfo(mUserInfo2.id)).thenReturn(mUserInfo2);
        when(mUserTracker.getUserId()).thenReturn(mUserInfo1.id);
        when(mUserTracker.getUserHandle()).thenReturn(UserHandle.of(mUserInfo1.id));

        mIconUpdater = new CarProfileIconUpdater(mContext, mExecutor, mUserTracker, mUserManager,
                mBroadcastDispatcher, mUserIconProvider);
        mIconUpdater.addCallback(mTestCallback);
    }

    @Test
    public void onStart_registersListeners() {
        mIconUpdater.start();

        verify(mUserTracker).addCallback(any(), any());
        verify(mBroadcastDispatcher).registerReceiver(any(), any(), any(), any());
    }

    @Test
    public void onUserInfoUpdate_userNameChanged_iconUpdated() {
        ArgumentCaptor<BroadcastReceiver> captor = ArgumentCaptor.forClass(BroadcastReceiver.class);
        mIconUpdater.start();
        verify(mBroadcastDispatcher).registerReceiver(captor.capture(), any(), any(), any());
        assertThat(captor.getValue()).isNotNull();

        when(mUserTracker.getUserId()).thenReturn(mUserInfo2.id);
        when(mUserTracker.getUserHandle()).thenReturn(UserHandle.of(mUserInfo2.id));
        captor.getValue().onReceive(getContext(),
                new Intent(Intent.ACTION_USER_INFO_CHANGED));

        verify(mUserIconProvider).setRoundedUserIcon(anyInt());
        verify(mTestCallback).onUserIconUpdated(anyInt());
    }

    @Test
    public void onUserInfoUpdate_userNameNotChanged_iconNotUpdated() {
        ArgumentCaptor<BroadcastReceiver> captor = ArgumentCaptor.forClass(BroadcastReceiver.class);
        mIconUpdater.start();
        verify(mBroadcastDispatcher).registerReceiver(captor.capture(), any(), any(), any());
        assertThat(captor.getValue()).isNotNull();

        captor.getValue().onReceive(getContext(),
                new Intent(Intent.ACTION_USER_INFO_CHANGED));

        verify(mUserIconProvider, never()).setRoundedUserIcon(anyInt());
        verify(mTestCallback, never()).onUserIconUpdated(anyInt());
    }

    @Test
    public void onUserSwitched_refreshInfoListener() {
        ArgumentCaptor<UserTracker.Callback> captor = ArgumentCaptor.forClass(
                UserTracker.Callback.class);
        mIconUpdater.start();
        verify(mUserTracker).addCallback(captor.capture(), any());
        verify(mBroadcastDispatcher).registerReceiver(any(), any(), any(), any());
        assertThat(captor.getValue()).isNotNull();

        clearInvocations(mBroadcastDispatcher);
        when(mUserTracker.getUserId()).thenReturn(mUserInfo2.id);
        when(mUserTracker.getUserHandle()).thenReturn(UserHandle.of(mUserInfo2.id));
        captor.getValue().onUserChanged(mUserInfo2.id, mContext);

        InOrder inOrder = Mockito.inOrder(mBroadcastDispatcher);
        inOrder.verify(mBroadcastDispatcher).unregisterReceiver(any());
        inOrder.verify(mBroadcastDispatcher).registerReceiver(any(), any(), any(), any());
    }
}
