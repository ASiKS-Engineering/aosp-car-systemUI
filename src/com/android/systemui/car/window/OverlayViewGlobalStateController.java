/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;

import android.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsets.Side.InsetsSide;
import android.view.WindowInsets.Type.InsetsType;

import androidx.annotation.VisibleForTesting;

import com.android.systemui.dagger.SysUISingleton;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;

/**
 * This controller is responsible for the following:
 * <p><ul>
 * <li>Holds the global state for SystemUIOverlayWindow.
 * <li>Allows {@link SystemUIOverlayWindowManager} to register {@link OverlayViewMediator}(s).
 * <li>Enables {@link OverlayViewController)(s) to reveal/conceal themselves while respecting the
 * global state of SystemUIOverlayWindow.
 * </ul>
 */
@SysUISingleton
public class OverlayViewGlobalStateController {
    private static final boolean DEBUG = false;
    private static final String TAG = OverlayViewGlobalStateController.class.getSimpleName();
    private final SystemUIOverlayWindowController mSystemUIOverlayWindowController;

    private final OverlayVisibilityMediator mOverlayVisibilityMediator;

    @VisibleForTesting
    Set<OverlayViewController> mViewsHiddenForOcclusion;
    private boolean mIsOccluded;

    @Inject
    public OverlayViewGlobalStateController(
            SystemUIOverlayWindowController systemUIOverlayWindowController,
            OverlayVisibilityMediator overlayVisibilityMediator) {
        mSystemUIOverlayWindowController = systemUIOverlayWindowController;
        mOverlayVisibilityMediator = overlayVisibilityMediator;

        mSystemUIOverlayWindowController.registerOutsideTouchListener((v, event) -> {
            if (mOverlayVisibilityMediator.getHighestZOrderOverlayViewController() != null) {
                mOverlayVisibilityMediator.getHighestZOrderOverlayViewController()
                        .onTouchEvent(v, event);
            }
        });

        mViewsHiddenForOcclusion = new HashSet<>();
    }

    void init() {
        mSystemUIOverlayWindowController.init();
    }

    /**
     * Register {@link OverlayViewMediator} to use in SystemUIOverlayWindow.
     */
    public void registerMediator(OverlayViewMediator overlayViewMediator) {
        Log.d(TAG, "Registering content mediator: " + overlayViewMediator.getClass().getName());

        overlayViewMediator.registerListeners();
        overlayViewMediator.setUpOverlayContentViewControllers();
    }

    void ensureInflated(OverlayViewController viewController) {
        if (viewController.isInflated()) {
            return;
        }

        String type = viewController.getOverlayType();
        ViewGroup placeholder = mSystemUIOverlayWindowController.getContainerForType(type);

        if (placeholder != null) {
            View view = viewController.inflate();
            if (view != null) {
                viewController.setLayout(view);
                placeholder.addView(view);
            }
        } else {
            Log.e(TAG, "Placeholder FrameLayout not found for type: " + type);
        }
    }

    /**
     * Show content in Overlay Window using {@link OverlayPanelViewController}.
     *
     * This calls {@link OverlayViewGlobalStateController#showView(OverlayViewController, Runnable)}
     * where the runnable is nullified since the actual showing of the panel is handled by the
     * controller itself.
     */
    public void showView(OverlayPanelViewController panelViewController) {
        showView(panelViewController, /* show */ null);
    }

    /**
     * Show content in Overlay Window using {@link OverlayViewController}.
     */
    public void showView(OverlayViewController viewController, @Nullable Runnable show) {
        debugLog();
        if (mIsOccluded && !viewController.shouldShowWhenOccluded()) {
            mViewsHiddenForOcclusion.add(viewController);
            return;
        }

        ensureInflated(viewController);

        ViewGroup placeholder = mSystemUIOverlayWindowController.getContainerForType(
                viewController.getOverlayType());
        if (placeholder != null) {
            placeholder.setVisibility(View.VISIBLE);
        } else {
            Log.e(TAG, "Cannot show, placeholder not found for type: "
                    + viewController.getOverlayType());
            return;
        }

        if (!mOverlayVisibilityMediator.isAnyOverlayViewVisible()) {
            setWindowVisible(true);
        }

        if (show != null) {
            show.run();
        }

        mOverlayVisibilityMediator.showView(viewController);
        refreshUseStableInsets();
        refreshInsetsToFit();
        refreshWindowFocus();
        refreshWindowDefaultDimBehind();
        refreshInsetTypeVisibility(navigationBars());
        refreshInsetTypeVisibility(statusBars());
        refreshRotaryFocusIfNeeded();

        Log.d(TAG, "Content shown: " + viewController.getClass().getName());
        debugLog();
    }

    /**
     * Hide content in Overlay Window using {@link OverlayPanelViewController}.
     *
     * This calls {@link OverlayViewGlobalStateController#hideView(OverlayViewController, Runnable)}
     * where the runnable is nullified since the actual hiding of the panel is handled by the
     * controller itself.
     */
    public void hideView(OverlayPanelViewController panelViewController) {
        hideView(panelViewController, /* hide */ null);
    }

    /**
     * Hide content in Overlay Window using {@link OverlayViewController}.
     */
    public void hideView(OverlayViewController viewController, @Nullable Runnable hide) {
        debugLog();
        if (mIsOccluded && mViewsHiddenForOcclusion.contains(viewController)) {
            mViewsHiddenForOcclusion.remove(viewController);
            return;
        }
        if (!viewController.isInflated()) {
            Log.d(TAG, "Content cannot be hidden since it isn't inflated: "
                    + viewController.getClass().getName());
            return;
        }
        if (!mOverlayVisibilityMediator.hasOverlayViewBeenShown(viewController)) {
            Log.d(TAG, "Content cannot be hidden since it has never been shown: "
                    + viewController.getClass().getName());
            return;
        }
        if (!mOverlayVisibilityMediator.isOverlayViewVisible(viewController)) {
            Log.d(TAG, "Content cannot be hidden since it isn't currently shown: "
                    + viewController.getClass().getName());
            return;
        }

        if (hide != null) {
            hide.run();
        }

        mOverlayVisibilityMediator.hideView(viewController);

        ViewGroup placeholder = mSystemUIOverlayWindowController.getContainerForType(
                viewController.getOverlayType());
        if (placeholder != null) {
            placeholder.setVisibility(View.GONE);
        }

        refreshUseStableInsets();
        refreshInsetsToFit();
        refreshWindowFocus();
        refreshWindowDefaultDimBehind();
        refreshInsetTypeVisibility(navigationBars());
        refreshInsetTypeVisibility(statusBars());
        refreshRotaryFocusIfNeeded();

        if (!mOverlayVisibilityMediator.isAnyOverlayViewVisible()) {
            setWindowVisible(false);
        }

        Log.d(TAG, "Content hidden: " + viewController.getClass().getName());
        debugLog();
    }

    /**
     * After the default dim amount is set via {@link OverlayViewController#getDefaultDimAmount},
     * this function can be called to make further updates to the dim amount when an overlay view
     * is the top z-ordered window. Returns {@code true} if the dim amount of the window has been
     * updated
     */
    public boolean updateWindowDimBehind(OverlayViewController viewController, float dimAmount) {
        OverlayViewController highestZOrder = mOverlayVisibilityMediator
                .getHighestZOrderOverlayViewController();
        if (highestZOrder == null || viewController != highestZOrder) {
            return false;
        }
        mSystemUIOverlayWindowController.setDimBehind(dimAmount);
        return true;
    }

    private void refreshInsetTypeVisibility(@InsetsType int insetType) {
        if (!mOverlayVisibilityMediator.isAnyOverlayViewVisible()) {
            mSystemUIOverlayWindowController.showInsets(insetType);
            return;
        }

        // Do not hide navigation bar insets if the window is not focusable.
        OverlayViewController highestZOrder = mOverlayVisibilityMediator
                .getHighestZOrderOverlayViewController();
        boolean shouldShowInsets =
                (insetType == navigationBars() && highestZOrder.shouldShowNavigationBarInsets())
                || (insetType == statusBars() && highestZOrder.shouldShowStatusBarInsets());
        if (highestZOrder.shouldFocusWindow() && !shouldShowInsets) {
            mSystemUIOverlayWindowController.hideInsets(insetType);
        } else {
            mSystemUIOverlayWindowController.showInsets(insetType);
        }
    }

    private void refreshWindowFocus() {
        OverlayViewController highestZOrder = mOverlayVisibilityMediator
                .getHighestZOrderOverlayViewController();
        setWindowFocusable(highestZOrder == null ? false : highestZOrder.shouldFocusWindow());
    }

    private void refreshWindowDefaultDimBehind() {
        OverlayViewController highestZOrder = mOverlayVisibilityMediator
                .getHighestZOrderOverlayViewController();
        float dimAmount = highestZOrder == null ? 0f : highestZOrder.getDefaultDimAmount();
        mSystemUIOverlayWindowController.setDimBehind(dimAmount);
    }

    private void refreshUseStableInsets() {
        OverlayViewController highestZOrder = mOverlayVisibilityMediator
                .getHighestZOrderOverlayViewController();
        mSystemUIOverlayWindowController.setUsingStableInsets(
                highestZOrder == null ? false : highestZOrder.shouldUseStableInsets());
    }

    /**
     * Refreshes the insets to fit (or honor) either by {@link InsetsType} or {@link InsetsSide}.
     *
     * By default, the insets to fit are defined by the {@link InsetsType}. But if an
     * {@link OverlayViewController} overrides {@link OverlayViewController#getInsetSidesToFit()} to
     * return an {@link InsetsSide}, then that takes precedence over {@link InsetsType}.
     */
    private void refreshInsetsToFit() {
        if (!mOverlayVisibilityMediator.isAnyOverlayViewVisible()) {
            setFitInsetsTypes(statusBars());
        } else {
            OverlayViewController highestZOrder = mOverlayVisibilityMediator
                    .getHighestZOrderOverlayViewController();
            if (highestZOrder.getInsetSidesToFit() != OverlayViewController.INVALID_INSET_SIDE) {
                // First fit all system bar insets as setFitInsetsSide defines which sides of system
                // bar insets to actually honor.
                setFitInsetsTypes(WindowInsets.Type.systemBars());
                setFitInsetsSides(highestZOrder.getInsetSidesToFit());
            } else {
                setFitInsetsTypes(highestZOrder.getInsetTypesToFit());
            }
        }
    }

    private void refreshRotaryFocusIfNeeded() {
        OverlayViewController highestZOrder = mOverlayVisibilityMediator
                .getHighestZOrderOverlayViewController();
        for (OverlayViewController controller : mOverlayVisibilityMediator
                .getVisibleOverlayViewsByZOrder()) {
            boolean isTop = Objects.equals(controller, highestZOrder);
            controller.setAllowRotaryFocus(isTop);
        }

        if (mOverlayVisibilityMediator.isAnyOverlayViewVisible()) {
            highestZOrder.refreshRotaryFocusIfNeeded();
        }
    }

    /** Returns {@code true} is the window is visible. */
    public boolean isWindowVisible() {
        return mSystemUIOverlayWindowController.isWindowVisible();
    }

    private void setWindowVisible(boolean visible) {
        mSystemUIOverlayWindowController.setWindowVisible(visible);
    }

    /** Sets the insets to fit based on the {@link InsetsType} */
    private void setFitInsetsTypes(@InsetsType int types) {
        mSystemUIOverlayWindowController.setFitInsetsTypes(types);
    }

    /** Sets the insets to fit based on the {@link InsetsSide} */
    private void setFitInsetsSides(@InsetsSide int sides) {
        mSystemUIOverlayWindowController.setFitInsetsSides(sides);
    }

    /**
     * Sets the {@link android.view.WindowManager.LayoutParams#FLAG_ALT_FOCUSABLE_IM} flag of the
     * sysui overlay window.
     */
    public void setWindowNeedsInput(boolean needsInput) {
        mSystemUIOverlayWindowController.setWindowNeedsInput(needsInput);
    }

    /** Returns {@code true} if the window is focusable. */
    public boolean isWindowFocusable() {
        return mSystemUIOverlayWindowController.isWindowFocusable();
    }

    /** Sets the focusable flag of the sysui overlawy window. */
    public void setWindowFocusable(boolean focusable) {
        mSystemUIOverlayWindowController.setWindowFocusable(focusable);
        if (mOverlayVisibilityMediator.getHighestZOrderOverlayViewController() != null) {
            mOverlayVisibilityMediator.getHighestZOrderOverlayViewController()
                    .onWindowFocusableChanged(focusable);
        }
    }

    /**
     * Return {@code true} if OverlayWindow is in a state where HUNs should be displayed above it.
     */
    public boolean shouldShowHUN() {
        OverlayViewController highestZOrder = mOverlayVisibilityMediator
                .getHighestZOrderOverlayViewController();
        return !mOverlayVisibilityMediator.isAnyOverlayViewVisible()
                || highestZOrder.shouldShowHUN();
    }

    /**
     * Set the OverlayViewWindow to be in occluded or unoccluded state. When OverlayViewWindow is
     * occluded, all views mounted to it that are not configured to be shown during occlusion will
     * be hidden.
     */
    public void setOccluded(boolean occluded) {
        if (occluded) {
            // Hide views before setting mIsOccluded to true so the regular hideView logic is used,
            // not the one used during occlusion.
            hideViewsForOcclusion();
            mIsOccluded = true;
        } else {
            mIsOccluded = false;
            // show views after setting mIsOccluded to false so the regular showView logic is used,
            // not the one used during occlusion.
            showViewsHiddenForOcclusion();
        }
    }

    private void hideViewsForOcclusion() {
        HashSet<OverlayViewController> viewsCurrentlyShowing = new HashSet<>(
                mOverlayVisibilityMediator.getVisibleOverlayViewsByZOrder());
        viewsCurrentlyShowing.forEach(overlayController -> {
            if (!overlayController.shouldShowWhenOccluded()) {
                hideView(overlayController, overlayController::hideInternal);
                mViewsHiddenForOcclusion.add(overlayController);
            }
        });
    }

    private void showViewsHiddenForOcclusion() {
        mViewsHiddenForOcclusion.forEach(overlayViewController -> {
            showView(overlayViewController, overlayViewController::showInternal);
        });
        mViewsHiddenForOcclusion.clear();
    }

    private void debugLog() {
        if (!DEBUG) {
            return;
        }

        Log.d(TAG, "HighestZOrder: " + mOverlayVisibilityMediator
                .getHighestZOrderOverlayViewController());
        Log.d(TAG, "Number of visible overlays: " + mOverlayVisibilityMediator
                .getVisibleOverlayViewsByZOrder().size());
        Log.d(TAG, "Is any overlay visible: " + mOverlayVisibilityMediator
                .isAnyOverlayViewVisible());
        Log.d(TAG, "mIsOccluded: " + mIsOccluded);
        Log.d(TAG, "mViewsHiddenForOcclusion: " + mViewsHiddenForOcclusion);
        Log.d(TAG, "mViewsHiddenForOcclusion.size(): " + mViewsHiddenForOcclusion.size());
    }
}
