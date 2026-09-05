/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.car.statusicon.mediavolume;

import static android.media.AudioAttributes.USAGE_MEDIA;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.media.CarAudioManager;
import android.graphics.drawable.Drawable;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.statusicon.base.StatusIconView;
import com.android.systemui.settings.UserTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
public class MediaVolumeStatusIconControllerTest extends CarSysuiTestCase {
    @Mock
    Car mCar;
    @Mock
    CarOccupantZoneManager mCarOccupantZoneManager;
    @Mock
    CarOccupantZoneManager.OccupantZoneInfo mInfo;
    @Mock
    CarAudioManager mCarAudioManager;
    @Mock
    CarServiceProvider mCarServiceProvider;
    @Mock
    UserTracker mUserTracker;
    @Mock
    CarSystemBarElementStatusBarDisableController mDisableController;
    @Mock
    CarSystemBarElementStateController mStateController;

    private StatusIconView mView;
    private MediaVolumeStatusIconController mMediaVolumeStatusIconController;
    private MockitoSession mMockingSession;
    private CarAudioManager.CarVolumeCallback mVolumeChangeCallback;

    private final int mInitZoneId = 100;
    private final int mInitGroupId = 10;
    private final int mInitVolumeLevel = 50;
    private Drawable mInitialStatusIconDrawable;

    @Before
    public void setUp() {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .spyStatic(Car.class)
                .strictness(Strictness.WARN)
                .startMocking();

        mView = new StatusIconView(mContext);
        mMediaVolumeStatusIconController =
                new MediaVolumeStatusIconController(mView, mDisableController, mStateController,
                        mContext, mUserTracker, mContext.getResources(), mCarServiceProvider);
    }

    @After
    public void tearDown() {
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    @Test
    public void onViewAttached_registersListeners() {
        mMediaVolumeStatusIconController.onViewAttached();
        verify(mUserTracker).addCallback(any(), any());
        verify(mCarServiceProvider).addListener(any());
    }

    @Test
    public void onViewDetached_unregistersListeners() {
        attachAndSetAudioMocks();
        mMediaVolumeStatusIconController.onViewDetached();
        verify(mCarServiceProvider).removeListener(any());
        verify(mCarAudioManager).unregisterCarVolumeCallback(any());
        verify(mUserTracker).removeCallback(any());
    }

    @Test
    public void onGroupVolumeChanged_whenZoneIdAndGroupIdAreSame_updateStatus() {
        attachAndSetAudioMocks();
        int zoneId = 100;
        int groupId = 10;
        int volume = 0;
        doReturn(volume).when(mCarAudioManager).getGroupVolume(zoneId, groupId);

        mVolumeChangeCallback.onGroupVolumeChanged(zoneId, groupId, /* flag= */ 0);

        assertThat(mInitialStatusIconDrawable).isNotEqualTo(
                mMediaVolumeStatusIconController.getIconDrawableToDisplay());
    }

    @Test
    public void onGroupVolumeChanged_whenZoneIdIsNotSame_doNotUpdateStatus() {
        attachAndSetAudioMocks();
        int zoneId = 99;
        int groupId = 10;
        int volume = 0;
        doReturn(groupId).when(mCarAudioManager).getVolumeGroupIdForUsage(zoneId, USAGE_MEDIA);
        doReturn(volume).when(mCarAudioManager).getGroupVolume(zoneId, groupId);

        mVolumeChangeCallback.onGroupVolumeChanged(zoneId, groupId, /* flag= */ 0);

        assertThat(mInitialStatusIconDrawable).isEqualTo(
                mMediaVolumeStatusIconController.getIconDrawableToDisplay());
    }

    @Test
    public void onGroupVolumeChanged_whenGroupIdIsNotSame_doNotUpdateStatus() {
        attachAndSetAudioMocks();
        int zoneId = 100;
        int groupId = 9;
        int volume = 0;
        doReturn(volume).when(mCarAudioManager).getGroupVolume(zoneId, groupId);

        mVolumeChangeCallback.onGroupVolumeChanged(zoneId, groupId, /* flag= */ 0);

        assertThat(mInitialStatusIconDrawable).isEqualTo(
                mMediaVolumeStatusIconController.getIconDrawableToDisplay());
    }

    @Test
    public void onGroupMuteChanged_whenZoneIdAndGroupIdAreSame_updateStatus() {
        attachAndSetAudioMocks();
        int zoneId = 100;
        int groupId = 10;
        int volume = 0;
        doReturn(volume).when(mCarAudioManager).getGroupVolume(zoneId, groupId);

        mVolumeChangeCallback.onGroupMuteChanged(zoneId, groupId, /* flag= */ 0);

        assertThat(mInitialStatusIconDrawable).isNotEqualTo(
                mMediaVolumeStatusIconController.getIconDrawableToDisplay());
    }

    @Test
    public void onGroupMuteChanged_whenZoneIdIsNotSame_doNotUpdateStatus() {
        attachAndSetAudioMocks();
        int zoneId = 99;
        int groupId = 10;
        int volume = 0;
        doReturn(groupId).when(mCarAudioManager).getVolumeGroupIdForUsage(zoneId, USAGE_MEDIA);
        doReturn(volume).when(mCarAudioManager).getGroupVolume(zoneId, groupId);

        mVolumeChangeCallback.onGroupMuteChanged(zoneId, groupId, /* flag= */ 0);

        assertThat(mInitialStatusIconDrawable).isEqualTo(
                mMediaVolumeStatusIconController.getIconDrawableToDisplay());
    }

    @Test
    public void onGroupMuteChanged_whenGroupIdIsNotSame_doNotUpdateStatus() {
        attachAndSetAudioMocks();
        int zoneId = 100;
        int groupId = 9;
        int volume = 0;
        doReturn(volume).when(mCarAudioManager).getGroupVolume(zoneId, groupId);

        mVolumeChangeCallback.onGroupMuteChanged(zoneId, groupId, /* flag= */ 0);

        assertThat(mInitialStatusIconDrawable).isEqualTo(
                mMediaVolumeStatusIconController.getIconDrawableToDisplay());
    }

    private void attachAndSetAudioMocks() {
        ArgumentCaptor<CarServiceProvider.CarServiceOnConnectedListener> listenerCaptor =
                ArgumentCaptor.forClass(CarServiceProvider.CarServiceOnConnectedListener.class);

        mMediaVolumeStatusIconController.onViewAttached();
        verify(mCarServiceProvider).addListener(listenerCaptor.capture());

        doReturn(mCarOccupantZoneManager).when(mCar).getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE);
        mInfo.zoneId = mInitZoneId;
        doReturn(mInfo).when(mCarOccupantZoneManager).getMyOccupantZone();
        doReturn(mInitZoneId).when(mCarOccupantZoneManager).getAudioZoneIdForOccupant(mInfo);
        doReturn(mCarAudioManager).when(mCar).getCarManager(Car.AUDIO_SERVICE);
        doReturn(mInitGroupId).when(mCarAudioManager)
                .getVolumeGroupIdForUsage(mInitZoneId, USAGE_MEDIA);
        doReturn(mInitVolumeLevel).when(mCarAudioManager).getGroupVolume(mInitZoneId, mInitGroupId);

        listenerCaptor.getValue().onConnected(mCar);

        ArgumentCaptor<CarAudioManager.CarVolumeCallback> callbackCaptor =
                ArgumentCaptor.forClass(CarAudioManager.CarVolumeCallback.class);
        verify(mCarAudioManager).registerCarVolumeCallback(callbackCaptor.capture());
        mVolumeChangeCallback = callbackCaptor.getValue();
        mInitialStatusIconDrawable =
                mMediaVolumeStatusIconController.getMediaVolumeStatusIconDrawable();
    }
}
