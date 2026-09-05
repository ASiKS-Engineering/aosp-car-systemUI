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
package com.android.systemui.car.wm.scalableui.panel.panelupdates;

import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.scalableui.model.Corner;
import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.car.scalableui.panel.PanelUpdatePublisher;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link PanelUpdatePublisher} and {@link PanelUpdateConsumer}
 * that manages ScalableUI Panel updates.
 *
 * <p>This class allows Non-ScalableUI components to register for updates on specific panel
 * (panel_id)
 * and enables ScalableUI components to publish these updates. It ensures that UI updates
 * are dispatched on the main thread. It also provides an option to replay the last known
 * state to newly registered listeners.
 */
public class ScalableUIPanelUpdateImpl implements PanelUpdatePublisher, PanelUpdateConsumer {

    /**
     * Stores a set of {@link PanelUpdateCallback} listeners for each panel ID.
     * Uses a {@link LinkedHashSet} to maintain insertion order and uniqueness of callbacks.
     */
    private final Map<String, LinkedHashSet<PanelUpdateCallback>> mPanelUpdateListenersPerPanelMap =
            new HashMap<>();
    /**
     * Caches the last known {@link PanelState} (e.g., bounds) for each panel ID.
     * Uses a {@link ConcurrentHashMap} for thread-safe access and modification.
     */
    private final Map<String, PanelState> mLastKnownPanelState = new ConcurrentHashMap<>();

    /**
     * Handler associated with the main application thread (UI thread).
     * Used to ensure that {@link PanelUpdateCallback} methods are invoked on the main thread.
     */
    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

    @Override
    @GuardedBy("mPanelUpdateListenersPerPanelMap")
    public void registerCallback(@NonNull String panelId, @NonNull PanelUpdateCallback callback) {
        synchronized (mPanelUpdateListenersPerPanelMap) {
            LinkedHashSet<PanelUpdateCallback> callbacks =
                    mPanelUpdateListenersPerPanelMap.computeIfAbsent(panelId,
                            k -> new LinkedHashSet<>());
            callbacks.add(callback);

            if (!mLastKnownPanelState.containsKey(panelId)) {
                return;
            }

            Rect bounds = getBounds(panelId);
            if (bounds != null) {
                callback.onBoundsChange(panelId, bounds);
            }

            Float alpha = getAlpha(panelId);
            if (alpha != null) {
                callback.onAlphaChange(panelId, alpha);
            }

            Corner radius = getCornerRadius(panelId);
            if (radius != null) {
                callback.onCornerRadiusChange(panelId, radius);
            }

            Boolean visibility = isVisible(panelId);
            if (visibility != null) {
                callback.onVisibilityChange(panelId, visibility);
            }

            Insets insets = getInsets(panelId);
            if (insets != null) {
                callback.onInsetsChange(panelId, insets);
            }

            int gravity = getGravity(panelId);
            callback.onGravityChange(panelId, gravity);
        }
    }

    @Override
    @GuardedBy("mPanelUpdateListenersPerPanelMap")
    public void unregisterCallback(@NonNull PanelUpdateCallback callback) {
        synchronized (mPanelUpdateListenersPerPanelMap) {
            for (Map.Entry<String, LinkedHashSet<PanelUpdateCallback>> entry :
                    new HashMap<>(mPanelUpdateListenersPerPanelMap).entrySet()) {
                unregisterCallback(entry.getKey(), callback);
            }
        }
    }

    @Override
    @GuardedBy("mPanelUpdateListenersPerPanelMap")
    public void unregisterCallback(@NonNull String panelId, @NonNull PanelUpdateCallback callback) {
        synchronized (mPanelUpdateListenersPerPanelMap) {
            LinkedHashSet<PanelUpdateCallback> callbacksSet =
                    mPanelUpdateListenersPerPanelMap.get(panelId);
            if (callbacksSet == null) {
                return;
            }
            boolean removed = callbacksSet.remove(callback);
            if (removed && callbacksSet.isEmpty()) {
                // Clean up map if set becomes empty
                mPanelUpdateListenersPerPanelMap.remove(panelId);
            }
        }
    }

    @Override
    @GuardedBy("mPanelUpdateListenersPerPanelMap")
    public void postBounds(String panelId, Rect bounds) {
        PanelState panelState =
                mLastKnownPanelState.computeIfAbsent(panelId, k -> new PanelState());
        panelState.setBounds(bounds);
        mLastKnownPanelState.put(panelId, panelState);
        synchronized (mPanelUpdateListenersPerPanelMap) {
            LinkedHashSet<PanelUpdateCallback> callbacks =
                    mPanelUpdateListenersPerPanelMap.get(panelId);
            if (callbacks == null) {
                return;
            }
            mMainThreadHandler.post(() ->
                    callbacks.forEach(cb -> cb.onBoundsChange(panelId, bounds)));
        }
    }

    @Override
    @GuardedBy("mPanelUpdateListenersPerPanelMap")
    public void postAlpha(String panelId, float alpha) {
        PanelState panelState =
                mLastKnownPanelState.computeIfAbsent(panelId, k -> new PanelState());
        panelState.setAlpha(alpha);
        mLastKnownPanelState.put(panelId, panelState);
        synchronized (mPanelUpdateListenersPerPanelMap) {
            LinkedHashSet<PanelUpdateCallback> callbacks =
                    mPanelUpdateListenersPerPanelMap.get(panelId);
            if (callbacks == null) {
                return;
            }
            mMainThreadHandler.post(() ->
                    callbacks.forEach(cb -> cb.onAlphaChange(panelId, alpha)));
        }
    }

    @Override
    @GuardedBy("mPanelUpdateListenersPerPanelMap")
    public void postCornerRadius(String panelId, Corner radius) {
        PanelState panelState =
                mLastKnownPanelState.computeIfAbsent(panelId, k -> new PanelState());
        panelState.setRadius(radius);
        mLastKnownPanelState.put(panelId, panelState);
        synchronized (mPanelUpdateListenersPerPanelMap) {
            LinkedHashSet<PanelUpdateCallback> callbacks =
                    mPanelUpdateListenersPerPanelMap.get(panelId);
            if (callbacks == null) {
                return;
            }
            mMainThreadHandler.post(() ->
                    callbacks.forEach(cb -> cb.onCornerRadiusChange(panelId, radius)));
        }
    }

    @Override
    @GuardedBy("mPanelUpdateListenersPerPanelMap")
    public void postVisibility(String panelId, boolean isVisible) {
        PanelState panelState =
                mLastKnownPanelState.computeIfAbsent(panelId, k -> new PanelState());
        panelState.setVisible(isVisible);
        mLastKnownPanelState.put(panelId, panelState);
        synchronized (mPanelUpdateListenersPerPanelMap) {
            LinkedHashSet<PanelUpdateCallback> callbacks =
                    mPanelUpdateListenersPerPanelMap.get(panelId);
            if (callbacks == null) {
                return;
            }
            mMainThreadHandler.post(() ->
                    callbacks.forEach(cb -> cb.onVisibilityChange(panelId, isVisible)));
        }
    }

    @Override
    @GuardedBy("mPanelUpdateListenersPerPanelMap")
    public void postInsets(String panelId, Insets insets) {
        PanelState panelState =
                mLastKnownPanelState.computeIfAbsent(panelId, k -> new PanelState());
        panelState.setInsets(insets);
        mLastKnownPanelState.put(panelId, panelState);
        synchronized (mPanelUpdateListenersPerPanelMap) {
            LinkedHashSet<PanelUpdateCallback> callbacks =
                    mPanelUpdateListenersPerPanelMap.get(panelId);
            if (callbacks == null) {
                return;
            }
            mMainThreadHandler.post(() ->
                    callbacks.forEach(cb -> cb.onInsetsChange(panelId, insets)));
        }
    }

    @Override
    @GuardedBy("mPanelUpdateListenersPerPanelMap")
    public void postControllerMetadata(String panelId, PanelControllerMetadata metadata) {
        PanelState panelState =
                mLastKnownPanelState.computeIfAbsent(panelId, k -> new PanelState());
        panelState.setMetadata(metadata);
        mLastKnownPanelState.put(panelId, panelState);
    }

    @Override
    @GuardedBy("mPanelUpdateListenersPerPanelMap")
    public void postGravity(String panelId, int gravity) {
        PanelState panelState =
                mLastKnownPanelState.computeIfAbsent(panelId, k -> new PanelState());
        panelState.setGravity(gravity);
        mLastKnownPanelState.put(panelId, panelState);
        synchronized (mPanelUpdateListenersPerPanelMap) {
            LinkedHashSet<PanelUpdateCallback> callbacks =
                    mPanelUpdateListenersPerPanelMap.get(panelId);
            if (callbacks == null) {
                return;
            }
            mMainThreadHandler.post(() ->
                    callbacks.forEach(cb -> cb.onGravityChange(panelId, gravity)));
        }
    }

    @Override
    @Nullable
    public Rect getBounds(String panelId) {
        if (mLastKnownPanelState.get(panelId) != null) {
            return Rect.copyOrNull(mLastKnownPanelState.get(panelId).getBounds());
        }
        return null;
    }

    @Override
    public Float getAlpha(String panelId) {
        if (mLastKnownPanelState.get(panelId) != null) {
            return mLastKnownPanelState.get(panelId).getAlpha();
        }
        return null;
    }

    @Override
    public Corner getCornerRadius(String panelId) {
        if (mLastKnownPanelState.get(panelId) != null) {
            return mLastKnownPanelState.get(panelId).getRadius();
        }
        return null;
    }

    @Override
    public Boolean isVisible(String panelId) {
        if (mLastKnownPanelState.get(panelId) != null) {
            return mLastKnownPanelState.get(panelId).isVisible();
        }
        return null;
    }

    @Nullable
    @Override
    public Insets getInsets(String panelId) {
        if (mLastKnownPanelState.get(panelId) != null) {
            return mLastKnownPanelState.get(panelId).getInsets();
        }
        return null;
    }

    @Nullable
    @Override
    public PanelControllerMetadata getPanelControllerMetadata(String panelId) {
        if (mLastKnownPanelState.get(panelId) != null) {
            return mLastKnownPanelState.get(panelId).getMetadata();
        }
        return null;
    }

    @Override
    public int getGravity(String panelId) {
        if (mLastKnownPanelState.get(panelId) != null) {
            return mLastKnownPanelState.get(panelId).getGravity();
        }
        return Gravity.NO_GRAVITY;
    }

    /**
     * Internal data class to hold the last known state of a panel.
     */
    private static class PanelState {
        @Nullable
        private Rect mBounds;
        @Nullable
        private Float mAlpha;
        private Corner mRadius = Corner.DEFAULT_CORNER;
        @Nullable
        private Boolean mIsVisible;
        @Nullable
        private Insets mInsets;
        @Nullable
        private PanelControllerMetadata mMetadata;
        private int mGravity = Gravity.NO_GRAVITY;

        @Nullable
        Float getAlpha() {
            return mAlpha;
        }

        void setAlpha(@Nullable Float alpha) {
            mAlpha = alpha;
        }

        Corner getRadius() {
            return mRadius;
        }

        void setRadius(Corner radius) {
            mRadius = radius;
        }

        @Nullable
        Boolean isVisible() {
            return mIsVisible;
        }

        void setVisible(@Nullable Boolean visible) {
            mIsVisible = visible;
        }

        @Nullable
        Insets getInsets() {
            return mInsets;
        }

        void setInsets(@Nullable Insets insets) {
            mInsets = insets;
        }

        @Nullable
        PanelControllerMetadata getMetadata() {
            return mMetadata;
        }

        void setMetadata(@Nullable PanelControllerMetadata metadata) {
            mMetadata = metadata;
        }

        @Nullable
        Rect getBounds() {
            return mBounds;
        }

        void setBounds(@Nullable Rect bounds) {
            mBounds = bounds;
        }

        int getGravity() {
            return mGravity;
        }

        void setGravity(int gravity) {
            mGravity = gravity;
        }
    }

}
