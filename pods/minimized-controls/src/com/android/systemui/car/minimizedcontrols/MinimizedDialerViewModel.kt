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

import android.telecom.Call
import android.telecom.CallAudioState
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.car.telephony.calling.CallComparator
import com.android.car.telephony.calling.InCallServiceManager
import com.android.systemui.car.telecom.InCallServiceImpl
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

/**
 * ViewModel for Minimized Dialer Controls.
 * Exposes live data related to ongoing calls and provides interaction methods.
 */
class MinimizedDialerViewModel(
    private val inCallServiceManager: InCallServiceManager
) : InCallServiceImpl.InCallListener, PropertyChangeListener {

    private val _primaryCallLiveData = MutableLiveData<Call?>()
    val primaryCallLiveData: LiveData<Call?>
        get() = _primaryCallLiveData

    private val _isMutedLiveData = MutableLiveData<Boolean>()
    val isMutedLiveData: LiveData<Boolean>
        get() = _isMutedLiveData

    private val callComparator = CallComparator()

    init {
        inCallServiceManager.addObserver(this)
        if (inCallServiceManager.inCallService != null) {
            onInCallServiceConnected()
        }
    }

    private fun onInCallServiceConnected() {
        val inCallService = inCallServiceManager.inCallService as? InCallServiceImpl
        inCallService?.addListener(this)

        updatePrimaryCall()
        // isMuted is deprecated in favor of AudioManager #isMicrophoneMute, but we don't
        // have direct access to AudioManager here, and rely on the telecom framework's state.
        @Suppress("DEPRECATION")
        _isMutedLiveData.value = inCallService?.callAudioState?.isMuted == true
    }

    override fun propertyChange(evt: PropertyChangeEvent) {
        if (InCallServiceManager.PROPERTY_IN_CALL_SERVICE == evt.propertyName) {
            val oldService = evt.oldValue as? InCallServiceImpl
            oldService?.removeListener(this)

            if (inCallServiceManager.inCallService != null) {
                onInCallServiceConnected()
            } else {
                _primaryCallLiveData.value = null
                _isMutedLiveData.value = false
            }
        }
    }

    fun tearDown() {
        inCallServiceManager.removeObserver(this)
        val inCallService = inCallServiceManager.inCallService as? InCallServiceImpl
        inCallService?.removeListener(this)
    }

    override fun onCallAdded(call: Call) = updatePrimaryCall()
    override fun onCallRemoved(call: Call) = updatePrimaryCall()
    override fun onStateChanged(call: Call, state: Int) = updatePrimaryCall()
    override fun onParentChanged(call: Call, parent: Call?) = updatePrimaryCall()
    override fun onChildrenChanged(call: Call, children: List<Call>) = updatePrimaryCall()

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        // isMuted is deprecated in favor of AudioManager #isMicrophoneMute, but we don't
        // have direct access to AudioManager here, and rely on the telecom framework's state.
        @Suppress("DEPRECATION")
        _isMutedLiveData.value = audioState.isMuted
    }

    private fun updatePrimaryCall() {
        val inCallService = inCallServiceManager.inCallService as? InCallServiceImpl
        val calls = inCallService?.calls ?: emptyList()

        val ongoingCalls = calls.filter {
            it?.details?.state != Call.STATE_RINGING && it?.parent == null
        }.sortedWith(callComparator)

        _primaryCallLiveData.value = ongoingCalls.firstOrNull()
    }

    fun toggleMute() {
        val isMuted = _isMutedLiveData.value == true
        inCallServiceManager.inCallService?.setMuted(!isMuted)
    }

    fun disconnectCall() {
        val currentCall = _primaryCallLiveData.value ?: return
        currentCall.disconnect()
    }
}
