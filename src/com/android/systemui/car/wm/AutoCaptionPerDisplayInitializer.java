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

package com.android.systemui.car.wm;

import static com.android.systemui.car.Flags.displayCompatibilityAutoDecorSafeRegion;
import static com.android.systemui.car.users.CarSystemUIUserUtil.isSecondaryMUMDSystemUI;

import android.content.Context;
import android.graphics.Rect;
import android.os.UserManager;
import android.util.SparseArray;
import android.window.DisplayAreaInfo;

import com.android.systemui.car.shared.R;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.automotive.AutoCaptionController;
import com.android.wm.shell.automotive.AutoLayoutManager;
import com.android.wm.shell.common.DisplayController;

/**
 * Initialises caption bars(using AutoDecor) and safe regions for displays in the system.
 * TODO(b/409131368): Remove this class when Hudson is migrated to scalable ui
 */
public class AutoCaptionPerDisplayInitializer implements
        DisplayController.OnDisplaysChangedListener {
    private final AutoCaptionController mAutoCaptionController;
    private final AutoCaptionBarViewControllerImpl mAutoCaptionBarViewControllerImpl;
    private final boolean mEnableSafeAreaAndToolbarPerDisplay;
    private final Rect mSafeRegion;
    private final Rect mCaptionRegion;
    private final RootTaskDisplayAreaOrganizer mRootTaskDisplayAreaOrganizer;
    private final SparseArray<RootTaskDisplayAreaOrganizer.RootTaskDisplayAreaListener>
            mDisplayIdToListenerMap = new SparseArray<>();
    private final AutoLayoutManager mAutoLayoutManager;

    public AutoCaptionPerDisplayInitializer(
            Context context,
            ShellTaskOrganizer shellTaskOrganizer,
            AutoCaptionController autoCaptionController,
            DisplayController displayController,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            AutoLayoutManager autoLayoutManager) {
        mAutoCaptionController = autoCaptionController;
        mAutoCaptionBarViewControllerImpl =
                new AutoCaptionBarViewControllerImpl(context, shellTaskOrganizer);
        // TODO(b/443340830): enable safe region for mumd
        mEnableSafeAreaAndToolbarPerDisplay = context.getResources().getBoolean(
                R.bool.config_enableSafeAreaAndToolbarPerDisplay)
                && !UserManager.isVisibleBackgroundUsersEnabled();
        mSafeRegion = new Rect(
                context.getResources().getDimensionPixelSize(R.dimen.safe_region_left),
                context.getResources().getDimensionPixelSize(R.dimen.safe_region_top),
                context.getResources().getDimensionPixelSize(R.dimen.safe_region_right),
                context.getResources().getDimensionPixelSize(R.dimen.safe_region_bottom)
        );
        mCaptionRegion = new Rect(
                context.getResources().getDimensionPixelSize(R.dimen.caption_region_left),
                context.getResources().getDimensionPixelSize(R.dimen.caption_region_top),
                context.getResources().getDimensionPixelSize(R.dimen.caption_region_right),
                context.getResources().getDimensionPixelSize(R.dimen.caption_region_bottom)
        );
        mRootTaskDisplayAreaOrganizer = rootTaskDisplayAreaOrganizer;
        mAutoLayoutManager = autoLayoutManager;
        if (!displayCompatibilityAutoDecorSafeRegion() || !mEnableSafeAreaAndToolbarPerDisplay) {
            return;
        }
        if (!isSecondaryMUMDSystemUI()) {
            displayController.addDisplayWindowListener(this);
        }
    }

    @Override
    public void onDisplayAdded(int displayId) {
        if (!displayCompatibilityAutoDecorSafeRegion() || !mEnableSafeAreaAndToolbarPerDisplay) {
            return;
        }
        RootTDAListener listener = new RootTDAListener();
        mRootTaskDisplayAreaOrganizer.registerListener(displayId, listener);
        mDisplayIdToListenerMap.append(displayId, listener);
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        if (!displayCompatibilityAutoDecorSafeRegion() || !mEnableSafeAreaAndToolbarPerDisplay) {
            return;
        }
        RootTaskDisplayAreaOrganizer.RootTaskDisplayAreaListener listener =
                mDisplayIdToListenerMap.removeReturnOld(displayId);
        if (listener != null) {
            mRootTaskDisplayAreaOrganizer.unregisterListener(listener);
        }
        mAutoCaptionController.removeCaptionRegion(displayId);
        mAutoLayoutManager.setOrUpdateSafeRegion(displayId, mSafeRegion);
    }

    /**
     * {@link RootTaskDisplayAreaOrganizer.RootTaskDisplayAreaListener} that attaches the safe
     * region and toolbar to new display areas.
     */
    private class RootTDAListener implements
            RootTaskDisplayAreaOrganizer.RootTaskDisplayAreaListener {
        @Override
        public void onDisplayAreaAppeared(DisplayAreaInfo displayAreaInfo) {
            if (displayAreaInfo == null) {
                return;
            }
            mAutoLayoutManager.setOrUpdateSafeRegion(displayAreaInfo.displayId, mSafeRegion);
            mAutoCaptionController.setCaptionRegion(displayAreaInfo.displayId,
                    mCaptionRegion,
                    mAutoCaptionBarViewControllerImpl
            );
        }
    }
}
