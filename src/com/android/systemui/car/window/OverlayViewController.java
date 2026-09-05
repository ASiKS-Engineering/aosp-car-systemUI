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

import static android.view.WindowInsets.Type.statusBars;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS;

import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import androidx.annotation.IdRes;
import androidx.annotation.MainThread;

import com.android.car.ui.FocusArea;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;

/**
 * Owns a {@link View} that is present in SystemUIOverlayWindow.
 */
public abstract class OverlayViewController {
    protected static final int INVALID_INSET_SIDE = -1;
    protected static final int NO_INSET_SIDE = 0;

    private final OverlayViewGlobalStateController mOverlayViewGlobalStateController;

    protected View mLayout;

    protected final ArrayList<OverlayViewStateListener> mViewStateListeners =
            new ArrayList<>();

    public OverlayViewController(
            OverlayViewGlobalStateController overlayViewGlobalStateController) {
        mLayout = null;
        mOverlayViewGlobalStateController = overlayViewGlobalStateController;
    }

    /**
     * Returns the unique String key for this overlay type.
     * This key MUST match the android:tag of the container
     * in sysui_overlay_window.xml.
     */
    public abstract String getOverlayType();

    /**
     * Shows content of {@link OverlayViewController}.
     *
     * Should be used to show view externally and in particular by {@link OverlayViewMediator}.
     */
    @MainThread
    public final void start() {
        mOverlayViewGlobalStateController.showView(/* viewController= */ this, this::show);
    }

    /**
     * Hides content of {@link OverlayViewController}.
     *
     * Should be used to hide view externally and in particular by {@link OverlayViewMediator}.
     */
    @MainThread
    public final void stop() {
        mOverlayViewGlobalStateController.hideView(/* viewController= */ this, this::hide);
    }

    /**
     * Called by OverlayViewGlobalStateController. Subclasses MUST override this to inflate
     * and return their content View. The returned View will be added to the container by the
     * OverlayViewGlobalStateController.
     *
     * @return The inflated View.
     */
    @MainThread
    public abstract View inflate();

    /**
     * Touches will be passed to ONLY the top most OverlayViewController which have the highest
     * z-ordering. This method will not be called for controllers that are not at the top.
     */
    @MainThread
    protected void onTouchEvent(View v, MotionEvent event) {
        // no-op
    }

    /**
     * Returns {@code true} if layout owned by controller has been inflated.
     */
    public final boolean isInflated() {
        return mLayout != null;
    }

    private void show() {
        if (mLayout == null) {
            // layout must be inflated before show() is called.
            return;
        }
        showInternal();
    }

    /**
     * Subclasses should override this method to implement reveal animations and implement logic
     * specific to when the layout owned by the controller is shown.
     *
     * Should only be overridden by Superclass but not called by any {@link OverlayViewMediator}.
     */
    @MainThread
    protected void showInternal() {
        mLayout.setVisibility(View.VISIBLE);
        for (OverlayViewStateListener l : mViewStateListeners) {
            l.onVisibilityChanged(/* isVisible= */ true);
        }
    }

    private void hide() {
        if (mLayout == null) {
            // layout must be inflated before hide() is called.
            return;
        }
        hideInternal();
    }

    /**
     * Subclasses should override this method to implement conceal animations and implement logic
     * specific to when the layout owned by the controller is hidden.
     *
     * Should only be overridden by Superclass but not called by any {@link OverlayViewMediator}.
     */
    @MainThread
    protected void hideInternal() {
        mLayout.setVisibility(View.GONE);
        for (OverlayViewStateListener l : mViewStateListeners) {
            l.onVisibilityChanged(/* isVisible= */ false);
        }
    }

    /** Returns the {@link OverlayViewGlobalStateController}. */
    protected final OverlayViewGlobalStateController getOverlayViewGlobalStateController() {
        return mOverlayViewGlobalStateController;
    }

    /** Returns whether the view controlled by this controller is visible. */
    public final boolean isVisible() {
        return mLayout != null && mLayout.getVisibility() == View.VISIBLE;
    }

    /**
     * Called when the window layout is updated to account for display cutouts.
     * Subclasses should override this method if their layout needs to be adjusted
     * to avoid display obstructions.
     * @param safeInsets The insets from the edges of the screen that are safe from obstructions.
     */
    @MainThread
    public void adjustForDisplayCutout(Rect safeInsets) {
        // no-op
    }

    /**
     * Returns the ID of the focus area that should receive focus when this view is the
     * topmost view or {@link View#NO_ID} if there is no focus area.
     */
    @IdRes
    protected int getFocusAreaViewId() {
        return View.NO_ID;
    }

    /** Returns whether the view controlled by this controller has rotary focus. */
    protected final boolean hasRotaryFocus() {
        return !mLayout.isInTouchMode() && mLayout.hasFocus();
    }

    /**
     * Callback for the individual view controllers when the window focusable state has changed.
     * This will only go to the highest z-order window and will be re-called when the window
     * visibilities change.
     */
    public void onWindowFocusableChanged(boolean focusable) {
    }

    /**
     * Sets whether this view allows rotary focus. This should be set to {@code true} for the
     * topmost layer in the overlay window and {@code false} for the others.
     *
     * @return true if the rotary focus allowed state has changed.
     */
    public boolean setAllowRotaryFocus(boolean allowRotaryFocus) {
        if (!isInflated() || !(mLayout instanceof ViewGroup)) {
            return false;
        }

        ViewGroup viewGroup = (ViewGroup) mLayout;
        int newFocusability = allowRotaryFocus
                ? ViewGroup.FOCUS_BEFORE_DESCENDANTS
                : ViewGroup.FOCUS_BLOCK_DESCENDANTS;
        if (viewGroup.getDescendantFocusability() == newFocusability) {
            return false;
        }
        viewGroup.setDescendantFocusability(newFocusability);
        return true;
    }

    /**
     * Refreshes the rotary focus in this view if we are in rotary mode. If the view already has
     * rotary focus, it leaves the focus alone. Returns {@code true} if a new view was focused.
     */
    public boolean refreshRotaryFocusIfNeeded() {
        if (mLayout.isInTouchMode()) {
            return false;
        }

        if (hasRotaryFocus()) {
            return false;
        }

        View view = mLayout.findViewById(getFocusAreaViewId());
        if (view == null || !(view instanceof FocusArea)) {
            return mLayout.requestFocus();
        }

        FocusArea focusArea = (FocusArea) view;
        return focusArea.performAccessibilityAction(ACTION_FOCUS, /* arguments= */ null);
    }

    /**
     * Returns {@code true} if heads up notifications should be displayed over this view.
     */
    protected boolean shouldShowHUN() {
        return true;
    }

    /**
     * Returns {@code true} if navigation bar insets should be displayed over this view. Has no
     * effect if {@link #shouldFocusWindow} returns {@code false}.
     */
    protected boolean shouldShowNavigationBarInsets() {
        return false;
    }

    /**
     * Returns {@code true} if status bar insets should be displayed over this view. Has no
     * effect if {@link #shouldFocusWindow} returns {@code false}.
     */
    protected boolean shouldShowStatusBarInsets() {
        return false;
    }

    /**
     * Returns {@code true} if this view should be hidden during the occluded state.
     */
    protected boolean shouldShowWhenOccluded() {
        return false;
    }

    /**
     * Returns {@code true} if the window should be focued when this view is visible. Note that
     * returning {@code false} here means that {@link #shouldShowStatusBarInsets} and
     * {@link #shouldShowNavigationBarInsets} will have no effect.
     */
    protected boolean shouldFocusWindow() {
        return true;
    }

    /**
     * Returns the amount of dimming to apply to the overlay window when initially brought to front.
     * Range is from 1.0 for completely opaque to 0.0 for no dim.
     */
    protected float getDefaultDimAmount() {
        return 0f;
    }

    /**
     * Returns {@code true} if the window should use stable insets. Using stable insets means that
     * even when system bars are temporarily not visible, inset from the system bars will still be
     * applied.
     *
     * NOTE: When system bars are hidden in transient mode, insets from them will not be applied
     * even when the system bars become visible. Setting the return value to {@true} here can
     * prevent the OverlayView from overlapping with the system bars when that happens.
     */
    protected boolean shouldUseStableInsets() {
        return false;
    }

    /**
     * Returns the insets types to fit to the sysui overlay window when this
     * {@link OverlayViewController} is in the foreground.
     */
    @WindowInsets.Type.InsetsType
    protected int getInsetTypesToFit() {
        return statusBars();
    }

    /**
     * Optionally returns the sides of enabled system bar insets to fit to the sysui overlay window
     * when this {@link OverlayViewController} is in the foreground.
     *
     * For example, if the bottom and left system bars are enabled and this method returns
     * WindowInsets.Side.LEFT, then the inset from the bottom system bar will be ignored.
     *
     * NOTE: By default, this method returns {@link #INVALID_INSET_SIDE}, so insets to fit are
     * defined by {@link #getInsetTypesToFit()}, and not by this method, unless it is overridden
     * by subclasses.
     *
     * NOTE: {@link #NO_INSET_SIDE} signifies no insets from any system bars will be honored. Each
     * {@link OverlayViewController} can first take this value and add sides of the system bar
     * insets to honor to it.
     *
     * NOTE: If getInsetSidesToFit is overridden to return {@link WindowInsets.Side}, it always
     * takes precedence over {@link #getInsetTypesToFit()}. That is, the return value of {@link
     * #getInsetTypesToFit()} will be ignored.
     */
    @WindowInsets.Side.InsetsSide
    protected int getInsetSidesToFit() {
        return INVALID_INSET_SIDE;
    }

    /** Interface for listening to the state of the overlay panel view. */
    public interface OverlayViewStateListener {

        /** Called when the panel's visibility changes. */
        void onVisibilityChanged(boolean isVisible);
    }

    /**
     * Add a new listener to the state of this overlay panel view.
     */
    public void registerViewStateListener(OverlayViewStateListener listener) {
        mViewStateListeners.add(listener);
    }

    /**
     * Removes listener for state of this overlay panel view.
     */
    public void removePanelViewStateListener(OverlayViewStateListener listener) {
        mViewStateListeners.remove(listener);
    }

    /**
     * Provides access to layout owned by controller.
     */
    @VisibleForTesting
    public final View getLayout() {
        return mLayout;
    }

    /**
     * Set the controller's layout
     */
    @VisibleForTesting
    public void setLayout(View layout) {
        mLayout = layout;
    }
}
