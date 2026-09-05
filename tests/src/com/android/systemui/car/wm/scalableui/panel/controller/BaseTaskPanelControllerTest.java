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

import static com.android.car.scalableui.loader.xml.parser.PanelControllerParser.DEFAULT_COMPONENT_TAG;
import static com.android.car.scalableui.loader.xml.parser.PanelControllerParser.PERSISTENT_ACTIVITY_TAG;
import static com.android.car.scalableui.loader.xml.parser.PanelControllerParser.UPDATABLE_INTENT_FILTER_TAG;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.car.scalableui.panel.TaskPanelHandler;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.wm.scalableui.panel.PanelUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@SmallTest
public class BaseTaskPanelControllerTest extends CarSysuiTestCase {
    private static final String TEST_PANEL_ID = "TEST_PANEL";
    private static final ComponentName DEFAULT_ACTIVITY = new ComponentName("com.example",
            "DefaultActivity");
    private static final ComponentName ACTIVITY_1 = new ComponentName("com.test",
            "Activity1");
    private static final ComponentName ACTIVITY_2 = new ComponentName("com.test", "Activity2");

    @Mock
    private Context mMockContext;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private TaskPanelHandler mTaskPanelHandler;
    @Mock
    private PanelControllerMetadata mPanelControllerMetadata;
    @Mock
    private PanelUtils mPanelUtils;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mMockContext.getPackageManager()).thenReturn(mPackageManager);
    }

    @Test
    public void constructor_parsesDefaultComponent() {
        when(mPanelControllerMetadata.getStringConfiguration(
                DEFAULT_COMPONENT_TAG)).thenReturn(
                DEFAULT_ACTIVITY.flattenToString());
        BaseTaskPanelController controller = createBaseTaskPanelController();
        Intent defaultIntent = controller.getDefaultComponent();
        assertNotNull(defaultIntent);
        assertEquals(DEFAULT_ACTIVITY, defaultIntent.getComponent());
    }

    @Test
    public void constructor_noDefaultComponent() {
        when(mPanelControllerMetadata.getStringConfiguration(
                DEFAULT_COMPONENT_TAG)).thenReturn(null);
        BaseTaskPanelController controller = createBaseTaskPanelController();
        Intent defaultIntent = controller.getDefaultComponent();
        assertNull(defaultIntent);
    }

    @Test
    public void constructor_parsesPersistentActivities() {
        when(mPanelControllerMetadata.getListConfiguration(
                PERSISTENT_ACTIVITY_TAG)).thenReturn(
                List.of(ACTIVITY_1.flattenToString(), ACTIVITY_2.flattenToString()));
        when(mPanelControllerMetadata.hasConfiguration(
                PERSISTENT_ACTIVITY_TAG)).thenReturn(true);
        BaseTaskPanelController controller = createBaseTaskPanelController();
        Set<ComponentName> persistentActivities = controller.getPersistentActivities();
        assertEquals(2, persistentActivities.size());
        assertTrue(persistentActivities.contains(ACTIVITY_1));
        assertTrue(persistentActivities.contains(ACTIVITY_2));
    }

    @Test
    public void constructor_noPersistentActivities() {
        when(mPanelControllerMetadata.getStringConfiguration(
                PERSISTENT_ACTIVITY_TAG)).thenReturn(null);
        BaseTaskPanelController controller = createBaseTaskPanelController();
        Set<ComponentName> persistentActivities = controller.getPersistentActivities();
        assertTrue(persistentActivities.isEmpty());
    }

    @Test
    public void constructor_registersReceiverForUpdateFilter() throws URISyntaxException {
        String updateFilterString = "android-app://com.example.package/path";
        when(mPanelControllerMetadata.getStringConfiguration(
                UPDATABLE_INTENT_FILTER_TAG)).thenReturn(updateFilterString);

        createBaseTaskPanelController();

        ArgumentCaptor<BroadcastReceiver> receiverCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        ArgumentCaptor<IntentFilter> filterCaptor = ArgumentCaptor.forClass(IntentFilter.class);

        verify(mMockContext).registerReceiverAsUser(receiverCaptor.capture(), eq(UserHandle.ALL),
                filterCaptor.capture(), isNull(), isNull(),
                eq(Context.RECEIVER_EXPORTED));

        IntentFilter filter = filterCaptor.getValue();
        assertEquals(4, filter.countActions());
        assertTrue(filter.hasAction(Intent.ACTION_PACKAGE_ADDED));
        assertTrue(filter.hasAction(Intent.ACTION_PACKAGE_REMOVED));
        assertTrue(filter.hasAction(Intent.ACTION_PACKAGE_CHANGED));
        assertTrue(filter.hasAction(Intent.ACTION_PACKAGE_REPLACED));
        assertEquals("package", filter.getDataScheme(0));
    }

    @Test
    public void constructor_noUpdateFilter_doesNotRegisterReceiver() {
        when(mPanelControllerMetadata.getStringConfiguration(
                UPDATABLE_INTENT_FILTER_TAG)).thenReturn(null);

        createBaseTaskPanelController();

        verify(mMockContext, never()).registerReceiver(any(BroadcastReceiver.class),
                any(IntentFilter.class), eq(Context.RECEIVER_EXPORTED));
    }

    @Test
    public void updatePersistentActivities_addsStaticPersistentActivities() {
        mPanelControllerMetadata = PanelControllerMetadata.builder("id").addConfiguration(
                PERSISTENT_ACTIVITY_TAG,
                ACTIVITY_1.flattenToString()).build();
        BaseTaskPanelController controller = createBaseTaskPanelController();
        controller.registerTaskPanelHandler(mTaskPanelHandler);
        // Trigger update (e.g., after package event)
        controller.updatePersistentActivities();
        Set<ComponentName> updatedPersistent = controller.getPersistentActivities();
        assertEquals(1, updatedPersistent.size());
        assertTrue(updatedPersistent.contains(ACTIVITY_1));
        verify(mTaskPanelHandler, times(1)).onApplicationChanged();
    }

    @Test
    public void handles_returnsTrueIfPersistent() {
        mPanelControllerMetadata = PanelControllerMetadata.builder("id").addConfiguration(
                PERSISTENT_ACTIVITY_TAG,
                ACTIVITY_1.flattenToString()).build();
        BaseTaskPanelController controller = createBaseTaskPanelController();
        assertTrue(controller.handles(ACTIVITY_1));
    }

    @Test
    public void handles_returnsFalseIfNotPersistent() {
        mPanelControllerMetadata = PanelControllerMetadata.builder("id").addConfiguration(
                PERSISTENT_ACTIVITY_TAG,
                ACTIVITY_1.flattenToString()).build();

        BaseTaskPanelController controller = createBaseTaskPanelController();
        assertFalse(controller.handles(ACTIVITY_2));
    }

    @Test
    public void getDefaultComponent_returnsIntentWithDefaultComponent() {
        when(mPanelControllerMetadata.getStringConfiguration(
                DEFAULT_COMPONENT_TAG)).thenReturn(
                DEFAULT_ACTIVITY.flattenToString());
        BaseTaskPanelController controller = createBaseTaskPanelController();
        Intent intent = controller.getDefaultComponent();
        assertNotNull(intent);
        assertEquals(DEFAULT_ACTIVITY, intent.getComponent());
    }

    private BaseTaskPanelController createBaseTaskPanelController() {
        BaseTaskPanelController controller = new BaseTaskPanelController(mMockContext,
                TEST_PANEL_ID, mPanelControllerMetadata, mPanelUtils);
        controller.init();
        return controller;
    }
}
