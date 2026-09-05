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

import android.app.INotificationManager;
import android.app.Notification;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import javax.inject.Inject;

/**
 * Class that handles listening to and returning active media sessions.
 */
public class MediaSessionHelper extends MediaController.Callback {
    private static final String TAG = "MediaSessionHelper";
    private final MutableLiveData<List<MediaController>> mLiveData = new MutableLiveData<>();
    private final MediaSessionManager mMediaSessionManager;
    private UserHandle mUserHandle;
    @VisibleForTesting
    final List<MediaController> mMediaControllersList = new ArrayList<>();
    private final Executor mExecutor;
    private final Context mContext;
    private final INotificationManager mINotificationManager;

    private final MediaSessionManager.OnActiveSessionsChangedListener mChangedListener =
            this::onMediaSessionChange;

    @Inject
    public MediaSessionHelper(Context context, INotificationManager iNotificationManager) {
        mMediaSessionManager = context.getSystemService(MediaSessionManager.class);
        mExecutor = context.getMainExecutor();
        mContext = context;
        mINotificationManager = iNotificationManager;
    }

    /** Performs initialization */
    public void init(UserHandle userHandle) {
        mUserHandle = userHandle;
        // Set initial data
        onMediaSessionChange(mMediaSessionManager
                .getActiveSessionsForUser(/* notificationListener= */ null, mUserHandle));

        mMediaSessionManager.addOnActiveSessionsChangedListener(/* notificationListener= */ null,
                mUserHandle, mExecutor, mChangedListener);
    }

    /** Returns MediaControllers of current active media sessions */
    public LiveData<List<MediaController>> getActiveMediaSessions() {
        return mLiveData;
    }

    /** Performs cleanup when MediaSessionHelper should no longer be used. */
    public void cleanup() {
        mMediaSessionManager.removeOnActiveSessionsChangedListener(mChangedListener);
        unregisterPlaybackChanges();
    }

    @Override
    public void onPlaybackStateChanged(@Nullable PlaybackState state) {
        if (isPausedOrActive(state)) {
            onMediaSessionChange(mMediaSessionManager
                    .getActiveSessionsForUser(/* notificationListener= */ null, mUserHandle));
        }
    }

    private void onMediaSessionChange(List<MediaController> mediaControllers) {
        unregisterPlaybackChanges();
        if (mediaControllers == null || mediaControllers.isEmpty()) {
            return;
        }

        List<MediaController> activeMediaControllers = new ArrayList<>();
        List<String> mediaNotificationPackages = getActiveMediaNotificationPackages();

        for (MediaController mediaController : mediaControllers) {
            if (isPausedOrActive(mediaController.getPlaybackState())
                    && mediaNotificationPackages.contains(mediaController.getPackageName())) {
                activeMediaControllers.add(mediaController);
            } else {
                // Since playback state changes don't trigger an active media session change, we
                // need to listen to the other media sessions in case another one becomes active.
                registerForPlaybackChanges(mediaController);
            }
        }
        mLiveData.setValue(activeMediaControllers);
    }

    /** Returns whether the MediaController is paused active */
    private boolean isPausedOrActive(PlaybackState playbackState) {
        if (playbackState == null) {
            return false;
        }
        return playbackState.isActive() || playbackState.getState() == PlaybackState.STATE_PAUSED;
    }

    private void registerForPlaybackChanges(MediaController controller) {
        if (mMediaControllersList.contains(controller)) {
            return;
        }

        controller.registerCallback(this);
        mMediaControllersList.add(controller);
    }

    private void unregisterPlaybackChanges() {
        for (MediaController mediaController : mMediaControllersList) {
            if (mediaController != null) {
                mediaController.unregisterCallback(this);
            }
        }
        mMediaControllersList.clear();
    }

    // We only want to detect media sessions with an associated media notification
    private List<String> getActiveMediaNotificationPackages() {
        try {
            List<StatusBarNotification> activeNotifications = List.of(
                    mINotificationManager.getActiveNotificationsWithAttribution(
                            mContext.getPackageName(), /* callingAttributionTag= */ null
                    ));

            List<String> packageNames = new ArrayList<>();
            for (StatusBarNotification statusBarNotification : activeNotifications) {
                Notification notification = statusBarNotification.getNotification();
                if (notification.extras != null
                        && notification.isMediaNotification()) {
                    packageNames.add(statusBarNotification.getPackageName());
                }
            }

            return packageNames;
        } catch (RemoteException e) {
            Log.e(TAG, "Exception trying to get active notifications " + e);
            return Collections.emptyList();
        }
    }
}
