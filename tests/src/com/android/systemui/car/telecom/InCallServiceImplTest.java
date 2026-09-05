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
package com.android.systemui.car.telecom;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import android.telecom.Call;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.car.telephony.calling.InCallServiceManager;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class InCallServiceImplTest extends CarSysuiTestCase {
    private InCallServiceImpl mInCallService;
    @Mock
    private Call mMockCall;
    private CallListener mCallListener;
    private InCallServiceManager mInCallServiceManager;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mInCallServiceManager = new InCallServiceManager();
        mInCallService = new InCallServiceImpl(mInCallServiceManager);
        mCallListener = new CallListener();
        mInCallService.addListener(mCallListener);
    }

    @Test
    public void testOnCreate() {
        mInCallService.onCreate();
        assertThat(mInCallServiceManager.getInCallService()).isEqualTo(mInCallService);
    }

    @Test
    public void testOnDestroy() {
        mInCallService.onDestroy();
        assertThat(mInCallServiceManager.getInCallService()).isEqualTo(null);
    }

    @Test
    public void testOnCallAdded() {
        mInCallService.onCallAdded(mMockCall);

        verify(mMockCall).registerCallback(any());
        assertThat(mCallListener.mCall).isEqualTo(mMockCall);
    }

    @Test
    public void testOnCallRemoved() {
        mInCallService.onCallRemoved(mMockCall);

        verify(mMockCall).unregisterCallback(any());
        assertThat(mCallListener.mCall).isEqualTo(mMockCall);
    }

    @Test
    public void testOnStateChanged() {
        mInCallService.onCallAdded(mMockCall);

        verify(mMockCall).registerCallback(any());
        assertThat(mCallListener.mCall).isEqualTo(mMockCall);

        mInCallService.mCallStateChangedCallback.onStateChanged(mMockCall, Call.STATE_ACTIVE);
        assertThat(mCallListener.mCall).isEqualTo(mMockCall);
        assertThat(mCallListener.mState).isEqualTo(Call.STATE_ACTIVE);
    }

    private static class CallListener implements InCallServiceImpl.InCallListener {
        public Call mCall;
        public int mState;
        public Call mParent;
        public List<Call> mChildren;

        @Override
        public void onCallAdded(Call call) {
            mCall = call;
        }

        @Override
        public void onCallRemoved(Call call) {
            mCall = call;
        }

        @Override
        public void onStateChanged(Call call, int state) {
            mCall = call;
            mState = state;
        }

        @Override
        public void onParentChanged(Call call, Call parent) {
            mCall = call;
            mParent = parent;
        }

        @Override
        public void onChildrenChanged(Call call, List<Call> children) {
            mCall = call;
            mChildren = children;
        }
    }
}
