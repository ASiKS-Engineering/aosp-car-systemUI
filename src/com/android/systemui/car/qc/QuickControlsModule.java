/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.car.qc;

import com.android.systemui.car.qc.base.QCBaseModule;
import com.android.systemui.car.qc.base.SystemUIQCViewController;
import com.android.systemui.car.qc.footer.base.QCFooterBaseModule;
import com.android.systemui.car.qc.footer.logout.QCLogoutModule;
import com.android.systemui.car.qc.footer.screenoff.QCScreenOffModule;
import com.android.systemui.car.qc.footer.userpicker.QCUserPickerModule;
import com.android.systemui.car.qc.profileswitcher.QCProfileSwitcherModule;
import com.android.systemui.car.systembar.privacy.camera.CameraQcPanelModule;
import com.android.systemui.car.systembar.privacy.mic.MicQcPanelModule;

import dagger.Module;

/**
 * Dagger injection module for {@link SystemUIQCViewController}
 */
@Module(includes = {CameraQcPanelModule.class,
        MicQcPanelModule.class,
        QCBaseModule.class,
        QCFooterBaseModule.class,
        QCLogoutModule.class,
        QCProfileSwitcherModule.class,
        QCScreenOffModule.class,
        QCUserPickerModule.class})
public abstract class QuickControlsModule {}
