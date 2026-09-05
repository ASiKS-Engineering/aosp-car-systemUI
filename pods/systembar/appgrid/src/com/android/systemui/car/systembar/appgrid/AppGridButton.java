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

package com.android.systemui.car.systembar.appgrid;

import android.content.Context;
import android.util.AttributeSet;

import com.android.systemui.car.systembar.base.CarSystemBarButton;
import com.android.systemui.statusbar.AlphaOptimizedImageView;

/**
 * AppGridButton is used to display the app grid and toggle recents.
 */
public class AppGridButton extends CarSystemBarButton {
    private boolean mIsRecentsActive;

    /**
     * @param context the context
     * @param attrs   the attribute set
     */
    public AppGridButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public Class<?> getElementControllerClass() {
        Class<?> superClass = super.getElementControllerClass();
        if (superClass != null) {
            return superClass;
        }
        return AppGridButtonController.class;
    }

    /**
     * Sets whether the recents activity is active and forces an icon update.
     *
     * @param isActive true if recents is active, false otherwise.
     */
    public void setIsRecentsActive(boolean isActive) {
        if (mIsRecentsActive != isActive) {
            mIsRecentsActive = isActive;
            setSelected(getSelected()); // Trigger updateImage and refreshIconAlpha
        }
    }

    /**
     * @return the default click listener
     */
    public OnClickListener getDefaultButtonClickListener() {
        return super.getButtonClickListener();
    }

    @Override
    protected void updateImage(AlphaOptimizedImageView icon) {
        if (mIsRecentsActive) {
            icon.setImageResource(R.drawable.car_ic_recents);
            return;
        }
        super.updateImage(icon);
    }

    @Override
    protected void refreshIconAlpha(AlphaOptimizedImageView icon) {
        if (mIsRecentsActive) {
            icon.setAlpha(getSelectedAlpha());
            return;
        }
        super.refreshIconAlpha(icon);
    }
}
