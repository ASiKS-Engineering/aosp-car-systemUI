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
package com.android.systemui.car.wm.scalableui.view;

import static com.android.car.scalableui.loader.xml.parser.PanelControllerParser.VIEW_TAG;

import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.car.scalableui.panel.DecorPanelController;

import java.util.Map;

import javax.inject.Provider;

public abstract class DecorPanelControllerBase implements DecorPanelController {
    private static final String TAG = DecorPanelControllerBase.class.getSimpleName();
    private static final boolean DEBUG = true;
    @Nullable
    private final Provider<View> mViewProvider;
    protected final String mPanelId;
    @Nullable
    private View mView;
    protected final PanelControllerMetadata mMetadata;

    protected DecorPanelControllerBase(@NonNull String panelId, PanelControllerMetadata metadata,
            Map<Class<?>, Provider<View>> decorPanelViewMap) {
        mPanelId = panelId;
        mMetadata = metadata;
        String viewName = metadata.getStringConfiguration(VIEW_TAG);
        if (viewName == null) {
            throw new RuntimeException("ViewName must be set " + metadata);
        }
        mViewProvider = getViewProvider(decorPanelViewMap, viewName);
    }

    @Nullable
    private Provider<View> getViewProvider(Map<Class<?>, Provider<View>> decorPanelViewMap,
            @NonNull String viewName) {
        try {
            Class<?> clazz = Class.forName(viewName);
            return decorPanelViewMap.get(clazz);
        } catch (ClassNotFoundException e) {
            // Handle the case where the class is not found
            Log.e(TAG, "Class not found: " + viewName, e);
        }
        Log.e(TAG, "Unable to create DecorPanelView: " + viewName);
        return null;
    }

    @Nullable
    private View initView() {
        View view = null;
        if (mViewProvider != null) {
            view = mViewProvider.get();
        }
        logIfDebuggable("Get View " + view + ", with panel provider " + mViewProvider);
        return view;
    }

    @Override
    public String toString() {
        return mMetadata.toString();
    }

    @Override
    @Nullable
    public View getView() {
        mView = mView == null ? initView() : mView;
        logIfDebuggable("getView =" + mView);
        return mView;
    }

    @Override
    public void destroy() {
        mView = null;
        logIfDebuggable(mPanelId + ", getView =" + mView);
    }

    @Override
    public void refreshTheme() {
        mView = initView();
    }

    protected void logIfDebuggable(String msg) {
        if (DEBUG) {
            Log.d(TAG, mPanelId + ", " + msg);
        }
    }
}
