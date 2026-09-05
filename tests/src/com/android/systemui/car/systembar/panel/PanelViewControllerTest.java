/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.car.systembar.panel;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserHandle;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.test.filters.SmallTest;

import com.android.car.qc.QCItem;
import com.android.car.ui.FocusParkingView;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.shared.R;
import com.android.systemui.car.systembar.base.element.CarSystemBarElementInitializer;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.ConfigurationController;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class PanelViewControllerTest extends CarSysuiTestCase {
    private PanelViewController mViewController;
    private ImageView mAnchorView;
    private UserHandle mUserHandle;

    @Mock
    private UserTracker mUserTracker;
    @Mock
    private BroadcastDispatcher mBroadcastDispatcher;
    @Mock
    private ConfigurationController mConfigurationController;
    @Mock
    private CarDeviceProvisionedController mDeviceProvisionedController;
    @Mock
    private CarSystemBarElementInitializer mCarSystemBarElementInitializer;
    @Mock
    private PanelContentProvider mPanelContentProvider;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = spy(mContext);
        mUserHandle = UserHandle.of(1000);
        when(mUserTracker.getUserHandle()).thenReturn(mUserHandle);

        mAnchorView = spy(new ImageView(mContext));
        mAnchorView.setImageDrawable(mContext.getResources().getDrawable(
                com.android.systemui.car.statusicon.bluetooth.R.drawable.ic_bluetooth_status_off,
                mContext.getTheme()));
        mAnchorView.setColorFilter(mContext.getResources().getColor(
                R.color.car_status_icon_color, mContext.getTheme()));

        ViewGroup qcDisplayPanel = (ViewGroup) LayoutInflater.from(getContext()).inflate(
                R.layout.qc_display_panel, /* root= */ null);
        when(mPanelContentProvider.createPanelContentView(any())).thenReturn(qcDisplayPanel);
        int panelWidth = mContext.getResources().getDimensionPixelSize(
                com.android.systemui.car.systembar.panel.R.dimen
                        .car_status_icon_panel_default_width);
        when(mPanelContentProvider.getPanelWidthPx()).thenReturn(panelWidth);
        when(mPanelContentProvider.getShowAsDropDown()).thenReturn(true);

        mViewController = new PanelViewController(mContext, mUserTracker,
                mBroadcastDispatcher, mConfigurationController, mDeviceProvisionedController,
                mCarSystemBarElementInitializer, mAnchorView, mPanelContentProvider);
        spyOn(mViewController);
        reset(mAnchorView);
        mViewController.init();
    }

    @After
    public void tearDown() {
        // The view controller must be detached before we move on since it creates a real
        // PopupWindow instance. Otherwise, the window will continue to be present and cause
        // instability in future tests.
        mViewController.onViewDetached();
    }

    @Test
    public void onViewAttached_registersListeners() {
        mViewController.onViewAttached();
        verify(mBroadcastDispatcher).registerReceiver(any(), any(), any(), any());
        verify(mUserTracker).addCallback(any(), any());
        verify(mConfigurationController).addCallback(any());
    }

    @Test
    public void onViewDetached_unregistersListeners() {
        mViewController.onViewDetached();
        verify(mConfigurationController).removeCallback(any());
        verify(mUserTracker).removeCallback(any());
        verify(mBroadcastDispatcher).unregisterReceiver(any());
        assertThat(mViewController.getPanel()).isNull();
    }

    @Test
    public void onPanelAnchorViewClicked_panelShowing() {
        clickAnchorView();
        waitForIdleSync();

        assertThat(mViewController.getPanel().isShowing()).isTrue();
    }

    @Test
    public void onPanelAnchorViewClicked_panelShowing_panelDismissed() {
        clickAnchorView();

        clickAnchorView();
        waitForIdleSync();

        assertThat(mViewController.getPanel().isShowing()).isFalse();
    }

    @Test
    public void onDismissSystemDialogReceived_fromSelf_panelOpen_doesNotDismissPanel() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        intent.setIdentifier(mViewController.getIdentifier());
        clickAnchorView();
        waitForIdleSync();

        mViewController.getBroadcastReceiver().onReceive(mContext, intent);

        assertThat(mViewController.getPanel().isShowing()).isTrue();
    }

    @Test
    public void onDismissSystemDialogReceived_notFromSelf_panelOpen_dismissesPanel() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        clickAnchorView();
        waitForIdleSync();

        mViewController.getBroadcastReceiver().onReceive(mContext, intent);

        assertThat(mViewController.getPanel().isShowing()).isFalse();
    }

    @Test
    public void onLayoutDirectionChanged_recreatePanel() {
        mViewController.getConfigurationListener()
                .onLayoutDirectionChanged(/* isLayoutRtl= */ true);

        assertThat(mViewController.getPanel()).isNotNull();
    }

    @Test
    public void onUserChanged_unregisterRegisterReceiver() {
        int newUser = 999;
        Context userContext = mock(Context.class);
        reset(mBroadcastDispatcher);

        mViewController.getUserTrackerCallback()
                .onUserChanged(newUser, userContext);

        verify(mBroadcastDispatcher).unregisterReceiver(
                eq(mViewController.getBroadcastReceiver()));
        verify(mBroadcastDispatcher).registerReceiver(
                eq(mViewController.getBroadcastReceiver()),
                any(IntentFilter.class), eq(null), eq(mUserHandle));
    }

    @Test
    public void onGlobalFocusChanged_panelShowing_panelDismissed() {
        FocusParkingView newFocusView = mock(FocusParkingView.class);
        clickAnchorView();
        waitForIdleSync();

        mViewController.getFocusChangeListener()
                .onGlobalFocusChanged(mAnchorView, newFocusView);

        assertThat(mViewController.getPanel().isShowing()).isFalse();
    }

    @Test
    public void onQCAction_pendingIntentAction_panelDismissed() {
        QCItem qcItem = mock(QCItem.class);
        PendingIntent action = mock(PendingIntent.class);
        when(action.isActivity()).thenReturn(true);
        clickAnchorView();
        waitForIdleSync();

        mViewController.getQCActionListener().onQCAction(qcItem, action);

        assertThat(mViewController.getPanel().isShowing()).isFalse();
    }

    @Test
    public void onQCAction_actionHandler_panelDismissed() {
        QCItem qcItem = mock(QCItem.class);
        QCItem.ActionHandler action = mock(QCItem.ActionHandler.class);
        when(action.isActivity()).thenReturn(true);
        clickAnchorView();
        waitForIdleSync();

        mViewController.getQCActionListener().onQCAction(qcItem, action);

        assertThat(mViewController.getPanel().isShowing()).isFalse();
    }

    @Test
    public void onPanelAnchorViewClicked_setsFitInsetsTypesToZero() {
        clickAnchorView();
        waitForIdleSync();

        View container = mViewController.getPanel().getContentView().getRootView();
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) container.getLayoutParams();

        assertThat(lp.getFitInsetsTypes()).isEqualTo(0);
    }

    private void clickAnchorView() {
        mViewController.getOnClickListener().onClick(mAnchorView);
    }
}
