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
package com.android.systemui.car.ndo;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.os.UserHandle;
import android.telecom.Call;
import android.util.Slog;

import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.android.car.telephony.calling.InCallServiceManager;
import com.android.systemui.car.telecom.InCallServiceImpl;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

import javax.inject.Inject;

/**
 * ViewModel for blocking activities. Provides livedatas that listen to media and call state.
 */
public class BlockerViewModel extends ViewModel implements PropertyChangeListener {
    private static final String TAG = "SysUi.BlockerViewModel";
    private static final String PROPERTY_IN_CALL_SERVICE = "PROPERTY_IN_CALL_SERVICE";
    private final Context mContext;
    private boolean mIsInitialized;
    private String mBlockedActivity;

    @VisibleForTesting
    InCallLiveData mInCallLiveData;
    private final InCallServiceManager mServiceManager;
    private final MediaSessionHelper mMediaSessionHelper;
    private final MediatorLiveData<BlockingType> mBlockingTypeLiveData = new MediatorLiveData<>();

    @Inject
    public BlockerViewModel(Context context, InCallServiceManager serviceManager,
            MediaSessionHelper mediaSessionHelper) {
        mContext = context;
        mServiceManager = serviceManager;
        mMediaSessionHelper = mediaSessionHelper;
    }

    /** Initialize data sources **/
    public void initialize(String blockedActivity, UserHandle userHandle) {
        // Prevent reinitialization if the ABA gets a config change.
        if (mIsInitialized) {
            return;
        }
        mBlockedActivity = blockedActivity;
        mInCallLiveData = new InCallLiveData(mServiceManager, blockedActivity);

        // Listens to the call manager for when the inCallService is started after ABA.
        mServiceManager.addObserver(this);
        if (mServiceManager.getInCallService() != null) {
            onInCallServiceConnected();
        }

        mMediaSessionHelper.init(userHandle);

        // Set initial liveData value
        onUpdate();

        mBlockingTypeLiveData.addSource(mInCallLiveData, call -> onUpdate());
        mBlockingTypeLiveData.addSource(mMediaSessionHelper.getActiveMediaSessions(),
                mediaSources -> onUpdate());

        mIsInitialized = true;
    }

    /**
     * Returns the livedata that indicates whether the blocked activity is voip or media.
     */
    public LiveData<BlockingType> getBlockingTypeLiveData() {
        return mBlockingTypeLiveData;
    }

    /**
     * {@link InCallServiceManager} will call this method to let it know the InCallService has been
     * added.
     */
    @Override
    public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
        if (PROPERTY_IN_CALL_SERVICE.equals(propertyChangeEvent.getPropertyName())
                && mServiceManager.getInCallService() != null) {
            onInCallServiceConnected();
        }
    }

    @Override
    public void onCleared() {
        InCallServiceImpl inCallService = (InCallServiceImpl) mServiceManager.getInCallService();
        if (inCallService != null) {
            inCallService.removeListener(mInCallLiveData);
        }
        mServiceManager.removeObserver(this);
        mMediaSessionHelper.cleanup();
        mBlockingTypeLiveData.removeSource(mInCallLiveData);
        mBlockingTypeLiveData.removeSource(mMediaSessionHelper.getActiveMediaSessions());
    }

    @VisibleForTesting
    void onUpdate() {
        // Prioritize dialer first
        Call call = mInCallLiveData.getValue();
        if (call != null) {
            mBlockingTypeLiveData.setValue(BlockingType.DIALER);
        } else if (isBlockingActiveMediaSession(
                mMediaSessionHelper.getActiveMediaSessions().getValue())) {
            mBlockingTypeLiveData.setValue(BlockingType.MEDIA);
        } else {
            mBlockingTypeLiveData.setValue(BlockingType.NONE);
        }
    }

    private void onInCallServiceConnected() {
        Slog.d(TAG, "inCallService Connected");
        InCallServiceImpl inCallService = (InCallServiceImpl) mServiceManager.getInCallService();
        inCallService.addListener(mInCallLiveData);
    }

    /** @return whether the ABA is blocking an app with an active media session or not */
    private boolean isBlockingActiveMediaSession(List<MediaController> mediaControllers) {
        ComponentName componentName = ComponentName.unflattenFromString(mBlockedActivity);
        if (componentName == null || mediaControllers == null) {
            return false;
        }

        for (MediaController mediaController : mediaControllers) {
            if (mediaController.getPackageName().equals(componentName.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Enum for the different types of apps that are being blocked
     */
    public enum BlockingType {
        NONE,
        DIALER,
        MEDIA
    }
}
