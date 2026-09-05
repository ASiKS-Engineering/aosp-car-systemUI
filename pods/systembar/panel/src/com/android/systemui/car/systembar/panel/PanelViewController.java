/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG;
import static android.widget.ListPopupWindow.WRAP_CONTENT;
import static android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED;

import android.app.PendingIntent;
import android.car.drivingstate.CarUxRestrictions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.car.qc.QCItem;
import com.android.car.qc.view.QCView;
import com.android.car.ui.FocusParkingView;
import com.android.car.ui.utils.CarUxRestrictionsUtil;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.flexibleui.CarSystemBarElementController;
import com.android.systemui.car.qc.base.SystemUIQCViewController;
import com.android.systemui.car.systembar.base.element.CarSystemBarElementInitializer;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.util.ViewController;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.ArrayList;
import java.util.List;

/**
 * A controller for a panel view associated with a status icon.
 */
public class PanelViewController extends ViewController<View> {
    private final Context mContext;
    private final UserTracker mUserTracker;
    private final BroadcastDispatcher mBroadcastDispatcher;
    private final ConfigurationController mConfigurationController;
    private final CarDeviceProvisionedController mCarDeviceProvisionedController;
    private final CarSystemBarElementInitializer mCarSystemBarElementInitializer;
    private final String mIdentifier;
    private final PanelContentProvider mPanelContentProvider;
    private final boolean mIsDisabledWhileDriving;
    private final boolean mIsDisabledWhileUnprovisioned;
    private final ArrayList<SystemUIQCViewController> mQCViewControllers = new ArrayList<>();

    private PopupWindow mPanel;
    private ViewGroup mPanelContent;
    private CarUxRestrictionsUtil mCarUxRestrictionsUtil;
    private float mDimValue = -1.0f;
    private View.OnClickListener mOnClickListener;

    private final ConfigurationController.ConfigurationListener mConfigurationListener =
            new ConfigurationController.ConfigurationListener() {
                @Override
                public void onLayoutDirectionChanged(boolean isLayoutRtl) {
                    recreatePanel();
                }
            };

    private final View.OnLayoutChangeListener mPanelContentLayoutChangeListener =
            new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (mPanelContent != null) {
                        mPanelContent.invalidateOutline();
                    }
                }
            };

    private final CarUxRestrictionsUtil.OnUxRestrictionsChangedListener
            mUxRestrictionsChangedListener =
            new CarUxRestrictionsUtil.OnUxRestrictionsChangedListener() {
                @Override
                public void onRestrictionsChanged(@NonNull CarUxRestrictions carUxRestrictions) {
                    if (mIsDisabledWhileDriving
                            && carUxRestrictions.isRequiresDistractionOptimization()
                            && isPanelShowing()) {
                        mPanel.dismiss();
                    }
                }
            };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            boolean isIntentFromSelf =
                    intent.getIdentifier() != null && intent.getIdentifier().equals(mIdentifier);

            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action) && !isIntentFromSelf
                    && isPanelShowing()) {
                mPanel.dismiss();
            }
        }
    };

    private final UserTracker.Callback mUserTrackerCallback = new UserTracker.Callback() {
        @Override
        public void onUserChanged(int newUser, Context userContext) {
            mBroadcastDispatcher.unregisterReceiver(mBroadcastReceiver);
            mBroadcastDispatcher.registerReceiver(mBroadcastReceiver,
                    new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS), /* executor= */ null,
                    mUserTracker.getUserHandle());
        }
    };

    private final ViewTreeObserver.OnGlobalFocusChangeListener mFocusChangeListener =
            (oldFocus, newFocus) -> {
                if (isPanelShowing() && oldFocus != null && newFocus instanceof FocusParkingView) {
                    // When nudging out of the panel, RotaryService will focus on the
                    // FocusParkingView to clear the focus highlight. When this occurs, dismiss the
                    // panel.
                    mPanel.dismiss();
                }
            };

    private final QCView.QCActionListener mQCActionListener = (item, action) -> {
        if (!isPanelShowing()) {
            return;
        }
        if (action instanceof PendingIntent) {
            if (((PendingIntent) action).isActivity()) {
                mPanel.dismiss();
            }
        } else if (action instanceof QCItem.ActionHandler) {
            if (((QCItem.ActionHandler) action).isActivity()) {
                mPanel.dismiss();
            }
        }
    };

    @AssistedInject
    public PanelViewController(Context context,
            UserTracker userTracker,
            BroadcastDispatcher broadcastDispatcher,
            ConfigurationController configurationController,
            CarDeviceProvisionedController deviceProvisionedController,
            CarSystemBarElementInitializer elementInitializer,
            @Assisted View anchorView,
            @Assisted PanelContentProvider panelContentProvider) {
        super(anchorView);
        mContext = context.createWindowContext(context.getDisplay(),
                WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG, null);
        mUserTracker = userTracker;
        mBroadcastDispatcher = broadcastDispatcher;
        mConfigurationController = configurationController;
        mCarDeviceProvisionedController = deviceProvisionedController;
        mCarSystemBarElementInitializer = elementInitializer;
        mPanelContentProvider = panelContentProvider;
        mIsDisabledWhileDriving = mPanelContentProvider.isDisabledWhileDriving();
        mIsDisabledWhileUnprovisioned = mPanelContentProvider.isDisabledWhileUnprovisioned();
        mIdentifier = Integer.toString(System.identityHashCode(this));
    }

    @Override
    protected void onInit() {
        mOnClickListener = v -> {
            if (mIsDisabledWhileUnprovisioned && !isDeviceSetupForUser()) {
                return;
            }
            if (mIsDisabledWhileDriving && mCarUxRestrictionsUtil.getCurrentRestrictions()
                    .isRequiresDistractionOptimization()) {
                dismissAllSystemDialogs();
                Toast.makeText(mContext,
                        com.android.car.ui.R.string.car_ui_restricted_while_driving,
                        Toast.LENGTH_LONG).show();
                return;
            }

            if (mPanel == null && !createPanel()) {
                return;
            }

            if (mPanel.isShowing()) {
                mPanel.dismiss();
                return;
            }

            // Dismiss all currently open system dialogs before opening this panel.
            dismissAllSystemDialogs();

            registerFocusListener(true);

            int xOffsetPx = mPanelContentProvider.getXOffsetPx();
            int yOffsetPx = mPanelContentProvider.getYOffsetPx();
            int gravity = mPanelContentProvider.getPanelGravity();
            boolean showAsDropDown = mPanelContentProvider.getShowAsDropDown();

            if (showAsDropDown) {
                // TODO(b/202563671): remove yOffsetPx when the PopupWindow API is updated.
                mPanel.showAsDropDown(mView, xOffsetPx, yOffsetPx, gravity);
            } else {
                int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;
                int animationStyle = verticalGravity == Gravity.BOTTOM
                        ? com.android.internal.R.style.Animation_DropDownUp
                        : com.android.internal.R.style.Animation_DropDownDown;
                mPanel.setAnimationStyle(animationStyle);
                mPanel.showAtLocation(mView, gravity, xOffsetPx, yOffsetPx);
            }
            mView.setSelected(true);
            setAnimatedStatusIconHighlightedStatus(true);
            dimBehind(mPanel);
        };

        mView.setOnClickListener(mOnClickListener);
    }

    @Override
    protected void onViewAttached() {
        if (mPanel == null) {
            createPanel();
        }
        mBroadcastDispatcher.registerReceiver(mBroadcastReceiver,
                new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS), /* executor= */ null,
                mUserTracker.getUserHandle());
        mUserTracker.addCallback(mUserTrackerCallback, mContext.getMainExecutor());
        mConfigurationController.addCallback(mConfigurationListener);

        if (mIsDisabledWhileDriving) {
            mCarUxRestrictionsUtil = CarUxRestrictionsUtil.getInstance(mContext);
            mCarUxRestrictionsUtil.register(mUxRestrictionsChangedListener);
        }
    }

    @Override
    protected void onViewDetached() {
        reset();
        if (mCarUxRestrictionsUtil != null) {
            mCarUxRestrictionsUtil.unregister(mUxRestrictionsChangedListener);
        }
        mConfigurationController.removeCallback(mConfigurationListener);
        mUserTracker.removeCallback(mUserTrackerCallback);
        mBroadcastDispatcher.unregisterReceiver(mBroadcastReceiver);
    }

    @VisibleForTesting
    PopupWindow getPanel() {
        return mPanel;
    }

    @VisibleForTesting
    BroadcastReceiver getBroadcastReceiver() {
        return mBroadcastReceiver;
    }

    @VisibleForTesting
    String getIdentifier() {
        return mIdentifier;
    }

    @VisibleForTesting
    View.OnClickListener getOnClickListener() {
        return mOnClickListener;
    }

    @VisibleForTesting
    ConfigurationController.ConfigurationListener getConfigurationListener() {
        return mConfigurationListener;
    }

    @VisibleForTesting
    UserTracker.Callback getUserTrackerCallback() {
        return mUserTrackerCallback;
    }

    @VisibleForTesting
    ViewTreeObserver.OnGlobalFocusChangeListener getFocusChangeListener() {
        return mFocusChangeListener;
    }

    @VisibleForTesting
    QCView.QCActionListener getQCActionListener() {
        return mQCActionListener;
    }

    /**
     * Create the PopupWindow panel and assign to {@link mPanel}.
     *
     * @return true if the panel was created, false otherwise
     */
    private boolean createPanel() {
        mPanelContent = mPanelContentProvider.createPanelContentView(mContext);
        if (mPanelContent == null) {
            return false;
        }

        int panelWidth = mPanelContentProvider.getPanelWidthPx();
        Drawable panelBackgroundDrawable = mContext.getResources()
                .getDrawable(R.drawable.status_icon_panel_bg, mContext.getTheme());
        // clip content to the panel background (to handle rounded corners)
        mPanelContent.setOutlineProvider(new DrawableViewOutlineProvider(panelBackgroundDrawable));
        mPanelContent.setClipToOutline(true);
        mPanelContent.addOnLayoutChangeListener(mPanelContentLayoutChangeListener);

        // initialize special views
        initQCElementViews(mPanelContent);

        // initialize panel
        mPanel = new PopupWindow(mPanelContent, panelWidth, WRAP_CONTENT);
        mPanel.setBackgroundDrawable(panelBackgroundDrawable);
        mPanel.setWindowLayoutType(TYPE_SYSTEM_DIALOG);
        mPanel.setFocusable(true);
        mPanel.setInputMethodMode(INPUT_METHOD_NOT_NEEDED);
        mPanel.setOutsideTouchable(false);
        mPanel.setOnDismissListener(() -> {
            setAnimatedStatusIconHighlightedStatus(false);
            mView.setSelected(false);
            registerFocusListener(false);
        });

        return true;
    }

    private void dimBehind(PopupWindow popupWindow) {
        View container = popupWindow.getContentView().getRootView();
        WindowManager wm = mContext.getSystemService(WindowManager.class);

        if (wm == null) return;

        if (mDimValue < 0) {
            mDimValue = mContext.getResources().getFloat(R.dimen.car_status_icon_panel_dim);
        }

        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) container.getLayoutParams();
        lp.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        if (mPanelContentProvider.getShowAsDropDown()) {
            // We don't need to account for insets in showAsDropDown since we use offsets
            lp.setFitInsetsTypes(0);
        }
        lp.dimAmount = mDimValue;
        wm.updateViewLayout(container, lp);
    }

    private void dismissAllSystemDialogs() {
        Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        intent.setIdentifier(mIdentifier);
        mContext.getApplicationContext().sendBroadcastAsUser(intent, mUserTracker.getUserHandle());
    }

    private void registerFocusListener(boolean register) {
        if (mPanelContent == null) {
            return;
        }
        if (register) {
            mPanelContent.getViewTreeObserver().addOnGlobalFocusChangeListener(
                    mFocusChangeListener);
        } else {
            mPanelContent.getViewTreeObserver().removeOnGlobalFocusChangeListener(
                    mFocusChangeListener);
        }
    }

    private void reset() {
        if (mPanel == null) return;

        mPanel.dismiss();
        mPanel = null;
        if (mPanelContent != null) {
            mPanelContent.removeOnLayoutChangeListener(mPanelContentLayoutChangeListener);
        }
        mPanelContent = null;
        mQCViewControllers.forEach(SystemUIQCViewController::destroyQCViews);
        mQCViewControllers.clear();
    }

    private void recreatePanel() {
        reset();
        createPanel();
    }

    private void initQCElementViews(ViewGroup rootView) {
        List<CarSystemBarElementController> controllers =
                mCarSystemBarElementInitializer.initializeCarSystemBarElements(rootView);
        for (CarSystemBarElementController controller : controllers) {
            if (controller instanceof SystemUIQCViewController) {
                SystemUIQCViewController qcController = (SystemUIQCViewController) controller;
                qcController.setActionListener(mQCActionListener);
                mQCViewControllers.add(qcController);
            }
        }
    }

    private <T extends View> List<T> findViewsOfType(ViewGroup rootView, Class<T> clazz) {
        List<T> views = new ArrayList<>();
        for (int i = 0; i < rootView.getChildCount(); i++) {
            View v = rootView.getChildAt(i);
            if (clazz.isInstance(v)) {
                views.add(clazz.cast(v));
            } else if (v instanceof ViewGroup) {
                views.addAll(findViewsOfType((ViewGroup) v, clazz));
            }
        }
        return views;
    }

    private void setAnimatedStatusIconHighlightedStatus(boolean isHighlighted) {
        if (mView instanceof AnimatedStatusIcon) {
            ((AnimatedStatusIcon) mView).setIconHighlighted(isHighlighted);
        }
    }

    private boolean isPanelShowing() {
        return mPanel != null && mPanel.isShowing();
    }

    private boolean isDeviceSetupForUser() {
        return mCarDeviceProvisionedController.isCurrentUserSetup()
                && !mCarDeviceProvisionedController.isCurrentUserSetupInProgress();
    }

    private static class DrawableViewOutlineProvider extends ViewOutlineProvider {
        private final Drawable mDrawable;

        private DrawableViewOutlineProvider(Drawable drawable) {
            mDrawable = drawable;
        }

        @Override
        public void getOutline(View view, Outline outline) {
            if (mDrawable != null) {
                mDrawable.getOutline(outline);
            } else {
                outline.setRect(0, 0, view.getWidth(), view.getHeight());
                outline.setAlpha(0.0f);
            }
        }
    }

    /**
     * Factory for creating {@link PanelViewController} instances.
     */
    @AssistedFactory
    public interface Factory {
        /**
         * Creates a new instance of {@link PanelViewController}.
         *
         * @param anchorView The view that the panel is anchored to.
         * @param panelContentProvider The provider for the panel's content.
         * @return A new {@link PanelViewController} instance.
         */
        PanelViewController create(View anchorView,
                PanelContentProvider panelContentProvider);
    }
}
