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

package com.android.systemui.car.qc.datasubscription;

import static com.android.systemui.CarSystemUIStatsLog.CAR_SYSTEM_UI_DATA_SUBSCRIPTION_EVENT_REPORTED__EVENT_TYPE__UNSPECIFIED_EVENT_TYPE;
import static com.android.systemui.CarSystemUIStatsLog.CAR_SYSTEM_UI_DATA_SUBSCRIPTION_EVENT_REPORTED__EVENT_TYPE__SESSION_STARTED;
import static com.android.systemui.CarSystemUIStatsLog.CAR_SYSTEM_UI_DATA_SUBSCRIPTION_EVENT_REPORTED__EVENT_TYPE__SESSION_FINISHED;
import static com.android.systemui.CarSystemUIStatsLog.CAR_SYSTEM_UI_DATA_SUBSCRIPTION_EVENT_REPORTED__EVENT_TYPE__BUTTON_CLICKED;
import static com.android.systemui.CarSystemUIStatsLog.CAR_SYSTEM_UI_DATA_SUBSCRIPTION_EVENT_REPORTED__MESSAGE_TYPE__UNSPECIFIED_MESSAGE_TYPE;
import static com.android.systemui.CarSystemUIStatsLog.CAR_SYSTEM_UI_DATA_SUBSCRIPTION_EVENT_REPORTED__MESSAGE_TYPE__PROACTIVE;
import static com.android.systemui.CarSystemUIStatsLog.CAR_SYSTEM_UI_DATA_SUBSCRIPTION_EVENT_REPORTED__MESSAGE_TYPE__REACTIVE;

import android.annotation.IntDef;
import android.os.Build;
import android.util.Log;

import com.android.systemui.CarSystemUIStatsLog;
import com.android.systemui.dagger.SysUISingleton;

import java.util.UUID;

import javax.inject.Inject;

/**
 * Helper class that directly interacts with {@link CarSystemUIStatsLog}, a generated class that
 * contains logging methods for DataSubscriptionController.
 */
@SysUISingleton
public class DataSubscriptionStatsLogHelper {

    private static final String TAG = DataSubscriptionStatsLogHelper.class.getSimpleName();
    private long mSessionId;
    private int mCurrentMessageType;

    /**
     * IntDef representing enum values of CarSystemUiDataSubscriptionEventReported.event_type.
     */
    @IntDef({
            DataSubscriptionEventType.UNSPECIFIED_EVENT_TYPE,
            DataSubscriptionEventType.SESSION_STARTED,
            DataSubscriptionEventType.SESSION_FINISHED,
            DataSubscriptionEventType.BUTTON_CLICKED,
    })

    public @interface DataSubscriptionEventType {
        int UNSPECIFIED_EVENT_TYPE =
                CAR_SYSTEM_UI_DATA_SUBSCRIPTION_EVENT_REPORTED__EVENT_TYPE__UNSPECIFIED_EVENT_TYPE;
        int SESSION_STARTED =
                CAR_SYSTEM_UI_DATA_SUBSCRIPTION_EVENT_REPORTED__EVENT_TYPE__SESSION_STARTED;
        int SESSION_FINISHED =
                CAR_SYSTEM_UI_DATA_SUBSCRIPTION_EVENT_REPORTED__EVENT_TYPE__SESSION_FINISHED;
        int BUTTON_CLICKED =
                CAR_SYSTEM_UI_DATA_SUBSCRIPTION_EVENT_REPORTED__EVENT_TYPE__BUTTON_CLICKED;
    }

    /**
     * IntDef representing enum values of CarSystemUiDataSubscriptionEventReported.message_type.
     */
    @IntDef({
            DataSubscriptionMessageType.UNSPECIFIED_MESSAGE_TYPE,
            DataSubscriptionMessageType.PROACTIVE,
            DataSubscriptionMessageType.REACTIVE,
    })

    public @interface DataSubscriptionMessageType {
        int UNSPECIFIED_MESSAGE_TYPE =
                CAR_SYSTEM_UI_DATA_SUBSCRIPTION_EVENT_REPORTED__MESSAGE_TYPE__UNSPECIFIED_MESSAGE_TYPE;
        int PROACTIVE =
                CAR_SYSTEM_UI_DATA_SUBSCRIPTION_EVENT_REPORTED__MESSAGE_TYPE__PROACTIVE;
        int REACTIVE =
                CAR_SYSTEM_UI_DATA_SUBSCRIPTION_EVENT_REPORTED__MESSAGE_TYPE__REACTIVE;
    }

    /**
     * Construct logging instance of DataSubscriptionStatsLogHelper.
     */
    @Inject
    public DataSubscriptionStatsLogHelper() {}

    /**
     * Logs that a new Data Subscription session has started.
     * Additionally, resets measurements and IDs such as
     * session ID and start time.
     */
    public void logSessionStarted(@DataSubscriptionMessageType int messageType) {
        mSessionId = UUID.randomUUID().getMostSignificantBits();
        mCurrentMessageType = messageType;
        writeDataSubscriptionEventReported(DataSubscriptionEventType.SESSION_STARTED, messageType);
    }

    /**
     * Logs that the current Data Subscription session has finished.
     */
    public void logSessionFinished() {
        writeDataSubscriptionEventReported(DataSubscriptionEventType.SESSION_FINISHED);
    }

    /**
     * Logs that the "See plans" button is clicked. This method should be called after
     * logSessionStarted() is called.
     */
    public void logButtonClicked() {
        writeDataSubscriptionEventReported(DataSubscriptionEventType.BUTTON_CLICKED);
    }

    /**
     * Writes to CarSystemUiDataSubscriptionEventReported atom with {@code messageType} as the only
     * field, and log all other fields as unspecified.
     *
     * @param eventType one of {@link DataSubscriptionEventType}
     */
    private void writeDataSubscriptionEventReported(int eventType) {
        writeDataSubscriptionEventReported(
                eventType, /* messageType */ mCurrentMessageType);
    }

    /**
     * Writes to CarSystemUiDataSubscriptionEventReported atom with all the optional fields filled.
     *
     * @param eventType   one of {@link DataSubscriptionEventType}
     * @param messageType one of {@link DataSubscriptionMessageType}
     */
    private void writeDataSubscriptionEventReported(int eventType, int messageType) {
        if (Build.isDebuggable()) {
            Log.d(TAG, "writing CAR_SYSTEM_UI_DATA_SUBSCRIPTION_EVENT_REPORTED. sessionId="
                    + mSessionId + ", eventType= " + eventType
                    + ", messageType=" + messageType);
        }
        CarSystemUIStatsLog.write(
                /* atomId */ CarSystemUIStatsLog.CAR_SYSTEM_UI_DATA_SUBSCRIPTION_EVENT_REPORTED,
                /* sessionId */ mSessionId,
                /* eventType */ eventType,
                /* messageType */ messageType);
    }
}
