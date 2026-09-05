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
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;

import com.android.systemui.car.flags.Flag;
import com.android.systemui.car.flags.FlagManager;
import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.sysui.ConfigurationChangeListener;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;

import javax.annotation.concurrent.GuardedBy;

/**
 * Class to include ScalableUI constructs that need to be initialized on startup.
 */
@WMSingleton
public class ScalableUIWMInitializer implements ConfigurationChangeListener {
    private static final String TAG = ScalableUIWMInitializer.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    private final ActionConfigReader mActionConfigReader;
    private final PanelConfigReader mPanelConfigReader;
    private final PanelAutoTaskStackTransitionHandlerDelegate
            mPanelAutoTaskStackTransitionHandlerDelegate;
    private final ScalableUIDumpsys mScalableUIDumpsys;
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private Configuration mConfiguration;
    private final AutoTaskStackHelper mAutoTaskStackHelper;

    public ScalableUIWMInitializer(Context context, ShellInit shellInit,
            ActionConfigReader actionConfigReader, PanelConfigReader panelConfigReader,
            PanelAutoTaskStackTransitionHandlerDelegate delegate,
            ScalableUIDumpsys scalableUIDumpsys, AutoTaskStackHelper autoTaskStackHelper,
            FlagManager flagManager, ShellController shellController) {
        shellInit.addInitCallback(this::onInit, this);
        mActionConfigReader = actionConfigReader;
        mPanelConfigReader = panelConfigReader;
        mPanelAutoTaskStackTransitionHandlerDelegate = delegate;
        mScalableUIDumpsys = scalableUIDumpsys;
        mAutoTaskStackHelper = autoTaskStackHelper;
        if (flagManager.isEnabled(Flag.ScalableUiHandleConfigurationChange)) {
            shellController.addConfigurationChangeListener(this);
        }
        mConfiguration = new Configuration(context.getResources().getConfiguration());
    }

    private void onInit() {
        mPanelAutoTaskStackTransitionHandlerDelegate.init();
        mPanelConfigReader.init();
        mActionConfigReader.init();
        mScalableUIDumpsys.init();
    }

    private void reloadPanels() {
        mAutoTaskStackHelper.reloadTaskConfigs();
        synchronized (mLock) {
            mPanelConfigReader.reloadConfig(mConfiguration);
        }
        mActionConfigReader.init();
        mScalableUIDumpsys.init();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        debugLog("onConfigChanged" + newConfig);
        synchronized (mLock) {
            int diff = mConfiguration.updateFrom(newConfig);
            boolean orientationChanged = ((diff & ActivityInfo.CONFIG_ORIENTATION) != 0);
            boolean assetPathChanged = (diff & ActivityInfo.CONFIG_ASSETS_PATHS) != 0;
            debugLog("onConfigurationChanged: orientationChanged=" + orientationChanged
                    + " assetPathChanged=" + assetPathChanged);
            if (orientationChanged || assetPathChanged) {
                reloadPanels();
            }
        }
    }

    private void debugLog(String logMsg) {
        if (DEBUG) {
            Log.d(TAG, logMsg);
        }
    }
}
