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

package com.android.systemui.car.systembar.privacy.share;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.car.systembar.privacy.base.OngoingActivityPrivacyChip;

/** Car optimized ShareToApp Privacy Chip View that is shown when app sharing is being used. */
public class ShareToAppPrivacyChip extends OngoingActivityPrivacyChip {

    private static final String ACTIVITY_NAME = "share-to-app";
    private static final String ACTIVITY_NAME_WITH_FIRST_LETTER_CAPITALIZED = "ShareToApp";

    private final Drawable mShareLightIcon;
    private final Drawable mShareDarkIcon;

    public ShareToAppPrivacyChip(@NonNull Context context) {
        this(context, /* attrs= */ null);
    }

    public ShareToAppPrivacyChip(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, /* defStyleAttrs= */ 0);
    }

    public ShareToAppPrivacyChip(@NonNull Context context,
            @Nullable AttributeSet attrs, int defStyleAttrs) {
        super(context, attrs, defStyleAttrs);
        mShareLightIcon = getContext().getDrawable(R.drawable.ic_present_to_all_light);
        mShareDarkIcon = getContext().getDrawable(R.drawable.ic_present_to_all_dark);
    }

    @Override
    protected Drawable getLightIconDrawable() {
        return mShareLightIcon;
    }

    @Override
    protected Drawable getDarkIconDrawable() {
        return mShareDarkIcon;
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
        return ShareToAppPrivacyChipViewController.class;
    }
}
