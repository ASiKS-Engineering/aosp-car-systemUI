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

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.Display;

import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.car.scalableui.model.PanelState;
import com.android.car.scalableui.panel.TaskPanelController;
import com.android.car.tos.TosHelper;
import com.android.systemui.car.wm.CarWMUserHelper;
import com.android.systemui.car.wm.scalableui.panel.PanelUtils;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

public final class MapsPanelController extends BaseTaskPanelController {
    private static final String TAG = MapsPanelController.class.getSimpleName();

    private final CarWMUserHelper mUserHelper;
    private int mDisplayId = Display.INVALID_DISPLAY;

    @AssistedInject
    public MapsPanelController(Context context, @Assisted String panelId,
            @Assisted PanelControllerMetadata panelControllerMetadata,
            PanelUtils panelUtils, CarWMUserHelper userHelper) {
        super(context, panelId, panelControllerMetadata, panelUtils);
        mUserHelper = userHelper;

        PanelState state = StateManager.getInstance().getPanelState(getPanelId());
        if (state != null) {
            mDisplayId = state.getDisplayId();
        }
    }

    @AssistedFactory
    public interface Factory extends TaskPanelController.Factory<MapsPanelController> {
        /**
         * Creates an instance of MapsPanelController using the provided PanelControllerMetadata.
         */
        MapsPanelController create(String panelId, PanelControllerMetadata metadata);
    }

    @Override
    public Intent getDefaultComponent() {
        Intent mapIntent = super.getDefaultComponent();
        int userId = mUserHelper.getUserIdForDisplay(mDisplayId);
        if (userId == INVALID_USER_ID) {
            Log.e(TAG, "getDefaultMapComponent - invalid user id for display "
                    + mDisplayId);
            return mapIntent;
        }
        Intent result = TosHelper.maybeReplaceWithTosMapIntent(mContext, mapIntent,
                com.android.car.tos.R.string.config_tosMapIntent, userId);
        logIfDebuggable(TAG + ", getDefaultComponent =  " + result);
        return result;
    }
}
