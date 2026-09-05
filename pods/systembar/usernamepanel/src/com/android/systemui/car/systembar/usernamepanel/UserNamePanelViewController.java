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

package com.android.systemui.car.systembar.usernamepanel;

import android.app.ActivityOptions;
import android.car.app.CarActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.android.car.ui.utils.CarUxRestrictionsUtil;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.flexibleui.CarSystemBarElementController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.systembar.panel.CarSystemBarPanelButtonView;
import com.android.systemui.car.systembar.panel.CarSystemBarPanelButtonViewController;
import com.android.systemui.car.systembar.panel.PanelViewController;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.settings.UserTracker;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.net.URISyntaxException;

import javax.inject.Provider;

public class UserNamePanelViewController extends CarSystemBarPanelButtonViewController {
    private static final String TAG = UserNamePanelViewController.class.getName();
    private final Context mContext;
    private final UserTracker mUserTracker;
    private final CarServiceProvider mCarServiceProvider;
    private final CarDeviceProvisionedController mCarDeviceProvisionedController;
    private final boolean mIsMUMDSystemUI;
    private CarActivityManager mCarActivityManager;

    private final CarServiceProvider.CarServiceOnConnectedListener mCarServiceOnConnectedListener =
            car -> {
                mCarActivityManager = car.getCarManager(CarActivityManager.class);
            };

    @AssistedInject
    protected UserNamePanelViewController(@Assisted CarSystemBarPanelButtonView view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            Provider<PanelViewController.Factory> statusIconPanelFactoryProvider,
            Context context, UserTracker userTracker, CarServiceProvider carServiceProvider,
            CarDeviceProvisionedController deviceProvisionedController) {
        super(view, disableController, stateController, statusIconPanelFactoryProvider);
        mContext = context;
        mUserTracker = userTracker;
        mCarServiceProvider = carServiceProvider;
        mCarDeviceProvisionedController = deviceProvisionedController;
        mIsMUMDSystemUI = CarSystemUIUserUtil.isMUMDSystemUI();
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<CarSystemBarPanelButtonView,
                    UserNamePanelViewController> {
    }

    @Override
    protected void onInit() {
        if (mIsMUMDSystemUI) {
            // TODO(b/269490856): consider removal of UserPicker carve-outs
            mView.setOnClickListener(getMUMDUserPickerClickListener());
        } else {
            super.onInit();
        }
        if (!Build.IS_ENG && !Build.IS_USERDEBUG) {
            return;
        }
        String longIntentString = mContext.getString(R.string.user_profile_long_press_intent);
        if (!TextUtils.isEmpty(longIntentString)) {
            Intent intent;
            try {
                intent = Intent.parseUri(longIntentString, Intent.URI_INTENT_SCHEME);
            } catch (URISyntaxException e) {
                return;
            }
            Intent finalIntent = intent;
            mView.setOnLongClickListener(v -> {
                Intent broadcast = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
                mContext.sendBroadcastAsUser(broadcast, mUserTracker.getUserHandle());
                try {
                    ActivityOptions options = ActivityOptions.makeBasic();
                    options.setLaunchDisplayId(mContext.getDisplayId());
                    mContext.startActivityAsUser(finalIntent, options.toBundle(),
                            mUserTracker.getUserHandle());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch intent", e);
                }
                return true;
            });
        }
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        if (mIsMUMDSystemUI) {
            mCarServiceProvider.addListener(mCarServiceOnConnectedListener);
        }
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        if (mIsMUMDSystemUI) {
            mCarServiceProvider.removeListener(mCarServiceOnConnectedListener);
            mCarActivityManager = null;
        }
    }

    @Override
    protected boolean shouldRestoreState() {
        // TODO(b/269490856): consider removal of UserPicker carve-outs
        return !CarSystemUIUserUtil.isMUMDSystemUI();
    }

    private View.OnClickListener getMUMDUserPickerClickListener() {
        CarUxRestrictionsUtil carUxRestrictionsUtil;
        if (mView.isDisabledWhileDriving()) {
            carUxRestrictionsUtil = CarUxRestrictionsUtil.getInstance(mContext);
        } else {
            carUxRestrictionsUtil = null;
        }
        return v -> {
            if (mView.isDisabledWhileUnprovisioned() && !isDeviceSetupForUser()) {
                return;
            }
            if (mView.isDisabledWhileDriving() && carUxRestrictionsUtil.getCurrentRestrictions()
                    .isRequiresDistractionOptimization()) {
                Toast.makeText(mContext,
                        com.android.car.ui.R.string.car_ui_restricted_while_driving,
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (mCarActivityManager != null) {
                mCarActivityManager.startUserPickerOnDisplay(mContext.getDisplayId());
            }
        };
    }

    private boolean isDeviceSetupForUser() {
        return mCarDeviceProvisionedController.isCurrentUserSetup()
                && !mCarDeviceProvisionedController.isCurrentUserSetupInProgress();
    }
}
