/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.car.systembar.privacy.cast;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.car.systembar.privacy.base.OngoingActivityPrivacyChip;

/** Car optimized CastToOtherDevice Privacy Chip View that is shown when cast is being used. */
public class CastToOtherDevicePrivacyChip extends OngoingActivityPrivacyChip {

    private static final String ACTIVITY_NAME = "cast-to-other-device";
    private static final String ACTIVITY_NAME_WITH_FIRST_LETTER_CAPITALIZED = "CastToOtherDevice";

    private final Drawable mCastLightIcon;
    private final Drawable mCastDarkIcon;

    public CastToOtherDevicePrivacyChip(@NonNull Context context) {
        this(context, /* attrs= */ null);
    }

    public CastToOtherDevicePrivacyChip(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, /* defStyleAttrs= */ 0);
    }

    public CastToOtherDevicePrivacyChip(@NonNull Context context,
            @Nullable AttributeSet attrs, int defStyleAttrs) {
        super(context, attrs, defStyleAttrs);
        mCastLightIcon = getContext().getDrawable(R.drawable.ic_cast_connected_light);
        mCastDarkIcon = getContext().getDrawable(R.drawable.ic_cast_connected_dark);
    }

    @Override
    protected Drawable getLightIconDrawable() {
        return mCastLightIcon;
    }

    @Override
    protected Drawable getDarkIconDrawable() {
        return mCastDarkIcon;
    }

    @Override
    protected String getActivityName() {
        return ACTIVITY_NAME;
    }

    @Override
    protected String getActivityNameWithFirstLetterCapitalized() {
        return ACTIVITY_NAME_WITH_FIRST_LETTER_CAPITALIZED;
    }

    @Override
    public Class<?> getElementControllerClass() {
        Class<?> superClass = super.getElementControllerClass();
        if (superClass != null) {
            return superClass;
        }
        return CastToOtherDevicePrivacyChipViewController.class;
    }
}
