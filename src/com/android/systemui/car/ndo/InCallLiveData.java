/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.systemui.car.ndo;

import android.telecom.Call;
import android.telecom.PhoneAccountHandle;
import android.util.Slog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MediatorLiveData;

import com.android.car.telephony.calling.InCallServiceManager;
import com.android.car.telephony.common.CallDetail;
import com.android.systemui.car.telecom.InCallServiceImpl;

import java.util.List;

/**
 * Livedata that provides first active call of the currently blocked calling activity.
 * Returns null if no calls are found for the currently blocked activity.
 * <p>
 * Calls must be in an active or holding {@link Call.CallState}. Other states will not emit a value.
 */
public class InCallLiveData extends MediatorLiveData<Call> implements
        InCallServiceImpl.InCallListener {
    private static final String TAG = "SysUi.InCallLiveData";

    private final InCallServiceManager mServiceManager;
    private final String mBlockedActivity;

    public InCallLiveData(InCallServiceManager serviceManager, String packageName) {
        mServiceManager = serviceManager;
        mBlockedActivity = packageName;
    }

    @Override
    protected void onActive() {
        super.onActive();

        if (mServiceManager.getInCallService() == null) {
            setValue(null);
            return;
        }
        update();
    }

    @Override
    protected void onInactive() {
        super.onInactive();
        setValue(null);
    }

    @Override
    public void onCallAdded(Call call) {
        Slog.d(TAG, "Call added: " + call);
        update();
    }

    @Override
    public void onCallRemoved(Call call) {
        Slog.d(TAG, "Call removed: " + call);
        update();
    }

    @Override
    public void onStateChanged(Call call, int state) {
        Slog.d(TAG, "onStateChanged: " + call + " state: " + state);
        update();
    }

    @Override
    public void onParentChanged(Call call, Call parent) {
        Slog.d(TAG, "onParentChanged: " + call);
        update();
    }

    @Override
    public void onChildrenChanged(Call call, List<Call> children) {
        Slog.d(TAG, "onChildrenChanged: " + call);
        update();
    }

    private void update() {
        setValue(getFirstBlockedActivityCall());
    }

    @Nullable
    private Call getFirstBlockedActivityCall() {
        InCallServiceImpl inCallService = (InCallServiceImpl) mServiceManager.getInCallService();

        if (inCallService == null) {
            Slog.i(TAG, "null InCallService");
            return null;
        }

        List<Call> callList = inCallService.getCalls();
        List<Call> blockedAppCallList = callList.stream()
                .filter(call -> contains(mBlockedActivity, getSelfManagedCallAppPackageName(call)))
                .toList();

        return blockedAppCallList.isEmpty() ? null : blockedAppCallList.get(0);
    }

    private boolean contains(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.contains(b);
    }

    @Nullable
    private String getSelfManagedCallAppPackageName(@NonNull Call call) {
        int callState = call.getDetails().getState();

        if (callState != Call.STATE_ACTIVE && callState != Call.STATE_HOLDING) {
            return null;
        }
        CallDetail callDetails = CallDetail.fromTelecomCallDetail(call.getDetails());
        if (callDetails.isSelfManaged()) {
            PhoneAccountHandle phoneAccountHandle = callDetails.getPhoneAccountHandle();
            return phoneAccountHandle == null ? null
                    : phoneAccountHandle.getComponentName().getPackageName();
        }
        return null;
    }
}
