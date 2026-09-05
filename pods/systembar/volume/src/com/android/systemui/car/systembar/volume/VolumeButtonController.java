/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.car.systembar.volume;

import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioManager.FLAG_SHOW_UI;

import android.car.media.CarAudioManager;

import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.flexibleui.CarSystemBarElementController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.systembar.base.ButtonSelectionStateController;
import com.android.systemui.car.systembar.base.CarSystemBarButton;
import com.android.systemui.car.systembar.base.CarSystemBarButtonController;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.settings.UserTracker;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

/**
 * A CarSystemBarElementController for handling Volume button interactions.
 */
public class VolumeButtonController extends CarSystemBarButtonController {

    private final VolumeButton mVolumeButton;
    private final CarServiceProvider mCarServiceProvider;
    private CarAudioManager mCarAudioManager;

    private final CarServiceProvider.CarServiceOnConnectedListener mCarServiceLifecycleListener;

    @AssistedInject
    public VolumeButtonController(@Assisted CarSystemBarButton volumeButton,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            UserTracker userTracker, EventDispatcher eventDispatcher,
            ButtonSelectionStateController buttonSelectionStateController,
            CarServiceProvider carServiceProvider) {
        super(volumeButton, disableController, stateController, userTracker, eventDispatcher,
                buttonSelectionStateController);

        mVolumeButton = (VolumeButton) volumeButton;
        mCarServiceProvider = carServiceProvider;
        mCarServiceLifecycleListener = car -> {
            mCarAudioManager = car.getCarManager(CarAudioManager.class);
            mVolumeButton.setOnClickListener(v -> {
                if (mCarAudioManager != null) {
                    // TODO(b/304797002): Use highest priority active group instead of USAGE_MEDIA
                    int groupId = mCarAudioManager.getVolumeGroupIdForUsage(USAGE_MEDIA);
                    mCarAudioManager.setGroupVolume(groupId,
                            mCarAudioManager.getGroupVolume(groupId), FLAG_SHOW_UI);
                }
            });
        };
    }

    @Override
    protected void onInit() {
        super.onInit();
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        mCarServiceProvider.addListener(mCarServiceLifecycleListener);
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        mCarServiceProvider.removeListener(mCarServiceLifecycleListener);
        mCarAudioManager = null;
        mVolumeButton.setOnClickListener(null);
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<CarSystemBarButton,
                    VolumeButtonController> {
    }
}
