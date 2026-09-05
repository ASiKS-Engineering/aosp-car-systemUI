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
package com.android.systemui.car.wm.scalableui.panel.controller

import com.android.wm.shell.automotive.AutoCaptionBarViewController

/**
 * An abstract base class for controllers that create and manage a task toolbar view.
 *
 * This class extends [AutoCaptionBarViewController] and defines a common factory pattern
 * for its subclasses, enabling them to be created with a specific `panelId`.
 *
 * @see CompatibilityToolBarController
 * @see com.android.wm.shell.automotive.AutoCaptionBarViewController
 */
interface TaskToolbarController : AutoCaptionBarViewController {
    interface Factory<T : TaskToolbarController> {
        /**
         * Create an instance of the [TaskToolbarController] implementation using the provided
         * panelId.
         */
        fun create(panelId: String): T
    }
}
