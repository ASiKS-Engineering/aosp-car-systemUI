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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.IdRes;
import androidx.annotation.VisibleForTesting;

import com.android.car.ui.FocusParkingView;
import com.android.car.ui.utils.ViewUtils;
import com.android.systemui.Gefingerpoken;
import com.android.systemui.car.shared.R;
import com.android.systemui.car.systembar.base.element.CarSystemBarElementInitializer;
import com.android.systemui.car.window.OverlayPanelViewController;
import com.android.systemui.car.window.OverlayViewController;
import com.android.systemui.car.window.OverlayVisibilityMediator;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.util.ViewController;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.Set;

/**
 * A controller for initializing the system bar views.
 */
public class CarSystemBarViewControllerImpl
        extends ViewController<CarSystemBarViewControllerImpl.TouchInterceptingFrameLayout>
        implements CarSystemBarViewController, Gefingerpoken {

    private static final String TAG = CarSystemBarViewControllerImpl.class.getSimpleName();
    private static final String LAST_FOCUSED_VIEW_ID = "last_focused_view_id";

    protected final Context mContext;

    private final UserTracker mUserTracker;
    private final CarSystemBarElementInitializer mCarSystemBarElementInitializer;
    private final SystemBarConfigs mSystemBarConfigs;
    private final ButtonRoleHolderController mButtonRoleHolderController;
    private final String mName;
    private final OverlayVisibilityMediator mOverlayVisibilityMediator;

    private final boolean mConsumeTouchWhenPanelOpen;
    private final boolean mButtonsDraggable;
    private View mNavButtons;
    private View mLockScreenButtons;
    private View mOcclusionButtons;
    // used to wire in open/close gestures for overlay panels
    private Set<View.OnTouchListener> mSystemBarTouchListeners;

    @AssistedInject
    public CarSystemBarViewControllerImpl(Context context,
            UserTracker userTracker,
            CarSystemBarElementInitializer elementInitializer,
            SystemBarConfigs systemBarConfigs,
            ButtonRoleHolderController buttonRoleHolderController,
            OverlayVisibilityMediator overlayVisibilityMediator,
            @Assisted String name,
            @Assisted ViewGroup systemBarView) {
        super(new TouchInterceptingFrameLayout(context, systemBarView));

        mContext = context;
        mUserTracker = userTracker;
        mCarSystemBarElementInitializer = elementInitializer;
        mSystemBarConfigs = systemBarConfigs;
        mButtonRoleHolderController = buttonRoleHolderController;
        mName = name;
        mOverlayVisibilityMediator = overlayVisibilityMediator;

        mConsumeTouchWhenPanelOpen = getResources().getBoolean(
                R.bool.config_consumeSystemBarTouchWhenNotificationPanelOpen);
        mButtonsDraggable = getResources().getBoolean(R.bool.config_systemBarButtonsDraggable);
    }

    @Override
    protected void onInit() {
        // Include a FocusParkingView at the beginning. The rotary controller "parks" the focus here
        // when the user navigates to another window. This is also used to prevent wrap-around.
        mView.addView(new FocusParkingView(mContext), 0);
        mView.setTouchListener(this);

        setupSystemBarButtons(mView, mUserTracker);
        mCarSystemBarElementInitializer.initializeCarSystemBarElements(mView);

        mNavButtons = mView.findViewById(R.id.nav_buttons);
        mLockScreenButtons = mView.findViewById(R.id.lock_screen_nav_buttons);
        mOcclusionButtons = mView.findViewById(R.id.occlusion_buttons);
        // Needs to be clickable so that it will receive ACTION_MOVE events.
        mView.setClickable(true);
        // Needs to not be focusable so rotary won't highlight the entire nav bar.
        mView.setFocusable(false);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        // The focused view will be destroyed during re-layout, causing the framework to adjust
        // the focus unexpectedly. To avoid that, move focus to a view that won't be
        // destroyed during re-layout and has no focus highlight (the FocusParkingView), then
        // move focus back to the previously focused view after re-layout.
        outState.putInt(LAST_FOCUSED_VIEW_ID, cacheAndHideFocus(mView));
    }

    @Override
    public void onRestoreInstanceState(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            Log.w(TAG, "savedInstanceState is null for " + mName);
            return;
        }
        restoreFocus(mView, savedInstanceState.getInt(LAST_FOCUSED_VIEW_ID, View.NO_ID));
    }

    @Override
    public ViewGroup getView() {
        return mView;
    }

    @Override
    public void setSystemBarTouchListeners(
            Set<View.OnTouchListener> systemBarTouchListeners) {
        mSystemBarTouchListeners = systemBarTouchListeners;
    }

    @Override
    public void showButtonsOfType(@ButtonsType int buttonsType) {
        switch(buttonsType) {
            case BUTTON_TYPE_NAVIGATION:
                setNavigationButtonsVisibility(View.VISIBLE);
                setKeyguardButtonsVisibility(View.GONE);
                setOcclusionButtonsVisibility(View.GONE);
                break;
            case BUTTON_TYPE_KEYGUARD:
                setNavigationButtonsVisibility(View.GONE);
                setKeyguardButtonsVisibility(View.VISIBLE);
                setOcclusionButtonsVisibility(View.GONE);
                break;
            case BUTTON_TYPE_OCCLUSION:
                setNavigationButtonsVisibility(View.GONE);
                setKeyguardButtonsVisibility(View.GONE);
                setOcclusionButtonsVisibility(View.VISIBLE);
                break;
        }
    }

    /**
     * Used to forward touch events even if the touch was initiated from a child component
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mSystemBarTouchListeners != null && !mSystemBarTouchListeners.isEmpty()) {
            if (!mButtonsDraggable) {
                return false;
            }

            OverlayViewController topOverlay =
                    mOverlayVisibilityMediator.getHighestZOrderOverlayViewController();
            boolean shouldConsumeEvent = topOverlay instanceof OverlayPanelViewController
                    ? ((OverlayPanelViewController) topOverlay).shouldPanelConsumeSystemBarTouch()
                    : false;

            // Forward touch events to the status bar window so it can drag
            // windows if required (ex. Notification shade)
            triggerAllTouchListeners(mView, ev);

            if (mConsumeTouchWhenPanelOpen && shouldConsumeEvent) {
                return true;
            }
        }
        return false;
    }

    /**
     * Used for forwarding onTouch events on the systembar.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        triggerAllTouchListeners(mView, event);
        return false;
    }

    @Override
    protected void onViewAttached() {
        mSystemBarConfigs.insetSystemBar(mName, mView);

        mButtonRoleHolderController.addAllButtonsWithRoleName(mView);
    }

    @Override
    protected void onViewDetached() {
        mButtonRoleHolderController.removeAll();
    }

    @AssistedFactory
    public interface Factory
            extends CarSystemBarViewControllerFactory<CarSystemBarViewControllerImpl> {
    }

    private void setupSystemBarButtons(View v, UserTracker userTracker) {
        if (v instanceof CarSystemBarButton) {
            ((CarSystemBarButton) v).setUserTracker(userTracker);
        } else if (v instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) v;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                setupSystemBarButtons(viewGroup.getChildAt(i), userTracker);
            }
        }
    }

    private void setNavigationButtonsVisibility(@View.Visibility int visibility) {
        if (mNavButtons != null) {
            mNavButtons.setVisibility(visibility);
        }
    }

    private void setKeyguardButtonsVisibility(@View.Visibility int visibility) {
        if (mLockScreenButtons != null) {
            mLockScreenButtons.setVisibility(visibility);
        }
    }

    private void setOcclusionButtonsVisibility(@View.Visibility int visibility) {
        if (mOcclusionButtons != null) {
            mOcclusionButtons.setVisibility(visibility);
        }
    }

    private void triggerAllTouchListeners(View view, MotionEvent event) {
        if (mSystemBarTouchListeners == null) {
            return;
        }
        for (View.OnTouchListener listener : mSystemBarTouchListeners) {
            listener.onTouch(view, event);
        }
    }

    @VisibleForTesting
    static int cacheAndHideFocus(@Nullable View rootView) {
        if (rootView == null) return View.NO_ID;
        View focusedView = rootView.findFocus();
        if (focusedView == null || focusedView instanceof FocusParkingView) return View.NO_ID;
        int focusedViewId = focusedView.getId();
        ViewUtils.hideFocus(rootView);
        return focusedViewId;
    }

    private static boolean restoreFocus(@Nullable View rootView, @IdRes int viewToFocusId) {
        if (rootView == null || viewToFocusId == View.NO_ID) return false;
        View focusedView = rootView.findViewById(viewToFocusId);
        if (focusedView == null) return false;
        focusedView.requestFocus();
        return true;
    }

    static class TouchInterceptingFrameLayout extends FrameLayout {
        @Nullable
        private Gefingerpoken mTouchListener;

        TouchInterceptingFrameLayout(Context context, ViewGroup content) {
            super(context);
            addView(content);
        }

        void setTouchListener(@Nullable Gefingerpoken listener) {
            mTouchListener = listener;
        }

        /** Called when a touch is being intercepted in a ViewGroup. */
        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return (mTouchListener != null && mTouchListener
                    .onInterceptTouchEvent(ev)) ? true : super.onInterceptTouchEvent(ev);
        }

        /** Called when a touch is being handled by a view. */
        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            return (mTouchListener != null && mTouchListener
                    .onTouchEvent(ev)) ? true : super.onTouchEvent(ev);
        }
    }
}
