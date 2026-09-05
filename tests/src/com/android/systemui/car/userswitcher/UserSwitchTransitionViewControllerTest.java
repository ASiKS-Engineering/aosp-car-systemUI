/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.car.userswitcher;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.pm.UserInfo;
import android.os.UserManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.testing.TestableResources;
import android.widget.TextView;

import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.shared.R;
import com.android.systemui.car.window.OverlayViewGlobalStateController;
import com.android.systemui.util.concurrency.FakeExecutor;
import com.android.systemui.util.time.FakeSystemClock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class UserSwitchTransitionViewControllerTest extends CarSysuiTestCase {
    private static final int TEST_USER_1 = 100;
    private static final int TEST_USER_2 = 110;

    private UserSwitchTransitionViewController mController;
    private TestableResources mTestableResources;
    private FakeExecutor mExecutor;
    private FakeSystemClock mClock;
    @Mock
    private ActivityManager mMockActivityManager;
    @Mock
    private UserManager mMockUserManager;
    @Mock
    private UserIconProvider mUserIconProvider;
    @Mock
    private OverlayViewGlobalStateController mOverlayViewGlobalStateController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTestableResources = mContext.getOrCreateTestableResources();
        mClock = new FakeSystemClock();
        mExecutor = new FakeExecutor(mClock);
        mController = new UserSwitchTransitionViewController(
                mContext,
                mTestableResources.getResources(),
                mExecutor,
                mMockActivityManager,
                mUserIconProvider,
                mOverlayViewGlobalStateController
        );

        when(mMockUserManager.getUserInfo(TEST_USER_1))
                .thenReturn(new UserInfo(TEST_USER_1, "USER_1", /* flags= */ 0));
        when(mMockUserManager.getUserInfo(TEST_USER_2))
                .thenReturn(new UserInfo(TEST_USER_2, "USER_2", /* flags= */ 0));
        mockGlobalShowView();
        mController.inflate();
    }

    @Test
    public void showSwitchingUI_newUserSelected_showsDialog() {
        mController.showSwitchingUI(/* newUserId= */ TEST_USER_1);
        mExecutor.runAllReady();

        verify(mOverlayViewGlobalStateController).showView(eq(mController), any());
    }

    @Test
    public void showSwitchingUI_showsDefaultLoadingMessage() {
        mController.showSwitchingUI(/* newUserId= */ TEST_USER_1);
        mExecutor.runAllReady();

        TextView textView = mController.getLayout().findViewById(R.id.user_loading);
        assertThat(textView.getText().toString()).isEqualTo(
                mTestableResources.getResources().getString(R.string.car_loading_profile));
    }

    @Test
    public void showSwitchingUI_showsUserSwitchingMessage() {
        String message = "Hello world!";
        when(mMockActivityManager.getSwitchingFromUserMessage(anyInt())).thenReturn(message);

        mController.showSwitchingUI(/* newUserId= */ TEST_USER_1);
        mExecutor.runAllReady();

        TextView textView = mController.getLayout().findViewById(R.id.user_loading);
        assertThat(textView.getText().toString()).isEqualTo(message);
    }

    @Test
    public void showSwitchingUI_alreadyShowing_ignoresRequest() {
        mController.showSwitchingUI(/* newUserId= */ TEST_USER_1);
        mExecutor.runAllReady();
        mController.showSwitchingUI(/* newUserId= */ TEST_USER_2);
        mExecutor.runAllReady();

        // Verify that the request was processed only once.
        verify(mOverlayViewGlobalStateController).showView(eq(mController), any());
    }

    @Test
    public void showSwitchingUI_sameUserSelected_ignoresRequest() {
        mController.showSwitchingUI(/* newUserId= */ TEST_USER_1);
        mExecutor.runAllReady();
        mController.hideSwitchingUI();
        mExecutor.runAllReady();
        mController.showSwitchingUI(/* newUserId= */ TEST_USER_1);
        mExecutor.runAllReady();

        // Verify that the request was processed only once.
        verify(mOverlayViewGlobalStateController).showView(eq(mController), any());
    }

    @Test
    public void hideSwitchingUI_currentlyShowing_hidesDialog() {
        mController.showSwitchingUI(/* newUserId= */ TEST_USER_1);
        mExecutor.runAllReady();
        mController.hideSwitchingUI();
        mExecutor.runAllReady();

        verify(mOverlayViewGlobalStateController).hideView(eq(mController), any());
    }

    @Test
    public void hideSwitchingUI_notShowing_ignoresRequest() {
        mController.showSwitchingUI(/* newUserId= */ TEST_USER_1);
        mExecutor.runAllReady();
        mController.hideSwitchingUI();
        mExecutor.runAllReady();
        mController.hideSwitchingUI();
        mExecutor.runAllReady();

        // Verify that the request was processed only once.
        verify(mOverlayViewGlobalStateController).hideView(eq(mController), any());
    }

    @Test
    public void onWindowShownTimeoutPassed_viewNotHidden_hidesUserSwitchTransitionView() {
        mController.showSwitchingUI(/* newUserId= */ TEST_USER_1);
        mExecutor.runAllReady();
        reset(mOverlayViewGlobalStateController);

        mClock.advanceTime(mController.getWindowShownTimeoutMs() + 10);
        mExecutor.runAllReady();

        verify(mOverlayViewGlobalStateController).hideView(eq(mController), any());
    }

    @Test
    public void onWindowShownTimeoutPassed_viewHidden_doesNotHideUserSwitchTransitionViewAgain() {
        mController.showSwitchingUI(/* newUserId= */ TEST_USER_1);
        mExecutor.runAllReady();
        mController.hideSwitchingUI();
        mExecutor.runAllReady();
        reset(mOverlayViewGlobalStateController);

        mClock.advanceTime(mController.getWindowShownTimeoutMs() + 10);
        mExecutor.runAllReady();

        verify(mOverlayViewGlobalStateController, never()).hideView(eq(mController), any());
    }

    @Test
    public void onWindowShownTimeoutPassed_skipTimeoutSet_doesNotHideView() {
        mController.setShouldSkipTimeout(true);
        mController.showSwitchingUI(TEST_USER_1);
        mExecutor.runAllReady();
        reset(mOverlayViewGlobalStateController);

        mClock.advanceTime(mController.getWindowShownTimeoutMs() + 10);
        mExecutor.runAllReady();

        verify(mOverlayViewGlobalStateController, never()).hideView(eq(mController), any());
    }

    private void mockGlobalShowView() {
        // Because mOverlayViewGlobalStateController is a mock, calls to show view will not execute
        // the runnable parameter. To simulate a more accurate real-world environment, any non-null
        // runnable will be manually executed.
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            if (args[1] == null || !(args[1] instanceof Runnable)) {
                return null;
            }
            Runnable runnable = (Runnable) args[1];
            runnable.run();
            return null;
        }).when(mOverlayViewGlobalStateController).showView(any(), any());
    }
}
