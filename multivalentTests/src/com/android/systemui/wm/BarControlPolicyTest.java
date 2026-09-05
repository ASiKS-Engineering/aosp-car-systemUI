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

package com.android.systemui.wm;

import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;

import static com.android.systemui.car.Flags.FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY;

import static com.google.common.truth.Truth.assertThat;

import android.car.settings.CarSettings;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.provider.Settings;
import android.testing.TestableLooper.RunWithLooper;
import android.testing.TestableResources;
import android.view.WindowInsets.Type.InsetsType;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.shared.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@RunWithLooper
@SmallTest
public class BarControlPolicyTest extends CarSysuiTestCase {

    private static final String PACKAGE_NAME = "sample.app";
    private static final String PACKAGE_NAME2 = "sample2.app";

    @InsetsType
    private static final int REQUESTED_VISIBILITY_IRRELEVANT = 0;
    @InsetsType
    private static final int REQUESTED_VISIBILITY_HIDE_ALL_BARS = 0;
    @InsetsType
    private static final int REQUESTED_VISIBILITY_STATUS_BARS = statusBars();
    @InsetsType
    private static final int REQUESTED_VISIBILITY_NAVIGATION_BARS = navigationBars();
    @InsetsType
    private static final int REQUESTED_VISIBILITY_SHOW_ALL_BARS = statusBars() | navigationBars();
    private BarControlPolicy mBarControlPolicy = new BarControlPolicy();

    @Before
    public void setUp() {
        mBarControlPolicy.reset();
    }

    @After
    public void tearDown() {
        Settings.Global.clearProviderForTest();
    }

    @Test
    @DisableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities_nullPackageName_showsSystemBars() {
        BarVisibility visibilities = mBarControlPolicy.getBarVisibilities(null);

        assertThat(visibilities.getShowTypes()).isEqualTo(statusBars() | navigationBars());
        assertThat(visibilities.getHideTypes()).isEqualTo(0);
    }

    @Test
    @EnableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities2_nullPackageName_showsSystemBars() {
        BarVisibility visibilities =
                mBarControlPolicy.getBarVisibilities(null, REQUESTED_VISIBILITY_IRRELEVANT);

        assertThat(visibilities.getShowTypes()).isEqualTo(statusBars() | navigationBars());
        assertThat(visibilities.getHideTypes()).isEqualTo(0);
    }

    @Test
    public void reloadFromSetting_notSet_doesNotSetFilters() {
        mBarControlPolicy.reloadFromSetting(mContext);

        assertThat(mBarControlPolicy.getImmersiveStatusFilter()).isNull();
    }

    @Test
    public void reloadFromSetting_invalidPolicyControlString_doesNotSetFilters() {
        configureBarPolicy("sample text");

        assertThat(mBarControlPolicy.getImmersiveStatusFilter()).isNull();
    }

    @Test
    public void reloadFromSetting_validPolicyControlString_setsFilters() {
        configureBarPolicy("immersive.status=" + PACKAGE_NAME);

        assertThat(mBarControlPolicy.getImmersiveStatusFilter()).isNotNull();
    }

    @Test
    public void reloadFromSetting_filtersSet_doesNotSetFiltersAgain() {
        configureBarPolicy("immersive.status=" + PACKAGE_NAME);

        assertThat(mBarControlPolicy.reloadFromSetting(mContext)).isFalse();
    }

    @Test
    @DisableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities_policyControlNotSet_showsSystemBars() {
        BarVisibility visibilities = mBarControlPolicy.getBarVisibilities(PACKAGE_NAME);

        assertThat(visibilities.getShowTypes()).isEqualTo(statusBars() | navigationBars());
        assertThat(visibilities.getHideTypes()).isEqualTo(0);
    }

    @Test
    @DisableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities_immersiveStatusForAppAndMatchingApp_hidesStatusBar() {
        configureBarPolicy("immersive.status=" + PACKAGE_NAME);

        BarVisibility visibilities = mBarControlPolicy.getBarVisibilities(PACKAGE_NAME);

        assertThat(visibilities.getShowTypes()).isEqualTo(navigationBars());
        assertThat(visibilities.getHideTypes()).isEqualTo(statusBars());
    }

    @Test
    @DisableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities_immersiveStatusForAppAndNonMatchingApp_showsSystemBars() {
        configureBarPolicy("immersive.status=" + PACKAGE_NAME);

        BarVisibility visibilities = mBarControlPolicy.getBarVisibilities(PACKAGE_NAME2);

        assertThat(visibilities.getShowTypes()).isEqualTo(statusBars() | navigationBars());
        assertThat(visibilities.getHideTypes()).isEqualTo(0);
    }

    @Test
    @DisableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities_immersiveStatusForAppsAndNonApp_showsSystemBars() {
        configureBarPolicy("immersive.status=apps");

        BarVisibility visibilities = mBarControlPolicy.getBarVisibilities(PACKAGE_NAME);

        assertThat(visibilities.getShowTypes()).isEqualTo(statusBars() | navigationBars());
        assertThat(visibilities.getHideTypes()).isEqualTo(0);
    }

    @Test
    @DisableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities_immersiveFullForAppAndMatchingApp_hidesSystemBars() {
        configureBarPolicy("immersive.full=" + PACKAGE_NAME);

        BarVisibility visibilities = mBarControlPolicy.getBarVisibilities(PACKAGE_NAME);

        assertThat(visibilities.getShowTypes()).isEqualTo(0);
        assertThat(visibilities.getHideTypes()).isEqualTo(statusBars() | navigationBars());
    }

    @Test
    @DisableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities_immersiveFullForAppAndNonMatchingApp_showsSystemBars() {
        configureBarPolicy("immersive.full=" + PACKAGE_NAME);

        BarVisibility visibilities = mBarControlPolicy.getBarVisibilities(PACKAGE_NAME2);

        assertThat(visibilities.getShowTypes()).isEqualTo(statusBars() | navigationBars());
        assertThat(visibilities.getHideTypes()).isEqualTo(0);
    }

    @Test
    @DisableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities_immersiveFullForAppsAndNonApp_showsSystemBars() {
        configureBarPolicy("immersive.full=apps");

        BarVisibility visibilities = mBarControlPolicy.getBarVisibilities(PACKAGE_NAME);

        assertThat(visibilities.getShowTypes()).isEqualTo(statusBars() | navigationBars());
        assertThat(visibilities.getHideTypes()).isEqualTo(0);
    }

    @Test
    @EnableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities2_policyControlNotSet_showsSystemBars() {
        BarVisibility visibilities =
                mBarControlPolicy.getBarVisibilities(PACKAGE_NAME, REQUESTED_VISIBILITY_IRRELEVANT);

        assertThat(visibilities.getShowTypes()).isEqualTo(statusBars() | navigationBars());
        assertThat(visibilities.getHideTypes()).isEqualTo(0);
    }

    @Test
    @EnableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities2_immersiveStatusForAppAndMatchingApp_hidesStatusBar() {
        configureBarPolicy("immersive.status=" + PACKAGE_NAME);

        BarVisibility visibilities =
                mBarControlPolicy.getBarVisibilities(PACKAGE_NAME, REQUESTED_VISIBILITY_IRRELEVANT);

        assertThat(visibilities.getShowTypes()).isEqualTo(navigationBars());
        assertThat(visibilities.getHideTypes()).isEqualTo(statusBars());
    }

    @Test
    @EnableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities2_immersiveStatusForAppAndNonMatchingApp_showsSystemBars() {
        configureBarPolicy("immersive.status=" + PACKAGE_NAME);

        BarVisibility visibilities =
                mBarControlPolicy.getBarVisibilities(PACKAGE_NAME2,
                        REQUESTED_VISIBILITY_IRRELEVANT);

        assertThat(visibilities.getShowTypes()).isEqualTo(statusBars() | navigationBars());
        assertThat(visibilities.getHideTypes()).isEqualTo(0);
    }

    @Test
    @EnableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities2_immersiveStatusForAppsAndNonApp_showsSystemBars() {
        configureBarPolicy("immersive.status=apps");

        BarVisibility visibilities =
                mBarControlPolicy.getBarVisibilities(PACKAGE_NAME, REQUESTED_VISIBILITY_IRRELEVANT);

        assertThat(visibilities.getShowTypes()).isEqualTo(statusBars() | navigationBars());
        assertThat(visibilities.getHideTypes()).isEqualTo(0);
    }

    @Test
    @EnableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities2_immersiveFullForAppAndMatchingApp_hidesSystemBars() {
        configureBarPolicy("immersive.full=" + PACKAGE_NAME);

        BarVisibility visibilities =
                mBarControlPolicy.getBarVisibilities(PACKAGE_NAME, REQUESTED_VISIBILITY_IRRELEVANT);

        assertThat(visibilities.getShowTypes()).isEqualTo(0);
        assertThat(visibilities.getHideTypes()).isEqualTo(statusBars() | navigationBars());
    }

    @Test
    @EnableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities2_immersiveFullForAppAndNonMatchingApp_showsSystemBars() {
        configureBarPolicy("immersive.full=" + PACKAGE_NAME);

        BarVisibility visibilities =
                mBarControlPolicy.getBarVisibilities(PACKAGE_NAME2,
                        REQUESTED_VISIBILITY_IRRELEVANT);

        assertThat(visibilities.getShowTypes()).isEqualTo(statusBars() | navigationBars());
        assertThat(visibilities.getHideTypes()).isEqualTo(0);
    }

    @Test
    @EnableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities2_immersiveFullForAppsAndNonApp_showsSystemBars() {
        configureBarPolicy("immersive.full=apps");

        BarVisibility visibilities =
                mBarControlPolicy.getBarVisibilities(PACKAGE_NAME, REQUESTED_VISIBILITY_IRRELEVANT);

        assertThat(visibilities.getShowTypes()).isEqualTo(statusBars() | navigationBars());
        assertThat(visibilities.getHideTypes()).isEqualTo(0);
    }

    @Test
    @EnableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities_immersiveStatusWithAllowPolicy_allowsShowStatus() {
        configureBarPolicy("immersive.status=+" + PACKAGE_NAME);

        BarVisibility visibilitiesShowStatus = mBarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_STATUS_BARS);

        assertThat(barsShown(visibilitiesShowStatus, navigationBars() | statusBars())).isTrue();
        assertThat(barsHidden(visibilitiesShowStatus, /* barTypes= */ 0)).isTrue();

        BarVisibility visibilitiesShowAllBars = mBarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_SHOW_ALL_BARS);

        assertThat(barsShown(visibilitiesShowAllBars, navigationBars() | statusBars())).isTrue();
        assertThat(barsHidden(visibilitiesShowAllBars, /* barTypes= */ 0)).isTrue();
    }

    @Test
    @EnableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities_immersiveStatusWithAllowPolicy_allowsHideStatus() {
        configureBarPolicy("immersive.status=+" + PACKAGE_NAME);

        BarVisibility visibilitiesShowStatus = mBarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_NAVIGATION_BARS);

        assertThat(barsShown(visibilitiesShowStatus, navigationBars())).isTrue();
        assertThat(barsHidden(visibilitiesShowStatus, statusBars())).isTrue();

        BarVisibility visibilitiesShowAllBars = mBarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_HIDE_ALL_BARS);

        assertThat(barsShown(visibilitiesShowAllBars, navigationBars())).isTrue();
        assertThat(barsHidden(visibilitiesShowAllBars, statusBars())).isTrue();
    }

    @Test
    @EnableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities_immersiveNavigationWithAllowPolicy_allowsShowNavigation() {
        configureBarPolicy("immersive.navigation=+" + PACKAGE_NAME);

        BarVisibility visibilitiesShowStatus = mBarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_NAVIGATION_BARS);

        assertThat(barsShown(visibilitiesShowStatus, navigationBars() | statusBars())).isTrue();
        assertThat(barsHidden(visibilitiesShowStatus, /* barTypes= */ 0)).isTrue();

        BarVisibility visibilitiesShowAllBars = mBarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_SHOW_ALL_BARS);

        assertThat(barsShown(visibilitiesShowAllBars, navigationBars() | statusBars())).isTrue();
        assertThat(barsHidden(visibilitiesShowAllBars, /* barTypes= */ 0)).isTrue();
    }

    @Test
    @EnableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities_immersiveNavigationWithAllowPolicy_allowsHideNavigation() {
        configureBarPolicy("immersive.navigation=+" + PACKAGE_NAME);

        BarVisibility visibilitiesShowStatus = mBarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_STATUS_BARS);

        assertThat(barsShown(visibilitiesShowStatus, statusBars())).isTrue();
        assertThat(barsHidden(visibilitiesShowStatus, navigationBars())).isTrue();

        BarVisibility visibilitiesShowAllBars = mBarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_SHOW_ALL_BARS);

        assertThat(barsShown(visibilitiesShowAllBars, navigationBars() | statusBars())).isTrue();
        assertThat(barsHidden(visibilitiesShowAllBars, /* barTypes= */ 0)).isTrue();
    }

    @Test
    @EnableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities_immersiveFullWithAllowPolicy_allowsShowAndHideBars() {
        configureBarPolicy("immersive.full=+" + PACKAGE_NAME);

        BarVisibility visibilitiesShowStatus = mBarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_SHOW_ALL_BARS);

        assertThat(barsShown(visibilitiesShowStatus, navigationBars() | statusBars())).isTrue();
        assertThat(barsHidden(visibilitiesShowStatus, /* barTypes= */ 0)).isTrue();

        BarVisibility visibilitiesShowAllBars = mBarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_HIDE_ALL_BARS);

        assertThat(barsShown(visibilitiesShowAllBars, /* barTypes= */ 0)).isTrue();
        assertThat(barsHidden(visibilitiesShowAllBars, navigationBars() | statusBars())).isTrue();
    }

    @Test
    @EnableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities_combinedImmersiveStatusWithAllowPolicy_hidesSelectively() {
        configureBarPolicy(String.format("immersive.status=%s,+%s", PACKAGE_NAME, PACKAGE_NAME2));

        BarVisibility visibilities0 = mBarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_SHOW_ALL_BARS);

        assertThat(barsShown(visibilities0, navigationBars())).isTrue();
        assertThat(barsHidden(visibilities0, statusBars())).isTrue();

        BarVisibility visibilities1 = mBarControlPolicy.getBarVisibilities(
                PACKAGE_NAME2, REQUESTED_VISIBILITY_SHOW_ALL_BARS);

        assertThat(barsShown(visibilities1, statusBars() | navigationBars())).isTrue();
        assertThat(barsHidden(visibilities1, /* barTypes= */ 0)).isTrue();
    }

    @Test
    @EnableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void getBarVisibilities_combinedImmersiveNavigationWithAllowPolicy_hidesSelectively() {
        configureBarPolicy(
                String.format("immersive.navigation=+%s,%s", PACKAGE_NAME, PACKAGE_NAME2));

        BarVisibility visibilities0 = mBarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_SHOW_ALL_BARS);

        assertThat(barsShown(visibilities0, statusBars() | navigationBars())).isTrue();
        assertThat(barsHidden(visibilities0, /* barTypes= */ 0)).isTrue();

        BarVisibility visibilities1 = mBarControlPolicy.getBarVisibilities(
                PACKAGE_NAME2, REQUESTED_VISIBILITY_SHOW_ALL_BARS);

        assertThat(barsShown(visibilities1, statusBars())).isTrue();
        assertThat(barsHidden(visibilities1, navigationBars())).isTrue();
    }

    @Test
    @EnableFlags(FLAG_PACKAGE_LEVEL_SYSTEM_BAR_VISIBILITY)
    public void reloadFromSetting_settingNotSet_loadsBackupPolicy() {
        TestableResources testableResources = mContext.getOrCreateTestableResources();

        // GIVEN system bar persistency is set to barpolicy and the flag is enabled
        testableResources.addOverride(R.integer.config_systemBarPersistency, 3);
        String backupPolicy = "immersive.full=*";
        testableResources.addOverride(
                R.string.system_bar_visibility_override_backup, backupPolicy);
        Settings.Global.putString(mContext.getContentResolver(),
                CarSettings.Global.SYSTEM_BAR_VISIBILITY_OVERRIDE, null);

        // WHEN reloadFromSetting is called
        boolean reloaded = mBarControlPolicy.reloadFromSetting(mContext);

        // THEN the backup policy is loaded and the method returns true
        assertThat(reloaded).isTrue();
        assertThat(mBarControlPolicy.getSettingValue()).isEqualTo(backupPolicy);
        assertThat(mBarControlPolicy.getImmersiveStatusFilter()).isNotNull();
        assertThat(mBarControlPolicy.getImmersiveNavigationFilter()).isNotNull();
    }

    private void configureBarPolicy(String configuration) {
        Settings.Global.putString(
                mContext.getContentResolver(),
                CarSettings.Global.SYSTEM_BAR_VISIBILITY_OVERRIDE,
                configuration);
        mBarControlPolicy.reloadFromSetting(mContext);
    }

    private static boolean barsShown(BarVisibility visibilities,   int barTypes) {
        return visibilities.getShowTypes() == barTypes;
    }

    private static boolean barsHidden(BarVisibility visibilities, @InsetsType int barTypes) {
        return visibilities.getHideTypes() == barTypes;
    }
}
