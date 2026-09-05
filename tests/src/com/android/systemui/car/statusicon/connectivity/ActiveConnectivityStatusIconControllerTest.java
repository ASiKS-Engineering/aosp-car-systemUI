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

package com.android.systemui.car.statusicon.connectivity;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import android.content.res.Resources;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.qc.datasubscription.DataSubscriptionToolkitView;
import com.android.systemui.car.statusicon.base.StatusIconView;
import com.android.systemui.statusbar.connectivity.IconState;
import com.android.systemui.statusbar.connectivity.NetworkController;
import com.android.systemui.statusbar.connectivity.WifiIndicators;
import com.android.systemui.statusbar.policy.HotspotController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
public class ActiveConnectivityStatusIconControllerTest extends CarSysuiTestCase {

    @Mock
    Resources mResources;
    @Mock
    NetworkController mNetworkController;
    @Mock
    HotspotController mHotspotController;
    @Mock
    DataSubscriptionToolkitView mDataSubscriptionToolkitView;
    @Mock
    CarSystemBarElementStatusBarDisableController mDisableController;
    @Mock
    CarSystemBarElementStateController mStateController;

    private StatusIconView mView;
    private ActiveConnectivityStatusIconController mActiveConnectivityStatusIconController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mView = new StatusIconView(mContext);
        mActiveConnectivityStatusIconController = new ActiveConnectivityStatusIconController(mView,
                mDisableController, mStateController, mContext, mResources, mNetworkController,
                mHotspotController, mDataSubscriptionToolkitView);
    }

    @Test
    public void onViewAttached_registersNetworkCallbacks() {
        mActiveConnectivityStatusIconController.onViewAttached();
        verify(mNetworkController).addCallback(any());
        verify(mHotspotController).addCallback(any());
    }

    @Test
    public void onViewDetached_unregistersNetworkCallbacks() {
        mActiveConnectivityStatusIconController.onViewAttached();
        mActiveConnectivityStatusIconController.onViewDetached();
        verify(mNetworkController).removeCallback(any());
        verify(mHotspotController).removeCallback(any());
    }

    @Test
    public void onUpdateStatus_wifiDisabled_hotspotDisabled_showsMobileDataIcon() {
        mActiveConnectivityStatusIconController.setWifiIndicators(
                getWifiIndicator(/* enabled= */ false));
        mActiveConnectivityStatusIconController.setEthernetIndicators(
                getEthernetIndicator(/* enabled= */  false));
        mActiveConnectivityStatusIconController.onHotspotChanged(
                /* enabled= */ false, /* numDevices= */  0);

        // onUpdateStatus is called by the events above.

        assertThat(mActiveConnectivityStatusIconController.getIconDrawableToDisplay()).isEqualTo(
                mActiveConnectivityStatusIconController.getMobileSignalIconDrawable());
    }

    @Test
    public void onUpdateStatus_wifiEnabled_hotspotDisabled_showsWifiIcon() {
        mActiveConnectivityStatusIconController.setWifiIndicators(
                getWifiIndicator(/* enabled= */ true));
        mActiveConnectivityStatusIconController.setEthernetIndicators(
                getEthernetIndicator(/* enabled= */  false));
        mActiveConnectivityStatusIconController.onHotspotChanged(
                /* enabled= */ false, /* numDevices= */  0);

        // onUpdateStatus is called by the events above.

        assertThat(mActiveConnectivityStatusIconController.getIconDrawableToDisplay()).isEqualTo(
                mActiveConnectivityStatusIconController.getWifiSignalIconDrawable());
    }

    @Test
    public void onUpdateStatus_wifiDisabled_hotspotEnabled_showsHotspotIcon() {
        mActiveConnectivityStatusIconController.setWifiIndicators(
                getWifiIndicator(/* enabled= */ false));
        mActiveConnectivityStatusIconController.setEthernetIndicators(
                getEthernetIndicator(/* enabled= */  false));
        mActiveConnectivityStatusIconController.onHotspotChanged(
                /* enabled= */ true, /* numDevices= */  0);

        // onUpdateStatus is called by the events above.

        assertThat(mActiveConnectivityStatusIconController.getIconDrawableToDisplay()).isEqualTo(
                mActiveConnectivityStatusIconController.getHotSpotIconDrawable());
    }

    @Test
    public void onUpdateStatus_wifiEnabled_hotspotEnabled_showsHotspotIcon() {
        mActiveConnectivityStatusIconController.setWifiIndicators(
                getWifiIndicator(/* enabled= */ true));
        mActiveConnectivityStatusIconController.setEthernetIndicators(
                getEthernetIndicator(/* enabled= */  false));
        mActiveConnectivityStatusIconController.onHotspotChanged(
                /* enabled= */ true, /* numDevices= */  0);

        // onUpdateStatus is called by the events above.

        assertThat(mActiveConnectivityStatusIconController.getIconDrawableToDisplay()).isEqualTo(
                mActiveConnectivityStatusIconController.getHotSpotIconDrawable());
    }

    @Test
    public void onUpdateStatus_wifiEnabled_hotspotEnabled_ethernetEnabled_showsHotspotIcon() {
        mActiveConnectivityStatusIconController.setWifiIndicators(
                getWifiIndicator(/* enabled= */ true));
        mActiveConnectivityStatusIconController.setEthernetIndicators(
                getEthernetIndicator(/* enabled= */ true));
        mActiveConnectivityStatusIconController.onHotspotChanged(
                /* enabled= */ true, /* numDevices= */  0);

        // onUpdateStatus is called by the events above.

        assertThat(mActiveConnectivityStatusIconController.getIconDrawableToDisplay()).isEqualTo(
                mActiveConnectivityStatusIconController.getHotSpotIconDrawable());
    }

    @Test
    public void onUpdateStatus_wifiEnabled_hotspotDisabled_ethernetEnabled_showsEthernetIcon() {
        mActiveConnectivityStatusIconController.setWifiIndicators(
                getWifiIndicator(/* enabled= */ true));
        mActiveConnectivityStatusIconController.setEthernetIndicators(
                getEthernetIndicator(/* enabled= */ true));
        mActiveConnectivityStatusIconController.onHotspotChanged(
                /* enabled= */ false, /* numDevices= */  0);

        // onUpdateStatus is called by the events above.

        assertThat(mActiveConnectivityStatusIconController.getIconDrawableToDisplay()).isEqualTo(
                mActiveConnectivityStatusIconController.getEthernetIconDrawable());
    }

    private WifiIndicators getWifiIndicator(boolean enabled) {
        IconState iconState = new IconState(true, android.R.drawable.sym_def_app_icon, "");
        return new WifiIndicators(enabled, iconState, null, false, false, "", false, "");
    }

    private IconState getEthernetIndicator(boolean enabled) {
        return new IconState(enabled, android.R.drawable.stat_sys_data_bluetooth, "");
    }
}
