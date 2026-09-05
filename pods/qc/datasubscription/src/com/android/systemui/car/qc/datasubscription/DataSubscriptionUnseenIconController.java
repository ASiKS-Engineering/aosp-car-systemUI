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

package com.android.systemui.car.qc.datasubscription;

import android.view.View;

import androidx.annotation.VisibleForTesting;

import com.android.car.datasubscription.DataSubscription;
import com.android.car.datasubscription.Flags;
import com.android.systemui.car.flexibleui.CarSystemBarElementController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.flexibleui.layout.CarSystemBarImageView;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

/**
 * Controller to display the unseen icon for signal status icon
 */
public class DataSubscriptionUnseenIconController extends
        CarSystemBarElementController<CarSystemBarImageView>
        implements DataSubscription.DataSubscriptionChangeListener{
    private DataSubscription mSubscription;

    @AssistedInject
    DataSubscriptionUnseenIconController(@Assisted CarSystemBarImageView view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController) {
        super(view, disableController, stateController);
        mSubscription = new DataSubscription(mView.getContext());
    }

    @Override
    public void onStatusChanged(int value) {
        updateShouldDisplayUnseenIcon();
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<CarSystemBarImageView,
                    DataSubscriptionUnseenIconController> {
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        if (Flags.dataSubscriptionPopUp()) {
            if (mSubscription.isDataSubscriptionInactive()) {
                mView.setVisibility(View.VISIBLE);
            }
            mSubscription.addDataSubscriptionListener(this);
        }
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        if (Flags.dataSubscriptionPopUp()) {
            mSubscription.removeDataSubscriptionListener();
        }
    }


    /**
     * update unseen icon's visibility based on data subscription status
     */
    public void updateShouldDisplayUnseenIcon() {
        mView.post(() -> {
            if (mSubscription.isDataSubscriptionInactive()) {
                mView.setVisibility(View.VISIBLE);
            } else {
                mView.setVisibility(View.GONE);
            }
        });
    }


    @VisibleForTesting
    void setSubscription(DataSubscription subscription) {
        mSubscription = subscription;
    }
}
