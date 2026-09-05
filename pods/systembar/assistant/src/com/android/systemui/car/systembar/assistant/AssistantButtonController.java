/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static android.service.voice.VoiceInteractionSession.SHOW_SOURCE_ASSIST_GESTURE;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.CallSuper;

import com.android.internal.app.AssistUtils;
import com.android.internal.app.IVoiceInteractionSessionListener;
import com.android.internal.app.IVoiceInteractionSessionShowCallback;
import com.android.systemui.car.flexibleui.CarSystemBarElementController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.systembar.base.ButtonSelectionStateController;
import com.android.systemui.car.systembar.base.CarSystemBarButton;
import com.android.systemui.car.systembar.base.CarSystemBarButtonController;
import com.android.systemui.car.systembar.base.SystemBarUtil;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.settings.UserTracker;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.Set;

/**
 * A CarSystemBarElementController for handling Assistant button interactions.
 */
public class AssistantButtonController extends CarSystemBarButtonController {
    private static final String TAG = AssistantButtonController.class.getSimpleName();

    private final AssistUtils mAssistUtils;
    private final AssistantButton mAssistantButton;
    private final IVoiceInteractionSessionShowCallback mShowCallback =
            new IVoiceInteractionSessionShowCallback.Stub() {
                @Override
                public void onFailed() {
                    Log.w(TAG, "Failed to show VoiceInteractionSession");
                }

                @Override
                public void onShown() {
                    Log.d(TAG, "IVoiceInteractionSessionShowCallback onShown()");
                }
            };

    private final IVoiceInteractionSessionListener mSessionListener =
            new IVoiceInteractionSessionListener.Stub() {
                @Override
                public void onVoiceSessionShown() throws RemoteException {
                    assistantSetSelected(true);
                }

                @Override
                public void onVoiceSessionHidden() throws RemoteException {
                    assistantSetSelected(false);
                }

                @Override
                public void onVoiceSessionWindowVisibilityChanged(boolean visible)
                        throws RemoteException { }

                @Override
                public void onSetUiHints(Bundle hints) {
                }

                @Override
                public void onSetInvocationEffectEnabled(boolean enabled) {
                }
            };

    @AssistedInject
    public AssistantButtonController(@Assisted CarSystemBarButton assistantButton,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            UserTracker userTracker, EventDispatcher eventDispatcher,
            ButtonSelectionStateController buttonSelectionStateController) {
        super(assistantButton, disableController, stateController, userTracker, eventDispatcher,
                buttonSelectionStateController);

        mAssistantButton = (AssistantButton) assistantButton;
        mAssistUtils = new AssistUtils(mAssistantButton.getContext());
    }

    @Override
    @CallSuper
    protected void onInit() {
        super.onInit();
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        mAssistantButton.setOnClickListener(v -> showAssistant());
        mAssistUtils.registerVoiceInteractionSessionListener(mSessionListener);
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        // AssistUtils doesn't have unregisterVoiceInteractionSessionListener method.
        mAssistantButton.setOnClickListener(null);
    }

    private void showAssistant() {
        if (canShowTosAcceptanceFlow()) {
            SystemBarUtil.INSTANCE.showTosAcceptanceFlow(mAssistantButton.getContext(),
                    getUserTracker());
            return;
        }
        final Bundle args = new Bundle();
        mAssistUtils.showSessionForActiveService(args,
                SHOW_SOURCE_ASSIST_GESTURE, mShowCallback, /*activityToken=*/ null);
    }

    private boolean canShowTosAcceptanceFlow() {
        ComponentName activeAssistantComponent = mAssistUtils.getActiveServiceComponentName();
        String defaultAssistantInConfig =
                mAssistantButton.getContext().getString(
                        com.android.internal.R.string.config_defaultAssistant);
        UserTracker userTracker = getUserTracker();
        Integer userId = userTracker != null ? userTracker.getUserId() : null;
        Set<String> tosDisabledApps = SystemBarUtil.INSTANCE
                .getTosDisabledPackages(mAssistantButton.getContext(), userId);
        boolean defaultAssistantDisabled = tosDisabledApps.contains(defaultAssistantInConfig);

        return activeAssistantComponent == null && defaultAssistantDisabled;
    }

    private void assistantSetSelected(boolean selected) {
        mAssistantButton.assistantSetSelected(selected);
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<CarSystemBarButton,
                    AssistantButtonController> {
    }
}
