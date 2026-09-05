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

package com.android.systemui.car.statusicon.bluetooth;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import androidx.annotation.VisibleForTesting;

import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.statusicon.base.StatusIconView;
import com.android.systemui.car.statusicon.base.StatusIconViewController;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.statusbar.policy.BluetoothController;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

/**
 * A controller for Bluetooth status icon.
 */
public class BluetoothStatusIconController extends StatusIconViewController implements
        BluetoothController.Callback {
    private final BluetoothController mBluetoothController;
    private final Drawable mBluetoothOffDrawable;
    private final Drawable mBluetoothOnDisconnectedDrawable;
    private final Drawable mBluetoothOnConnectedDrawable;
    private final String mBluetoothOffContentDescription;
    private final String mBluetoothOnDisconnectedContentDescription;
    private final String mBluetoothOnConnectedContentDescription;

    private boolean mBluetoothEnabled;
    private boolean mBluetoothConnected;

    @AssistedInject
    BluetoothStatusIconController(
            @Assisted StatusIconView view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            @Main Resources resources,
            BluetoothController bluetoothController) {
        super(view, disableController, stateController);
        mBluetoothController = bluetoothController;

        mBluetoothOffDrawable = resources.getDrawable(
                R.drawable.ic_bluetooth_status_off,
                /* theme= */ view.getContext().getTheme());
        mBluetoothOnDisconnectedDrawable = resources.getDrawable(
                R.drawable.ic_bluetooth_status_on_disconnected,
                /* theme= */ view.getContext().getTheme());
        mBluetoothOnConnectedDrawable = resources.getDrawable(
                R.drawable.ic_bluetooth_status_on_connected,
                /* theme= */ view.getContext().getTheme());

        mBluetoothOffContentDescription = resources.getString(
                R.string.status_icon_bluetooth_off);
        mBluetoothOnDisconnectedContentDescription = resources.getString(
                R.string.status_icon_bluetooth_disconnected);
        mBluetoothOnConnectedContentDescription = resources.getString(
                R.string.status_icon_bluetooth_connected);
    }

    @AssistedFactory
    public interface Factory extends
            StatusIconViewController.Factory<BluetoothStatusIconController> {
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        mBluetoothController.addCallback(this);
        mBluetoothConnected = !mBluetoothController.getConnectedDevices().isEmpty();
        updateStatus();
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        mBluetoothController.removeCallback(this);
    }

    @Override
    protected void updateStatus() {
        if (!mBluetoothEnabled) {
            setIconDrawableToDisplay(mBluetoothOffDrawable);
            setIconContentDescription(mBluetoothOffContentDescription);
        } else if (mBluetoothConnected) {
            setIconDrawableToDisplay(mBluetoothOnConnectedDrawable);
            setIconContentDescription(mBluetoothOnConnectedContentDescription);
        } else {
            setIconDrawableToDisplay(mBluetoothOnDisconnectedDrawable);
            setIconContentDescription(mBluetoothOnDisconnectedContentDescription);
        }
        onStatusUpdated();
    }

    @Override
    public void onBluetoothStateChange(boolean enabled) {
        mBluetoothEnabled = enabled;
        updateStatus();
    }

    @Override
    public void onBluetoothDevicesChanged() {
        mBluetoothConnected = !mBluetoothController.getConnectedDevices().isEmpty();
        updateStatus();
    }

    @VisibleForTesting
    Drawable getBluetoothOffDrawable() {
        return mBluetoothOffDrawable;
    }

    @VisibleForTesting
    Drawable getBluetoothOnDisconnectedDrawable() {
        return mBluetoothOnDisconnectedDrawable;
    }

    @VisibleForTesting
    Drawable getBluetoothOnConnectedDrawable() {
        return mBluetoothOnConnectedDrawable;
    }
}
