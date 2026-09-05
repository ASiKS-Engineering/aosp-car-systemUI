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

import static android.view.WindowInsets.Type.all;

import android.annotation.NonNull;
import android.app.trust.TrustManager;
import android.graphics.Insets;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.shared.R;
import com.android.systemui.settings.UserTracker;

/**
 * Credential ViewController for the password credential type.
 */
public class PassengerKeyguardPasswordViewController extends
        PassengerKeyguardCredentialViewController {
    private EditText mPasswordField;

    protected PassengerKeyguardPasswordViewController(View view,
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
        mView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets insets) {
                // apply insets for the IME - use all() insets type for the case of the IME
                // affecting other insets (such as the navigation bar)
                Insets allInsets = insets.getInsets(all());
                v.setPadding(allInsets.left, allInsets.top, allInsets.right, allInsets.bottom);
                return insets;
            }
        });

        mPasswordField = mView.requireViewById(R.id.password_entry);

        mPasswordField.setOnEditorActionListener((textView, actionId, keyEvent) -> {
            // Check if this was the result of hitting the enter or "done" key.
            if (actionId == EditorInfo.IME_NULL
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_NEXT) {

                verifyCredential(() -> {
                    mPasswordField.setText("");
                    setErrorMessage(
                            getContext().getString(R.string.passenger_keyguard_wrong_password));
                });
                return true;
            }
            return false;
        });

        mPasswordField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                clearError();
            }
        });
    }

    @Override
    protected LockscreenCredential getCurrentCredential() {
        return LockscreenCredential.createPasswordOrNone(mPasswordField.getText());
    }

    @Override
    protected void onLockedOutChanged(boolean isLockedOut) {
        mPasswordField.setEnabled(!isLockedOut);
        mPasswordField.setText("");
    }

    @Override
    public void clearAllCredentials() {
        mPasswordField.setText("");
        super.clearAllCredentials();
    }
}
