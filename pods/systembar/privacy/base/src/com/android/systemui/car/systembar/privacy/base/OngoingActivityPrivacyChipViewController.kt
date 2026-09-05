/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.car.systembar.privacy.base

import android.content.Context
import android.os.Build
import android.util.Log
import com.android.systemui.car.Flags.showMediaProjectionIndicator
import com.android.systemui.car.flexibleui.CarSystemBarElementController
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Controls OngoingActivity Privacy Chip view in system icons. */
public abstract class OngoingActivityPrivacyChipViewController(
    view: PrivacyChip,
    disableController: CarSystemBarElementStatusBarDisableController,
    stateController: CarSystemBarElementStateController,
    private val context: Context,
    private val scope: CoroutineScope,
    private val ongoingActivityChipViewModel: OngoingActivityChipViewModel,
) : CarSystemBarElementController<PrivacyChip>(view, disableController, stateController) {

    private var job: Job? = null

    override fun onViewAttached() {
        super.onViewAttached()

        job?.cancel()
        job =
            scope.launch {
                ongoingActivityChipViewModel.chip.collect { ongoingActivityChipModel ->
                    setChipVisibility(ongoingActivityChipModel is OngoingActivityChipModel.Active)
                }
            }
    }

    override fun onViewDetached() {
        super.onViewDetached()

        job?.cancel()
        job = null
    }

    private fun setChipVisibility(chipVisible: Boolean) {
        if (!showMediaProjectionIndicator()) {
            if (DEBUG) {
                Log.d(TAG, "ShowMediaProjectionIndicator flag is not enabled.")
                return
            }
        }
        // Since this is launched using a callback thread, its UI based elements need
        // to execute on main executor.
        context.mainExecutor.execute {
            if (DEBUG) {
                Log.d(TAG, "Change chipVisibility to " + chipVisible)
            }
            if (chipVisible) {
                mView.animateIn()
            } else {
                mView.animateOut()
            }
        }
    }

    private companion object {
        val DEBUG = Build.IS_ENG || Build.IS_USERDEBUG
        val TAG = OngoingActivityPrivacyChipViewController::class.java.simpleName
    }
}
