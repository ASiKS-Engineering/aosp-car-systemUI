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
package com.android.systemui.car.systembar.base

import android.annotation.IdRes
import android.annotation.LayoutRes
import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * An implementation of {@link CarSystemBarWindowSupplier} that works with layouts.
 */
class CarSystemBarWindowSupplierUsingLayout(
    @LayoutRes private val windowLayout: Int,
    @IdRes private val windowId: Int
) : CarSystemBarWindowSupplier {
    override fun getSystemBarWindow(ctx: Context): ViewGroup {
        val window = View.inflate(ctx, windowLayout, null) as ViewGroup
        window.id = windowId
        return window
    }
}
