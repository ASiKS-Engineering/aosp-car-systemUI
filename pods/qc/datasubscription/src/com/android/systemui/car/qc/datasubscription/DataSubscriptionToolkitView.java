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

package com.android.systemui.car.qc.datasubscription;

import static android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED;

import static com.android.car.datasubscription.DataSubscription.DATA_SUBSCRIPTION_ACTION;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.car.datasubscription.DataSubscriptionController;
import com.android.car.datasubscription.DataSubscriptionMessageCreator;
import com.android.car.datasubscription.DataSubscriptionMessageEventListener;
import com.android.car.datasubscription.DataSubscriptionViewActionListener;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.settings.UserTracker;

import java.util.concurrent.Executor;

import javax.inject.Inject;

/**
 * Create a toolkit view for data subscription controller
 */
public class DataSubscriptionToolkitView implements DataSubscriptionMessageEventListener {
    private final DataSubscriptionStatsLogHelper mDataSubscriptionStatsLogHelper;
    private final Context mContext;
    @NonNull
    private PopupWindow mPopupWindow;
    private Button mExplorationButton;
    private final View mPopupView;
    private final Intent mIntent;
    private final int mPopUpTimeOut;
    private final UserTracker mUserTracker;
    private View mAnchorView;
    private boolean mIsProactiveMessage;
    private DataSubscriptionViewActionListener mListener;
    private TextView mPopUpPrompt;
    private TextView mUxrPrompt;
    private final Executor mMainExecutor;
    private final Runnable mDismissRunnable;

    private final UserTracker.Callback mUserChangedCallback =
            new UserTracker.Callback() {
                @Override
                public void onUserChanged(int newUser, Context userContext) {
                    mListener.setUserId(newUser);
                }
            };

    @Inject
    public DataSubscriptionToolkitView(
            Context context,
            UserTracker userTracker,
            DataSubscriptionStatsLogHelper dataSubscriptionStatsLogHelper,
            DataSubscriptionMessageCreator dataSubscriptionMessageCreator,
            @Main Executor mainExecutor) {
        mContext = context;
        mUserTracker = userTracker;
        mDataSubscriptionStatsLogHelper = dataSubscriptionStatsLogHelper;
        mListener = new DataSubscriptionController(mContext, dataSubscriptionMessageCreator);
        mMainExecutor = mainExecutor;
        mIntent = new Intent(DATA_SUBSCRIPTION_ACTION);
        mIntent.setPackage(mContext.getString(
                com.android.car.datasubscription.R.string.connectivity_flow_app));
        mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        LayoutInflater inflater = LayoutInflater.from(mContext);
        mPopupView = inflater.inflate(R.layout.data_subscription_popup_window, null);
        mPopUpTimeOut = mContext.getResources().getInteger(
                R.integer.data_subscription_pop_up_timeout);
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true;
        mPopupWindow = new PopupWindow(mPopupView, width, height, focusable);
        mPopupWindow.setTouchModal(false);
        mPopupWindow.setOutsideTouchable(true);
        mPopupWindow.setInputMethodMode(INPUT_METHOD_NOT_NEEDED);
        mDismissRunnable = () -> {
            if (mPopupWindow != null && mPopupWindow.isShowing()) {
                mIsProactiveMessage = false;
                mPopupWindow.dismiss();
                mDataSubscriptionStatsLogHelper.logSessionFinished();
            }
        };
        mPopupView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mAnchorView != null) {
                    mAnchorView.getHandler().removeCallbacks(mDismissRunnable);
                }
                mPopupWindow.dismiss();
                mDataSubscriptionStatsLogHelper.logSessionFinished();
                return true;
            }
        });

        mExplorationButton = mPopupView.findViewById(
                R.id.data_subscription_explore_options_button);
        mExplorationButton.setOnClickListener(v -> {
            if (mAnchorView != null) {
                mAnchorView.getHandler().removeCallbacks(mDismissRunnable);
            }
            mPopupWindow.dismiss();
            mContext.startActivityAsUser(mIntent, mUserTracker.getUserHandle());
            mDataSubscriptionStatsLogHelper.logButtonClicked();
        });
        mPopUpPrompt = mPopupView.findViewById(R.id.popup_text_view);
        mUxrPrompt = mPopupView.findViewById(R.id.popup_uxr_text_view);
    }

    @Override
    public boolean onDataSubscriptionStatusChanged(boolean isUxrRequired,
            String proactiveMessage, String uxrPrompt) {
        if (isUxrRequired && mPopupWindow.isShowing()) {
            mPopupWindow.dismiss();
            mDataSubscriptionStatsLogHelper.logSessionFinished();
            return false;
        }
        if (proactiveMessage != null && !proactiveMessage.isEmpty()
                && !mPopupWindow.isShowing()) {
            mIsProactiveMessage = true;
            showPopUpWindow(proactiveMessage, uxrPrompt);
            return true;
        }
        return false;
    }
    @Override
    public boolean onAppForegrounded(boolean isUxrRequired, String reactiveMessage,
            String uxrPrompt) {
        if (isUxrRequired) {
            mExplorationButton.setVisibility(View.GONE);
        } else {
            mExplorationButton.setVisibility(View.VISIBLE);
        }

        if (reactiveMessage != null && !reactiveMessage.isEmpty()
                && !mPopupWindow.isShowing()) {
            mIsProactiveMessage = false;
            showPopUpWindow(reactiveMessage, uxrPrompt);
            return true;
        }
        return false;
    }

    @Override
    public boolean onUxrChanged(boolean isUxrRequired, String uxrPrompt) {
        if (!mPopupWindow.isShowing()) {
            return true;
        }
        if (mIsProactiveMessage && isUxrRequired) {
            if (mAnchorView != null) {
                mAnchorView.getHandler().removeCallbacks(mDismissRunnable);
            }
            mPopupWindow.dismiss();
            mDataSubscriptionStatsLogHelper.logSessionFinished();
            return true;
        }

        if (!mIsProactiveMessage) {
            if (mAnchorView != null) {
                mAnchorView.getHandler().removeCallbacks(mDismissRunnable);
            }
            mPopupWindow.dismiss();

            mUxrPrompt.setText(uxrPrompt);
            if (isUxrRequired) {
                mExplorationButton.setVisibility(View.GONE);
            } else {
                mExplorationButton.setVisibility(View.VISIBLE);
            }

            showPopupWindowWithAutoDismiss();
            return true;
        }
        return false;
    }

    @VisibleForTesting
    void showPopUpWindow(String message, String uxrPrompt) {
        if (mAnchorView != null) {
            mPopUpPrompt.setText(message);
            mUxrPrompt.setText(uxrPrompt);
            if (mIsProactiveMessage) {
                mDataSubscriptionStatsLogHelper.logSessionStarted(
                        DataSubscriptionStatsLogHelper.DataSubscriptionMessageType
                                .PROACTIVE);
            } else {
                mDataSubscriptionStatsLogHelper.logSessionStarted(
                        DataSubscriptionStatsLogHelper.DataSubscriptionMessageType
                                .REACTIVE);
            }
            showPopupWindowWithAutoDismiss();
        }
    }

    /**
     * Shows the popup window at the anchor's position and sets the auto-dismiss timer.
     */
    private void showPopupWindowWithAutoDismiss() {
        if (mAnchorView == null) {
            return;
        }

        // ALWAYS cancel any previously scheduled dismiss runnable first.
        mAnchorView.getHandler().removeCallbacks(mDismissRunnable);

        int xOffsetInPx = mContext.getResources().getDimensionPixelSize(
                R.dimen.data_subscription_pop_up_horizontal_offset);
        int yOffsetInPx = mContext.getResources().getDimensionPixelSize(
                R.dimen.data_subscription_pop_up_vertical_offset);

        mAnchorView.post(() -> {
            mPopupWindow.showAsDropDown(mAnchorView, -xOffsetInPx, yOffsetInPx);
            mAnchorView.getHandler().postDelayed(mDismissRunnable, mPopUpTimeOut);
        });
    }

    /** Set the anchor view. If null, unregisters active data subscription listeners */
    public void setAnchorView(View view) {
        mAnchorView = view;
        if (view != null) {
            mListener.setDataSubscriptionMessageEventListener(this);
            mListener.registerListeners();
            mListener.setUserId(mUserTracker.getUserId());
            mUserTracker.addCallback(mUserChangedCallback, mMainExecutor);
        } else {
            if (mListener != null) {
                mListener.setDataSubscriptionMessageEventListener(null);
                mListener.unregisterListeners();
                mUserTracker.removeCallback(mUserChangedCallback);
            }
        }
    }

    @VisibleForTesting
    void setPopupWindow(PopupWindow popupWindow) {
        mPopupWindow = popupWindow;
    }

    @VisibleForTesting
    void setIsProactiveMessage(boolean isProactiveMessage) {
        mIsProactiveMessage = isProactiveMessage;
    }

    @VisibleForTesting
    Button getExplorationButton() {
        return mExplorationButton;
    }

    @VisibleForTesting
    TextView getPopUpPrompt() {
        return mPopUpPrompt;
    }

    @VisibleForTesting
    void setDataSubscriptionViewActionListener(DataSubscriptionViewActionListener listener) {
        mListener = listener;
    }
}
