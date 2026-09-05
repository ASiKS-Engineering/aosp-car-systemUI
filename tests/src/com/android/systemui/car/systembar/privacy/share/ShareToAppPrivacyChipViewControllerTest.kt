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

package com.android.systemui.car.systembar.privacy.share

import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.LayoutInflater
import androidx.test.filters.SmallTest
import com.android.internal.logging.InstanceId
import com.android.systemui.CarSysuiTestCase
import com.android.systemui.car.CarSystemUiTest
import com.android.systemui.car.Flags.FLAG_SHOW_MEDIA_PROJECTION_INDICATOR
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController
import com.android.systemui.car.systembar.privacy.share.R as ShareR
import com.android.systemui.statusbar.chips.sharetoapp.ui.viewmodel.ShareToAppChipViewModel
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import java.util.concurrent.Executor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

@CarSystemUiTest
@RunWith(AndroidTestingRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
@EnableFlags(FLAG_SHOW_MEDIA_PROJECTION_INDICATOR)
@RunWithLooper
@SmallTest
class ShareToAppPrivacyChipViewControllerTest : CarSysuiTestCase() {

    @get:Rule val setFlagsRule = SetFlagsRule()

    private val testScope = TestScope()
    private val chipModelFlow: MutableStateFlow<OngoingActivityChipModel> =
        MutableStateFlow(OngoingActivityChipModel.Inactive())
    private val instanceId = mock<InstanceId>()
    private val barElementDisableController = mock<CarSystemBarElementStatusBarDisableController>()
    private val barElementStateController = mock<CarSystemBarElementStateController>()
    private val shareToAppChipViewModel =
        mock<ShareToAppChipViewModel> { on { chip } doReturn chipModelFlow }
    private val executor = mock<Executor>()
    private val runnableArgumentCaptor = argumentCaptor<Runnable>()

    private lateinit var shareToAppPrivacyChip: ShareToAppPrivacyChip
    private lateinit var shareToAppPrivacyChipViewController: ShareToAppPrivacyChipViewController

    @Before
    fun setUp() {
        val context = spy(mContext).stub { on { mainExecutor } doReturn executor }
        shareToAppPrivacyChip =
            spy(
                LayoutInflater.from(mContext).inflate(
                        ShareR.layout.share_to_app_privacy_chip,
                                null)
                    as ShareToAppPrivacyChip
            )
        shareToAppPrivacyChipViewController =
            ShareToAppPrivacyChipViewController(
                shareToAppPrivacyChip,
                barElementDisableController,
                barElementStateController,
                context,
                testScope.backgroundScope,
                shareToAppChipViewModel,
            )
    }

    @Test
    fun onViewAttached_callsAnimateIn_whenStateIsActive() =
        testScope.runTest {
            shareToAppPrivacyChipViewController.onViewAttached()

            chipModelFlow.value = createActiveChipModel()
            runCurrent()
            verify(executor).execute(runnableArgumentCaptor.capture())
            runnableArgumentCaptor.firstValue.run()

            verify(shareToAppPrivacyChip).animateIn()
        }

    @Test
    fun onViewAttached_callsAnimateOut_whenStateIsInactive() =
        testScope.runTest {
            shareToAppPrivacyChipViewController.onViewAttached()

            chipModelFlow.value = OngoingActivityChipModel.Inactive()
            runCurrent()
            verify(executor).execute(runnableArgumentCaptor.capture())
            runnableArgumentCaptor.firstValue.run()

            verify(shareToAppPrivacyChip).animateOut()
        }

    @Test
    fun onViewDetached_stopsCollectingStatus() =
        testScope.runTest {
            shareToAppPrivacyChipViewController.onViewDetached()

            chipModelFlow.value = createActiveChipModel()
            runCurrent()

            verify(executor, never()).execute(any())
        }

    private fun createActiveChipModel() =
        OngoingActivityChipModel.Active(
            key = "ShareToApp",
            icon = null,
            content = OngoingActivityChipModel.Content.Text("ShareToApp"),
            colors = ColorsModel.Red,
            clickBehavior = OngoingActivityChipModel.ClickBehavior.None,
            instanceId = instanceId,
        )
}
