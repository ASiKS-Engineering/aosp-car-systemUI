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
package com.android.systemui.car.wm.scalableui.systemevents;

import static android.app.role.RoleManager.ROLE_HOME;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_VISIBLE;
import static android.content.pm.ActivityInfo.CONFIG_UI_MODE;

import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_BEFORE_USER_SWITCH_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_ENTER_SUW_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_EXIT_SUW_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_KEYGUARD_HIDDEN_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_KEYGUARD_SHOWN_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_USER_AUTHENTICATED_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_USER_SWITCH_COMPLETE_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_USER_SWITCH_ON_AUTHENTICATED_TOKEN_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_UXR_RESTRICTED_TOKEN_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_UXR_STATE_CHANGED_EVENT_ID;

import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.app.role.RoleManager;
import android.car.drivingstate.CarUxRestrictions;
import android.car.user.CarUserManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.view.Display;

import androidx.annotation.NonNull;

import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.Event;
import com.android.car.scalableui.panel.Panel;
import com.android.car.scalableui.panel.PanelPool;
import com.android.car.ui.utils.CarUxRestrictionsUtil;
import com.android.systemui.CoreStartable;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarDeviceProvisionedListener;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.display.DisplayStateHelper;
import com.android.systemui.car.flags.Flag;
import com.android.systemui.car.flags.FlagManager;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.car.wm.scalableui.ScalableUIUtils;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.settings.DisplayTracker;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.window.flags.Flags;

import dagger.Lazy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import javax.inject.Inject;

/**
 * A system event handler that listens for user lifecycle events and device provisioning state
 * changes.
 *
 * <p>This class dispatches events to the {@link StateManager} when a user is unlocked or when
 * the device
 * is being set up.
 */
@SuppressLint("MissingPermission")
@SysUISingleton
public class SystemEventHandler implements CoreStartable,
        ConfigurationController.ConfigurationListener {
    private static final String TAG = SystemEventHandler.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    // Flag to track if certain states have already been triggered for a user
    private static final int USER_FLAG_RESET = 1 << 0;
    private static final int USER_FLAG_SWITCHED = 1 << 1;
    private static final int USER_FLAG_SUW_LAUNCHED = 1 << 2;

    private final Context mContext;
    private final UserManager mUserManager;
    private final CarServiceProvider mCarServiceProvider;
    private final UserTracker mUserTracker;
    private final DisplayTracker mDisplayTracker;
    private final Lazy<DisplayStateHelper> mDisplayStateHelper;
    private final KeyguardStateController mKeyguardStateController;
    private final Executor mBackgroundExecutor;
    private final CarDeviceProvisionedController mCarDeviceProvisionedController;
    private final EventDispatcher mEventDispatcher;
    private final FlagManager mFlagManager;
    private final CarUxRestrictionsUtil mCarUxRestrictionsUtil;
    // Mapping of userId to user flags
    private final ConcurrentHashMap<Integer, Integer> mUserFlags = new ConcurrentHashMap<>();
    private final RoleManager mRoleManager;

    private CarUserManager mCarUserManager;
    private boolean mIsUserSetupInProgress;
    private boolean mIsKeyguardShowing;
    private boolean mIsUxrRestricted;
    private Configuration mConfiguration;

    private final CarUserManager.UserLifecycleListener mUserLifecycleListener =
            new CarUserManager.UserLifecycleListener() {
                @Override
                public void onEvent(@NonNull CarUserManager.UserLifecycleEvent event) {
                    logIfDebuggable("on User event = " + event);
                    if (event.getUserHandle().isSystem()) {
                        Log.i(TAG, "Ignore system event");
                        return;
                    }

                    if (event.getUserId() != mUserTracker.getUserId()) {
                        Log.i(TAG, "Not current user" + event.getUserId());
                        return;
                    }

                    if (event.getEventType() == USER_LIFECYCLE_EVENT_TYPE_VISIBLE) {
                        // Attempt to launch SUW as soon as the user is visible to launch sooner
                        // should the SUW app be direct boot aware. If it is not available, it will
                        // be launched after user unlock instead.
                        // TODO(b/497823844)Remove the home role check once task routing does
                        // not depend on home role.
                        // On user visible, it's possible that home role is not set, cause task
                        // routing fail to route suw to suw panel. So adding role check here.
                        if (isHomeRoleExist(event.getUserId())) {
                            handleSuwLaunchIfNecessary(event.getUserId());
                        }
                    } else if (event.getEventType() == USER_LIFECYCLE_EVENT_TYPE_UNLOCKED) {
                        handleUserUnlocked(event.getUserHandle());
                    } else {
                        Log.i(TAG, "Ignore system event" + event.getEventType());
                    }
                }
            };

    private final CarDeviceProvisionedListener mCarDeviceProvisionedListener =
            new CarDeviceProvisionedListener() {
                @Override
                public void onUserSetupChanged() {
                    updateUserSetupState(/* force= */ false);
                }

                @Override
                public void onUserSetupInProgressChanged() {
                    updateUserSetupState(/* force= */ false);
                }

                @Override
                public void onDeviceProvisionedChanged() {
                    updateUserSetupState(/* force= */ false);
                }

                @Override
                public void onUserSwitched() {
                    updateUserSetupState(/* force= */ true);
                }
            };

    private final UserTracker.Callback mUserTrackerCallback = new UserTracker.Callback() {
        @Override
        public void onBeforeUserSwitching(int newUser) {
            // reset flags for new user when switching
            mUserFlags.put(newUser, 0);
            mEventDispatcher.executeEvent(
                    getEventWithDisplays(new Event.Builder(SYSTEM_BEFORE_USER_SWITCH_EVENT_ID)));
        }

        @Override
        public void onUserChanged(int newUser, @NonNull Context userContext) {
            mEventDispatcher.executeEvent(
                    getEventWithDisplays(new Event.Builder(SYSTEM_USER_SWITCH_COMPLETE_EVENT_ID)));
        }
    };

    private final DisplayStateHelper.Listener mDisplayStateListener =
            new DisplayStateHelper.Listener() {
                @Override
                public void onDisplayPowerStateChanged(int displayId, boolean isOn) {
                    if (isOn && mUserManager.isUserUnlocked(mUserTracker.getUserId())
                            && !mIsKeyguardShowing) {
                        mEventDispatcher.executeEvent(getUserAuthEvent());
                        addUserFlag(mUserTracker.getUserId(), USER_FLAG_SWITCHED);
                    }
                }
            };

    private final CarUxRestrictionsUtil.OnUxRestrictionsChangedListener mUxrListener =
            new CarUxRestrictionsUtil.OnUxRestrictionsChangedListener() {
                @Override
                public void onRestrictionsChanged(@NonNull CarUxRestrictions carUxRestrictions) {
                    boolean restricted = carUxRestrictions.isRequiresDistractionOptimization();
                    if (mIsUxrRestricted == restricted) {
                        return;
                    }
                    mIsUxrRestricted = restricted;
                    mEventDispatcher.executeEvent(getEventWithDisplays(
                            new Event.Builder(SYSTEM_UXR_STATE_CHANGED_EVENT_ID).addToken(
                                    SYSTEM_UXR_RESTRICTED_TOKEN_ID,
                                    Boolean.toString(mIsUxrRestricted))));
                }
            };

    @Inject
    public SystemEventHandler(
            Context context,
            UserManager userManager,
            @Background Executor bgExecutor,
            CarServiceProvider carServiceProvider,
            UserTracker userTracker,
            DisplayTracker displayTracker,
            Lazy<DisplayStateHelper> displayStateHelper,
            KeyguardStateController keyguardStateController,
            CarDeviceProvisionedController carDeviceProvisionedController,
            EventDispatcher dispatcher,
            FlagManager flagManager,
            CarUxRestrictionsUtil carUxRestrictionsUtil
    ) {
        mContext = context;
        mUserManager = userManager;
        mBackgroundExecutor = bgExecutor;
        mCarServiceProvider = carServiceProvider;
        mUserTracker = userTracker;
        mDisplayTracker = displayTracker;
        mDisplayStateHelper = displayStateHelper;
        mKeyguardStateController = keyguardStateController;
        mCarDeviceProvisionedController = carDeviceProvisionedController;
        mEventDispatcher = dispatcher;
        mFlagManager = flagManager;
        mCarUxRestrictionsUtil = carUxRestrictionsUtil;
        // Make a copy of current Configuration
        mConfiguration = new Configuration(mContext.getResources().getConfiguration());
        mRoleManager = context.getSystemService(RoleManager.class);
    }

    /**
     * Update the current user setup state and send relevant events if necessary.
     *
     * @param force always send event regardless of if anything has changed
     */
    private void updateUserSetupState(boolean force) {
        if (mUserTracker.getUserHandle().isSystem()) {
            // don't handle headless system user
            return;
        }
        boolean isUserSetupInProgress =
                mCarDeviceProvisionedController.isCurrentUserSetupInProgress();
        if (isUserSetupInProgress != mIsUserSetupInProgress || force) {
            logIfDebuggable("User setup state changed setupInProgress=" + isUserSetupInProgress);
            mIsUserSetupInProgress = isUserSetupInProgress;
            notifySuwStateEvent();
            if (mUserManager.isUserUnlocked(mUserTracker.getUserId())
                    && shouldResetPanels()) {
                logIfDebuggable("Resetting panels during user setup state change");
                StateManager.handlePanelReset();
                addUserFlag(mUserTracker.getUserId(), USER_FLAG_RESET);
            }
        }
    }

    private void notifySuwStateEvent() {
        String eventId =
                mIsUserSetupInProgress ? SYSTEM_ENTER_SUW_EVENT_ID : SYSTEM_EXIT_SUW_EVENT_ID;
        mEventDispatcher.executeEvent(
                getEventWithDisplays(new Event.Builder(eventId)));
    }

    @Override
    public void start() {
        if (ScalableUIUtils.isScalableUIEnabled(mContext, mFlagManager)) {
            registerUserEventListener();
            registerProvisionedStateListener();
            mUserTracker.addCallback(mUserTrackerCallback, mBackgroundExecutor);
            mDisplayStateHelper.get().addListener(mDisplayStateListener);
            registerKeyguardStateListener();
            registerUxrListener();
        }
    }

    @Override
    public void onConfigChanged(Configuration newConfig) {
        int diff = mConfiguration.updateFrom(newConfig);
        if ((diff & CONFIG_UI_MODE) != 0) {
            PanelPool.getInstance().forEach(Panel::refreshTheme);
        }
    }

    private void registerProvisionedStateListener() {
        mIsUserSetupInProgress = mCarDeviceProvisionedController.isCurrentUserSetupInProgress();
        if (mIsUserSetupInProgress) {
            notifySuwStateEvent();
        }
        mCarDeviceProvisionedController.addCallback(mCarDeviceProvisionedListener);
    }

    private void handleUserUnlocked(UserHandle userHandle) {
        if (userHandle.isSystem()) {
            return;
        }
        int userId = userHandle.getIdentifier();

        if (shouldResetPanels()) {
            logIfDebuggable("Resetting panels during user unlock");
            if (!Flags.homeActivityAlwaysPresent()) {
                Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                homeIntent.addCategory(Intent.CATEGORY_HOME);
                homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setAvoidMoveToFront();
                mContext.startActivityAsUser(homeIntent, options.toBundle(), userHandle);
            }
            StateManager.handlePanelReset();
            addUserFlag(userId, USER_FLAG_RESET);
        } else {
            logIfDebuggable("Received user unlock while user is not setup");
        }

        handleSuwLaunchIfNecessary(userId);

        if (mIsKeyguardShowing) {
            return;
        }
        mEventDispatcher.executeEvent(getUserAuthEvent());
        addUserFlag(userId, USER_FLAG_SWITCHED);
    }

    private void handleSuwLaunchIfNecessary(int userId) {
        if (!mFlagManager.isEnabled(Flag.ScalableUiNoSuwHome)) {
            return;
        }
        if (isUserFlagSet(userId, USER_FLAG_SUW_LAUNCHED)) {
            return;
        }
        if (!mCarDeviceProvisionedController.isUserSetup(userId)) {
            Intent suwIntent = new Intent(Intent.ACTION_MAIN);
            suwIntent.addCategory(Intent.CATEGORY_SETUP_WIZARD);
            suwIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (!isIntentAvailableForUser(suwIntent, mUserTracker.getUserHandle())) {
                Log.w(TAG, "SUW not currently available for user " + userId);
                return;
            }
            logIfDebuggable("Launch SUW intent for non-setup user");
            mContext.startActivityAsUser(suwIntent,
                    mUserTracker.getUserHandle());
            addUserFlag(mUserTracker.getUserId(), USER_FLAG_SUW_LAUNCHED);
        } else if (mIsUserSetupInProgress) {
            // This is an unintended state - send the home event to the SUW
            // to get it to reset itself.
            Log.e(TAG, "Unlocked while SUW is already in progress");
            Intent suwIntent = new Intent(Intent.ACTION_MAIN);
            suwIntent.addCategory(Intent.CATEGORY_SETUP_WIZARD);
            ResolveInfo info = mContext.getPackageManager()
                    .resolveActivityAsUser(suwIntent, 0,
                            mUserTracker.getUserId());
            if (info != null && info.activityInfo != null) {
                Intent suwHomeIntent = new Intent(Intent.ACTION_MAIN);
                suwHomeIntent.addCategory(Intent.CATEGORY_HOME);
                suwHomeIntent.setPackage(info.activityInfo.packageName);
                mContext.startActivityAsUser(suwHomeIntent,
                        mUserTracker.getUserHandle());
            }
        }
    }

    @SuppressLint("MissingPermission")
    private boolean isHomeRoleExist(int userId) {
        if (mRoleManager == null) {
            Log.e(TAG, "RoleManager is null");
            return false;
        }
        List<String> holders = mRoleManager.getRoleHoldersAsUser(ROLE_HOME,
                UserHandle.of(userId));
        return !holders.isEmpty();
    }

    private boolean isIntentAvailableForUser(Intent intent, UserHandle userHandle) {
        List<ResolveInfo> resolvedApps = mContext.getPackageManager().queryIntentActivitiesAsUser(
                intent,
                /* flags= */ 0,
                userHandle);
        return !resolvedApps.isEmpty();
    }

    private void registerUserEventListener() {
        mCarServiceProvider.addListener(car -> {
            mCarUserManager = car.getCarManager(CarUserManager.class);
            if (mCarUserManager != null) {
                UserHandle userHandle = mUserTracker.getUserHandle();
                if (mUserManager.isUserUnlocked(userHandle)) {
                    handleUserUnlocked(userHandle);
                }
                mCarUserManager.addListener(mBackgroundExecutor, mUserLifecycleListener);
            }
        });
    }

    private void registerKeyguardStateListener() {
        if (isKeyguardShowing()) {
            keyguardShowingChanged(true);
        }
        mKeyguardStateController.addCallback(new KeyguardStateController.Callback() {
            @Override
            public void onKeyguardShowingChanged() {
                keyguardShowingChanged(isKeyguardShowing());
            }
        });
    }

    private void keyguardShowingChanged(boolean showing) {
        if (mIsKeyguardShowing == showing) {
            return;
        }
        mIsKeyguardShowing = showing;
        if (mIsKeyguardShowing) {
            mEventDispatcher.executeEvent(
                    getEventWithDisplays(new Event.Builder(SYSTEM_KEYGUARD_SHOWN_EVENT_ID)));
        } else {
            ArrayList<Event> eventsToSend = new ArrayList<>();
            eventsToSend.add(
                    getEventWithDisplays(new Event.Builder(SYSTEM_KEYGUARD_HIDDEN_EVENT_ID)));

            if (mUserManager.isUserUnlocked(mUserTracker.getUserId())) {
                eventsToSend.add(getUserAuthEvent());
                addUserFlag(mUserTracker.getUserId(), USER_FLAG_SWITCHED);
            }
            mEventDispatcher.executeEvents(eventsToSend);
        }
    }

    private boolean isKeyguardShowing() {
        return mKeyguardStateController.isShowing();
    }

    private void registerUxrListener() {
        mCarUxRestrictionsUtil.register(mUxrListener);
        if (mCarUxRestrictionsUtil.getCurrentRestrictions().isRequiresDistractionOptimization()) {
            // Send event for initial state
            mIsUxrRestricted = true;
            mEventDispatcher.executeEvent(getEventWithDisplays(
                    new Event.Builder(SYSTEM_UXR_STATE_CHANGED_EVENT_ID).addToken(
                            SYSTEM_UXR_RESTRICTED_TOKEN_ID,
                            Boolean.toString(mIsUxrRestricted))));
        }
    }

    private boolean shouldResetPanels() {
        return mCarDeviceProvisionedController.isUserSetup(mUserTracker.getUserId())
                && !isUserFlagSet(mUserTracker.getUserId(), USER_FLAG_RESET);
    }

    private Event getEventWithDisplays(Event.Builder builder) {
        return builder.addApplicableDisplays(
                Arrays.stream(mDisplayTracker.getAllDisplays())
                        .map(Display::getDisplayId)
                        .collect(Collectors.toList())).build();
    }

    private Event getUserAuthEvent() {
        boolean isUserSwitching = !isUserFlagSet(mUserTracker.getUserId(), USER_FLAG_SWITCHED);
        return getEventWithDisplays(new Event.Builder(
                SYSTEM_USER_AUTHENTICATED_EVENT_ID)
                .addToken(SYSTEM_USER_SWITCH_ON_AUTHENTICATED_TOKEN_ID,
                        Boolean.toString(isUserSwitching)));
    }

    private void addUserFlag(int userId, int flag) {
        mUserFlags.compute(userId,
                (k, v) -> (v == null) ? flag : v | flag);
    }

    private boolean isUserFlagSet(int userId, int flag) {
        if (!mUserFlags.containsKey(userId)) {
            return false;
        }
        return (mUserFlags.get(userId) & flag) != 0;
    }

    private static void logIfDebuggable(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }
}
