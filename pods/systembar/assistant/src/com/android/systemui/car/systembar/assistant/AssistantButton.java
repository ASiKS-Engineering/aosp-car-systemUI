/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.car.systembar.assistant;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import com.android.systemui.car.systembar.base.CarSystemBarButton;

/**
 * AssistantButton is an UI component that will trigger the Voice Interaction Service.
 */
public class AssistantButton extends CarSystemBarButton {

    public AssistantButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public Class<?> getElementControllerClass() {
        Class<?> superClass = super.getElementControllerClass();
        if (superClass != null) {
            return superClass;
        }
        return AssistantButtonController.class;
    }

    @Override
    protected void setUpIntents(TypedArray typedArray) {
        // left blank because for the assistant button Intent will not be passed from the layout.
    }

    @Override
    protected String getRoleName() {
        return RoleManager.ROLE_ASSISTANT;
    }

    @Override
    public void setSelected(boolean selected) {
        // override to no-op as AssistantButton will maintain its own selected state by listening to
        // the actual voice interaction session.
    }

    void assistantSetSelected(boolean selected) {
        if (hasSelectionState()) {
            getContext().getMainExecutor().execute(
                    () -> AssistantButton.super.setSelected(selected));
        }
    }
}
