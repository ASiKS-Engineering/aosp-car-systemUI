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

import static com.android.car.scalableui.loader.xml.parser.PanelControllerParser.DEFAULT_COMPONENT_TAG;
import static com.android.car.scalableui.loader.xml.parser.PanelControllerParser.DEFAULT_INTENT_TAG;
import static com.android.car.scalableui.loader.xml.parser.PanelControllerParser.PERSISTENT_ACTIVITY_TAG;
import static com.android.car.scalableui.loader.xml.parser.PanelControllerParser.PERSISTENT_PACKAGE_TAG;
import static com.android.car.scalableui.loader.xml.parser.PanelControllerParser.UPDATABLE_INTENT_FILTER_TAG;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.car.scalableui.panel.TaskPanelController;
import com.android.car.scalableui.panel.TaskPanelHandler;
import com.android.systemui.car.wm.scalableui.panel.PanelUtils;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A base controller for managing {@link com.android.systemui.car.wm.scalableui.panel.TaskPanel}.
 *
 * <p>This class provides a foundational implementation for {@link TaskPanelController},
 * handling common tasks such as initializing based on {@link PanelControllerMetadata},
 * managing persistent activities, setting a default component, and responding to
 * application installation and uninstallation events. Subclasses can extend this
 * class to implement specific panel behaviors.
 */
public class BaseTaskPanelController implements TaskPanelController {
    private static final String TAG = BaseTaskPanelController.class.getSimpleName();
    protected static final boolean DEBUG = Build.isDebuggable();
    private static final String PACKAGE_DATA_SCHEME = "package";
    protected final Context mContext;
    @NonNull
    private final Object mLock = new Object();
    @NonNull
    private final String mPanelId;
    @NonNull
    private final PanelControllerMetadata mPanelControllerMetadata;
    @NonNull
    private final Set<ComponentName> mPersistentActivities;
    @NonNull
    private final PanelUtils mPanelUtils;
    @Nullable
    private ComponentName mDefaultComponent;
    @Nullable
    private Intent mDefaultIntent;
    @Nullable
    private Intent mUpdateFilter;
    @GuardedBy("mLock")
    @Nullable
    private TaskPanelHandler mTaskPanelHandler;

    /**
     * Constructs a new {@code BaseTaskPanelController}.
     *
     * @param context                 The application context.
     * @param panelControllerMetadata The metadata associated with this panel controller,
     *                                containing configuration information.
     */
    @AssistedInject
    public BaseTaskPanelController(@NonNull Context context, @Assisted String panelId,
            @NonNull @Assisted PanelControllerMetadata panelControllerMetadata,
            @NonNull PanelUtils panelUtils) {
        mContext = context;
        mPanelId = panelId;
        mPanelControllerMetadata = panelControllerMetadata;
        mPersistentActivities = new HashSet<>();
        mPanelUtils = panelUtils;
    }

    @AssistedFactory
    public interface Factory extends TaskPanelController.Factory<BaseTaskPanelController> {
        /**
         * Creates an instance of BaseTaskPanelController using the provided
         * PanelControllerMetadata.
         */
        BaseTaskPanelController create(String panelId, PanelControllerMetadata metadata);
    }

    @Override
    @CallSuper
    public void init() {
        mDefaultComponent = parseDefaultComponent(mPanelControllerMetadata);
        mDefaultIntent = parseDefaultIntent(mPanelControllerMetadata);
        mUpdateFilter = parseUpdateFilter(mPanelControllerMetadata);
        if (mUpdateFilter != null) {
            registerApplicationInstallUninstallReceiver();
        }
        updatePersistentActivities();
        logIfDebuggable("Panel Controller init: " + this);
    }

    @Override
    public void destroy() {
        logIfDebuggable("Panel Controller destroyed");
    }

    private Intent parseUpdateFilter(@NonNull PanelControllerMetadata metadata) {
        String intentString = metadata.getStringConfiguration(
                UPDATABLE_INTENT_FILTER_TAG);
        return getIntentFromString(intentString);
    }

    private ComponentName parseDefaultComponent(@NonNull PanelControllerMetadata metadata) {
        String defaultIntentString =
                metadata.getStringConfiguration(DEFAULT_COMPONENT_TAG);
        return defaultIntentString == null ? null : ComponentName.unflattenFromString(
                defaultIntentString);
    }

    private Intent parseDefaultIntent(@NonNull PanelControllerMetadata metadata) {
        String intentString = metadata.getStringConfiguration(
                DEFAULT_INTENT_TAG);
        return getIntentFromString(intentString);
    }

    private void registerApplicationInstallUninstallReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme(PACKAGE_DATA_SCHEME);
        BroadcastReceiver mAppsUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updatePersistentActivities();
            }
        };
        mContext.registerReceiverAsUser(
                mAppsUpdateReceiver,
                UserHandle.ALL, // Necessary because CarSystemUi lives in User 0
                filter,
                /* broadcastPermission= */ null,
                /* scheduler= */ null,
                Context.RECEIVER_EXPORTED);
    }

    @SuppressLint("MissingPermission")
    @VisibleForTesting
    void updatePersistentActivities() {
        mPersistentActivities.clear();
        if (mUpdateFilter != null) {
            List<ResolveInfo> result = mContext.getPackageManager().queryIntentActivitiesAsUser(
                    mUpdateFilter, PackageManager.MATCH_DIRECT_BOOT_AWARE
                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                    ActivityManager.getCurrentUser());
            for (ResolveInfo info : result) {
                if (info == null || info.activityInfo == null
                        || info.activityInfo.getComponentName() == null) {
                    continue;
                }
                if (mPersistentActivities.add(info.activityInfo.getComponentName())) {
                    logIfDebuggable("adding the following component to show on fullscreen: "
                            + info.activityInfo.getComponentName());
                }
            }
        }
        mPersistentActivities.addAll(
                mPanelUtils.parsePersistentActivitiesFromPackages(mPanelControllerMetadata,
                        PERSISTENT_PACKAGE_TAG));
        mPersistentActivities.addAll(parsePersistentActivities(mPanelControllerMetadata,
                PERSISTENT_ACTIVITY_TAG));
        synchronized (mLock) {
            if (mTaskPanelHandler != null) {
                mTaskPanelHandler.onApplicationChanged();
            }
        }
    }

    @NonNull
    protected String getPanelId() {
        return mPanelId;
    }

    protected void logIfDebuggable(String s) {
        if (DEBUG) {
            Log.d(TAG, mPanelId + ", " + s);
        }
    }

    @Nullable
    private Intent getIntentFromString(@Nullable String string) {
        if (string == null) {
            return null;
        }
        try {
            return Intent.parseUri(string, Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException e) {
            Log.e(TAG, "Fail to parse intent string" + string + ", e=" + e);
            return null;
        }
    }

    private Set<ComponentName> parsePersistentActivities(
            @NonNull PanelControllerMetadata panelControllerMetadata, @NonNull String configName) {
        Set<ComponentName> set = new HashSet<>();
        if (!mPanelControllerMetadata.hasConfiguration(configName)) {
            return set;
        }
        List<String> list = panelControllerMetadata.getListConfiguration(configName);
        if (list == null) {
            String value = panelControllerMetadata.getStringConfiguration(configName);
            if (value != null) {
                set.add(ComponentName.unflattenFromString(value));
            }
        } else {
            for (String item : list) {
                ComponentName componentName = ComponentName.unflattenFromString(item);
                if (componentName == null) {
                    continue;
                }
                set.add(componentName);
            }
        }
        return set;
    }

    @Override
    public Intent getDefaultComponent() {
        Intent intent = null;
        if (mDefaultComponent != null) {
            intent = new Intent();
            intent.setComponent(mDefaultComponent);
        } else if (mDefaultIntent != null) {
            intent = new Intent(mDefaultIntent); // make a copy for safety
        }
        logIfDebuggable("getDefaultComponent =  " + intent);
        return intent;
    }

    @Override
    @NonNull
    public Set<ComponentName> getPersistentActivities() {
        return mPersistentActivities;
    }

    @Override
    public void registerTaskPanelHandler(TaskPanelHandler taskPanelHandler) {
        synchronized (mLock) {
            mTaskPanelHandler = taskPanelHandler;
        }
    }

    @Override
    public boolean handles(ComponentName componentName) {
        return mPersistentActivities.contains(componentName);
    }

    @Override
    public String toString() {
        String persistentActivities = mPersistentActivities.stream()
                .filter(Objects::nonNull)
                .map(ComponentName::toString)
                .collect(Collectors.joining("\n,"));
        return "PanelController{"
                + "mPanelControllerMetadata=" + mPanelControllerMetadata
                + "\n, mPersistentActivities=" + persistentActivities
                + "\n, mDefaultComponent=" + getDefaultComponent()
                + "\n, mUpdateFilter=" + mUpdateFilter
                + '}';
    }
}
