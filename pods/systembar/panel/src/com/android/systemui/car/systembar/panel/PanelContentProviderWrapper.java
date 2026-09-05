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

package com.android.systemui.car.systembar.panel;

import android.content.Context;
import android.view.ViewGroup;

/**
 * A wrapper class for PanelContentProvider that delegates calls to the base implementation.
 */
public class PanelContentProviderWrapper implements PanelContentProvider {

    protected final PanelContentProvider mBase;

    public PanelContentProviderWrapper(PanelContentProvider base) {
        mBase = base;
    }

    @Override
    public ViewGroup createPanelContentView(Context context) {
        return mBase.createPanelContentView(context);
    }

    @Override
    public int getPanelWidthPx() {
        return mBase.getPanelWidthPx();
    }

    @Override
    public int getXOffsetPx() {
        return mBase.getXOffsetPx();
    }

    @Override
    public int getYOffsetPx() {
        return mBase.getYOffsetPx();
    }

    @Override
    public int getPanelGravity() {
        return mBase.getPanelGravity();
    }

    @Override
    public boolean isDisabledWhileDriving() {
        return mBase.isDisabledWhileDriving();
    }

    @Override
    public boolean isDisabledWhileUnprovisioned() {
        return mBase.isDisabledWhileUnprovisioned();
    }

    @Override
    public boolean getShowAsDropDown() {
        return mBase.getShowAsDropDown();
    }
}
