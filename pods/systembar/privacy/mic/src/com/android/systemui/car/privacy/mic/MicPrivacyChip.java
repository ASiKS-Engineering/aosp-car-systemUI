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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.car.systembar.privacy.base.PrivacyChip;

/** Car optimized Mic Privacy Chip View that is shown when microphone is being used. */
public class MicPrivacyChip extends PrivacyChip {

    private static final String SENSOR_NAME = "microphone";
    private static final String SENSOR_NAME_WITH_FIRST_LETTER_CAPITALIZED = "Microphone";

    private final Drawable mMicOffLightIcon;
    private final Drawable mMicOffDarkIcon;
    private final Drawable mMicLightIcon;
    private final Drawable mMicDarkIcon;

    public MicPrivacyChip(@NonNull Context context) {
        this(context, /* attrs= */ null);
    }

    public MicPrivacyChip(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, /* defStyleAttrs= */ 0);
    }

    public MicPrivacyChip(@NonNull Context context,
            @Nullable AttributeSet attrs, int defStyleAttrs) {
        super(context, attrs, defStyleAttrs);
        mMicOffLightIcon = getContext().getDrawable(R.drawable.ic_mic_off_light);
        mMicOffDarkIcon = getContext().getDrawable(R.drawable.ic_mic_off_dark);
        mMicLightIcon = getContext().getDrawable(R.drawable.ic_mic_light);
        mMicDarkIcon = getContext().getDrawable(R.drawable.ic_mic_dark);
    }

    @Override
    protected Drawable getLightMutedIconDrawable() {
        return mMicOffLightIcon;
    }

    @Override
    protected Drawable getDarkMutedIconDrawable() {
        return mMicOffDarkIcon;
    }

    @Override
    protected Drawable getLightIconDrawable() {
        return mMicLightIcon;
    }

    @Override
    protected Drawable getDarkIconDrawable() {
        return mMicDarkIcon;
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
        return MicPrivacyChipViewController.class;
    }
}
