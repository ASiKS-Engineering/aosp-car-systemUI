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
package com.android.systemui.car.systembar.privacy.base;

import static android.hardware.SensorPrivacyManager.Sources.QS_TILE;
import static android.hardware.SensorPrivacyManager.TOGGLE_TYPE_SOFTWARE;
import static android.os.UserHandle.USER_SYSTEM;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.hardware.SensorPrivacyManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.permission.PermissionGroupUsage;
import android.permission.PermissionManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.systemui.privacy.PrivacyDialogDelegate;
import com.android.systemui.privacy.PrivacyItem;
import com.android.systemui.privacy.PrivacyItemController;
import com.android.systemui.privacy.PrivacyType;
import com.android.systemui.privacy.logging.PrivacyLogger;
import com.android.systemui.settings.UserTracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Helper class to provide privacy elements and updates to sensor panels.
 */
public abstract class SensorPrivacyInfoProvider {
    private static final String TAG = SensorPrivacyInfoProvider.class.getSimpleName();
    private static final String EMPTY_APP_NAME = "";

    private static final Map<String, PrivacyType> PERM_GROUP_TO_PRIVACY_TYPE_MAP =
            Map.of(Manifest.permission_group.CAMERA, PrivacyType.TYPE_CAMERA,
                    Manifest.permission_group.MICROPHONE, PrivacyType.TYPE_MICROPHONE,
                    Manifest.permission_group.LOCATION, PrivacyType.TYPE_LOCATION);

    private final Context mContext;
    private final PermissionManager mPermissionManager;
    private final UserTracker mUserTracker;
    private final PrivacyLogger mPrivacyLogger;
    private final PackageManager mPackageManager;
    private final SensorPrivacyManager mSensorPrivacyManager;
    private final PrivacyItemController mPrivacyItemController;
    private final UserManager mUserManager;
    private boolean mListenersRegistered;
    @Nullable
    private SensorInfoUpdateListener mSensorInfoUpdateListener;

    private final SensorPrivacyManager.OnSensorPrivacyChangedListener
            mOnSensorPrivacyChangedListener =
            new SensorPrivacyManager.OnSensorPrivacyChangedListener() {
                @Override
                public void onSensorPrivacyChanged(int sensor, boolean enabled) {
                    // Since this is launched using a callback thread, its UI based elements need
                    // to execute on main executor.
                    mContext.getMainExecutor().execute(() -> {
                        if (mSensorInfoUpdateListener != null) {
                            mSensorInfoUpdateListener.onSensorPrivacyChanged();
                        }
                    });
                }
            };

    private final PrivacyItemController.Callback mPicCallback =
            new PrivacyItemController.Callback() {
                @Override
                public void onPrivacyItemsChanged(@NonNull List<PrivacyItem> privacyItems) {
                    if (mSensorInfoUpdateListener != null) {
                        mSensorInfoUpdateListener.onSensorPrivacyChanged();
                    }
                }
            };

    public SensorPrivacyInfoProvider(
            Context context,
            PermissionManager permissionManager,
            PackageManager packageManager,
            SensorPrivacyManager sensorPrivacyManager,
            PrivacyItemController privacyItemController,
            UserTracker userTracker,
            PrivacyLogger privacyLogger) {
        mContext = context;
        mPermissionManager = permissionManager;
        mPackageManager = packageManager;
        mSensorPrivacyManager = sensorPrivacyManager;
        mPrivacyItemController = privacyItemController;
        mUserTracker = userTracker;
        mPrivacyLogger = privacyLogger;
        mUserManager = context.getSystemService(UserManager.class);
    }

    /** Whether the sensor specified by {@link #getChipSensor} is enabled */
    public boolean isSensorEnabled() {
        // We need to negate return of isSensorPrivacyEnabled since when it is {@code true}, it
        // means the sensor (microphone/camera) has been toggled off
        return !mSensorPrivacyManager.isSensorPrivacyEnabled(/* toggleType= */ TOGGLE_TYPE_SOFTWARE,
                /* sensor= */ getChipSensor());
    }

    /** Toggle the sensor specified by {@link #getChipSensor} */
    public void toggleSensor() {
        mSensorPrivacyManager.setSensorPrivacy(/* source= */ QS_TILE, /* sensor= */ getChipSensor(),
                /* enable= */ isSensorEnabled(), mUserTracker.getUserId());
    }

    /** Set the {@link SensorInfoUpdateListener} for this provider */
    public void setSensorInfoUpdateListener(@Nullable SensorInfoUpdateListener listener) {
        mSensorInfoUpdateListener = listener;
        if (listener != null) {
            registerListeners();
        } else {
            unregisterListeners();
        }
    }

    /** Obtain privacy elements for the privacy type of {@link #getProviderPrivacyType} */
    public List<PrivacyDialogDelegate.PrivacyElement> getPrivacyElements() {
        List<PrivacyDialogDelegate.PrivacyElement> elements =
                filterAndSort(createPrivacyElements());
        mPrivacyLogger.logShowDialogContents(elements);
        return elements;
    }

    protected abstract PrivacyType getProviderPrivacyType();

    protected abstract @SensorPrivacyManager.Sensors.Sensor int getChipSensor();

    private void registerListeners() {
        if (mListenersRegistered) {
            return;
        }
        mListenersRegistered = true;
        mPrivacyItemController.addCallback(mPicCallback);
        mSensorPrivacyManager.removeSensorPrivacyListener(getChipSensor(),
                mOnSensorPrivacyChangedListener);
        mSensorPrivacyManager.addSensorPrivacyListener(getChipSensor(),
                mOnSensorPrivacyChangedListener);
    }

    private void unregisterListeners() {
        if (!mListenersRegistered) {
            return;
        }
        mListenersRegistered = false;
        mPrivacyItemController.removeCallback(mPicCallback);
        mSensorPrivacyManager.removeSensorPrivacyListener(getChipSensor(),
                mOnSensorPrivacyChangedListener);
    }

    private List<PrivacyDialogDelegate.PrivacyElement> createPrivacyElements() {
        List<UserInfo> userInfos = mUserTracker.getUserProfiles();
        List<PermissionGroupUsage> permGroupUsages = getPermGroupUsages();
        mPrivacyLogger.logUnfilteredPermGroupUsage(permGroupUsages);
        List<PrivacyDialogDelegate.PrivacyElement> items = new ArrayList<>();

        permGroupUsages.forEach(usage -> {
            PrivacyType type =
                    verifyType(PERM_GROUP_TO_PRIVACY_TYPE_MAP.get(usage.getPermissionGroupName()));
            if (type == null) return;

            int userId = UserHandle.getUserId(usage.getUid());
            Optional<UserInfo> optionalUserInfo = userInfos.stream()
                    .filter(ui -> ui.id == userId)
                    .findFirst();
            if (!optionalUserInfo.isPresent() && userId != USER_SYSTEM) return;

            UserInfo userInfo =
                    optionalUserInfo.orElseGet(() -> mUserManager.getUserInfo(USER_SYSTEM));

            String appName = usage.isPhoneCall()
                    ? EMPTY_APP_NAME
                    : getLabelForPackage(usage.getPackageName(), usage.getUid());

            items.add(
                    new PrivacyDialogDelegate.PrivacyElement(
                            type,
                            usage.getPackageName(),
                            userId,
                            appName,
                            usage.getAttributionTag(),
                            /* attributionLabel= */ null,
                            usage.getProxyLabel(),
                            usage.getLastAccessTimeMillis(),
                            usage.isActive(),
                            userInfo.isManagedProfile(),
                            usage.isPhoneCall(),
                            usage.getPermissionGroupName(),
                            /* navigationIntent= */ null)
            );
        });

        return items;
    }

    private Optional<ApplicationInfo> getApplicationInfo(String packageName, int userId) {
        ApplicationInfo applicationInfo;
        try {
            applicationInfo = mPackageManager
                    .getApplicationInfoAsUser(packageName, /* flags= */ 0, userId);
            return Optional.of(applicationInfo);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Application info not found for: " + packageName);
            return Optional.empty();
        }
    }

    @WorkerThread
    private List<PermissionGroupUsage> getPermGroupUsages() {
        return mPermissionManager.getIndicatorAppOpUsageData();
    }

    @WorkerThread
    private String getLabelForPackage(String packageName, int userId) {
        Optional<ApplicationInfo> applicationInfo = getApplicationInfo(packageName, userId);

        if (!applicationInfo.isPresent()) return packageName;

        return (String) applicationInfo.get().loadLabel(mPackageManager);
    }

    /**
     * If {@link PrivacyType} is available then returns the argument, or else returns {@code null}.
     */
    @Nullable
    private PrivacyType verifyType(PrivacyType type) {
        if ((type == PrivacyType.TYPE_CAMERA || type == PrivacyType.TYPE_MICROPHONE)
                && mPrivacyItemController.getMicCameraAvailable()) {
            return type;
        } else if (type == PrivacyType.TYPE_LOCATION
                && mPrivacyItemController.getLocationAvailable()) {
            return type;
        } else {
            return null;
        }
    }

    private List<PrivacyDialogDelegate.PrivacyElement> filterAndSort(
            List<PrivacyDialogDelegate.PrivacyElement> list) {
        return list.stream()
                .filter(it -> it.getType() == getProviderPrivacyType())
                .sorted(new PrivacyElementComparator())
                .collect(Collectors.toList());
    }

    private static class PrivacyElementComparator
            implements Comparator<PrivacyDialogDelegate.PrivacyElement> {
        @Override
        public int compare(PrivacyDialogDelegate.PrivacyElement it1,
                PrivacyDialogDelegate.PrivacyElement it2) {
            if (it1.getActive() && !it2.getActive()) {
                return 1;
            } else if (!it1.getActive() && it2.getActive()) {
                return -1;
            } else {
                return Long.compare(it1.getLastActiveTimestamp(), it2.getLastActiveTimestamp());
            }
        }
    }
}
