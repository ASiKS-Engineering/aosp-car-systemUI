/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.car.systembar.privacy.mic;

import static android.hardware.SensorPrivacyManager.Sensors.MICROPHONE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.hardware.SensorPrivacyManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.LayoutInflater;

import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.systembar.panel.PanelViewController;
import com.android.systemui.privacy.PrivacyItem;
import com.android.systemui.privacy.PrivacyItemController;
import com.android.systemui.privacy.PrivacyType;
import com.android.systemui.settings.UserTracker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.concurrent.Executor;

import javax.inject.Provider;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class MicPrivacyChipViewControllerTest extends CarSysuiTestCase {
    private static final int TEST_USER_ID = 1001;

    private MicPrivacyChipViewController mMicPrivacyChipViewController;
    private MicPrivacyChip mMicPrivacyChip;

    @Captor
    private ArgumentCaptor<Runnable> mRunnableArgumentCaptor;
    @Captor
    private ArgumentCaptor<PrivacyItemController.Callback> mPicCallbackArgumentCaptor;
    @Captor
    private ArgumentCaptor<SensorPrivacyManager.OnSensorPrivacyChangedListener>
            mOnSensorPrivacyChangedListenerArgumentCaptor;

    @Mock
    private CarSystemBarElementStatusBarDisableController mBarElementDisableController;
    @Mock
    private CarSystemBarElementStateController mBarElementStateController;
    @Mock
    private PrivacyItemController mPrivacyItemController;
    @Mock
    private PrivacyItem mPrivacyItem;
    @Mock
    private Executor mExecutor;
    @Mock
    private SensorPrivacyManager mSensorPrivacyManager;
    @Mock
    private UserTracker mUserTracker;
    @Mock
    private CarDeviceProvisionedController mCarDeviceProvisionedController;
    @Mock
    private Provider<PanelViewController.Factory> mPanelControllerFactoryProvider;
    @Mock
    private Car mCar;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(/* testClass= */ this);

        mMicPrivacyChip = spy((MicPrivacyChip) LayoutInflater.from(mContext)
                .inflate(com.android.systemui.car.systembar.privacy.mic.R.layout.mic_privacy_chip,
                        /* root= */ null));
        mContext = spy(mContext);

        when(mContext.getMainExecutor()).thenReturn(mExecutor);
        when(mCar.isConnected()).thenReturn(true);
        when(mUserTracker.getUserId()).thenReturn(TEST_USER_ID);

        mMicPrivacyChipViewController = new MicPrivacyChipViewController(mMicPrivacyChip,
                mBarElementDisableController, mBarElementStateController, mContext,
                mPrivacyItemController, mSensorPrivacyManager, mUserTracker,
                mCarDeviceProvisionedController, mPanelControllerFactoryProvider);
    }

    @Test
    public void onViewAttached_addCallbackCalled() {
        mMicPrivacyChipViewController.onViewAttached();

        verify(mPrivacyItemController).addCallback(any());
        verify(mUserTracker).addCallback(any(), any());
    }

    @Test
    public void onViewAttached_micStatusSet() {
        when(mSensorPrivacyManager.isSensorPrivacyEnabled(anyInt(), eq(MICROPHONE)))
                .thenReturn(false);
        mMicPrivacyChipViewController.onViewAttached();
        verify(mExecutor).execute(mRunnableArgumentCaptor.capture());

        mRunnableArgumentCaptor.getValue().run();

        verify(mMicPrivacyChip).setSensorEnabled(eq(true));
    }

    @Test
    public void onUserChanged_micStatusSet() {
        when(mSensorPrivacyManager.isSensorPrivacyEnabled(anyInt(), eq(MICROPHONE)))
                .thenReturn(false);
        mMicPrivacyChipViewController.onViewAttached();
        ArgumentCaptor<UserTracker.Callback> captor = ArgumentCaptor.forClass(
                UserTracker.Callback.class);
        verify(mUserTracker).addCallback(captor.capture(), any());

        captor.getValue().onUserChanged(mContext.getUserId(), mContext);
        verify(mMicPrivacyChip).setSensorEnabled(eq(true));
    }

    @Test
    public void onPrivacyItemsChanged_micIsPartOfPrivacyItems_animateInCalled() {
        when(mPrivacyItem.getPrivacyType()).thenReturn(PrivacyType.TYPE_MICROPHONE);
        mMicPrivacyChipViewController.onViewAttached();
        verify(mPrivacyItemController).addCallback(mPicCallbackArgumentCaptor.capture());
        mPicCallbackArgumentCaptor.getValue().onFlagAllChanged(true);
        mPicCallbackArgumentCaptor.getValue().onFlagMicCameraChanged(true);

        mPicCallbackArgumentCaptor.getValue()
                .onPrivacyItemsChanged(Collections.singletonList(mPrivacyItem));
        verify(mExecutor, times(2)).execute(mRunnableArgumentCaptor.capture());
        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mMicPrivacyChip).animateIn();
    }

    @Test
    public void onPrivacyItemsChanged_micIsPartOfPrivacyItemsTwice_animateInCalledOnce() {
        when(mPrivacyItem.getPrivacyType()).thenReturn(PrivacyType.TYPE_MICROPHONE);
        mMicPrivacyChipViewController.onViewAttached();
        verify(mPrivacyItemController).addCallback(mPicCallbackArgumentCaptor.capture());
        mPicCallbackArgumentCaptor.getValue().onFlagAllChanged(true);
        mPicCallbackArgumentCaptor.getValue().onFlagMicCameraChanged(true);

        mPicCallbackArgumentCaptor.getValue()
                .onPrivacyItemsChanged(Collections.singletonList(mPrivacyItem));
        mPicCallbackArgumentCaptor.getValue()
                .onPrivacyItemsChanged(Collections.singletonList(mPrivacyItem));
        verify(mExecutor, times(2)).execute(mRunnableArgumentCaptor.capture());
        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mMicPrivacyChip).animateIn();
    }

    @Test
    public void onPrivacyItemsChanged_micIsNotPartOfPrivacyItems_animateOutCalled() {
        when(mPrivacyItem.getPrivacyType()).thenReturn(PrivacyType.TYPE_MICROPHONE);
        mMicPrivacyChipViewController.onViewAttached();
        verify(mPrivacyItemController).addCallback(mPicCallbackArgumentCaptor.capture());
        mPicCallbackArgumentCaptor.getValue().onFlagAllChanged(true);
        mPicCallbackArgumentCaptor.getValue().onFlagMicCameraChanged(true);
        mPicCallbackArgumentCaptor.getValue()
                .onPrivacyItemsChanged(Collections.singletonList(mPrivacyItem));

        mPicCallbackArgumentCaptor.getValue().onPrivacyItemsChanged(Collections.emptyList());
        verify(mExecutor, times(3))
                .execute(mRunnableArgumentCaptor.capture());
        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mMicPrivacyChip).animateOut();
    }

    @Test
    public void onPrivacyItemsChanged_micIsNotPartOfPrivacyItemsTwice_animateOutCalledOnce() {
        when(mPrivacyItem.getPrivacyType()).thenReturn(PrivacyType.TYPE_MICROPHONE);
        mMicPrivacyChipViewController.onViewAttached();
        verify(mPrivacyItemController).addCallback(mPicCallbackArgumentCaptor.capture());
        mPicCallbackArgumentCaptor.getValue().onFlagAllChanged(true);
        mPicCallbackArgumentCaptor.getValue().onFlagMicCameraChanged(true);
        mPicCallbackArgumentCaptor.getValue()
                .onPrivacyItemsChanged(Collections.singletonList(mPrivacyItem));

        mPicCallbackArgumentCaptor.getValue().onPrivacyItemsChanged(Collections.emptyList());
        mPicCallbackArgumentCaptor.getValue().onPrivacyItemsChanged(Collections.emptyList());
        verify(mExecutor, times(3))
                .execute(mRunnableArgumentCaptor.capture());
        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mMicPrivacyChip).animateOut();
    }

    @Test
    public void onSensorPrivacyChanged_argTrue_setSensorEnabledWithFalseCalled() {
        mMicPrivacyChipViewController.onViewAttached();
        verify(mSensorPrivacyManager).addSensorPrivacyListener(eq(MICROPHONE),
                mOnSensorPrivacyChangedListenerArgumentCaptor.capture());
        reset(mMicPrivacyChip);
        reset(mExecutor);
        mOnSensorPrivacyChangedListenerArgumentCaptor.getValue()
                .onSensorPrivacyChanged(MICROPHONE, /* enabled= */ true);
        verify(mExecutor).execute(mRunnableArgumentCaptor.capture());

        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mMicPrivacyChip).setSensorEnabled(eq(false));
    }

    @Test
    public void onSensorPrivacyChanged_argFalse_setSensorEnabledWithTrueCalled() {
        mMicPrivacyChipViewController.onViewAttached();
        verify(mSensorPrivacyManager).addSensorPrivacyListener(eq(MICROPHONE),
                mOnSensorPrivacyChangedListenerArgumentCaptor.capture());
        reset(mMicPrivacyChip);
        reset(mExecutor);
        mOnSensorPrivacyChangedListenerArgumentCaptor.getValue()
                .onSensorPrivacyChanged(MICROPHONE, /* enabled= */ false);
        verify(mExecutor).execute(mRunnableArgumentCaptor.capture());

        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mMicPrivacyChip).setSensorEnabled(eq(true));
    }

    @Test
    public void isSensorEnabled_sensorPrivacyEnabled_returnFalse() {
        when(mSensorPrivacyManager.isSensorPrivacyEnabled(anyInt(), eq(MICROPHONE)))
                .thenReturn(true);

        assertThat(mMicPrivacyChipViewController.isSensorEnabled()).isFalse();
    }

    @Test
    public void isSensorEnabled_sensorPrivacyDisabled_returnTrue() {
        when(mSensorPrivacyManager.isSensorPrivacyEnabled(anyInt(), eq(MICROPHONE)))
                .thenReturn(false);

        assertThat(mMicPrivacyChipViewController.isSensorEnabled()).isTrue();
    }
}
