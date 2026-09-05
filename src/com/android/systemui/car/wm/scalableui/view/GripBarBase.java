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
package com.android.systemui.car.wm.scalableui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * A base class for custom views representing a "grip" or handle.
 * <p>
 * This view is often used for interacting with draggable or resizable UI elements.
 * Concrete classes should apply any styling required to the view.
 *
 * @see HorizontalGripBar
 * @see VerticalGripBar
 */
public abstract class GripBarBase extends ConstraintLayout {
    private static final String TAG = GripBarBase.class.getSimpleName();
    private final GestureDetector mGestureDetector;
    private final List<GripBarEventHandler> mGripBarEventHandlers;

    private class SingleTapListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(@NonNull MotionEvent e) {
            notifyClickEvent();
            return true;
        }
    }
    /**
     * Constructor for GripBar.
     */
    protected GripBarBase(@NonNull Context context) {
        this(context, null);
    }

    protected GripBarBase(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    protected GripBarBase(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @SuppressLint("ClickableViewAccessibility")
    protected GripBarBase(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setOnTouchListener(this::onTouchEvent);
        setOnClickListener(v -> onClickEvent());
        mGestureDetector = new GestureDetector(context, new SingleTapListener());
        mGripBarEventHandlers = new ArrayList<>();
    }

    private boolean onTouchEvent(View v, MotionEvent event) {
        if (mGestureDetector.onTouchEvent(event)) {
            return true;
        }
        notifyTouchEvent(event);
        return true;
    }

    private void notifyTouchEvent(MotionEvent event) {
        synchronized (mGripBarEventHandlers) {
            for (GripBarEventHandler gripBarEventHandler: mGripBarEventHandlers) {
                gripBarEventHandler.onTouch(event);
            }
        }
    }

    private boolean onClickEvent() {
        notifyClickEvent();
        return true;
    }

    private void notifyClickEvent() {
        synchronized (mGripBarEventHandlers) {
            for (GripBarEventHandler gripBarEventHandler: mGripBarEventHandlers) {
                gripBarEventHandler.onClick();
            }
        }
    }

    /** Register a GripBarEventHandler */
    public void addGripBarEventHandlers(@NonNull GripBarEventHandler handler) {
        synchronized (mGripBarEventHandlers) {
            mGripBarEventHandlers.add(handler);
        }
    }

    /** Unregister a GripBarEventHandler */
    public void removeGripBarEventHandlers(@NonNull GripBarEventHandler handler) {
        synchronized (mGripBarEventHandlers) {
            mGripBarEventHandlers.remove(handler);
        }
    }

    /**
     * Interface definition for callbacks to be invoked when a Grip Bar is interacted with.
     */
    public interface GripBarEventHandler {
        /**
         * Called when a touch event occurs on the Grip Bar.
         * @param motionEvent The details of the touch event.
         */
        void onTouch(MotionEvent motionEvent);

        /**
         * Called when a click event is detected on the Grip Bar.
         */
        void onClick();
    }
}
