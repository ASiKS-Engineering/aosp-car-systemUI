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
package com.android.systemui.car.systembar.base;

import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

/** A factory that creates and caches views for navigation bars. */
public class CarSystemBarViewFactoryImpl implements CarSystemBarViewFactory {

    private static final String TAG = CarSystemBarViewFactory.class.getSimpleName();

    private final Map<Pair<String, Boolean>, CarSystemBarViewController>
            mCachedViewControllerMap = new ArrayMap<>();
    private final Map<String, ViewGroup> mCachedWindowMap = new HashMap<>();
    // Dagger's multibinding mechanism requires this wildcard generic type to properly
    // interoperate with Kotlin modules that provide CarSystemBarViewControllerFactory<*>.
    // This ensures compatibility while maintaining type safety.
    private final Map<String, CarSystemBarViewControllerFactory<?>> mFactoriesMap;
    private final SystemBarConfigs mSystemBarConfigs;

    @Inject
    public CarSystemBarViewFactoryImpl(
            Map<String, CarSystemBarViewControllerFactory<?>> factoriesMap,
            SystemBarConfigs systemBarConfigs) {
        mFactoriesMap = factoriesMap;
        mSystemBarConfigs = systemBarConfigs;
    }

    /** Gets the top window by side. */
    @NonNull
    @Override
    public ViewGroup getSystemBarWindow(@NonNull String name) {
        return getWindowCached(name);
    }

    /** Gets the bar by side. */
    @NonNull
    @Override
    public CarSystemBarViewController getSystemBarViewController(@NonNull String name,
            boolean isSetUp) {
        CarSystemBarViewController controller = getBarCached(name, isSetUp);

        if (controller == null) {
            Log.e(TAG, "system bar failed inflate for side " + name + " setup " + isSetUp);
            throw new RuntimeException(
                    "Unable to inflate system bar for side " + name + " setup " + isSetUp
                    + " due to missing layout");
        }
        return controller;
    }

    private ViewGroup getWindowCached(@NonNull String name) {
        if (mCachedWindowMap.get(name) != null) {
            return mCachedWindowMap.get(name);
        }

        ViewGroup window = mSystemBarConfigs.getWindowLayoutByName(name);
        mCachedWindowMap.put(name, window);
        return window;
    }

    private CarSystemBarViewController getBarCached(@NonNull String name, boolean isSetUp) {
        Pair<String, Boolean> key = new Pair<>(name, isSetUp);
        if (mCachedViewControllerMap.get(key) != null) {
            return mCachedViewControllerMap.get(key);
        }

        ViewGroup barView = mSystemBarConfigs.getSystemBarLayoutByName(name, isSetUp);
        CarSystemBarViewController controller = mFactoriesMap.get(name).create(name, barView);
        controller.init();

        mCachedViewControllerMap.put(key, controller);
        return controller;
    }

    /** Resets the cached system bar views. */
    @Override
    public void resetSystemBarViewCache() {
        mCachedViewControllerMap.clear();
    }

    /** Resets the cached system bar windows and system bar views. */
    @Override
    public void resetSystemBarWindowCache() {
        resetSystemBarViewCache();
        mCachedWindowMap.clear();
    }
}
