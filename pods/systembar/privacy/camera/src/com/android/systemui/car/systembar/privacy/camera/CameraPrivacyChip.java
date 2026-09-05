/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.car.systembar.privacy.camera;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.car.systembar.privacy.base.PrivacyChip;

/** Car optimized Camera Privacy Chip View that is shown when camera is being used. */
public class CameraPrivacyChip extends PrivacyChip {

    private static final String SENSOR_NAME = "camera";
    private static final String SENSOR_NAME_WITH_FIRST_LETTER_CAPITALIZED = "Camera";

    private final Drawable mCameraOffLightIcon;
    private final Drawable mCameraOffDarkIcon;
    private final Drawable mCameraLightIcon;
    private final Drawable mCameraDarkIcon;

    public CameraPrivacyChip(@NonNull Context context) {
        this(context, /* attrs= */ null);
    }

    public CameraPrivacyChip(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, /* defStyleAttrs= */ 0);
    }

    public CameraPrivacyChip(@NonNull Context context,
            @Nullable AttributeSet attrs, int defStyleAttrs) {
        super(context, attrs, defStyleAttrs);
        mCameraOffLightIcon = getContext().getDrawable(R.drawable.ic_camera_off_light);
        mCameraOffDarkIcon = getContext().getDrawable(R.drawable.ic_camera_off_dark);
        mCameraLightIcon = getContext().getDrawable(R.drawable.ic_camera_light);
        mCameraDarkIcon = getContext().getDrawable(R.drawable.ic_camera_dark);
    }

    @Override
    protected Drawable getLightMutedIconDrawable() {
        return mCameraOffLightIcon;
    }

    @Override
    protected Drawable getDarkMutedIconDrawable() {
        return mCameraOffDarkIcon;
    }

    @Override
    protected Drawable getLightIconDrawable() {
        return mCameraLightIcon;
    }

    @Override
    protected Drawable getDarkIconDrawable() {
        return mCameraDarkIcon;
    }

    @Override
    protected String getSensorName() {
        return SENSOR_NAME;
    }

    @Override
    protected String getSensorNameWithFirstLetterCapitalized() {
        return SENSOR_NAME_WITH_FIRST_LETTER_CAPITALIZED;
    }

    @Override
    public Class<?> getElementControllerClass() {
        Class<?> superClass = super.getElementControllerClass();
        if (superClass != null) {
            return superClass;
        }
        return CameraPrivacyChipViewController.class;
    }
}
