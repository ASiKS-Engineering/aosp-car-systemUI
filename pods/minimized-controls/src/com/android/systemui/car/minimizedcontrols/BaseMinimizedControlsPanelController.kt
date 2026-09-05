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

package com.android.systemui.car.minimizedcontrols

import android.view.View
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import com.android.car.scalableui.model.PanelControllerMetadata
import com.android.systemui.car.wm.scalableui.view.DecorPanelControllerBase
import java.util.concurrent.Executor
import javax.inject.Provider

/**
 * Base class for Minimized Controls panel controllers.
 * Handles the boilerplate of initializing the [LifecycleRegistry] and attaching
 * it to the root [View] upon creation.
 */
abstract class BaseMinimizedControlsPanelController<V : View>(
    panelId: String,
    metadata: PanelControllerMetadata,
    decorPanelViewMap: Map<Class<*>, @JvmSuppressWildcards Provider<View>>,
    protected val mainExecutor: Executor
) : DecorPanelControllerBase(panelId, metadata, decorPanelViewMap), LifecycleOwner {

    private var lifecycleRegistry: LifecycleRegistry? = null
    protected var panelView: V? = null

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry ?: LifecycleRegistry(this).also { lifecycleRegistry = it }

    /**
     * Resets the lifecycle, transitioning the current one to DESTROYED and creating a new one
     * initialized to CREATED. Useful for components that need to completely re-bind their observers
     * (e.g. on user switch).
     */
    @MainThread
    protected fun resetLifecycle() {
        lifecycleRegistry?.currentState = Lifecycle.State.DESTROYED
        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleRegistry?.currentState = Lifecycle.State.CREATED
        panelView?.setViewTreeLifecycleOwner(this)
    }

    /**
     * Called the first time the view is retrieved and attached to its lifecycle owner.
     * Subclasses should use this to set up ViewModel observers, click listeners, etc.
     */
    @MainThread
    protected abstract fun onViewCreated(view: V)

    override fun getView(): View? {
        @Suppress("UNCHECKED_CAST")
        val currentView = super.getView() as? V
        if (currentView != null && currentView !== panelView) {
            panelView = currentView
            mainExecutor.execute {
                (lifecycle as LifecycleRegistry).let {
                    it.currentState = Lifecycle.State.CREATED
                    it.currentState = Lifecycle.State.RESUMED
                }
                currentView.setViewTreeLifecycleOwner(this@BaseMinimizedControlsPanelController)
                onViewCreated(currentView)
            }
        }
        return currentView
    }

    override fun destroy() {
        super.destroy()
        lifecycleRegistry?.let {
            mainExecutor.execute {
                it.currentState = Lifecycle.State.DESTROYED
            }
        }
    }
}
