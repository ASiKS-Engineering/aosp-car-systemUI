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
package com.android.systemui.car.ndo;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.media.session.MediaController;
import android.os.UserHandle;
import android.telecom.Call;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.lifecycle.LiveData;
import androidx.test.filters.SmallTest;

import com.android.car.telephony.calling.InCallServiceManager;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.telecom.InCallServiceImpl;
import com.android.systemui.lifecycle.InstantTaskExecutorRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class BlockerViewModelTest extends CarSysuiTestCase {
    private BlockerViewModel mBlockerViewModel;
    private static final String PROPERTY_IN_CALL_SERVICE = "PROPERTY_IN_CALL_SERVICE";
    private static final String BLOCKED_ACTIVITY_PKG_NAME = "com.blocked.activity";
    private static final String BLOCKED_ACTIVITY = BLOCKED_ACTIVITY_PKG_NAME + "/.TestActivity";
    private static final String NOT_BLOCKED_ACTIVITY_PKG_NAME = "com.not.blocked.activity";

    private InCallServiceManager mInCallServiceManager;
    private LiveData<BlockerViewModel.BlockingType> mBlockingLiveData;

    @Rule
    public final InstantTaskExecutorRule mTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private InCallLiveData mInCallLiveData;
    @Mock
    private MediaSessionHelper mMediaSessionHelper;
    @Mock
    private LiveData<List<MediaController>> mMediaLiveData;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(/* testClass= */ this);
        mInCallServiceManager = new InCallServiceManager();
        mBlockerViewModel = new BlockerViewModel(mContext, mInCallServiceManager,
                mMediaSessionHelper);
        mBlockingLiveData = mBlockerViewModel.getBlockingTypeLiveData();
    }

    @Test
    public void testPropertyChange_serviceConnected() {
        PropertyChangeEvent event = mock(PropertyChangeEvent.class);
        when(event.getPropertyName()).thenReturn(PROPERTY_IN_CALL_SERVICE);
        InCallServiceImpl mockInCallService = mock(InCallServiceImpl.class);
        mInCallServiceManager.setInCallService(mockInCallService);

        initializeViewModel();
        mBlockerViewModel.propertyChange(event);

        verify(mockInCallService, atLeastOnce()).addListener(any());
    }

    @Test
    public void testCallAndMedia_emitsNone() {
        initializeViewModel();

        mBlockerViewModel.onUpdate();

        assertThat(mBlockingLiveData.getValue()).isEqualTo(BlockerViewModel.BlockingType.NONE);
    }

    @Test
    public void testNullCall_emitsNone() {
        initializeViewModel();
        when(mInCallLiveData.getValue()).thenReturn(null);

        mBlockerViewModel.onUpdate();

        assertThat(mBlockingLiveData.getValue()).isEqualTo(BlockerViewModel.BlockingType.NONE);
    }

    @Test
    public void testCall_emitsDialer() {
        Call call = mock(Call.class);
        when(mInCallLiveData.getValue()).thenReturn(call);
        initializeViewModel();

        mBlockerViewModel.onUpdate();

        assertThat(mBlockingLiveData.getValue()).isEqualTo(BlockerViewModel.BlockingType.DIALER);
    }

    @Test
    public void testNoMedia_emitsNone() {
        List<MediaController> mediaControllers = new ArrayList<>();
        when(mMediaLiveData.getValue()).thenReturn(mediaControllers);
        initializeViewModel();

        mBlockerViewModel.onUpdate();

        assertThat(mBlockingLiveData.getValue()).isEqualTo(BlockerViewModel.BlockingType.NONE);
    }

    @Test
    public void testNotBlockedMedia_emitsNone() {
        List<MediaController> mediaControllers = new ArrayList<>();
        MediaController mediaController = mock(MediaController.class);
        when(mediaController.getPackageName()).thenReturn(NOT_BLOCKED_ACTIVITY_PKG_NAME);
        mediaControllers.add(mediaController);
        when(mMediaLiveData.getValue()).thenReturn(mediaControllers);
        initializeViewModel();

        mBlockerViewModel.onUpdate();

        assertThat(mBlockingLiveData.getValue()).isEqualTo(BlockerViewModel.BlockingType.NONE);
    }

    @Test
    public void testBlockedMedia_emitsMedia() {
        List<MediaController> mediaControllers = new ArrayList<>();
        MediaController mediaController = mock(MediaController.class);
        when(mediaController.getPackageName()).thenReturn(BLOCKED_ACTIVITY_PKG_NAME);
        mediaControllers.add(mediaController);
        when(mMediaLiveData.getValue()).thenReturn(mediaControllers);
        initializeViewModel();

        mBlockerViewModel.onUpdate();

        assertThat(mBlockingLiveData.getValue()).isEqualTo(BlockerViewModel.BlockingType.MEDIA);
    }

    private void initializeViewModel() {
        when(mMediaSessionHelper.getActiveMediaSessions()).thenReturn(mMediaLiveData);
        mBlockerViewModel.initialize(BLOCKED_ACTIVITY, UserHandle.CURRENT);
        mBlockerViewModel.mInCallLiveData = mInCallLiveData;
    }
}
