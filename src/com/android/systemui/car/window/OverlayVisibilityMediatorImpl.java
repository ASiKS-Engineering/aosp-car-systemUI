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
package com.android.systemui.car.window;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.inject.Inject;

/**
 * Manages the visibility state of the {@link OverlayViewController} on the screen.
 */
public class OverlayVisibilityMediatorImpl implements OverlayVisibilityMediator {

    private static final String TAG = OverlayVisibilityMediatorImpl.class.getSimpleName();
    private static final int UNKNOWN_Z_ORDER = -1;

    private final SystemUIOverlayWindowController mSystemUIOverlayWindowController;

    private Map<OverlayViewController, Integer> mZOrderMap;
    private SortedMap<Integer, OverlayViewController> mZOrderVisibleSortedMap;
    private OverlayViewController mHighestZOrder;

    @Inject
    public OverlayVisibilityMediatorImpl(
            SystemUIOverlayWindowController systemUIOverlayWindowController) {
        mSystemUIOverlayWindowController = systemUIOverlayWindowController;
        mZOrderMap = new HashMap<>();
        mZOrderVisibleSortedMap = new TreeMap<>();
    }

    @Override
    public void showView(OverlayViewController controller) {
        /*
         * Here we make sure that the other panels become hidden if the current panel expects to be
         * exclusivly visible on the screen.
         */
        if (controller instanceof OverlayPanelViewController
                && ((OverlayPanelViewController) controller).isExclusive()) {
            for (OverlayViewController value : mZOrderVisibleSortedMap.values()) {
                if (value instanceof OverlayPanelViewController && controller != value) {
                    ((OverlayPanelViewController) value).toggle();
                }
            }
        }

        updateInternalsWhenShowingView(controller);
    }

    @Override
    public void hideView(OverlayViewController viewController) {
        mZOrderVisibleSortedMap.remove(mZOrderMap.get(viewController));
        refreshHighestZOrderWhenHidingView(viewController);

    }

    @Override
    public boolean isAnyOverlayViewVisible() {
        return !mZOrderVisibleSortedMap.isEmpty();
    }

    @Override
    public boolean hasOverlayViewBeenShown(OverlayViewController viewController) {
        return mZOrderMap.containsKey(viewController);
    }

    @Override
    public boolean isOverlayViewVisible(OverlayViewController viewController) {
        return mZOrderVisibleSortedMap.containsKey(mZOrderMap.get(viewController));
    }

    @Override
    public OverlayViewController getHighestZOrderOverlayViewController() {
        return mHighestZOrder;
    }

    @Override
    public Collection<OverlayViewController> getVisibleOverlayViewsByZOrder() {
        return mZOrderVisibleSortedMap.values();
    }

    private void updateInternalsWhenShowingView(OverlayViewController viewController) {
        int zOrder;
        if (mZOrderMap.containsKey(viewController)) {
            zOrder = mZOrderMap.get(viewController);
        } else {
            zOrder = mSystemUIOverlayWindowController.getBaseLayout().indexOfChild(
                    mSystemUIOverlayWindowController.getContainerForType(
                            viewController.getOverlayType()));
            mZOrderMap.put(viewController, zOrder);
        }

        mZOrderVisibleSortedMap.put(zOrder, viewController);

        refreshHighestZOrderWhenShowingView(viewController);
    }

    private void refreshHighestZOrderWhenShowingView(OverlayViewController viewController) {
        if (mZOrderMap.getOrDefault(mHighestZOrder, UNKNOWN_Z_ORDER) < mZOrderMap.get(
                viewController)) {
            mHighestZOrder = viewController;
        }
    }

    private void refreshHighestZOrderWhenHidingView(OverlayViewController viewController) {
        if (mZOrderVisibleSortedMap.isEmpty()) {
            mHighestZOrder = null;
            return;
        }
        if (!mHighestZOrder.equals(viewController)) {
            return;
        }

        mHighestZOrder = mZOrderVisibleSortedMap.get(mZOrderVisibleSortedMap.lastKey());
    }
}
