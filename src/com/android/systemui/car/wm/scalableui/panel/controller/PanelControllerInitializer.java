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

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.car.scalableui.panel.DecorPanelController;
import com.android.car.scalableui.panel.TaskPanelController;
import com.android.wm.shell.automotive.AutoCaptionBarViewController;
import com.android.wm.shell.dagger.WMSingleton;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Initializes {@link TaskPanelController} and {@link DecorPanelController} instances based on
 * provided metadata.
 *
 * <p>This class is responsible for dynamically creating instances of {@link TaskPanelController}
 * and {@link DecorPanelController} by using the controller class name specified in the
 * {@link PanelControllerMetadata}. It handles potential exceptions that may occur during class
 * loading and instantiation.
 */
@WMSingleton
public class PanelControllerInitializer {
    private static final boolean DEBUG = Build.isDebuggable();
    private static final String TAG = PanelControllerInitializer.class.getSimpleName();
    private static final String DEFAULT_TASK_TOOLBAR_CONTROLLER_NAME =
            CompatibilityToolbarController.class.getName();
    private final Map<Class<?>, Provider<TaskPanelController.Factory>>
            mTaskPanelControllerMap;
    private final Map<Class<?>, Provider<DecorPanelController.Factory<?>>>
            mDecorPanelControllerMap;
    private final Map<Class<?>, Provider<TaskToolbarController.Factory>>
            mTaskToolBarControllerMap;

    @Inject
    public PanelControllerInitializer(
            Map<Class<?>, Provider<TaskPanelController.Factory>> taskPanelControllerMap,
            Map<Class<?>, Provider<DecorPanelController.Factory<?>>> decorPanelControllerMap,
            Map<Class<?>, Provider<TaskToolbarController.Factory>> taskToolBarControllerMap
    ) {
        mTaskPanelControllerMap = taskPanelControllerMap;
        mDecorPanelControllerMap = decorPanelControllerMap;
        mTaskToolBarControllerMap = taskToolBarControllerMap;
    }

    /**
     * Creates a {@link TaskPanelController} instance based on the provided metadata.
     *
     * <p>This method attempts to load the class specified by
     * {@link PanelControllerMetadata#getControllerName()}, and retrieves the class specified in
     * the dagger graph as part of the TaskPanelController mapping.
     *
     * @param metadata The metadata containing information about the panel controller to create.
     *                 If {@code null}, this method will return {@code null}.
     * @return A new instance of {@link TaskPanelController} if successful, otherwise {@code null}.
     */
    @Nullable
    public TaskPanelController createTaskPanelController(@NonNull String panelId,
            @Nullable PanelControllerMetadata metadata) {
        if (metadata == null) {
            logIfDebuggable("Metadata is null");
            return null;
        }
        String controllerName = metadata.getControllerName();
        if (controllerName == null) {
            logIfDebuggable("Controller name is null for " + metadata.getId());
            return null;
        }

        logIfDebuggable("Init TaskPanelController with class name" + controllerName + " for "
                + metadata.getId());
        try {
            Class<?> clazz = Class.forName(controllerName);
            Provider<TaskPanelController.Factory> factoryProvider =
                    mTaskPanelControllerMap.get(clazz);
            if (factoryProvider != null) {
                TaskPanelController controller = factoryProvider.get().create(panelId, metadata);
                controller.init();
                return controller;
            }
        } catch (ClassNotFoundException e) {
            // Handle the case where the class is not found
            Log.e(TAG, "Class not found: " + controllerName + " for " + metadata.getId(), e);
        }
        Log.e(TAG, "Unable to create TaskPanelController: " + controllerName + " for "
                + metadata.getId());
        return null;
    }

    /**
     * Creates a {@link DecorPanelController} instance based on the provided metadata.
     *
     * <p>This method attempts to load the class specified by
     * {@link PanelControllerMetadata#getControllerName()}, and retrieves the class specified in
     * the dagger graph as part of the DecorPanelController mapping.
     *
     * @param metadata The metadata containing information about the panel controller to create.
     *                 If {@code null}, this method will return {@code null}.
     * @return A new instance of {@link DecorPanelController} if successful, otherwise {@code null}.
     */
    @Nullable
    public DecorPanelController createDecorPanelController(@NonNull String panelId,
            @Nullable PanelControllerMetadata metadata) {
        if (metadata == null) {
            logIfDebuggable("Metadata is null");
            return null;
        }
        String controllerName = metadata.getControllerName();
        if (controllerName == null) {
            logIfDebuggable("Controller name is null for " + metadata.getId());
            return null;
        }

        logIfDebuggable("Init view provider with class name" + controllerName + " for "
                + metadata.getId());
        try {
            Class<?> clazz = Class.forName(controllerName);
            Provider<DecorPanelController.Factory<?>> factoryProvider =
                    mDecorPanelControllerMap.get(clazz);
            if (factoryProvider != null) {
                return factoryProvider.get().create(panelId, metadata);
            }
        } catch (ClassNotFoundException e) {
            // Handle the case where the class is not found
            Log.e(TAG, "Class not found: " + controllerName + " for " + metadata.getId(), e);
        }
        Log.e(TAG, "Unable to create DecorPanelController: " + controllerName + " for "
                + metadata.getId());
        return null;
    }

    private static void logIfDebuggable(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }

    /**
     * Creates an {@link AutoCaptionBarViewController} instance for a task toolbar.
     *
     * <p>This method uses the controller class name specified in the provided
     * {@link PanelControllerMetadata}. If the metadata is {@code null} or does not specify a
     * controller, it defaults to creating a {@link TaskToolbarController}. It then retrieves the
     * appropriate factory from a Dagger-injected map and uses it to create the controller instance.
     *
     * @param metadata The metadata containing configuration for the toolbar controller. Can be
     *                 {@code null}, in which case a default controller is created.
     * @param panelId  The unique identifier for the panel that will host the toolbar.
     * @return A new instance of {@link AutoCaptionBarViewController} if successful, or {@code null}
     * if the specified controller class cannot be found or instantiated.
     */
    public TaskToolbarController createTaskToolBarController(
            @Nullable PanelControllerMetadata metadata, @NonNull String panelId) {
        String controllerName;
        if (metadata == null || metadata.getTaskToolBarControllerName() == null) {
            controllerName = DEFAULT_TASK_TOOLBAR_CONTROLLER_NAME;
        } else {
            controllerName = metadata.getTaskToolBarControllerName();
        }

        String metadataId = metadata == null ? "null" : metadata.getId();
        logIfDebuggable("Init TaskToolbarController with class name " + controllerName
                + " for " + metadataId);
        try {
            Class<?> clazz = Class.forName(controllerName);
            Provider<TaskToolbarController.Factory> factoryProvider =
                    mTaskToolBarControllerMap.get(clazz);
            if (factoryProvider != null) {
                return factoryProvider.get().create(panelId);
            }
        } catch (ClassNotFoundException | NullPointerException e) {
            // Handle the case where the class is not found
            Log.e(TAG, "Class not found: " + controllerName + " for " + metadataId, e);
        }
        Log.e(TAG, "Unable to create TaskPanelController: " + controllerName + " for "
                + metadataId);
        return null;
    }
}
