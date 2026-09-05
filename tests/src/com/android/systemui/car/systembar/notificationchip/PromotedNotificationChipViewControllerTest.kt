/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.car.systembar.notificationchip

import android.graphics.drawable.Icon
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.view.View
import androidx.test.filters.SmallTest
import com.android.car.notification.CarNotificationListener
import com.android.car.notification.PromotedNotificationModel
import com.android.car.notification.PromotedNotificationsRepository
import com.android.systemui.CarSysuiTestCase
import com.android.systemui.car.CarSystemUiTest
import com.android.systemui.car.Flags.FLAG_PROMOTED_NOTIFICATIONS
import com.android.systemui.car.flags.Flag
import com.android.systemui.car.flags.FlagManager
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController
import com.android.systemui.car.notification.NotificationPanelViewController
import com.android.systemui.car.window.OverlayViewController
import com.android.systemui.graphics.ImageLoaderImpl
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@CarSystemUiTest
@RunWith(AndroidTestingRunner::class)
@TestableLooper.RunWithLooper
@EnableFlags(FLAG_PROMOTED_NOTIFICATIONS)
@SmallTest
class PromotedNotificationChipViewControllerTest : CarSysuiTestCase() {

    @Mock
    private lateinit var view: PromotedNotificationChipView
    @Mock
    private lateinit var disableController: CarSystemBarElementStatusBarDisableController
    @Mock
    private lateinit var stateController: CarSystemBarElementStateController
    @Mock
    private lateinit var flagManager: FlagManager
    @Mock
    private lateinit var carNotificationListener: CarNotificationListener
    @Mock
    private lateinit var notificationPanelViewController: NotificationPanelViewController

    @Mock
    private lateinit var icon: Icon

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val imageLoader = ImageLoaderImpl(context, testDispatcher)
    private lateinit var controller: PromotedNotificationChipViewController
    private lateinit var repository: PromotedNotificationsRepository

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        repository = PromotedNotificationsRepository.getInstance()
        repository.clearPromotedNotifications()

        controller = PromotedNotificationChipViewController(
            view,
            disableController,
            stateController,
            mContext,
            testScope,
            imageLoader,
            carNotificationListener,
            flagManager,
            notificationPanelViewController
        )
    }

    @After
    fun tearDown() {
        repository.clearPromotedNotifications()
    }

    @Test
    fun onPromotedNotificationAdded_showsChip() {
        `when`(flagManager.isEnabled(Flag.PromotedNotifications)).thenReturn(true)
        controller.onViewAttached()

        val model = PromotedNotificationModel(
            key = "key",
            isHeadsUp = false,
            postTime = 1000L,
            shortCriticalText = "Text",
            icon = icon,
            appName = "App"
        )

        repository.addPromotedNotification(model)
        testScope.testScheduler.advanceUntilIdle()

        verify(view).setPromotedInfo("Text", "App")
        verify(view).animateIn()
    }

    @Test
    fun onPromotedNotificationRemoved_hidesChip() {
        `when`(flagManager.isEnabled(Flag.PromotedNotifications)).thenReturn(true)
        controller.onViewAttached()
        Mockito.clearInvocations(view)

        val model = PromotedNotificationModel(
            key = "key",
            isHeadsUp = false,
            postTime = 1000L,
            shortCriticalText = "Text",
            icon = icon,
            appName = "App"
        )

        repository.addPromotedNotification(model)
        testScope.testScheduler.advanceUntilIdle()
        verify(view).setPromotedInfo("Text", "App")
        verify(view).animateIn()

        repository.removePromotedNotification("key")
        testScope.testScheduler.advanceUntilIdle()

        verify(view).animateOut()
    }

    @Test
    fun onChipClicked_showsHun() {
        `when`(flagManager.isEnabled(Flag.PromotedNotifications)).thenReturn(true)
        controller.onViewAttached()
        val model = PromotedNotificationModel(
            key = "key",
            isHeadsUp = false,
            postTime = 1000L,
            shortCriticalText = "Text",
            icon = icon,
            appName = "App"
        )
        repository.addPromotedNotification(model)
        testScope.testScheduler.advanceUntilIdle()

        val captor = ArgumentCaptor.forClass(View.OnClickListener::class.java)
        verify(view).setOnClickListener(captor.capture())
        captor.value.onClick(view)

        verify(carNotificationListener).showHunImmediately("key")
    }

    @Test
    fun onPanelExpanded_hidesChip() {
        `when`(flagManager.isEnabled(Flag.PromotedNotifications)).thenReturn(true)
        controller.onViewAttached()
        Mockito.clearInvocations(view)

        val model = PromotedNotificationModel(
            key = "key",
            isHeadsUp = false,
            postTime = 1000L,
            shortCriticalText = "Text",
            icon = icon,
            appName = "App"
        )
        repository.addPromotedNotification(model)
        testScope.testScheduler.advanceUntilIdle()
        verify(view).animateIn()

        // Simulate panel expansion
        val captor = ArgumentCaptor.forClass(
            OverlayViewController.OverlayViewStateListener::class.java
        )
        verify(notificationPanelViewController).registerViewStateListener(captor.capture())

        captor.value.onVisibilityChanged(true)
        testScope.testScheduler.advanceUntilIdle()

        verify(view).animateOut()
    }

    @Test
    fun onPanelCollapsed_withPromotedNotification_showsChip() {
        `when`(flagManager.isEnabled(Flag.PromotedNotifications)).thenReturn(true)
        controller.onViewAttached()
        Mockito.clearInvocations(view)

        val model = PromotedNotificationModel(
            key = "key",
            isHeadsUp = false,
            postTime = 1000L,
            shortCriticalText = "Text",
            icon = icon,
            appName = "App"
        )
        repository.addPromotedNotification(model)
        testScope.testScheduler.advanceUntilIdle()

        // Start with panel expanded and visible notification (so chip is hidden)
        val captor = ArgumentCaptor.forClass(
            OverlayViewController.OverlayViewStateListener::class.java
        )
        verify(notificationPanelViewController).registerViewStateListener(captor.capture())

        captor.value.onVisibilityChanged(true)
        testScope.testScheduler.advanceUntilIdle()

        verify(view).animateOut()

        Mockito.clearInvocations(view)

        // Now collapse panel
        captor.value.onVisibilityChanged(false)
        testScope.testScheduler.advanceUntilIdle()

        verify(view).animateIn()
    }

    @Test
    fun onPromotedNotificationAdded_isHeadsUp_doesNotShowChip() {
        `when`(flagManager.isEnabled(Flag.PromotedNotifications)).thenReturn(true)
        controller.onViewAttached()

        val model = PromotedNotificationModel(
            key = "key",
            isHeadsUp = true,
            postTime = 1000L,
            shortCriticalText = "Text",
            icon = icon,
            appName = "App"
        )

        repository.addPromotedNotification(model)
        testScope.testScheduler.advanceUntilIdle()

        verify(view, Mockito.never()).animateIn()
    }

    @Test
    fun onPromotedNotification_hunDismissed_showsChip() {
        `when`(flagManager.isEnabled(Flag.PromotedNotifications)).thenReturn(true)
        controller.onViewAttached()

        // Initial HUN
        val hunModel = PromotedNotificationModel(
            key = "key",
            isHeadsUp = true,
            postTime = 1000L,
            shortCriticalText = "Text",
            icon = icon,
            appName = "App"
        )
        repository.addPromotedNotification(hunModel)
        testScope.testScheduler.advanceUntilIdle()
        verify(view, Mockito.never()).animateIn()

        // Simulate HUN dismissal (isHeadsUp becomes false)
        val dismissedModel = PromotedNotificationModel(
            key = "key",
            isHeadsUp = false,
            postTime = 1000L,
            shortCriticalText = "Text",
            icon = icon,
            appName = "App"
        )
        repository.addPromotedNotification(dismissedModel)
        testScope.testScheduler.advanceUntilIdle()

        verify(view).animateIn()
    }

    @Test
    fun onNewHun_priorChipRemainsVisible() {
        `when`(flagManager.isEnabled(Flag.PromotedNotifications)).thenReturn(true)
        controller.onViewAttached()

        // Initial Promoted Notification (shows chip)
        val oldModel = PromotedNotificationModel(
            key = "old",
            isHeadsUp = false,
            postTime = 1000L,
            shortCriticalText = "Old",
            icon = icon,
            appName = "OldApp"
        )
        repository.addPromotedNotification(oldModel)
        testScope.testScheduler.advanceUntilIdle()
        verify(view).animateIn()
        Mockito.clearInvocations(view)

        // New HUN arrives
        val newHunModel = PromotedNotificationModel(
            key = "new",
            isHeadsUp = true,
            postTime = 2000L,
            shortCriticalText = "New",
            icon = icon,
            appName = "NewApp"
        )
        repository.addPromotedNotification(newHunModel)
        testScope.testScheduler.advanceUntilIdle()

        // Should NOT animate out (stays visible with old model)
        verify(view, Mockito.never()).animateOut()
        // Should NOT animate in again (no change in displayed notification)
        verify(view, Mockito.never()).animateIn()
    }
}
