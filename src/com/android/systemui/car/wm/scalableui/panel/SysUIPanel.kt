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
package com.android.systemui.car.wm.scalableui.panel

import android.content.Context
import android.graphics.Insets
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.SurfaceControl
import androidx.annotation.CallSuper
import com.android.car.scalableui.model.Corner
import com.android.car.scalableui.model.Focus
import com.android.car.scalableui.model.GravityVariant
import com.android.car.scalableui.model.PanelControllerMetadata
import com.android.car.scalableui.model.Role
import com.android.car.scalableui.model.Variant
import com.android.car.scalableui.panel.Panel
import com.android.car.scalableui.panel.PanelUpdatePublisher
import com.android.wm.shell.automotive.AutoSurfaceTransaction
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.shared.annotations.ExternalMainThread
import com.android.wm.shell.shared.annotations.ShellBackgroundThread
import com.android.wm.shell.shared.annotations.ShellMainThread
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.io.PrintWriter
import java.util.Optional

/**
 * A base class for implementing a [Panel].
 *
 * Provides common functionality and state management for different types of panels
 */
open class SysUIPanel @AssistedInject constructor(
    private val context: Context,
    @Assisted private val panelId: String,
    private val panelUpdatePublisherOptional: Optional<PanelUpdatePublisher>,
    @ExternalMainThread val mainExecutor: ShellExecutor,
    @ShellMainThread val shellMainExecutor: ShellExecutor,
    @ShellBackgroundThread val shellBgExecutor: ShellExecutor
) : Panel {
    private var layer = -1
    private var canFocusOnTransition = Focus.Companion.DEFAULT_FOCUS_ON_TRANSITION
    private var role = Role.DEFAULT_ROLE
    private var bounds = Rect()
    private var isVisible: Boolean? = null
    private var alpha = 0f
    private var displayId = 0
    private var cornerRadius = Corner.DEFAULT_CORNER
    private var insets = Insets.NONE
    private var panelControllerMetadata: PanelControllerMetadata? = null
    private var gravity: Int = Gravity.NO_GRAVITY

    override fun getContext() = context

    override fun getRole(): Role? = role

    override fun getDisplayId() = displayId

    override fun getPanelId() = panelId

    override fun getLayer() = layer

    override fun setLayer(layer: Int) {
        this.layer = layer
    }

    override fun canFocusOnTransition() = canFocusOnTransition

    override fun setCanFocusOnTransition(canFocusOnTransition: Boolean) {
        this.canFocusOnTransition = canFocusOnTransition
    }

    override fun getX1() = bounds.left

    override fun getX2() = bounds.right

    override fun getY1() = bounds.top

    override fun getY2() = bounds.bottom

    override fun setX1(x: Int) {
        setBounds(Rect(x, y1, x2, y2))
    }

    override fun setX2(x: Int) {
        setBounds(Rect(x1, y1, x, y2))
    }

    override fun setY1(y: Int) {
        setBounds(Rect(x1, y, x2, y2))
    }

    override fun setY2(y: Int) {
        setBounds(Rect(x1, y1, x2, y))
    }

    override fun isVisible(): Boolean {
        return isVisible == true
    }

    override fun reset() {
        logIfDebuggable("Reset panel $panelId")
    }

    override fun init() {
        logIfDebuggable("Init panel $panelId")
    }

    override fun setVisibility(isVisible: Boolean) {
        if (this.isVisible?.let { it == isVisible } == true) {
            return
        }
        this.isVisible = isVisible
        panelUpdateObserver?.postVisibility(panelId, isVisible)
    }

    override fun getAlpha() = alpha

    override fun setAlpha(alpha: Float) {
        this.alpha = alpha
        panelUpdateObserver?.postAlpha(panelId, alpha)
    }

    override fun setCornerRadius(radius: Corner) {
        this.cornerRadius = radius
        panelUpdateObserver?.postCornerRadius(panelId, radius)
    }

    override fun getCornerRadius() = cornerRadius

    override fun setDisplayId(displayId: Int) {
        this.displayId = displayId
    }

    override fun getBounds() = bounds

    override fun setBounds(bounds: Rect) {
        this.bounds = bounds
        panelUpdateObserver?.postBounds(panelId, bounds)
    }

    override fun getSafeBounds() = Rect()

    override fun setSafeBounds(safeBounds: Rect) {
        // no-op
    }

    override fun getTaskToolbarBounds() = Rect()

    override fun setTaskToolbarBounds(safeBounds: Rect) {
        // no-op
    }

    override fun setRole(role: Role?) {
        this.role = role
    }

    override fun setInsets(insets: Insets) {
        this.insets = insets
        panelUpdateObserver?.postInsets(panelId, insets)
    }

    override fun getInsets() = insets

    override fun getGravity() = gravity

    override fun setGravity(gravity: Int) {
        this.gravity = gravity
        panelUpdateObserver?.postGravity(panelId, gravity)
    }

    override fun getPanelControllerMetadata(): PanelControllerMetadata? = panelControllerMetadata

    /**
     * Updates surface of the [SysUIPanel] based on the provided [Variant] using
     * AutoSurfaceTransaction.
     * See [updateInternal] for more details.
     */
    @JvmOverloads
    open fun update(
        autoSurfaceTransaction: AutoSurfaceTransaction,
        variant: Variant? = null,
        updateChildren: Boolean = false
    ) {
        updateInternal(
            autoSurfaceTransaction,
            null, // tx
            variant,
            updateChildren
        )
    }

    /**
     * Updates surface of the [SysUIPanel] based on the provided [Variant] using
     * SurfaceControl.Transaction.
     * Note that without an AutoSurfaceTransaction, the child decors cannot be updated.
     * See [updateInternal] for more details.
     */
    @JvmOverloads
    open fun update(tx: SurfaceControl.Transaction, variant: Variant? = null) {
        updateInternal(
            null, // autoSurfaceTransaction
            tx,
            variant,
            false // updateChildren
        )
    }

    /**
     * Updates surface of the [SysUIPanel] based on the provided [Variant] using
     * AutoSurfaceTransaction and SurfaceControl.Transaction. For attributes that are part of both
     * interfaces, AutoSurfaceTransaction will be preferred.
     * See [updateInternal] for more details.
     */
    @JvmOverloads
    open fun update(
        autoSurfaceTransaction: AutoSurfaceTransaction,
        tx: SurfaceControl.Transaction,
        variant: Variant? = null,
        updateChildren: Boolean = false
    ) {
        updateInternal(autoSurfaceTransaction, tx, variant, updateChildren)
    }

    /**
     * Updates surface of the [SysUIPanel] based on the provided [Variant].
     *
     * <p> This should not be called directly but should be called through an [#update]
     * method to ensure the correct parameter state.
     *
     * <p> if provided [Variant] is null, update the surface with the data from [Panel]
     * itself.
     *
     * @param autoSurfaceTransaction The [AutoSurfaceTransaction] instance used to apply
     *                               surface property changes. One of
     *                               {@param autoSurfaceTransaction] and {@param tx] must not be
     *                               null. If both are supplies,  AutoSurfaceTransaction will be
     *                               preferred for attributes that are shared.
     * @param tx                     An [SurfaceControl.Transaction] that can be
     *                               used in addition to {@param autoSurfaceTransaction} to apply
     *                               changes.
     * @param variant                The [Variant} configuration object that provides the
     *                               desired properties (bounds, visibility, layer, corner radius,
     *                               alpha) for the decor surface.
     * @param updateChildren         Update the children components used in this panel, should only
     *                               set to true on animationEnd or reset.
     */
    @CallSuper
    protected open fun updateInternal(
        autoSurfaceTransaction: AutoSurfaceTransaction?,
        tx: SurfaceControl.Transaction?,
        variant: Variant?,
        updateChildren: Boolean
    ) {
        panelUpdateObserver?.let {
            (variant?.isVisible ?: isVisible)?.let { nonNullVisibility ->
                it.postVisibility(panelId, nonNullVisibility)
            }
            it.postAlpha(panelId, variant?.alpha ?: alpha)
            it.postCornerRadius(panelId, variant?.cornerRadius ?: cornerRadius)
            it.postBounds(panelId, variant?.bounds ?: bounds)
            it.postInsets(panelId, variant?.insets ?: insets)
            val gravityVariant = variant as? GravityVariant
            val targetGravity = gravityVariant?.gravity ?: if (variant == null) {
                gravity
            } else {
                Gravity.NO_GRAVITY
            }
            it.postGravity(panelId, targetGravity)
        }
    }

    override fun setPanelControllerMetadata(
        panelControllerMetadata: PanelControllerMetadata?
    ) {
        this.panelControllerMetadata = panelControllerMetadata
        panelUpdateObserver?.postControllerMetadata(panelId, panelControllerMetadata)
    }

    override fun getPanelUpdateObserver(): PanelUpdatePublisher? {
        return panelUpdatePublisherOptional.orElse(null)
    }

    @ExternalMainThread
    override fun refreshTheme() {
        logIfDebuggable("$panelId refreshTheme")
    }

    override fun destroy() {
        logIfDebuggable("Panel destroyed $this")
    }

    override fun dump(pw: PrintWriter) {
        pw.println(this)
    }

    override fun toString(): String {
        return ("SysUIPanel{" +
                "panelId='$panelId'" +
                "\n, bounds=$bounds" +
                "\n, isVisible=$isVisible" +
                "\n, alpha=$alpha" +
                "\n, insets=$insets" +
                "\n, metaData=$panelControllerMetadata" +
                "\n, cornerRadius=$cornerRadius}")
    }

    @AssistedFactory
    fun interface Factory {
        /** Create instance of [SysUIPanel] with specified id  */
        fun create(id: String): SysUIPanel
    }

    companion object {
        protected val DEBUG = Build.isDebuggable()
        protected const val RESET_TRANSACTION = "Reset : "
        protected const val REFRESH_TRANSACTION = "Refresh : "
        private val TAG = SysUIPanel::class.simpleName.orEmpty()
        private val TAG_THREAD = "${SysUIPanel::class.simpleName.orEmpty()}.Thread"

        @JvmStatic
        protected fun logIfDebuggable(msg: String) {
            if (DEBUG) {
                Log.d(TAG, msg)
            }
        }
        @JvmStatic
        protected fun logThreadIfDebuggable(msg: String, thread: Thread) {
            if (DEBUG) {
                Log.d(TAG_THREAD, "$msg on thread ${thread.name}")
            }
        }
    }
}
