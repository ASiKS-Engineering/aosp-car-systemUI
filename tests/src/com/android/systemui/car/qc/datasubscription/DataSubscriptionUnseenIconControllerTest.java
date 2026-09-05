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

package com.android.systemui.car.qc.datasubscription;

import static com.android.car.datasubscription.Flags.FLAG_DATA_SUBSCRIPTION_POP_UP;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.car.datasubscription.DataSubscription;
import com.android.car.datasubscription.DataSubscriptionConfig;
import com.android.car.datasubscription.DataSubscriptionConfig.DataSubscriptionStatusType;
import com.android.car.datasubscription.DataSubscriptionConfigParser;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController;
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.flexibleui.layout.CarSystemBarImageView;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class DataSubscriptionUnseenIconControllerTest extends CarSysuiTestCase {
    private DataSubscriptionUnseenIconController mController;
    @Mock
    private CarSystemBarImageView mView;
    @Mock
    private CarSystemBarElementStatusBarDisableController mDisableController;
    @Mock
    private CarSystemBarElementStateController mStateController;
    @Mock
    private DataSubscription mDataSubscription;
    @Mock
    private Resources mResources;
    @Mock
    private Context mContext;
    @Mock
    private XmlResourceParser mParser;
    private MockitoSession mMockingSession;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    private Map<Integer, DataSubscriptionConfig> mConfigData = new HashMap<>();

    @Before
    public void setUp() {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .mockStatic(DataSubscriptionConfigParser.class)
                .strictness(Strictness.WARN)
                .startMocking();
        DataSubscriptionConfig config1 = new DataSubscriptionConfig(
                DataSubscriptionStatusType.INACTIVE,
                true,
                "Proactive A", "Reactive A");
        DataSubscriptionConfig config2 = new DataSubscriptionConfig(
                DataSubscriptionStatusType.TRIAL,
                true,
                "Proactive B", "Reactive B");
        DataSubscriptionConfig config3 = new DataSubscriptionConfig(
                DataSubscriptionStatusType.PAID,
                true,
                "", "");
        DataSubscriptionConfig config4 = new DataSubscriptionConfig(
                DataSubscriptionStatusType.EXPIRING,
                true,
                "", "");

        mConfigData.put(1, config1);
        mConfigData.put(2, config2);
        mConfigData.put(3, config3);
        mConfigData.put(4, config4);


        when(mView.getContext()).thenReturn(mContext);
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getXml(anyInt())).thenReturn(mParser);

        doReturn(mConfigData).when(() -> DataSubscriptionConfigParser.loadConfig(any()));

        mController = new DataSubscriptionUnseenIconController(mView,
                mDisableController, mStateController);
        mController.setSubscription(mDataSubscription);
    }

    @After
    public void tearDown() {
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    @RequiresFlagsEnabled(FLAG_DATA_SUBSCRIPTION_POP_UP)
    @Test
    public void onViewAttached_registerListener() {
        when(mDataSubscription.isDataSubscriptionInactive()).thenReturn(true);

        mController.onViewAttached();

        verify(mDataSubscription).addDataSubscriptionListener(any());
    }

    @RequiresFlagsEnabled(FLAG_DATA_SUBSCRIPTION_POP_UP)
    @Test
    public void onViewDetached_UnregisterListener() {
        when(mDataSubscription.isDataSubscriptionInactive()).thenReturn(true);

        mController.onViewDetached();

        verify(mDataSubscription).removeDataSubscriptionListener();
    }
}
