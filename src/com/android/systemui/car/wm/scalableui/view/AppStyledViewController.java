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

import android.view.View;

import com.android.car.scalableui.model.Event;
import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.car.scalableui.panel.DecorPanelController;
import com.android.systemui.car.shared.R;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.car.wm.scalableui.panel.controller.DecorPanelViewMap;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.Map;

import javax.inject.Provider;

/**
 * A Controller for the {@link AppStyledViewScrim}
 */
public class AppStyledViewController extends DecorPanelControllerBase {

    private final EventDispatcher mEventDispatcher;
    @AssistedInject
    public AppStyledViewController(@Assisted String panelId,
            @Assisted PanelControllerMetadata metadata,
            @DecorPanelViewMap Map<Class<?>, Provider<View>> decorPanelViewMap,
            EventDispatcher eventDispatcher) {
        super(panelId, metadata, decorPanelViewMap);
        mEventDispatcher = eventDispatcher;
        getView().requireViewById(
                R.id.car_ui_app_styled_view_nav_icon_container).setOnClickListener(v -> {
                    Event event = new Event.Builder("_AppStyleView_CloseEvent").build();
                    mEventDispatcher.executeEvent(event);
                });
    }

    @AssistedFactory
    public interface Factory extends DecorPanelController.Factory<AppStyledViewController> {
        /**
         * Create an instance of AppStyledViewController2 with the provided
         * PanelControllerMetadata
         */
        AppStyledViewController create(String panelId, PanelControllerMetadata metadata);
    }
}
