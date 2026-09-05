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

package com.android.systemui.car.systembar.debugpanel;

import static android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.android.settingslib.development.DevelopmentSettingsEnabler;
import com.android.systemui.car.flexibleui.CarSystemBarElementController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.systembar.base.BuildInfoUtil;
import com.android.systemui.car.systembar.panel.CarSystemBarPanelButtonView;
import com.android.systemui.car.systembar.panel.CarSystemBarPanelButtonViewController;
import com.android.systemui.car.systembar.panel.PanelContentProvider;
import com.android.systemui.car.systembar.panel.PanelContentProviderWrapper;
import com.android.systemui.car.systembar.panel.PanelViewController;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.util.settings.GlobalSettings;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import javax.inject.Provider;

/**
 * A controller for the debug panel button.
 */
public class DebugPanelViewController extends CarSystemBarPanelButtonViewController {
    private final GlobalSettings mGlobalSettings;
    private final Uri mDevelopEnabled;
    private final ContentObserver mDeveloperSettingsObserver;

    @AssistedInject
    protected DebugPanelViewController(@Assisted CarSystemBarPanelButtonView view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            Provider<PanelViewController.Factory> statusIconPanelFactoryProvider,
            @Main Handler mainHandler, GlobalSettings globalSettings) {
        super(view, disableController, stateController, statusIconPanelFactoryProvider);
        mGlobalSettings = globalSettings;
        mDevelopEnabled = globalSettings.getUriFor(DEVELOPMENT_SETTINGS_ENABLED);
        mDeveloperSettingsObserver = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);
                updateVisibility();
            }
        };
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<CarSystemBarPanelButtonView,
                    DebugPanelViewController> {
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        mGlobalSettings.registerContentObserverAsync(mDevelopEnabled, mDeveloperSettingsObserver);
        updateVisibility();
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        mGlobalSettings.unregisterContentObserverAsync(mDeveloperSettingsObserver);
    }

    @Override
    protected boolean shouldBeVisible() {
        return BuildInfoUtil.isDevTesting(getContext())
                && DevelopmentSettingsEnabler.isDevelopmentSettingsEnabled(getContext());
    }

    @Override
    protected PanelContentProvider getPanelContentProvider() {
        return new DebugPanelContentProvider(mView);
    }

    private static final class DebugPanelContentProvider extends PanelContentProviderWrapper {

        DebugPanelContentProvider(PanelContentProvider base) {
            super(base);
        }

        @Override
        public ViewGroup createPanelContentView(Context context) {
            ViewGroup xmlPanelLayout = super.createPanelContentView(context);
            if (xmlPanelLayout != null) {
                return xmlPanelLayout;
            }
            return (ViewGroup) LayoutInflater.from(context).inflate(
                    R.layout.qc_debug_panel, /* root= */ null);
        }
    };
}
