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

package com.android.systemui.car.wm.scalableui.panel.controller;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.testing.TestableLooper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.flags.Flag;
import com.android.systemui.car.flags.FlagManager;
import com.android.systemui.car.wm.CarWMUserHelper;
import com.android.systemui.car.wm.scalableui.panel.PanelUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper
@SmallTest
public class SetupPanelControllerTest extends CarSysuiTestCase {

    private static final String PANEL_ID = "test_panel";
    private static final int TEST_USER_ID = 1000;
    private static final String SUW_PACKAGE = "com.android.car.test.setupwizard";

    private SetupPanelController mController;

    @Mock
    private Context mMockContext;
    @Mock
    private PanelControllerMetadata mMetadata;
    @Mock
    private PanelUtils mPanelUtils;
    @Mock
    private CarWMUserHelper mUserHelper;
    @Mock
    private FlagManager mFlagManager;
    @Mock
    private PackageManager mPackageManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mFlagManager.isEnabled(Flag.ScalableUiNoSuwHome)).thenReturn(true);
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = SUW_PACKAGE;
        when(mPackageManager.resolveActivityAsUser(any(), anyInt(), anyInt())).thenReturn(info);
        when(mMockContext.getPackageManager()).thenReturn(mPackageManager);
        when(mUserHelper.getUserIdForDisplay(anyInt())).thenReturn(TEST_USER_ID);

        mController = spy(new SetupPanelController(mMockContext, PANEL_ID, mMetadata, mPanelUtils,
                mUserHelper, mFlagManager));
        mController.init();
    }

    @Test
    public void panelBecomesInvisible_userSetupInProgress_sendsHomeIntent() {
        doReturn(true).when(mController).isUserSetupInProgress();
        // set panel as previously visible
        mController.onPanelVisibilityChanged(true);

        // set panel as invisible
        mController.onPanelVisibilityChanged(false);

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext).startActivityAsUser(captor.capture(), eq(UserHandle.of(TEST_USER_ID)));
        assertThat(captor.getValue().hasCategory(Intent.CATEGORY_HOME)).isTrue();
        assertThat(captor.getValue().getPackage()).isEqualTo(SUW_PACKAGE);
    }

    @Test
    public void panelBecomesInvisible_userSetupNotInProgress_doNothing() {
        doReturn(false).when(mController).isUserSetupInProgress();
        // set panel as previously visible
        mController.onPanelVisibilityChanged(true);

        // set panel as invisible
        mController.onPanelVisibilityChanged(false);

        verify(mMockContext, never()).startActivityAsUser(any(), any());
    }
}
