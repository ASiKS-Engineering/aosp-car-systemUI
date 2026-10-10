/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui;

import android.app.Service;
import android.content.Context;
import android.content.Intent;

/**
 * Starts up SystemUI using {@link CarSystemUIInitializer}.
 *
 * The {@link SystemUIAppComponentFactoryBase} is required for proper SystemUI functionality.
 *
 * @see SystemUIAppComponentFactoryBase
 */
public class CarSystemUIAppComponentFactory extends SystemUIAppComponentFactoryBase {
    private static final String GNSS_SERVICE_CLASS =
            "com.android.systemui.car.statusicon.location.GnssSatelliteCountService";

    @Override
    protected SystemUIInitializer createSystemUIInitializer(Context context) {
        return new CarSystemUIInitializer(context);
    }

    @Override
    public Service instantiateServiceCompat(ClassLoader cl, String className, Intent intent) {
        if (GNSS_SERVICE_CLASS.equals(className)) {
            // Runs in the :gnss process, where the SystemUI dependency graph is not created.
            try {
                return (Service) cl.loadClass(className).getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Cannot instantiate " + className, e);
            }
        }
        return super.instantiateServiceCompat(cl, className, intent);
    }
}
