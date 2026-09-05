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

package com.android.systemui.car.wm.scalableui.view;

import static com.android.car.scalableui.loader.xml.parser.PanelControllerParser.EVENT_ID_TAG;
import static com.android.car.scalableui.loader.xml.parser.PanelControllerParser.ORIENTATION_TAG;
import static com.android.car.scalableui.loader.xml.parser.PanelControllerParser.SNAPTHREADHOLD_TAG;
import static com.android.car.scalableui.loader.xml.parser.PanelControllerParser.VIEW_TAG;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.PANEL_DRAG_DIRECTION_ID;
import static com.android.systemui.car.wm.scalableui.view.GripBarViewController.DRAG_DECREASE;
import static com.android.systemui.car.wm.scalableui.view.GripBarViewController.DRAG_INCREASE;
import static com.android.systemui.car.wm.scalableui.view.GripBarViewController.DRAG_NO_CHANGE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.testing.TestableLooper;
import android.view.MotionEvent;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.car.scalableui.model.BreakPoint;
import com.android.car.scalableui.model.Event;
import com.android.car.scalableui.model.KeyFrameEvent;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.wm.scalableui.EventDispatcher;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import javax.inject.Provider;

@CarSystemUiTest
@TestableLooper.RunWithLooper
@SmallTest
public class GripBarViewControllerTest extends CarSysuiTestCase {

    private static final String TEST_PANEL_ID = "test_panel_id";
    private static final String DRAG_EVENT_ID = "drag_event";
    private static final int GRIP_BAR_ID = 12345;

    private static final String BREAKPOINT_1_EVENT_ID = "breakpoint_1_event";
    private static final String BREAKPOINT_2_EVENT_ID = "breakpoint_2_event";

    private GripBarViewController mGripBarViewController;
    private GripBarBase mGripBar;

    @Mock
    private com.android.car.scalableui.model.PanelControllerMetadata mMetadata;
    @Mock
    private Map<Class<?>, Provider<android.view.View>> mDecorPanelViewMap;
    @Mock
    private Provider<View> mViewProvider;

    @Mock
    private EventDispatcher mEventDispatcher;
    @Captor
    private ArgumentCaptor<Event> mEventArgumentCaptor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mGripBar = new HorizontalGripBar(mContext, null);
        mGripBar.setId(GRIP_BAR_ID);

        BreakPoint breakPoint1 = new BreakPoint.Builder(0, BREAKPOINT_1_EVENT_ID).build();
        BreakPoint breakPoint2 = new BreakPoint.Builder(100, BREAKPOINT_2_EVENT_ID).build();
        List<BreakPoint> breakPoints = List.of(breakPoint1, breakPoint2);

        when(mMetadata.getBreakPoints()).thenReturn(breakPoints);
        when(mMetadata.getStringConfiguration(EVENT_ID_TAG)).thenReturn(DRAG_EVENT_ID);
        when(mMetadata.getStringConfiguration(ORIENTATION_TAG)).thenReturn("0");
        when(mMetadata.getStringConfiguration(SNAPTHREADHOLD_TAG)).thenReturn("5");

        when(mMetadata.getStringConfiguration(VIEW_TAG))
                .thenReturn("com.android.systemui.car.wm.scalableui.view.HorizontalGripBar");
        when(mDecorPanelViewMap.get(any())).thenReturn(mViewProvider);
        when(mViewProvider.get()).thenReturn(mGripBar);
    }

    @Test
    public void onTouch_lessThanTwoBreakPoints_throwsException() {
        // Arrange
        when(mMetadata.getBreakPoints()).thenReturn(List.of());

        // Act & Assert
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            mGripBarViewController = new GripBarViewController(TEST_PANEL_ID, mMetadata,
                    mDecorPanelViewMap, mEventDispatcher);
            mGripBarViewController.getView();
        });
        assertThat(thrown).hasMessageThat().contains("Invalid breakpoints: 0");
    }

    @Test
    public void onTouch_dragIncrease_dispatchesKeyFrameAndDirectionEvents() {
        mGripBarViewController = new GripBarViewController(TEST_PANEL_ID, mMetadata,
                mDecorPanelViewMap, mEventDispatcher);
        mGripBarViewController.getView();

        // Act
        mGripBar.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0));
        mGripBar.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 0, 60, 0));
        mGripBar.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0, 60, 0));

        // Assert
        verify(mEventDispatcher,
                org.mockito.Mockito.times(2)).executeEvent(mEventArgumentCaptor.capture());
        List<Event> dispatchedEvents = mEventArgumentCaptor.getAllValues();

        KeyFrameEvent keyFrameEvent = (KeyFrameEvent) dispatchedEvents.getFirst();
        assertThat(keyFrameEvent.getId()).isEqualTo(DRAG_EVENT_ID);
        assertThat(keyFrameEvent.getPanelId()).isEqualTo(TEST_PANEL_ID);
        assertThat(keyFrameEvent.getFraction()).isEqualTo(0.6f);
        assertThat(keyFrameEvent.getTokens().get(PANEL_DRAG_DIRECTION_ID))
                .isEqualTo(DRAG_INCREASE);

        Event directionEvent = dispatchedEvents.get(1);
        assertThat(directionEvent.getId()).isEqualTo(BREAKPOINT_2_EVENT_ID);
        assertThat(directionEvent.getPanelId()).isEqualTo(TEST_PANEL_ID);
        assertThat(directionEvent.getTokens().get(PANEL_DRAG_DIRECTION_ID))
                .isEqualTo(DRAG_INCREASE);
    }

    @Test
    public void onTouch_dragDecrease_dispatchesKeyFrameAndDirectionEvents() {
        mGripBarViewController = new GripBarViewController(TEST_PANEL_ID, mMetadata,
                mDecorPanelViewMap, mEventDispatcher);
        mGripBarViewController.getView();

        // Act
        mGripBar.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 100, 0));
        mGripBar.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 0, 20, 0));
        mGripBar.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0, 20, 0));

        // Assert
        verify(mEventDispatcher,
                org.mockito.Mockito.times(2)).executeEvent(mEventArgumentCaptor.capture());
        List<Event> dispatchedEvents = mEventArgumentCaptor.getAllValues();

        KeyFrameEvent keyFrameEvent = (KeyFrameEvent) dispatchedEvents.getFirst();
        assertThat(keyFrameEvent.getId()).isEqualTo(DRAG_EVENT_ID);
        assertThat(keyFrameEvent.getPanelId()).isEqualTo(TEST_PANEL_ID);
        assertThat(keyFrameEvent.getFraction()).isEqualTo(0.2f);
        assertThat(keyFrameEvent.getTokens().get(PANEL_DRAG_DIRECTION_ID))
                .isEqualTo(DRAG_DECREASE);

        Event directionEvent = dispatchedEvents.get(1);
        assertThat(directionEvent.getId()).isEqualTo(BREAKPOINT_1_EVENT_ID);
        assertThat(directionEvent.getPanelId()).isEqualTo(TEST_PANEL_ID);
        assertThat(directionEvent.getTokens().get(PANEL_DRAG_DIRECTION_ID))
                .isEqualTo(DRAG_DECREASE);
    }

    @Test
    public void onTouch_noChangeEvent_dispatchesNoChangeDirection() {
        mGripBarViewController = new GripBarViewController(TEST_PANEL_ID, mMetadata,
                mDecorPanelViewMap, mEventDispatcher);
        mGripBarViewController.getView();

        // Act
        mGripBar.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 10, 0));
        mGripBar.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 0, 60, 0));
        mGripBar.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0, 10, 0));

        // Assert
        verify(mEventDispatcher,
                org.mockito.Mockito.times(2)).executeEvent(mEventArgumentCaptor.capture());
        List<Event> dispatchedEvents = mEventArgumentCaptor.getAllValues();

        KeyFrameEvent keyFrameEvent = (KeyFrameEvent) dispatchedEvents.getFirst();
        assertThat(keyFrameEvent.getId()).isEqualTo(DRAG_EVENT_ID);
        assertThat(keyFrameEvent.getPanelId()).isEqualTo(TEST_PANEL_ID);
        assertThat(keyFrameEvent.getFraction()).isEqualTo(0.6f);
        assertThat(keyFrameEvent.getTokens().get(PANEL_DRAG_DIRECTION_ID))
                .isEqualTo(DRAG_INCREASE);

        Event directionEvent = dispatchedEvents.get(1);
        assertThat(directionEvent.getId()).isEqualTo(BREAKPOINT_1_EVENT_ID);
        assertThat(directionEvent.getPanelId()).isEqualTo(TEST_PANEL_ID);
        assertThat(directionEvent.getTokens().get(PANEL_DRAG_DIRECTION_ID))
                .isEqualTo(DRAG_NO_CHANGE);
    }

    @Test
    public void onTouch_clickEvent_dispatchesBreakpointEvent() {
        mGripBarViewController = new GripBarViewController(TEST_PANEL_ID, mMetadata,
                mDecorPanelViewMap, mEventDispatcher);
        mGripBarViewController.getView();

        // Act
        mGripBar.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 10, 0));
        mGripBar.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0, 10, 0));

        // Assert
        verify(mEventDispatcher).executeEvent(mEventArgumentCaptor.capture());
        Event directionEvent = mEventArgumentCaptor.getValue();
        assertThat(directionEvent.getId()).isEqualTo(BREAKPOINT_1_EVENT_ID);
        assertThat(directionEvent.getPanelId()).isEqualTo(TEST_PANEL_ID);
        assertThat(directionEvent.getTokens().get(PANEL_DRAG_DIRECTION_ID)).isNull();
    }
}
