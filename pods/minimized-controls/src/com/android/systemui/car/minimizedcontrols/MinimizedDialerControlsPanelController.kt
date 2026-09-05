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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserHandle
import android.telecom.Call
import android.telecom.TelecomManager
import android.util.Log
import android.view.View
import androidx.car.app.CarAppService
import com.android.car.scalableui.model.Event
import com.android.car.scalableui.model.PanelControllerMetadata
import com.android.car.scalableui.panel.DecorPanelController
import com.android.car.telephony.calling.InCallServiceManager
import com.android.car.telephony.common.TelecomUtils
import com.android.systemui.car.wm.scalableui.EventDispatcher
import com.android.systemui.car.wm.scalableui.panel.controller.DecorPanelViewMap
import com.android.systemui.dagger.qualifiers.Main
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.sysui.ShellController
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.Executor
import javax.inject.Provider

/**
 * Controller for [MinimizedDialerControlsView].
 */
class MinimizedDialerControlsPanelController @AssistedInject constructor(
    @Assisted panelId: String,
    @Assisted metadata: PanelControllerMetadata,
    @DecorPanelViewMap decorPanelViewMap: Map<Class<*>, @JvmSuppressWildcards Provider<View>>,
    private val inCallServiceManager: InCallServiceManager,
    @Main mainExecutor: Executor,
    @ShellMainThread private val shellExecutor: ShellExecutor,
    private val shellController: ShellController,
    private val userContextFactory: UserContextUtils.UserContextFactory,
    private val eventDispatcher: EventDispatcher
) : BaseMinimizedControlsPanelController<MinimizedDialerControlsView>(
    panelId,
    metadata,
    decorPanelViewMap,
    mainExecutor
) {

    private val viewModel: MinimizedDialerViewModel by lazy {
        MinimizedDialerViewModel(inCallServiceManager)
    }

    private var isCallActive = false
    private val myPanelId = panelId
    private var userContext: Context? = null

    override fun onViewCreated(view: MinimizedDialerControlsView) {
        val baseContext = view.context
        if (baseContext != null) {
            val userId = shellController.currentUserId
            userContext = userContextFactory.create(baseContext, userId)
        }
        setupObservers(view)
    }

    private fun setupObservers(view: MinimizedDialerControlsView) {
        viewModel.primaryCallLiveData?.observe(this) { call ->
            val wasCallActive = isCallActive
            isCallActive = call != null
            if (wasCallActive != isCallActive) {
                val eventId = if (isCallActive) {
                    MinimizedDialerControlsEventConstants.SYSTEM_CALL_CONNECTED_EVENT_ID
                } else {
                    MinimizedDialerControlsEventConstants.SYSTEM_CALL_DISCONNECTED_EVENT_ID
                }
                val event = Event.Builder(eventId).build()
                eventDispatcher.executeEvent(event)
            }

            if (call != null) {
                userContext?.let { context ->
                    java.util.concurrent.CompletableFuture.runAsync {
                        val packageName = getAppPackageName(call, context)
                        var icon: android.graphics.drawable.Drawable? = null
                        if (!packageName.isNullOrEmpty()) {
                            try {
                                icon = context.packageManager.getApplicationIcon(packageName)
                            } catch (e: PackageManager.NameNotFoundException) {
                                Log.w(TAG, "Failed to load icon for $packageName", e)
                            }
                        }
                        shellExecutor.execute {
                            view.updateAppIcon(icon)
                        }
                    }
                }

                val number = call.details?.handle?.schemeSpecificPart
                if (!number.isNullOrEmpty()) {
                    userContext?.let { uContext ->
                        TelecomUtils.getPhoneNumberInfo(uContext, number)
                            .thenAccept { info ->
                            val callerDisplayName = call.details?.callerDisplayName
                            var displayName = if (!callerDisplayName.isNullOrEmpty()) {
                                callerDisplayName
                            } else {
                                info.displayName
                            }
                            displayName = resolveEnterpriseName(
                                uContext,
                                number,
                                displayName
                            ) ?: displayName

                        shellExecutor.execute {
                            val initials = if (info.initials.isNullOrEmpty()) {
                                TelecomUtils.getInitials(displayName, info.displayNameAlt)
                            } else {
                                info.initials
                            }
                            val connectTimeMillis = if (
                                call.state == android.telecom.Call.STATE_ACTIVE
                            ) {
                                call.details?.connectTimeMillis
                            } else {
                                null
                            }
                            view.updateAvatar(info.avatarUri, initials, displayName)
                            view.updateDisplay(displayName, connectTimeMillis)
                        }
                        }
                    }
                } else {
                shellExecutor.execute {
                    view.updateAvatar(null, null, null)
                    view.updateDisplay(null, null)
                }
                }
            } else {
                shellExecutor.execute {
                    view.updateAppIcon(null)
                    view.updateAvatar(null, null, null)
                    view.updateDisplay(null, null)
                }
            }
        }
        viewModel.isMutedLiveData?.observe(this) { isMuted ->
            shellExecutor.execute {
                view.updateAudioState(isMuted == true)
            }
        }

        shellExecutor.execute {
            view.setPrimaryActionClickListener {
                launchInCallUi(showDialpad = false)
            }

            view.setOnDialpadClickListener { v ->
                v.isSelected = !v.isSelected
                launchInCallUi(showDialpad = v.isSelected)
            }

            view.setOnMuteClickListener {
                viewModel.toggleMute()
            }

            view.setOnEndCallClickListener {
                viewModel.disconnectCall()
            }
        }
    }

    override fun destroy() {
        viewModel.tearDown()
        super.destroy()
    }

    private fun getAppPackageName(call: Call, context: Context): String? {
        val details = call.details
        if (details != null && details.hasProperty(Call.Details.PROPERTY_SELF_MANAGED)) {
            val packageName = details.accountHandle?.componentName?.packageName
            if (!packageName.isNullOrEmpty()) {
                return packageName
            }
        }
        val telecomManager = context.getSystemService(TelecomManager::class.java)
        return telecomManager?.systemDialerPackage ?: CAR_DIALER_PACKAGE_NAME
    }

    companion object {
        private const val TAG = "MinimizedDialerPanel"

        // Car App Library constants for intent resolution
        private const val CAR_APP_CATEGORY_CALLING = "androidx.car.app.category.CALLING"
        private const val CAR_APP_ACTIVITY_INTERFACE = "androidx.car.app.activity.CarAppActivity"
        private const val CAR_DIALER_PACKAGE_NAME = "com.android.car.dialer"
        private const val CAR_DIALER_INCALL_ACTIVITY =
            "com.android.car.dialer.ui.activecall.InCallActivity"

        // Android Automotive specific intent extras
        private const val EXTRA_SHOW_DIALPAD = "show_dialpad"
    }

    @AssistedFactory
    interface Factory : DecorPanelController.Factory<MinimizedDialerControlsPanelController> {
        override fun create(panelId: String, metadata: PanelControllerMetadata):
            MinimizedDialerControlsPanelController
    }

    private fun launchInCallUi(showDialpad: Boolean) {
        val call = viewModel.primaryCallLiveData?.value
        var intent: Intent? = null

        userContext?.let { context ->
            val pm = context.packageManager
            val telecomManager = context.getSystemService(TelecomManager::class.java)

            if (call != null) {
                val details = call.details
                val isSelfManaged = details?.hasProperty(Call.Details.PROPERTY_SELF_MANAGED) == true

                if (isSelfManaged) {
                    val callingAppPackageName = details?.accountHandle?.componentName?.packageName
                    if (!callingAppPackageName.isNullOrEmpty()) {
                        val serviceIntent = Intent(CarAppService.SERVICE_INTERFACE)
                            .setPackage(callingAppPackageName)
                            .addCategory(CAR_APP_CATEGORY_CALLING)

                        val hasCallingService = pm.queryIntentServices(
                            serviceIntent,
                            PackageManager.GET_RESOLVED_FILTER
                        ).isNotEmpty()

                        val activityIntent = Intent()
                        activityIntent.component = ComponentName(
                            callingAppPackageName,
                            CAR_APP_ACTIVITY_INTERFACE
                        )

                        val hasActivity = pm.resolveActivity(
                            activityIntent,
                            PackageManager.MATCH_DEFAULT_ONLY
                        ) != null

                        if (hasCallingService && hasActivity) {
                            intent = Intent().setComponent(
                                ComponentName(callingAppPackageName, CAR_APP_ACTIVITY_INTERFACE)
                            )
                        }
                    }
                }
            }

            if (intent == null) {
                intent = getSystemDialerIntent(pm, telecomManager)
            }

            intent?.let { resolvedIntent ->
                resolvedIntent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )

                val systemDialerPkg = telecomManager?.systemDialerPackage
                    ?: CAR_DIALER_PACKAGE_NAME
                val targetPackage = resolvedIntent.`package`
                    ?: resolvedIntent.component?.packageName
                if (targetPackage == systemDialerPkg) {
                    if (targetPackage == CAR_DIALER_PACKAGE_NAME) {
                        resolvedIntent.component = ComponentName(
                            CAR_DIALER_PACKAGE_NAME,
                            CAR_DIALER_INCALL_ACTIVITY
                        )
                    }
                    resolvedIntent.putExtra(EXTRA_SHOW_DIALPAD, showDialpad)
                }

                context.startActivityAsUser(resolvedIntent, UserHandle.CURRENT)
            }
        }
    }

    private fun getSystemDialerIntent(
        pm: PackageManager,
        telecomManager: TelecomManager?
    ): Intent? {
        val defaultDialerPackage = telecomManager?.systemDialerPackage ?: CAR_DIALER_PACKAGE_NAME
        return pm.getLaunchIntentForPackage(defaultDialerPackage)
    }

    private fun resolveEnterpriseName(
        context: Context,
        number: String,
        currentName: String?
    ): String? {
        if (currentName != number && !currentName.isNullOrEmpty()) return currentName

        val cr = context.contentResolver
        val uri = android.net.Uri.withAppendedPath(
            android.provider.ContactsContract.PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI,
            android.net.Uri.encode(number)
        )
        val cursor = cr.query(
            uri,
            arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null
        )
        var resolvedName = currentName

        if (cursor != null && cursor.moveToFirst()) {
            resolvedName = cursor.getString(0)
        }
        cursor?.close()
        return resolvedName
    }
}
