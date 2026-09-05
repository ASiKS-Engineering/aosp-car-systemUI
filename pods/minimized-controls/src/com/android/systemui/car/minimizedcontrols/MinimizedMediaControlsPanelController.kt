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

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.UserHandle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import com.android.car.media.common.source.MediaModels
import com.android.car.media.common.source.MediaSessionHelper
import com.android.car.media.common.ui.PlaybackCardController
import com.android.car.media.common.ui.PlaybackCardViewModel
import com.android.car.scalableui.model.PanelControllerMetadata
import com.android.car.scalableui.panel.DecorPanelController
import com.android.systemui.car.wm.scalableui.panel.controller.DecorPanelViewMap
import com.android.systemui.dagger.qualifiers.Main
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.UserChangeListener
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.Executor
import javax.inject.Provider

/**
 * Controller for [MinimizedMediaControlsView].
 */
class MinimizedMediaControlsPanelController @AssistedInject constructor(
    @Assisted panelId: String,
    @Assisted metadata: PanelControllerMetadata,
    @DecorPanelViewMap decorPanelViewMap: Map<Class<*>, @JvmSuppressWildcards Provider<View>>,
    private val shellController: ShellController,
    @param:ShellMainThread private val shellExecutor: ShellExecutor,
    @Main mainExecutor: Executor,
    private val playbackCardViewModelFactory: PlaybackCardViewModelFactory,
    private val userContextFactory: UserContextUtils.UserContextFactory,
    private val minimizedMediaControlsPlaybackCardControllerFactory:
        MinimizedMediaControlsPlaybackCardController.Factory,
    private val mediaModelsFactory: MediaModelsFactory
) : BaseMinimizedControlsPanelController<MinimizedMediaControlsView>(
    panelId,
    metadata,
    decorPanelViewMap,
    mainExecutor
) {

    /** Factory for creating [PlaybackCardViewModel]. */
    fun interface PlaybackCardViewModelFactory {
        fun create(
            application: android.app.Application,
            context: Context,
            mediaModels: MediaModels
        ): PlaybackCardViewModel
    }

    /** Factory for creating [MediaModels]. */
    fun interface MediaModelsFactory {
        fun create(
            context: Context,
            notificationProvider: MediaSessionHelper.NotificationProvider?,
            sessionProvider: MediaSessionHelper.SessionProvider,
            ignoreBrowser: Boolean
        ): MediaModels
    }

    private var viewModel: MinimizedMediaControlsViewModel? = null
    private var currentUserId: Int = UserHandle.USER_NULL

    private val userChangeListener = object : UserChangeListener {
        override fun onUserChanged(newUserId: Int, userContext: Context) {
            Log.d(TAG, "onUserChanged: newUserId=$newUserId")
            reinitMedia(newUserId)
        }
    }

    private var playbackCardController: PlaybackCardController? = null

    override fun onViewCreated(view: MinimizedMediaControlsView) {
        initMedia()
    }

    private fun initMedia() {
        if (viewModel != null) return // Already initialized

        // Register listener
        shellController.addUserChangeListener(userChangeListener)

        val userId = shellController.currentUserId
        Log.d(TAG, "initMedia: userId=$userId")
        reinitMedia(userId)
    }

    @VisibleForTesting
    var viewModelFactory: ((Context, Int, Executor) -> MinimizedMediaControlsViewModel) =
        { ctx, userId, appExecutor ->
            val models = mediaModelsFactory.create(
                ctx,
                /* notificationProvider = */
                null,
                object : MediaSessionHelper.SessionProvider {
                    override fun getActiveSessions(
                        manager: MediaSessionManager?
                    ): List<MediaController> {
                        // The default getActiveSessions uses the calling process ID.
                        // We need to explicitly call getActiveSessionsForUser with the target userId to get the correct sessions.
                        // SystemUI already has context access, so we use it directly instead of relying on the passed manager.
                        val systemSessionManager = ctx.getSystemService(
                            MediaSessionManager::class.java
                        )
                        return systemSessionManager.getActiveSessionsForUser(
                            /* notificationListener= */
                            null,
                            UserHandle.of(userId)
                        )
                    }

                    override fun registerActiveSessionsListener(
                        manager: MediaSessionManager?,
                        executor: Executor,
                        listener: MediaSessionManager.OnActiveSessionsChangedListener
                    ) {
                        // Similarly, addOnActiveSessionsChangedListener uses the calling process ID.
                        // We must use the user-aware variant to listen for the target user's session changes.
                        // Use appExecutor (Main Thread) for the listener to ensure setValue is safe!
                        val systemSessionManager = ctx.getSystemService(
                            MediaSessionManager::class.java
                        )
                        systemSessionManager.addOnActiveSessionsChangedListener(
                            /* notificationListener= */
                            null,
                            UserHandle.of(userId),
                            executor,
                            listener
                        )
                    }

                    override fun getSharedPrefName(): String {
                         return super.getSharedPrefName() + "_" + userId
                    }
                },
                /* ignoreBrowser= */
                true
            )
            MinimizedMediaControlsViewModel(models)
        }

    private fun reinitMedia(userId: Int) {
        mainExecutor.execute {
            if (currentUserId == userId) {
                Log.d(TAG, "reinitMedia: Skipping re-initialization for same user $userId")
                return@execute
            }
            currentUserId = userId

            val appCtx = panelView?.context?.applicationContext ?: return@execute
            Log.d(TAG, "reinitMedia: userId=$userId")

            // Destroy previous lifecycle to clean up observers and recreate it
            resetLifecycle()

            // Clean up old ViewModel on Main Thread.
            viewModel?.cleanUp()

            val wrappedContext = userContextFactory.create(appCtx, userId)

            // Create NEW ViewModel instance (on Main Thread)
            val newViewModel = viewModelFactory(wrappedContext, userId, mainExecutor)

            // Assign viewModel on Main Thread.
            viewModel = newViewModel

            val mediaModels = newViewModel.mediaModels
            if (mediaModels == null) {
                Log.e(TAG, "mediaModels is null, skipping initialization")
                return@execute
            }
            // Use the standard PlaybackViewModel from MediaModels
            val playbackViewModel = mediaModels.playbackViewModel

            val mediaItemsRepository = newViewModel.mediaItemsRepository

            if (mediaItemsRepository != null) {
                // Initialize PlaybackCardViewModel manually as we are in a Controller, not a Fragment/Activity
                // Initialize PlaybackCardViewModel using the factory
                val app = appCtx.applicationContext as android.app.Application
                val playbackCardViewModel = playbackCardViewModelFactory.create(
                    app,
                    wrappedContext,
                    mediaModels
                )

                // Use factory to create controller
                val controller = minimizedMediaControlsPlaybackCardControllerFactory.create(
                    panelView as ViewGroup,
                    playbackViewModel,
                    playbackCardViewModel,
                    mediaItemsRepository,
                    wrappedContext
                )
                controller.setupController()
                playbackCardController = controller
            } else {
                Log.e(TAG, "Failed to initialize PlaybackCardController: missing dependencies")
            }

            // Resume lifecycle to start observing
            (lifecycle as? LifecycleRegistry)?.currentState = Lifecycle.State.RESUMED
        }
    }

    override fun destroy() {
        Log.d(TAG, "destroy")
        shellController.removeUserChangeListener(userChangeListener)
        super.destroy()
        currentUserId = UserHandle.USER_NULL
        mainExecutor.execute {
            viewModel?.cleanUp()
            viewModel = null
        }
    }

    companion object {
        private const val TAG = "MinimizedMediaControlsPanelController"
    }

    @AssistedFactory
    interface Factory : DecorPanelController.Factory<MinimizedMediaControlsPanelController> {
        override fun create(panelId: String, metadata: PanelControllerMetadata):
                MinimizedMediaControlsPanelController
    }
}
