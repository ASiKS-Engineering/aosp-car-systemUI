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

import android.app.trust.TrustManager;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.shared.R;
import com.android.systemui.settings.UserTracker;

/**
 * Credential ViewController for the pin credential type.
 */
public class PassengerKeyguardPinViewController extends PassengerKeyguardCredentialViewController {
    private PassengerPinPadView mPinPad;
    private EditText mPasswordField;

    protected PassengerKeyguardPinViewController(View view,
            LockPatternUtils lockPatternUtils,
            UserTracker userTracker,
            TrustManager trustManager, Handler mainHandler,
            CarServiceProvider carServiceProvider,
            PassengerKeyguardLockoutHelper lockoutHelper) {
        super(view, lockPatternUtils, userTracker, trustManager, mainHandler, carServiceProvider,
                lockoutHelper);
    }

    @Override
    protected void onInit() {
        super.onInit();
        mPasswordField = mView.requireViewById(R.id.password_entry);
        mPinPad = mView.requireViewById(R.id.passenger_pin_pad);

        mPinPad.setPinPadClickListener(
                new PassengerPinPadView.PinPadClickListener() {
                    @Override
                    public void onDigitKeyClick(String digit) {
                        clearError();
                        mPasswordField.append(digit);
                    }

                    @Override
                    public void onBackspaceClick() {
                        clearError();
                        if (!TextUtils.isEmpty(mPasswordField.getText())) {
                            mPasswordField.getText().delete(mPasswordField.getSelectionEnd() - 1,
                                    mPasswordField.getSelectionEnd());
                        }
                    }

                    @Override
                    public void onEnterKeyClick() {
                        verifyCredential(() -> {
                            mPinPad.setEnabled(true);
                            mPasswordField.setText("");
                            setErrorMessage(
                                    getContext().getString(R.string.passenger_keyguard_wrong_pin));
                        });
                    }
                });
    }

    @Override
    protected LockscreenCredential getCurrentCredential() {
        return LockscreenCredential.createPinOrNone(mPasswordField.getText());
    }

    @Override
    protected void onLockedOutChanged(boolean isLockedOut) {
        mPinPad.setEnabled(!isLockedOut);
        mPasswordField.setText("");
    }

    @Override
    public void clearAllCredentials() {
        mPasswordField.setText("");
        super.clearAllCredentials();
    }
}
