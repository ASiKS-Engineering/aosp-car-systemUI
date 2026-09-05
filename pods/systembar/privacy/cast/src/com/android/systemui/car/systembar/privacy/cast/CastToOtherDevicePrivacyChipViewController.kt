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

package com.android.systemui.car.systembar.privacy.cast

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.android.systemui.car.flexibleui.CarSystemBarElementController
import com.android.systemui.car.flexibleui.CarSystemBarElementStateController
import com.android.systemui.car.flexibleui.CarSystemBarElementStatusBarDisableController
import com.android.systemui.car.systembar.privacy.base.OngoingActivityPrivacyChipViewController
import com.android.systemui.car.systembar.privacy.base.PrivacyChip
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.statusbar.chips.casttootherdevice.ui.viewmodel.CastToOtherDeviceChipViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope

/** Controls a CastToOther Privacy Chip view in system icons. */
class CastToOtherDevicePrivacyChipViewController
@AssistedInject
constructor(
    @Assisted view: PrivacyChip,
    disableController: CarSystemBarElementStatusBarDisableController,
    stateController: CarSystemBarElementStateController,
    private val context: Context,
    @Application private val scope: CoroutineScope,
    private val castToOtherDeviceChipViewModel: CastToOtherDeviceChipViewModel,
) :
    OngoingActivityPrivacyChipViewController(
        view,
        disableController,
        stateController,
        context,
        scope,
        castToOtherDeviceChipViewModel,
    ) {

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public override fun onViewAttached() {
        super.onViewAttached()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public override fun onViewDetached() {
        super.onViewDetached()
    }

    @AssistedFactory
    interface Factory :
        CarSystemBarElementController.Factory<PrivacyChip,
                CastToOtherDevicePrivacyChipViewController>
}
