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

package com.android.systemui.car.hvac;

import static com.android.systemui.car.hvac.HvacConstants.OVERLAY_TYPE_HVAC_PANEL;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import androidx.annotation.Nullable;

import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.hvac.ui.R;
import com.android.systemui.car.window.OverlayPanelViewController;
import com.android.systemui.car.window.OverlayViewGlobalStateController;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.wm.shell.animation.FlingAnimationUtils;

import javax.inject.Inject;

@SysUISingleton
public class HvacPanelOverlayViewController extends OverlayPanelViewController implements
        ConfigurationController.ConfigurationListener {
    private static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;
    private static final String TAG = HvacPanelOverlayViewController.class.getName();

    private final Context mContext;
    private final Resources mResources;
    private final Handler mHandler;
    private final HvacController mHvacController;
    private final float mFullyOpenDimAmount;
    private final int mAutoDismissDurationMs;
    private float mCurrentDimAmount = 0f;
    @Nullable
    private Animator mOpenAnimator;
    @Nullable
    private Animator mCloseAnimator;

    private HvacPanelView mHvacPanelView;

    private final Runnable mAutoDismiss = () -> {
        if (isPanelExpanded()) {
            toggle();
        }
    };

    @Inject
    public HvacPanelOverlayViewController(Context context,
            @Main Resources resources,
            @Main Handler handler,
            HvacController hvacController,
            OverlayViewGlobalStateController overlayViewGlobalStateController,
            FlingAnimationUtils.Builder flingAnimationUtilsBuilder,
            CarDeviceProvisionedController carDeviceProvisionedController,
            ConfigurationController configurationController) {
        super(context, resources, overlayViewGlobalStateController,
                flingAnimationUtilsBuilder, carDeviceProvisionedController);
        mContext = context;
        mResources = resources;
        mHandler = handler;
        mHvacController = hvacController;
        configurationController.addCallback(this);
        mFullyOpenDimAmount = mResources.getFloat(R.fraction.hvac_overlay_window_dim_amount);
        mAutoDismissDurationMs = mResources.getInteger(R.integer.config_hvacAutoDismissDurationMs);
    }

    @Override
    public String getOverlayType() {
        return OVERLAY_TYPE_HVAC_PANEL;
    }

    @Override
    public View inflate() {
        if (isInflated()) return mLayout;

        LayoutInflater inflater = LayoutInflater.from(mContext);
        mLayout = inflater.inflate(R.layout.hvac_panel_container, /* root= */ null,
                /* attachToRoot= */ false);

        View closeButton = mLayout.findViewById(R.id.hvac_panel_close_button);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> dismissHvacPanel());
        }

        mHvacPanelView = mLayout.findViewById(R.id.hvac_panel);
        mHvacController.registerHvacViews(mHvacPanelView);

        mHvacPanelView.setKeyEventHandler((event) -> {
            if (event.getKeyCode() != KeyEvent.KEYCODE_BACK) {
                return false;
            }

            if (event.getAction() == KeyEvent.ACTION_UP && isPanelExpanded()) {
                dismissHvacPanel();
            }
            return true;
        });

        mHvacPanelView.setMotionEventHandler((event -> {
            setAutoDismissTimeout();
        }));

        loadCustomAnimators();
        setUpHandleBar();
        return mLayout;
    }

    @Override
    public void adjustForDisplayCutout(Rect safeInsets) {
        if (!isInflated()) return;

        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) mLayout.getLayoutParams();
        if (params != null) {
            params.leftMargin = safeInsets.left;
            params.rightMargin = safeInsets.right;
            // Top and bottom insets are handled by padding in the hvac_panel_container.xml
            mLayout.setLayoutParams(params);
        }
    }

    @Override
    protected int getInsetTypesToFit() {
        return WindowInsets.Type.systemBars();
    }

    @Override
    protected boolean shouldShowStatusBarInsets() {
        return true;
    }

    @Override
    protected boolean shouldShowNavigationBarInsets() {
        return true;
    }

    @Override
    protected boolean shouldAnimateCollapsePanel() {
        return true;
    }

    @Override
    protected boolean shouldAnimateExpandPanel() {
        return true;
    }

    @Override
    protected boolean shouldAllowClosingScroll() {
        return true;
    }

    @Override
    protected float getDefaultDimAmount() {
        return mCurrentDimAmount;
    }

    @Override
    protected Integer getHandleBarViewId() {
        return R.id.handle_bar;
    }

    @Override
    protected int getFocusAreaViewId() {
        return R.id.hvac_panel_container;
    }

    @Override
    protected int getSettleClosePercentage() {
        return mResources.getInteger(R.integer.hvac_panel_settle_close_percentage);
    }

    @Override
    protected void onAnimateExpandPanel() {
        setAutoDismissTimeout();
    }

    @Override
    protected void onAnimateCollapsePanel() {
        removeAutoDismissTimeout();
    }

    @Override
    protected void onCollapseAnimationEnd() {
        // no-op.
    }

    @Override
    protected void onExpandAnimationEnd() {
        // no-op.
    }

    @Override
    protected void onOpenScrollStart() {
        // no-op.
    }

    @Override
    protected void onTouchEvent(View view, MotionEvent event) {
        if (mHvacPanelView == null) {
            return;
        }
        Rect outBounds = new Rect();
        mHvacPanelView.getBoundsInWindow(outBounds, /* clipToParent= */ true);
        if (isPanelExpanded() && (event.getAction() == MotionEvent.ACTION_UP)
                && isTouchOutside(outBounds, event.getX(), event.getY())) {
            dismissHvacPanel();
        }
    }

    @Override
    protected void onScroll(int y) {
        super.onScroll(y);

        float percentageOpen =
                ((float) (mAnimateDirection > 0 ? y : getLayout().getHeight() - y))
                        / getLayout().getHeight();
        mCurrentDimAmount = mFullyOpenDimAmount * percentageOpen;
        getOverlayViewGlobalStateController().updateWindowDimBehind(this, mCurrentDimAmount);
    }

    private boolean isTouchOutside(Rect bounds, float x, float y) {
        return x < bounds.left || x > bounds.right || y < bounds.top || y > bounds.bottom;
    }

    private void dismissHvacPanel() {
        removeAutoDismissTimeout();
        mHandler.post(mAutoDismiss);
    }

    private void setAutoDismissTimeout() {
        if (mAutoDismissDurationMs > 0) {
            mHandler.removeCallbacks(mAutoDismiss);
            mHandler.postDelayed(mAutoDismiss, mAutoDismissDurationMs);
        }
    }

    private void removeAutoDismissTimeout() {
        if (mAutoDismissDurationMs > 0) {
            mHandler.removeCallbacks(mAutoDismiss);
        }
    }

    private void loadCustomAnimators() {
        try {
            mOpenAnimator = AnimatorInflater.loadAnimator(mContext, R.anim.hvac_open_anim);
            mOpenAnimator.setTarget(getLayout());
        } catch (Resources.NotFoundException e) {
            if (DEBUG) {
                Log.d(TAG, "Custom open animator not found - using default");
            }
        }

        try {
            mCloseAnimator = AnimatorInflater.loadAnimator(mContext, R.anim.hvac_close_anim);
            mCloseAnimator.setTarget(getLayout());
        } catch (Resources.NotFoundException e) {
            if (DEBUG) {
                Log.d(TAG, "Custom close animator not found - using default");
            }
        }
    }

    @Override
    protected Animator getCustomAnimator(float from, float to, float velocity, boolean isClosing) {
        Animator animator = isClosing ? mCloseAnimator : mOpenAnimator;
        if (animator != null) {
            animator.removeAllListeners();
            if (animator instanceof ValueAnimator) {
                ((ValueAnimator) animator).setFloatValues(from, to);
            }
        }

        return animator;
    }

    @Override
    public void onConfigChanged(Configuration newConfig) {
        if (getLayout() == null) return;
        mHvacPanelView = getLayout().findViewById(R.id.hvac_panel);
        if (mHvacPanelView == null) return;
        ViewGroup hvacViewGroupParent = (ViewGroup) mHvacPanelView.getParent();

        // cache properties of {@link HvacPanelView}
        int inflatedId = mHvacPanelView.getId();
        ViewGroup.LayoutParams layoutParams = mHvacPanelView.getLayoutParams();
        HvacPanelView.KeyEventHandler hvacKeyEventHandler = mHvacPanelView
                .getKeyEventHandler();
        int indexOfView = hvacViewGroupParent.indexOfChild(mHvacPanelView);

        // remove {@link HvacPanelView} from its parent and reinflate it
        hvacViewGroupParent.removeView(mHvacPanelView);
        HvacPanelView newHvacPanelView = (HvacPanelView) LayoutInflater.from(mContext).inflate(
                R.layout.hvac_panel, /* root= */ hvacViewGroupParent,
                /* attachToRoot= */ false);
        hvacViewGroupParent.addView(newHvacPanelView, indexOfView);
        mHvacPanelView = newHvacPanelView;

        // reset {@link HvacPanelView} cached properties
        mHvacPanelView.setId(inflatedId);
        mHvacPanelView.setLayoutParams(layoutParams);
        mHvacController.registerHvacViews(mHvacPanelView);
        mHvacPanelView.setKeyEventHandler(hvacKeyEventHandler);

        // register handleBar again for reinflated {@link HvacPanelView}
        setUpHandleBar();
    }
}
