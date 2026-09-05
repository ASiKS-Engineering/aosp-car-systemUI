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
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.UserHandle
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.systemui.CarSysuiTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@RunWith(AndroidTestingRunner::class)
@SmallTest
class UserContextUtilsTest : CarSysuiTestCase() {

    @Mock
    private lateinit var baseContext: Context
    @Mock
    private lateinit var userContext: Context

    private val TEST_USER_ID = 10

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // Mock createContextAsUser to return our mock userContext
        `when`(baseContext.createContextAsUser(any(), anyInt())).thenReturn(userContext)
    }

    @Test
    fun testCreateWrappedUserContext_returnsContextWrapper() {
        val mixedContext = UserContextUtils.createWrappedUserContext(baseContext, TEST_USER_ID)
        assertTrue(mixedContext is ContextWrapper)
    }

    @Test
    fun testGetApplicationContext_returnsSelf() {
        val mixedContext = UserContextUtils.createWrappedUserContext(baseContext, TEST_USER_ID)
        assertEquals(mixedContext, mixedContext.applicationContext)
    }

    @Test
    fun testBindService_callsBindServiceAsUser() {
        // Create the wrapper
        val mixedContext = UserContextUtils.createWrappedUserContext(baseContext, TEST_USER_ID)

        val intent = Intent("action")
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {}
            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        val flags = 0

        // Act
        mixedContext.bindService(intent, conn, flags)

        // Assert
        // The wrapper should delegate to userContext.bindServiceAsUser with the correct UserHandle
        verify(
            userContext
        ).bindServiceAsUser(eq(intent), eq(conn), eq(flags), eq(UserHandle.of(TEST_USER_ID)))
    }

    @Test
    fun testCreateWrappedUserContext_userIdZero_returnsAppContext() {
        // When userId is 0, it should typically just return the context (or wrap it depending on impl, checking impl...)
        // Impl says: if (userId > 0) ... else appContext.
        // And then returns object : ContextWrapper(userContext)
        // So even for userId 0, it wraps 'appContext' (which is passed as userContext in the else block).

        val wrappedContext = UserContextUtils.createWrappedUserContext(baseContext, 0)
        assertTrue(wrappedContext is ContextWrapper)
        assertEquals(wrappedContext, wrappedContext.applicationContext)

        // Verify it binds as user 0
        val intent = Intent("action")
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {}
            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        wrappedContext.bindService(intent, conn, 0)

        // Since userId=0, userContext IS baseContext
        verify(baseContext).bindServiceAsUser(eq(intent), eq(conn), eq(0), eq(UserHandle.of(0)))
    }

    @Test
    fun testStartActivity_callsStartActivityAsUser() {
        val mixedContext = UserContextUtils.createWrappedUserContext(baseContext, TEST_USER_ID)
        val intent = Intent("action")
        val options = Bundle()

        mixedContext.startActivity(intent, options)

        verify(
            userContext
        ).startActivityAsUser(eq(intent), eq(options), eq(UserHandle.of(TEST_USER_ID)))
    }
}
