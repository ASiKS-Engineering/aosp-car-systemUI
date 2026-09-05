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

package com.android.systemui.car.systembar.panel;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.DimenRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;

import com.android.systemui.car.flexibleui.CarSystemBarElement;
import com.android.systemui.car.flexibleui.CarSystemBarElementFlags;
import com.android.systemui.car.flexibleui.CarSystemBarElementResolver;

/** Custom view that provides the layout and attributes for creating system bar panels. */
public class CarSystemBarPanelButtonView extends LinearLayout implements CarSystemBarElement,
        PanelContentProvider {
    static final int INVALID_RESOURCE_ID = -1;

    private Class<?> mElementControllerClassAttr;
    private int mSystemBarDisableFlags;
    private int mSystemBarDisable2Flags;
    private boolean mDisableForLockTaskModeLocked;

    @LayoutRes
    private int mPanelLayoutRes;
    @DimenRes
    private int mPanelWidthRes;
    private int mXOffset;
    private int mYOffset;
    private int mGravity;
    private boolean mDisabledWhileDriving;
    private boolean mDisabledWhileUnprovisioned;
    private boolean mShowAsDropDown;

    public CarSystemBarPanelButtonView(Context context) {
        super(context);
        init(context, /* attrs= */ null);
    }

    public CarSystemBarPanelButtonView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CarSystemBarPanelButtonView(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public CarSystemBarPanelButtonView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        mElementControllerClassAttr =
                CarSystemBarElementResolver.getElementControllerClassFromAttributes(context, attrs);
        mSystemBarDisableFlags =
                CarSystemBarElementFlags.getStatusBarManagerDisableFlagsFromAttributes(context,
                        attrs);
        mSystemBarDisable2Flags =
                CarSystemBarElementFlags.getStatusBarManagerDisable2FlagsFromAttributes(context,
                        attrs);
        mDisableForLockTaskModeLocked =
                CarSystemBarElementFlags.getDisableForLockTaskModeLockedFromAttributes(context,
                        attrs);

        TypedArray typedArray = context.obtainStyledAttributes(attrs,
                R.styleable.CarSystemBarPanelButtonView);
        mPanelLayoutRes = typedArray.getResourceId(
                R.styleable.CarSystemBarPanelButtonView_panelLayoutRes,
                INVALID_RESOURCE_ID);
        mPanelWidthRes = typedArray.getResourceId(
                R.styleable.CarSystemBarPanelButtonView_panelWidthRes,
                R.dimen.car_status_icon_panel_default_width);
        mXOffset = typedArray
                .getDimensionPixelSize(R.styleable.CarSystemBarPanelButtonView_xOffset, 0);
        mYOffset = typedArray
                .getDimensionPixelSize(R.styleable.CarSystemBarPanelButtonView_yOffset, 0);
        mGravity = typedArray.getInteger(R.styleable.CarSystemBarPanelButtonView_gravity,
                Gravity.TOP | Gravity.START);
        mDisabledWhileDriving = typedArray.getBoolean(
                R.styleable.CarSystemBarPanelButtonView_disabledWhileDriving, false);
        mDisabledWhileUnprovisioned = typedArray.getBoolean(
                R.styleable.CarSystemBarPanelButtonView_disabledWhileUnprovisioned, false);
        mShowAsDropDown = typedArray.getBoolean(
                R.styleable.CarSystemBarPanelButtonView_showAsDropDown, true);
        typedArray.recycle();
    }

    @Nullable
    @Override
    public ViewGroup createPanelContentView(Context context) {
        if (mPanelLayoutRes == INVALID_RESOURCE_ID) {
            return null;
        }
        return (ViewGroup) LayoutInflater.from(context)
                .inflate(mPanelLayoutRes, /* root= */ null);
    }

    @Override
    public int getPanelWidthPx() {
        return getContext().getResources().getDimensionPixelSize(mPanelWidthRes);
    }

    @Override
    public int getXOffsetPx() {
        return mXOffset;
    }

    @Override
    public int getYOffsetPx() {
        return mYOffset;
    }

    @Override
    public int getPanelGravity() {
        return mGravity;
    }

    @Override
    public boolean isDisabledWhileDriving() {
        return mDisabledWhileDriving;
    }

    @Override
    public boolean isDisabledWhileUnprovisioned() {
        return mDisabledWhileUnprovisioned;
    }

    @Override
    public boolean getShowAsDropDown() {
        return mShowAsDropDown;
    }

    @Override
    public Class<?> getElementControllerClass() {
        if (mElementControllerClassAttr != null) {
            return mElementControllerClassAttr;
        }
        return CarSystemBarPanelButtonViewController.class;
    }

    @Override
    public int getSystemBarDisableFlags() {
        return mSystemBarDisableFlags;
    }

    @Override
    public int getSystemBarDisable2Flags() {
        return mSystemBarDisable2Flags;
    }

    @Override
    public boolean disableForLockTaskModeLocked() {
        return mDisableForLockTaskModeLocked;
    }
}
