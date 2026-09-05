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

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.systemui.car.shared.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Rectangular pin pad entry view for passenger keyguard.
 */
public class PassengerPinPadView extends GridLayout {
    // Number of keys in the pin pad, 0-9 plus backspace and enter keys.
    @VisibleForTesting
    static final int NUM_KEYS = 12;

    @VisibleForTesting
    static final int[] PIN_PAD_DIGIT_KEYS = {R.id.key0, R.id.key1, R.id.key2, R.id.key3,
            R.id.key4, R.id.key5, R.id.key6, R.id.key7, R.id.key8, R.id.key9};

    /**
     * The delay in milliseconds between character deletion when the user continuously holds the
     * backspace key.
     */
    private static final int LONG_CLICK_DELAY_MILLS = 100;

    private final List<View> mPinKeys = new ArrayList<>(NUM_KEYS);
    private final Runnable mOnBackspaceLongClick = new Runnable() {
        public void run() {
            if (mOnClickListener != null) {
                mOnClickListener.onBackspaceClick();
                getHandler().postDelayed(this, LONG_CLICK_DELAY_MILLS);
            }
        }
    };

    private PinPadClickListener mOnClickListener;
    private ImageButton mEnterKey;

    public PassengerPinPadView(Context context) {
        super(context);
        init();
    }

    public PassengerPinPadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PassengerPinPadView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public PassengerPinPadView(Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    /**
     * Set the call back for key click.
     *
     * @param pinPadClickListener The call back.
     */
    public void setPinPadClickListener(PinPadClickListener pinPadClickListener) {
        mOnClickListener = pinPadClickListener;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (View key : mPinKeys) {
            key.setEnabled(enabled);
        }
    }

    private void init() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(R.layout.passenger_keyguard_pin_pad, this, true);

        for (int keyId : PIN_PAD_DIGIT_KEYS) {
            TextView key = requireViewById(keyId);
            String digit = key.getTag().toString();
            key.setOnClickListener(v -> mOnClickListener.onDigitKeyClick(digit));
            mPinKeys.add(key);
        }

        ImageButton backspace = requireViewById(R.id.key_backspace);
        backspace.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    getHandler().post(mOnBackspaceLongClick);
                    // Must return false so that ripple can show
                    return false;
                case MotionEvent.ACTION_UP:
                    getHandler().removeCallbacks(mOnBackspaceLongClick);
                    // Must return false so that ripple can show
                    return false;
                default:
                    return false;
            }
        });

        backspace.setOnKeyListener((v, code, event) -> {
            if (code != KeyEvent.KEYCODE_DPAD_CENTER) {
                return false;
            }
            switch (event.getAction()) {
                case KeyEvent.ACTION_DOWN:
                    getHandler().post(mOnBackspaceLongClick);
                    // Must return false so that ripple can show
                    return false;
                case KeyEvent.ACTION_UP:
                    getHandler().removeCallbacks(mOnBackspaceLongClick);
                    // Must return false so that ripple can show
                    return false;
                default:
                    return false;
            }
        });

        mPinKeys.add(backspace);
        mEnterKey = requireViewById(R.id.key_enter);
        mEnterKey.setOnClickListener(v -> mOnClickListener.onEnterKeyClick());
        mPinKeys.add(mEnterKey);
    }
    /**
     * The call back interface for onClick event in the view.
     */
    public interface PinPadClickListener {
        /**
         * One of the digit key has been clicked.
         *
         * @param digit A String representing a digit between 0 and 9.
         */
        void onDigitKeyClick(String digit);
        /**
         * The backspace key has been clicked.
         */
        void onBackspaceClick();
        /**
         * The enter key has been clicked.
         */
        void onEnterKeyClick();
    }
}
