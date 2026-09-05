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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.testing.TestableLooper;
import android.view.MotionEvent;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.shared.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper
@SmallTest
public class PassengerPinPadViewTest extends CarSysuiTestCase {
    private static int[] sAllKeys =
            Arrays.copyOf(PassengerPinPadView.PIN_PAD_DIGIT_KEYS, PassengerPinPadView.NUM_KEYS);

    static {
        sAllKeys[PassengerPinPadView.PIN_PAD_DIGIT_KEYS.length] = R.id.key_backspace;
        sAllKeys[PassengerPinPadView.PIN_PAD_DIGIT_KEYS.length + 1] = R.id.key_enter;
    }

    private TestPassengerPinPadView mPinPadView;
    private TestableLooper mTestableLooper;
    private Handler mHandler;

    @Mock
    private PassengerPinPadView.PinPadClickListener mClickListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTestableLooper = TestableLooper.get(this);
        mHandler = new Handler(mTestableLooper.getLooper());
        mPinPadView = new TestPassengerPinPadView(mContext);
        mPinPadView.setPinPadClickListener(mClickListener);
    }

    // Verify that when the pin pad is enabled or disabled, all the keys are in the same state.
    @Test
    public void testEnableDisablePinPad() {
        mPinPadView.setEnabled(false);

        for (int id : sAllKeys) {
            View key = mPinPadView.findViewById(id);
            assertThat(key.isEnabled()).isFalse();
        }

        mPinPadView.setEnabled(true);

        for (int id : sAllKeys) {
            View key = mPinPadView.findViewById(id);
            assertThat(key.isEnabled()).isTrue();
        }
    }

    // Verify that the click handler is called when the backspace key is clicked.
    @Test
    public void testBackspaceClickHandler() {
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        MotionEvent downEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN,
                0, 0, 0);
        downTime = SystemClock.uptimeMillis();
        eventTime = SystemClock.uptimeMillis();
        MotionEvent upEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP,
                0, 0, 0);

        mPinPadView.findViewById(R.id.key_backspace).dispatchTouchEvent(downEvent);
        waitForIdleSync();
        mPinPadView.findViewById(R.id.key_backspace).dispatchTouchEvent(upEvent);
        waitForIdleSync();

        verify(mClickListener).onBackspaceClick();
    }

    // Verify that the click handler is called when the enter key is clicked.
    @Test
    public void testEnterKeyClickHandler() {
        mPinPadView.findViewById(R.id.key_enter).performClick();

        verify(mClickListener).onEnterKeyClick();
    }

    // Verify that the click handler is called with the right argument when a digit key is clicked.
    @Test
    public void testDigitKeyClickHandler() {
        for (int i = 0; i < PassengerPinPadView.PIN_PAD_DIGIT_KEYS.length; ++i) {
            mPinPadView.findViewById(PassengerPinPadView.PIN_PAD_DIGIT_KEYS[i]).performClick();
            verify(mClickListener).onDigitKeyClick(String.valueOf(i));
        }
    }

    @Override
    protected void waitForIdleSync() {
        mTestableLooper.processAllMessages();
    }

    private class TestPassengerPinPadView extends PassengerPinPadView {

        TestPassengerPinPadView(Context context) {
            super(context);
        }

        @Override
        public Handler getHandler() {
            return mHandler;
        }
    }
}
