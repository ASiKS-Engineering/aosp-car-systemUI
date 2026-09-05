/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.car.userswitcher;

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.android.car.admin.ui.UserAvatarView;
import com.android.car.internal.user.UserHelper;
import com.android.internal.util.UserIcons;
import com.android.systemui.car.shared.R;
import com.android.systemui.dagger.SysUISingleton;

import javax.inject.Inject;

/**
 * Simple class for providing icons for users.
 */
@SysUISingleton
public class UserIconProvider {
    private final Context mContext;
    private final UserManager mUserManager;

    private final float mBadgeToIconSizeRatio;
    private final float mBadgePadding;

    @Inject
    public UserIconProvider(Context context, UserManager userManager) {
        mContext = context;
        mUserManager = userManager;

        mBadgeToIconSizeRatio =
                mContext.getResources().getDimension(R.dimen.car_user_switcher_managed_badge_size)
                        / mContext.getResources().getDimension(
                        R.dimen.car_user_switcher_image_avatar_size);
        mBadgePadding = mContext.getResources().getDimension(
                R.dimen.car_user_switcher_managed_badge_margin);
    }

    /**
     * Sets a rounded icon with the first letter of the given user name.
     * This method will update UserManager to use that icon.
     *
     * @param userId User for which the icon is requested.
     */
    public void setRoundedUserIcon(@UserIdInt int userId) {
        UserHelper.assignDefaultIcon(mContext, UserHandle.of(userId));
    }

    /**
     * Gets a scaled rounded icon for the given user.  If a user does not have an icon saved, this
     * method will default to a generic icon and update UserManager to use that icon.
     *
     * @param userId User for which the icon is requested.
     * @return {@link RoundedBitmapDrawable} representing the icon for the user.
     */
    public Drawable getRoundedUserIcon(@UserIdInt int userId) {
        Resources res = mContext.getResources();
        Bitmap icon = mUserManager.getUserIcon(userId);

        if (icon == null) {
            icon = UserHelper.assignDefaultIcon(mContext, UserHandle.of(userId));
        }

        return new BitmapDrawable(res, icon);
    }

    /**
     * Gets a user icon with badge if the user profile is managed.
     *
     * @param userId User for which the icon is requested and badge is set
     * @return {@link Drawable} with badge
     */
    public Drawable getDrawableWithBadge(@UserIdInt int userId) {
        return addBadge(getRoundedUserIcon(userId), userId);
    }

    /**
     * Gets an icon with badge if the device is managed.
     *
     * @param drawable icon without badge
     * @return {@link Drawable} with badge
     */
    public Drawable getDrawableWithBadge(Drawable drawable) {
        return addBadge(drawable, UserHandle.USER_NULL);
    }

    private Drawable addBadge(Drawable drawable, @UserIdInt int userId) {
        int iconSize = drawable.getIntrinsicWidth();
        UserAvatarView userAvatarView = new UserAvatarView(mContext);
        userAvatarView.setBadgeDiameter(iconSize * mBadgeToIconSizeRatio);
        userAvatarView.setBadgeMargin(mBadgePadding);
        if (userId != UserHandle.USER_NULL) {
            // When the userId is valid, add badge if the user is managed.
            userAvatarView.setDrawableWithBadge(drawable, userId);
        } else {
            // When the userId is not valid, add badge if the device is managed.
            userAvatarView.setDrawableWithBadge(drawable);
        }
        Drawable badgedIcon = userAvatarView.getUserIconDrawable();
        badgedIcon.setBounds(0, 0, iconSize, iconSize);
        return badgedIcon;
    }

    /** Returns a scaled, rounded, default icon for the Guest user */
    public Drawable getRoundedGuestDefaultIcon() {
        Bitmap icon = UserHelper.getGuestDefaultIcon(mContext);
        return new BitmapDrawable(mContext.getResources(), icon);
    }

    /** Returns a scaled, rounded, default icon for the add user entry. */
    public Drawable getRoundedAddUserIcon() {
        RoundedBitmapDrawable roundedIcon = RoundedBitmapDrawableFactory.create(
                mContext.getResources(),
                UserIcons.convertToBitmap(mContext.getDrawable(R.drawable.car_add_circle_round)));
        roundedIcon.setCircular(true);
        return roundedIcon;
    }
}
