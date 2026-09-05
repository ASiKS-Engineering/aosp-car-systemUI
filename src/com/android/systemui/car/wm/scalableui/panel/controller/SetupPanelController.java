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

package com.android.systemui.car.wm.scalableui.panel.controller;

import static android.car.CarOccupantZoneManager.INVALID_USER_ID;

import android.car.settings.CarSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.car.scalableui.model.PanelState;
import com.android.car.scalableui.panel.TaskPanelController;
import com.android.systemui.car.flags.Flag;
import com.android.systemui.car.flags.FlagManager;
import com.android.systemui.car.wm.CarWMUserHelper;
import com.android.systemui.car.wm.scalableui.panel.PanelUtils;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.Map;
import java.util.Set;

/**
 * TaskPanelController for the SetupWizard panel. This controller will handle sending a home intent
 * to the SUW package whenever the SUW panel goes from visible to invisible while SUW is in
 * progress.
 */
public class SetupPanelController extends BaseTaskPanelController {
    private static final String TAG = SetupPanelController.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;

    private final FlagManager mFlagManager;
    private final CarWMUserHelper mUserHelper;
    private int mSetupPanelDisplayId;
    private boolean mWasSetupPanelVisible = false;

    private final StateManager.PanelStateObserver mPanelStateObserver =
            new StateManager.PanelStateObserver() {
                @Override
                public void onBeforePanelStateChanged(Set<String> changedPanelIds,
                        Map<String, PanelState> panelStates) {
                    // no-op
                }
                @Override
                public void onPanelStateChanged(Set<String> changedPanelIds,
                        Map<String, PanelState> panelStates) {
                    if (!changedPanelIds.contains(getPanelId())) {
                        return;
                    }
                    PanelState state = panelStates.get(getPanelId());
                    if (state == null || state.getCurrentVariant() == null) {
                        return;
                    }
                    boolean isVisible = state.getCurrentVariant().isVisible();

                    // Validate if the panel state has already changed due to conflict
                    PanelState currState = StateManager.getPanelState(getPanelId());
                    if (currState != null && currState.getCurrentVariant() != null) {
                        boolean isCurrVisible = currState.getCurrentVariant().isVisible();
                        if (isCurrVisible != isVisible) {
                            Log.e(TAG, "Current visibility is not the same as callback - "
                                    + "ignoring change for future callback"
                                    + " callbackVisibility=" + isVisible
                                    + " currentVisibility=" + isCurrVisible);
                            return;
                        }
                    }

                    onPanelVisibilityChanged(isVisible);
                }
            };

    @AssistedInject
    public SetupPanelController(Context context, @Assisted String panelId,
            @Assisted PanelControllerMetadata panelControllerMetadata,
            PanelUtils panelUtils, CarWMUserHelper userHelper, FlagManager flagManager) {
        super(context, panelId, panelControllerMetadata, panelUtils);
        mFlagManager = flagManager;
        mUserHelper = userHelper;
    }

    @AssistedFactory
    public interface Factory extends TaskPanelController.Factory<SetupPanelController> {
        /**
         * Creates an instance of SetupPanelController using the provided PanelControllerMetadata.
         */
        SetupPanelController create(String panelId, PanelControllerMetadata metadata);
    }

    @Override
    public void init() {
        super.init();
        if (!mFlagManager.isEnabled(Flag.ScalableUiNoSuwHome)) {
            return;
        }
        if (!TextUtils.isEmpty(getPanelId())) {
            PanelState state = StateManager.getPanelState(getPanelId());
            if (state == null) {
                return;
            }
            mSetupPanelDisplayId = state.getDisplayId();
            if (state.getCurrentVariant() != null) {
                mWasSetupPanelVisible = state.getCurrentVariant().isVisible();
            }
            StateManager.getInstance().addPanelStateObserver(mPanelStateObserver, getPanelId());
        }
    }

    @VisibleForTesting
    void onPanelVisibilityChanged(boolean isVisible) {
        int userId = mUserHelper.getUserIdForDisplay(mSetupPanelDisplayId);
        if (userId == INVALID_USER_ID) {
            Log.e(TAG, "onPanelVisibilityChanged - invalid user id for display "
                    + mSetupPanelDisplayId);
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "onPanelVisibilityChanged visible=" + isVisible
                    + " user=" + userId);
        }
        if (userId == UserHandle.USER_SYSTEM) {
            return;
        }
        if (isVisible) {
            mWasSetupPanelVisible = true;
            return;
        }
        if (!mWasSetupPanelVisible || !isUserSetupInProgress()) {
            mWasSetupPanelVisible = false;
            return;
        }
        // Setup panel has become invisible while user setup is still in progress - send home
        // intent directly to SUW to notify of exit.
        mWasSetupPanelVisible = false;
        Intent suwIntent = new Intent(Intent.ACTION_MAIN);
        suwIntent.addCategory(Intent.CATEGORY_SETUP_WIZARD);
        ResolveInfo info = mContext.getPackageManager()
                .resolveActivityAsUser(suwIntent, /* flags= */ 0, userId);
        if (info != null && info.activityInfo != null) {
            if (DEBUG) {
                Log.d(TAG, "Sending SUW exit home intent");
            }
            Intent suwHomeIntent = new Intent(Intent.ACTION_MAIN);
            suwHomeIntent.addCategory(Intent.CATEGORY_HOME);
            suwHomeIntent.setPackage(info.activityInfo.packageName);
            mContext.startActivityAsUser(suwHomeIntent, UserHandle.of(userId));
        }
    }

    @VisibleForTesting
    boolean isUserSetupInProgress() {
        int userId = mUserHelper.getUserIdForDisplay(mSetupPanelDisplayId);
        if (userId == INVALID_USER_ID) {
            Log.e(TAG, "isUserSetupInProgress - invalid user id for display "
                    + mSetupPanelDisplayId);
            return false;
        }
        return Settings.Secure.getIntForUser(mContext.getContentResolver(),
                CarSettings.Secure.KEY_SETUP_WIZARD_IN_PROGRESS,
                /* def= */ 0, userId) != 0;
    }
}
