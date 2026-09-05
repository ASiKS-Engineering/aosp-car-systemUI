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
package com.android.systemui.car.wm.scalableui;

import android.content.Context;

import com.android.car.scalableui.manager.ActionManager;
import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.Event;
import com.android.car.scalableui.model.PanelTransaction;
import com.android.systemui.car.flags.FlagManager;
import com.android.wm.shell.dagger.WMSingleton;

import dagger.Lazy;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

/**
 * Class is responsible for dispatching events to the {@link StateManager} and
 * {@link ActionManager} and then potentially executing the resulting transaction.
 */
@WMSingleton
public class EventDispatcher {

    private final Context mContext;
    private final PanelTransitionCoordinator mPanelTransitionCoordinator;
    private final FlagManager mFlagManager;

    @Inject
    public EventDispatcher(Context context,
            Lazy<PanelTransitionCoordinator> panelTransitionCoordinator,
            FlagManager flagManager) {
        mContext = context;
        mFlagManager = flagManager;
        if (ScalableUIUtils.isScalableUIEnabled(mContext, mFlagManager)) {
            mPanelTransitionCoordinator = panelTransitionCoordinator.get();
        } else {
            mPanelTransitionCoordinator = null;
        }
    }

    /**
     * Retrieve a panel transaction describing the provided event parameter.
     */
    public static PanelTransaction getTransaction(Event event) {
        return StateManager.handleEvent(event);
    }

    /**
     * Retrieve a panel transaction describing the provided events.
     */
    public static PanelTransaction getTransaction(List<Event> events) {
        return StateManager.handleEvents(events);
    }

    /**
     * See {@link #executeEvent(Event)}
     */
    public void executeEvent(String event) {
        executeEvent(new Event.Builder(event).build());
    }

    /**
     * See {@link #executeEvents}
     */
    public void executeEvent(Event event) {
        executeEvents(Collections.singletonList(event));
    }

    /**
     * Executes one or more {@link Event} by getting a linked {@link PanelTransaction} and sending
     * an Action.
     */
    public void executeEvents(List<Event> events) {
        if (!ScalableUIUtils.isScalableUIEnabled(mContext, mFlagManager)) {
            throw new IllegalStateException("ScalableUI disabled - cannot execute transaction");
        }
        mPanelTransitionCoordinator.startTransition(getTransaction(events));
        for (Event event : events) {
            ActionManager.handleEvent(mContext, event);
        }
    }
}
