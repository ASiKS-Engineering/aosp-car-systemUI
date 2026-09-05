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

package com.android.systemui.car.keyguard.passenger;

import static com.android.systemui.car.keyguard.KeyguardConstants.OVERLAY_TYPE_PASSENGER_KEYGUARD;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.car.shared.R;
import com.android.systemui.car.window.OverlayViewController;
import com.android.systemui.car.window.OverlayViewGlobalStateController;
import com.android.systemui.dagger.SysUISingleton;

import javax.inject.Inject;

/**
 * Controller for the instantiation and visibility of the passenger keyguard overlay.
 */
@SysUISingleton
public class PassengerKeyguardOverlayViewController extends OverlayViewController {
    private final Context mContext;
    private final PassengerKeyguardCredentialViewControllerFactory mCredentialViewFactory;

    private PassengerKeyguardCredentialViewController mCredentialViewController;

    @Inject
    public PassengerKeyguardOverlayViewController(
            Context context,
            OverlayViewGlobalStateController overlayViewGlobalStateController,
            PassengerKeyguardCredentialViewControllerFactory credentialViewFactory) {
        super(overlayViewGlobalStateController);
        mContext = context;
        mCredentialViewFactory = credentialViewFactory;
    }

    @Override
    public String getOverlayType() {
        return OVERLAY_TYPE_PASSENGER_KEYGUARD;
    }

    @Override
    public View inflate() {
        if (isInflated()) return mLayout;

        LayoutInflater inflater = LayoutInflater.from(mContext);
        mLayout = inflater.inflate(R.layout.passenger_keyguard_overlay_window, /* root= */ null,
                /* attachToRoot= */ false);

        mCredentialViewController = mCredentialViewFactory.create(
                mLayout.requireViewById(R.id.passenger_keyguard_frame));
        mCredentialViewController.setAuthSucceededCallback(this::stop);
        return mLayout;
    }

    @Override
    protected void hideInternal() {
        super.hideInternal();
        if (mCredentialViewController != null) {
            mCredentialViewController.onOverlayHidden();
        }
    }
}
