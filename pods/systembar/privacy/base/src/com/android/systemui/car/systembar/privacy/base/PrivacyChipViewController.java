/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.hardware.SensorPrivacyManager.TOGGLE_TYPE_SOFTWARE;

import android.annotation.LayoutRes;
import android.content.Context;
import android.hardware.SensorPrivacyManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.flexibleui.CarSystemBarElementController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.systembar.panel.PanelContentProvider;
import com.android.systemui.car.systembar.panel.PanelViewController;
import com.android.systemui.privacy.PrivacyItem;
import com.android.systemui.privacy.PrivacyItemController;
import com.android.systemui.privacy.PrivacyType;
import com.android.systemui.settings.UserTracker;

import java.util.List;
import java.util.Optional;

import javax.inject.Provider;

/** Controls a Privacy Chip view in system icons. */
public abstract class PrivacyChipViewController extends CarSystemBarElementController<PrivacyChip> {

    private final PrivacyItemController mPrivacyItemController;
    private final SensorPrivacyManager mSensorPrivacyManager;
    private final UserTracker mUserTracker;
    private final CarDeviceProvisionedController mCarDeviceProvisionedController;
    private final Provider<PanelViewController.Factory> mPanelControllerFactoryProvider;
    private Context mContext;

    private final SensorPrivacyManager.OnSensorPrivacyChangedListener
            mOnSensorPrivacyChangedListener = (sensor, sensorPrivacyEnabled) -> {
                if (mContext == null) {
                    return;
                }
                // Since this is launched using a callback thread, its UI based elements need
                // to execute on main executor.
                mContext.getMainExecutor().execute(() -> {
                    // We need to negate enabled since when it is {@code true} it means
                    // the sensor (such as microphone or camera) has been toggled off.
                    mView.setSensorEnabled(/* enabled= */ !sensorPrivacyEnabled);
                });
            };

    private final UserTracker.Callback mUserSwitchCallback = new UserTracker.Callback() {
        @Override
        public void onUserChanged(int newUser, Context userContext) {
            mView.setSensorEnabled(isSensorEnabled());
        }
    };

    private boolean mAllIndicatorsEnabled;
    private boolean mMicCameraIndicatorsEnabled;
    private boolean mIsPrivacyChipVisible;
    private final PrivacyItemController.Callback mPicCallback =
            new PrivacyItemController.Callback() {
                @Override
                public void onPrivacyItemsChanged(@NonNull List<PrivacyItem> privacyItems) {
                    if (mView == null) {
                        return;
                    }

                    boolean shouldShowPrivacyChip = isSensorPartOfPrivacyItems(privacyItems);
                    if (mIsPrivacyChipVisible == shouldShowPrivacyChip) {
                        return;
                    }

                    mIsPrivacyChipVisible = shouldShowPrivacyChip;
                    setChipVisibility(shouldShowPrivacyChip);
                }

                @Override
                public void onFlagAllChanged(boolean enabled) {
                    onAllIndicatorsToggled(enabled);
                }

                @Override
                public void onFlagMicCameraChanged(boolean enabled) {
                    onMicCameraToggled(enabled);
                }

                private void onMicCameraToggled(boolean enabled) {
                    if (mMicCameraIndicatorsEnabled != enabled) {
                        mMicCameraIndicatorsEnabled = enabled;
                    }
                }

                private void onAllIndicatorsToggled(boolean enabled) {
                    if (mAllIndicatorsEnabled != enabled) {
                        mAllIndicatorsEnabled = enabled;
                    }
                }
            };

    public PrivacyChipViewController(PrivacyChip view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            Context context,
            PrivacyItemController privacyItemController,
            SensorPrivacyManager sensorPrivacyManager, UserTracker userTracker,
            CarDeviceProvisionedController carDeviceProvisionedController,
            Provider<PanelViewController.Factory> panelControllerFactoryProvider) {
        super(view, disableController, stateController);
        mContext = context;
        mPrivacyItemController = privacyItemController;
        mSensorPrivacyManager = sensorPrivacyManager;
        mUserTracker = userTracker;
        mCarDeviceProvisionedController = carDeviceProvisionedController;
        mPanelControllerFactoryProvider = panelControllerFactoryProvider;
        mIsPrivacyChipVisible = false;
    }

    @VisibleForTesting
    protected boolean isSensorEnabled() {
        // We need to negate return of isSensorPrivacyEnabled since when it is {@code true} it
        // means the sensor (microphone/camera) has been toggled off
        return !mSensorPrivacyManager.isSensorPrivacyEnabled(/* toggleType= */ TOGGLE_TYPE_SOFTWARE,
                /* sensor= */ getChipSensor());
    }

    protected abstract @SensorPrivacyManager.Sensors.Sensor int getChipSensor();

    protected abstract PrivacyType getChipPrivacyType();

    protected abstract @IdRes int getChipResourceId();

    protected abstract @LayoutRes int getPanelLayoutRes();

    private boolean isSensorPartOfPrivacyItems(@NonNull List<PrivacyItem> privacyItems) {
        Optional<PrivacyItem> optionalSensorPrivacyItem = privacyItems.stream()
                .filter(privacyItem -> privacyItem.getPrivacyType()
                        .equals(getChipPrivacyType()))
                .findAny();
        return optionalSensorPrivacyItem.isPresent();
    }

    @Override
    protected void onInit() {
        super.onInit();
        if (isDeviceSetupForUser() && getPanelLayoutRes() != 0) {
            PanelContentProvider panelContentProvider = new PanelContentProvider() {
                @Override
                public ViewGroup createPanelContentView(Context context) {
                    return (ViewGroup) LayoutInflater.from(context).inflate(getPanelLayoutRes(),
                            /* root= */ null);
                }

                @Override
                public int getPanelWidthPx() {
                    return mContext.getResources().getDimensionPixelSize(
                            com.android.systemui.car.shared.R.dimen.car_sensor_qc_panel_width);
                }

                @Override
                public int getXOffsetPx() {
                    return -mContext.getResources().getDimensionPixelOffset(
                            com.android.systemui.car.shared
                                    .R.dimen.privacy_chip_horizontal_padding);
                }

                @Override
                public int getYOffsetPx() {
                    return mContext.getResources().getDimensionPixelOffset(
                            com.android.systemui.car.shared
                                    .R.dimen.privacy_chip_vertical_padding);
                }

                @Override
                public int getPanelGravity() {
                    return Gravity.TOP | Gravity.END;
                }
            };
            PanelViewController panelViewController =
                    mPanelControllerFactoryProvider.get().create(mView, panelContentProvider);
            panelViewController.init();
        }
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        mAllIndicatorsEnabled = mPrivacyItemController.getAllIndicatorsAvailable();
        mMicCameraIndicatorsEnabled = mPrivacyItemController.getMicCameraAvailable();
        mPrivacyItemController.addCallback(mPicCallback);

        mSensorPrivacyManager.removeSensorPrivacyListener(getChipSensor(),
                mOnSensorPrivacyChangedListener);
        mSensorPrivacyManager.addSensorPrivacyListener(getChipSensor(),
                mOnSensorPrivacyChangedListener);

        // Since this can be launched using a callback thread, its UI based elements need
        // to execute on main executor.
        mContext.getMainExecutor().execute(() -> {
            mView.setSensorEnabled(isSensorEnabled());
        });
        mUserTracker.removeCallback(mUserSwitchCallback);
        mUserTracker.addCallback(mUserSwitchCallback, mContext.getMainExecutor());
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        mIsPrivacyChipVisible = false;
        mPrivacyItemController.removeCallback(mPicCallback);
        mSensorPrivacyManager.removeSensorPrivacyListener(getChipSensor(),
                mOnSensorPrivacyChangedListener);
        mUserTracker.removeCallback(mUserSwitchCallback);
    }

    private void setChipVisibility(boolean chipVisible) {
        // Since this is launched using a callback thread, its UI based elements need
        // to execute on main executor.
        mContext.getMainExecutor().execute(() -> {
            if (chipVisible && getChipEnabled()) {
                mView.animateIn();
            } else {
                mView.animateOut();
            }
        });
    }

    private boolean getChipEnabled() {
        return mMicCameraIndicatorsEnabled || mAllIndicatorsEnabled;
    }

    private boolean isDeviceSetupForUser() {
        return mCarDeviceProvisionedController.isCurrentUserSetup()
                && !mCarDeviceProvisionedController.isCurrentUserSetupInProgress();
    }
}
