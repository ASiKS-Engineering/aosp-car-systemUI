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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.Log;

import com.android.car.internal.dep.Trace;
import com.android.car.scalableui.designcompose.PanelStateDocLoader;
import com.android.car.scalableui.loader.xml.XmlModelLoader;
import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.PanelState;
import com.android.car.scalableui.model.PanelType;
import com.android.car.scalableui.panel.PanelPool;
import com.android.systemui.car.flags.Flag;
import com.android.systemui.car.flags.FlagManager;
import com.android.systemui.car.shared.R;
import com.android.systemui.car.wm.scalableui.panel.DecorPanel;
import com.android.systemui.car.wm.scalableui.panel.SysUIPanel;
import com.android.systemui.car.wm.scalableui.panel.TaskPanel;
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelConfigReadStateMonitor;
import com.android.wm.shell.dagger.WMSingleton;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads and loads panel configurations from various sources (XML or Design Compose files).
 *
 * This class is responsible for initializing and reloading the panel configurations used by the
 * system UI. It interacts with {@link PanelConfigReadStateMonitor} to signal when the
 * configurations have been successfully loaded and are ready for use by other components.
 */
@WMSingleton
public class PanelConfigReader {
    private static final String TAG = PanelConfigReader.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    private Context mContext;
    private final TaskPanel.Factory mTaskPanelFactory;
    private final DecorPanel.Factory mDecorPanelFactory;
    private final SysUIPanel.Factory mSysUiPanelFactory;
    private final FlagManager mFlagManager;
    private final PanelConfigReadStateMonitor mMonitor;

    public PanelConfigReader(Context context, TaskPanel.Factory taskPanelFactory,
            DecorPanel.Factory decorPanelFactory, SysUIPanel.Factory sysUiPanelFactory,
            PanelConfigReadStateMonitor monitor, FlagManager flagManager) {
        mFlagManager = flagManager;
        mContext = context;
        mTaskPanelFactory = taskPanelFactory;
        mDecorPanelFactory = decorPanelFactory;
        mSysUiPanelFactory = sysUiPanelFactory;
        mMonitor = monitor;
    }

    /**
     * Init the Panels.
     */
    public void init() {
        PanelPool.getInstance().clearPanels();
        PanelPool.getInstance().setDelegate((id, type) -> {
            if (type == PanelType.DECOR) {
                return mDecorPanelFactory.create(id);
            } else if (type == PanelType.SYSTEM_BAR || type == PanelType.HUN) {
                return mSysUiPanelFactory.create(id);
            } else {
                return mTaskPanelFactory.create(id);
            }
        });
        loadConfig();
    }

    /**
     * Loads the panel configurations.
     *
     * <p>This method clears any existing panel states and then loads the new configurations from
     * either a Design Compose file (.dcf) or XML files, depending on whether the
     * {@link Flag#ScalableUiDesignCompose} flag is enabled.
     *
     * <p>After successfully loading the configuration, it notifies the
     * {@link PanelConfigReadStateMonitor} that the configuration is ready.
     */
    public void loadConfig() {
        mMonitor.setReady(false);
        try {
            Map<String, PanelState> panelStates;
            Trace.beginSection(TAG + "#load");
            if (mFlagManager.isEnabled(Flag.ScalableUiDesignCompose)) {
                panelStates = loadFromDcf();
            } else {
                panelStates = loadFromXml();
            }
            if (panelStates != null) {
                StateManager.reloadPanelState(panelStates);
            }
        } finally {
            Trace.endSection();
        }
        mMonitor.setReady(true);
    }

    /**
     * Clears and reloads the panel configuration. This is intended to be called when a
     * configuration change, such as an orientation change, requires resources to be reloaded.
     */
    public void reloadConfig(Configuration configuration) {
        mContext = mContext.createConfigurationContext(configuration);
        loadConfig();
    }

    private Map<String, PanelState> loadFromDcf() {
        try {
            InputStream dcfStream = mContext.getResources().openRawResource(R.raw.ScalableSystemUi);
            if (dcfStream == null) {
                Log.e(TAG, "Failed to open file ScalableSystemUi.dcf");
                // Throw a runtime exception to cause a crash
                throw new RuntimeException("Failed to open ScalableSystemUi.dcf");
            }

            debugLog("Loading panel states from DCF file");
            PanelStateDocLoader dcLoader = new PanelStateDocLoader(mContext);
            String docId = mContext.getResources().getString(R.string.config_scalableUiDcfFileId);

            List<PanelState> states = dcLoader.loadPanelStates(dcfStream, docId);
            debugLog("Loaded Panels: " + states.size());

            return states.stream().collect(
                    Collectors.toMap(PanelState::getId, panelState -> panelState));
        } catch (Exception e) {
            Log.e(TAG, "Error opening or processing DCF file: " + e);
            // Throw a runtime exception to cause a crash
            throw new RuntimeException("Error opening or processing DCF file: ", e);
        }
    }

    private Map<String, PanelState> loadFromXml() {
        debugLog("Loading panel states from XML" + mContext);
        Resources res = mContext.getResources();
        Map<String, PanelState> panelStates = new HashMap<>();
        try (TypedArray states = res.obtainTypedArray(R.array.window_states)) {
            debugLog("Found win state length = " + states.length());
            for (int i = 0; i < states.length(); i++) {
                int xmlResId = states.getResourceId(i, 0);
                XmlModelLoader loader = new XmlModelLoader(mContext);
                PanelState panelState = loader.createPanelState(xmlResId);
                debugLog("PanelConfig loaded Panel state " + panelState);
                if (panelState != null) {
                    panelStates.put(panelState.getId(), panelState);
                }
            }
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "window_states no found " + e);
            throw new RuntimeException("window_states no found ", e);
        } catch (RuntimeException runtimeException) {
            Log.e(TAG, "fail to get res for state" + runtimeException);
            throw new RuntimeException("fail to get res for state", runtimeException);
        }
        return panelStates;
    }

    private void debugLog(String logMsg) {
        if (DEBUG) {
            Log.d(TAG, logMsg);
        }
    }
}