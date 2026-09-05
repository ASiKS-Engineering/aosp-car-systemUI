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

package com.android.systemui.car.flexibleui;

import android.os.Bundle;
import android.view.View;

import com.android.systemui.dagger.SysUISingleton;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.inject.Inject;

@SysUISingleton
public class CarSystemBarElementStateController {
    private final HashMap<Long, WeakReference<CarSystemBarElementController>> mControllers =
            new HashMap<>();
    private final HashMap<Long, Bundle> mStates = new HashMap<>();

    @Inject
    public CarSystemBarElementStateController() {
    }

    void registerController(CarSystemBarElementController controller) {
        long viewId = controller.getUniqueViewId();
        if (viewId == View.NO_ID) {
            throw new IllegalArgumentException("Cannot restore state on view with no id");
        }
        synchronized (mControllers) {
            if (mControllers.containsKey(viewId)) {
                throw new IllegalArgumentException("Cannot restore state on duplicate view ids");
            }
            mControllers.put(viewId, new WeakReference<>(controller));
            Bundle bundle = mStates.remove(viewId);
            if (bundle != null) {
                controller.restoreState(bundle);
            }
        }
    }

    void unregisterController(CarSystemBarElementController controller) {
        long viewId = controller.getUniqueViewId();
        if (viewId == View.NO_ID) {
            return;
        }
        synchronized (mControllers) {
            mControllers.remove(viewId);
        }
    }

    /**
     * Notifies the state controller that a restart is about to occur. This will trigger the state
     * controller to save the state of all registered controllers.
     */
    public void notifyPendingRestart() {
        synchronized (mControllers) {
            mStates.clear();
            for (Iterator<Map.Entry<Long, WeakReference<CarSystemBarElementController>>> iterator =
                    mControllers.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<Long, WeakReference<CarSystemBarElementController>> entry =
                        iterator.next();
                CarSystemBarElementController controller = entry.getValue().get();
                if (controller != null) {
                    Bundle outBundle = new Bundle();
                    mStates.put(entry.getKey(), controller.getState(outBundle));
                } else {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * Notifies the state controller that a restart has completed. This will trigger the state
     * controller to restore the state of all registered controllers.
     */
    public void notifyRestartComplete() {
        synchronized (mControllers) {
            for (Iterator<Map.Entry<Long, WeakReference<CarSystemBarElementController>>> iterator =
                    mControllers.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<Long, WeakReference<CarSystemBarElementController>> entry =
                        iterator.next();
                Bundle bundle = mStates.remove(entry.getKey());
                if (bundle != null) {
                    CarSystemBarElementController controller = entry.getValue().get();
                    if (controller != null) {
                        controller.restoreState(bundle);
                    } else {
                        iterator.remove();
                    }
                }
            }
        }
    }
}
