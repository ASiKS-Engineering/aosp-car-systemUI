/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.systemui.car.systembar.privacy.base;

import static com.android.car.qc.QCItem.QC_TYPE_ACTION_SWITCH;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.text.BidiFormatter;

import com.android.car.qc.QCActionItem;
import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.qc.provider.BaseLocalQCProvider;
import com.android.systemui.car.shared.R;
import com.android.systemui.privacy.PrivacyDialogDelegate;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A {@link BaseLocalQCProvider} that builds the sensor (such as microphone or camera) privacy
 * panel.
 */
public abstract class SensorQcPanel extends BaseLocalQCProvider
        implements SensorInfoUpdateListener {
    private static final String TAG = "SensorQcPanel";

    private final String mPhoneCallTitle;

    protected Icon mSensorOnIcon;
    protected String mSensorOnTitleText;
    protected Icon mSensorOffIcon;
    protected String mSensorOffTitleText;
    protected String mSensorSubtitleText;
    private SensorPrivacyInfoProvider mSensorInfoProvider;

    public SensorQcPanel(Context context, SensorPrivacyInfoProvider infoProvider) {
        super(context);
        mSensorInfoProvider = infoProvider;
        mPhoneCallTitle = context.getString(
                com.android.systemui.res.R.string.ongoing_privacy_dialog_phonecall);
        mSensorOnTitleText = context.getString(R.string.privacy_chip_use_sensor, getSensorName());
        mSensorOffTitleText = context.getString(R.string.privacy_chip_off_content,
                getSensorNameWithFirstLetterCapitalized());
        mSensorSubtitleText = context.getString(R.string.privacy_chip_use_sensor_subtext);

        mSensorOnIcon = Icon.createWithResource(context, getSensorOnIconResourceId());
        mSensorOffIcon = Icon.createWithResource(context, getSensorOffIconResourceId());
    }

    @Override
    public QCItem getQCItem() {
        if (mSensorInfoProvider == null) {
            return null;
        }

        QCList.Builder listBuilder = new QCList.Builder();
        listBuilder.addRow(createSensorToggleRow(mSensorInfoProvider.isSensorEnabled()));

        List<PrivacyDialogDelegate.PrivacyElement> elements =
                mSensorInfoProvider.getPrivacyElements();

        List<PrivacyDialogDelegate.PrivacyElement> activeElements = elements.stream()
                .filter(PrivacyDialogDelegate.PrivacyElement::getActive)
                .collect(Collectors.toList());
        addPrivacyElementsToQcList(listBuilder, activeElements);

        List<PrivacyDialogDelegate.PrivacyElement> inactiveElements = elements.stream()
                .filter(privacyElement -> !privacyElement.getActive())
                .collect(Collectors.toList());
        addPrivacyElementsToQcList(listBuilder, inactiveElements);

        return listBuilder.build();
    }

    private Optional<ApplicationInfo> getApplicationInfo(
            PrivacyDialogDelegate.PrivacyElement element) {
        return getApplicationInfo(element.getPackageName(), element.getUserId());
    }

    private Optional<ApplicationInfo> getApplicationInfo(String packageName, int userId) {
        ApplicationInfo applicationInfo;
        try {
            applicationInfo = mContext.getPackageManager()
                    .getApplicationInfoAsUser(packageName, /* flags= */ 0, userId);
            return Optional.of(applicationInfo);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Application info not found for: " + packageName);
            return Optional.empty();
        }
    }

    private QCRow createSensorToggleRow(boolean isMicEnabled) {
        QCActionItem actionItem = new QCActionItem.Builder(QC_TYPE_ACTION_SWITCH)
                .setChecked(isMicEnabled)
                .build();
        actionItem.setActionHandler(new SensorToggleActionHandler(mSensorInfoProvider));

        return new QCRow.Builder()
                .setIcon(isMicEnabled ? mSensorOnIcon : mSensorOffIcon)
                .setIconTintable(false)
                .setTitle(isMicEnabled ? mSensorOnTitleText : mSensorOffTitleText)
                .setSubtitle(mSensorSubtitleText)
                .addEndItem(actionItem)
                .build();
    }

    private void addPrivacyElementsToQcList(QCList.Builder listBuilder,
            List<PrivacyDialogDelegate.PrivacyElement> elements) {
        for (int i = 0; i < elements.size(); i++) {
            PrivacyDialogDelegate.PrivacyElement element = elements.get(i);
            Optional<ApplicationInfo> applicationInfo = getApplicationInfo(element);
            if (!applicationInfo.isPresent()) continue;

            String appName = element.getPhoneCall()
                    ? mPhoneCallTitle
                    : getAppLabel(applicationInfo.get(), mContext);

            String title;
            if (element.getActive()) {
                title = mContext.getString(R.string.privacy_chip_app_using_sensor_suffix,
                        appName, getSensorShortName());
            } else {
                if (i == elements.size() - 1) {
                    title = mContext
                            .getString(R.string.privacy_chip_app_recently_used_sensor_suffix,
                                    appName, getSensorShortName());
                } else {
                    title = mContext
                            .getString(R.string.privacy_chip_apps_recently_used_sensor_suffix,
                                    appName, elements.size() - 1 - i, getSensorShortName());
                }
            }

            listBuilder.addRow(new QCRow.Builder()
                    .setIcon(loadAppIcon(applicationInfo.get()))
                    .setIconTintable(false)
                    .setTitle(title)
                    .build());

            if (!element.getActive()) return;
        }
    }

    protected String getSensorShortName() {
        return null;
    }

    protected String getSensorName() {
        return null;
    }

    protected String getSensorNameWithFirstLetterCapitalized() {
        return null;
    }

    protected abstract @DrawableRes int getSensorOnIconResourceId();

    protected abstract @DrawableRes int getSensorOffIconResourceId();

    private String getAppLabel(@NonNull ApplicationInfo applicationInfo, @NonNull Context context) {
        return BidiFormatter.getInstance()
                .unicodeWrap(applicationInfo.loadSafeLabel(context.getPackageManager(),
                                /* ellipsizeDip= */ 0,
                                /* flags= */ TextUtils.SAFE_STRING_FLAG_TRIM
                                        | TextUtils.SAFE_STRING_FLAG_FIRST_LINE)
                        .toString());
    }

    private Icon loadAppIcon(@NonNull ApplicationInfo appInfo) {
        UserHandle user = UserHandle.getUserHandleForUid(appInfo.uid);
        Context userContext = mContext.createContextAsUser(user, /* flags= */ 0);
        PackageManager pm = userContext.getPackageManager();
        Drawable appIcon = pm.getApplicationIcon(appInfo);
        return Icon.createWithBitmap(createBitmapFromDrawable(pm.getUserBadgedIcon(appIcon, user)));
    }

    private static Bitmap createBitmapFromDrawable(Drawable drawable) {
        Bitmap bitmap;
        if (drawable instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) drawable).getBitmap();
        } else {
            int width = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 1;
            int height = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 1;
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }
        return bitmap;
    }

    @Override
    public void onSensorPrivacyChanged() {
        notifyChange();
    }

    @Override
    public void onPrivacyItemsChanged() {
        notifyChange();
    }

    @Override
    protected void onSubscribed() {
        mSensorInfoProvider.setSensorInfoUpdateListener(this);
    }

    @Override
    protected void onUnsubscribed() {
        mSensorInfoProvider.setSensorInfoUpdateListener(null);
    }

    private static class SensorToggleActionHandler implements QCItem.ActionHandler {
        private final SensorPrivacyInfoProvider mSensorInfoProvider;

        SensorToggleActionHandler(SensorPrivacyInfoProvider sensorInfoProvider) {
            this.mSensorInfoProvider = sensorInfoProvider;
        }

        @Override
        public void onAction(@NonNull QCItem item, @NonNull Context context,
                @NonNull Intent intent) {
            mSensorInfoProvider.toggleSensor();
        }
    }
}
