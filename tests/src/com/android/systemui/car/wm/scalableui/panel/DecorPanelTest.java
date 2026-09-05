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
package com.android.systemui.car.wm.scalableui.panel;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.car.scalableui.model.Role;
import com.android.car.scalableui.panel.PanelUpdatePublisher;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.wm.scalableui.panel.controller.PanelControllerInitializer;
import com.android.wm.shell.automotive.AutoDecor;
import com.android.wm.shell.automotive.AutoDecorManager;
import com.android.wm.shell.automotive.AutoSurfaceTransaction;
import com.android.wm.shell.automotive.AutoSurfaceTransactionFactory;
import com.android.wm.shell.common.ShellExecutor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@SmallTest
public class DecorPanelTest extends CarSysuiTestCase {

    private static final String TEST_PANEL_ID = "testPanel";
    private static final int TEST_LAYER = 1;
    private static final String TEST_PANEL_ID_NAME = "DecorName";
    private static final int TEST_DISPLAY_ID = 0;

    // --- Mocks for Dependencies ---
    @Mock
    private Context mMockContext;
    @Mock
    private Resources mResources;
    @Mock
    private AutoDecorManager mAutoDecorManager;
    @Mock
    private PanelUtils mPanelUtils;
    @Mock
    private PanelControllerInitializer mPanelControllerInitializer;
    @Mock
    private ShellExecutor mShellMainExecutor;
    @Mock
    private ShellExecutor mMainExecutor;
    @Mock
    private ShellExecutor mShellBgExecutor;
    @Mock
    private AutoDecor mMockExistingAutoDecor;
    @Mock
    private AutoDecor mMockNewAutoDecor;
    @Mock
    private View mMockDecorView;
    @Mock
    private Rect mMockBounds;
    @Mock
    private AutoDecor mAutoDecor;
    @Mock
    private Role mRole;
    @Mock
    private AutoSurfaceTransactionFactory mAutoSurfaceTransactionFactory;
    @Mock
    private AutoSurfaceTransaction mAutoSurfaceTransaction;
    @Mock
    private PanelUpdatePublisher mPanelUpdatePublisher;

    // --- Captors ---
    @Captor
    private ArgumentCaptor<Runnable> mRunnableArgumentCaptor;

    // --- Class Under Test (using @Spy) ---
    private DecorPanel mDecorPanel;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        // Manual Spy initialization is safer with complex/assisted constructors
        mDecorPanel = spy(new DecorPanel(
                mMockContext,
                mAutoDecorManager,
                mPanelUtils,
                mPanelControllerInitializer,
                mMainExecutor,
                mShellMainExecutor,
                mShellBgExecutor,
                mAutoSurfaceTransactionFactory,
                Optional.of(mPanelUpdatePublisher),
                TEST_PANEL_ID
        ));

        doReturn(mResources).when(mMockContext).getResources();
        doReturn(mMockDecorView).when(mRole).getView(any());
        doReturn(mRole).when(mDecorPanel).getRole();

        // --- Handle Executor ---
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            if (runnable != null) {
                runnable.run();
            }
            return null;
        }).when(mShellMainExecutor).execute(any(Runnable.class));
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            if (runnable != null) {
                runnable.run();
            }
            return null;
        }).when(mMainExecutor).execute(any(Runnable.class));
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            if (runnable != null) {
                runnable.run();
            }
            return null;
        }).when(mShellBgExecutor).execute(any(Runnable.class));
        // --- Stub SysUIPanel methods (called via spy) ---
        doReturn(TEST_LAYER).when(mDecorPanel).getLayer();
        doReturn(mMockBounds).when(mDecorPanel).getBounds();
        doReturn(TEST_PANEL_ID_NAME).when(mDecorPanel).getPanelId();
        doReturn(TEST_DISPLAY_ID).when(mDecorPanel).getDisplayId();

        // --- Stub AutoDecorManager ---
        when(mAutoDecorManager.createAutoDecor(any(), anyInt(), any(), any()))
                .thenReturn(mMockNewAutoDecor);

        when(mAutoSurfaceTransactionFactory.createTransaction(any())).thenReturn(
                mAutoSurfaceTransaction);
    }

    // --- Tests for init() ---
    @Test
    public void init_whenUserIsUnlocked_callsReset() {
        when(mPanelUtils.isUserUnlocked()).thenReturn(true);

        mDecorPanel.init();

        verify(mDecorPanel).reset();
        verify(mShellMainExecutor).execute(any(Runnable.class));
    }

    @Test
    public void init_whenUserIsLocked_doesNotCallReset() {
        when(mPanelUtils.isUserUnlocked()).thenReturn(false);

        mDecorPanel.init();

        verify(mDecorPanel, never()).reset();
        verify(mShellMainExecutor, never()).execute(any(Runnable.class));
    }

    // --- Tests for reset() ---

    @Test
    public void reset_whenNoExistingDecor_andInflateSucceeds_createsAndAttachesNewDecor() {
        mDecorPanel.mAutoDecor = null;
        when(mAutoDecorManager.createAutoDecor(any(), anyInt(), any(), anyString())).thenReturn(
                mAutoDecor);

        mDecorPanel.reset();

        verify(mShellMainExecutor).execute(mRunnableArgumentCaptor.capture());
        verify(mAutoDecorManager, never()).removeAutoDecor(any());
        verify(mDecorPanel).inflateDecorView();
        verify(mAutoDecorManager).createAutoDecor(
                eq(mMockDecorView),
                eq(TEST_LAYER),
                eq(mMockBounds),
                eq(TEST_PANEL_ID_NAME));
        verify(mAutoDecorManager).attachAutoDecorToDisplay(
                eq(mAutoDecor),
                eq(TEST_DISPLAY_ID));
        assertEquals(mAutoDecor, mDecorPanel.getAutoDecor());
    }

    @Test
    public void reset_whenExistingDecor_andInflateSucceeds_removesOldCreatesAndAttachesNewDecor() {
        mDecorPanel.mAutoDecor = mMockExistingAutoDecor;
        when(mAutoDecorManager.createAutoDecor(any(), anyInt(), any(), anyString())).thenReturn(
                mAutoDecor);

        mDecorPanel.reset();

        verify(mShellMainExecutor).execute(mRunnableArgumentCaptor.capture());
        verify(mAutoDecorManager).removeAutoDecor(mMockExistingAutoDecor);
        verify(mDecorPanel).inflateDecorView();
        verify(mAutoDecorManager).createAutoDecor(eq(mMockDecorView), eq(TEST_LAYER),
                eq(mMockBounds), eq(TEST_PANEL_ID_NAME));
        verify(mAutoDecorManager).attachAutoDecorToDisplay(eq(mAutoDecor),
                eq(TEST_DISPLAY_ID));
        assertEquals(mAutoDecor, mDecorPanel.getAutoDecor());
    }
}
