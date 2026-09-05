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
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.UserHandle
import android.util.Log

/**
 * Utility for creating user-aware contexts.
 */
object UserContextUtils {
    private const val TAG = "UserContextUtils"

    /**
     * Creates a Context for the specified [userId] that ensures standard APIs working across users.
     *
     * It wraps the context to:
     * 1. Fix potential NPEs in helper classes that call [Context.getApplicationContext].
     * 2. Force [Context.bindService] to use [Context.bindServiceAsUser].
     */
    fun createWrappedUserContext(appContext: Context, userId: Int): Context {
        val userContext = if (userId > 0) {
            UserHandle.of(userId).let { userHandle ->
                try {
                    appContext.createContextAsUser(userHandle, 0)
                } catch (e: Exception) {
                    Log.w(TAG, "Error creating user context for $userId", e)
                    appContext
                }
            }
        } else {
            appContext
        }

        return object : ContextWrapper(userContext) {
            override fun getApplicationContext(): Context {
                return this
            }

            override fun bindService(
                service: Intent,
                conn: ServiceConnection,
                flags: Int
            ): Boolean {
                Log.d(
                    TAG,
                    "ContextWrapper.bindService: intent=$service flags=$flags, " +
                        "forcing bindServiceAsUser($userId)"
                )
                return baseContext.bindServiceAsUser(
                    service,
                    conn,
                    flags,
                    UserHandle.of(userId)
                )
            }

            override fun startActivity(intent: Intent?, options: Bundle?) {
                if (intent != null) {
                    baseContext.startActivityAsUser(intent, options, UserHandle.of(userId))
                }
            }
        }
    }

    /** Factory for creating User Context. */
    fun interface UserContextFactory {
        fun create(context: Context, userId: Int): Context
    }
}
