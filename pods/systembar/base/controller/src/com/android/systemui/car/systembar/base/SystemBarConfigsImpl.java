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
package com.android.systemui.car.systembar.base;

import static android.view.WindowInsets.Type.mandatorySystemGestures;
import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import static com.android.car.scalableui.loader.xml.parser.SystemBarParser.TYPE_NAVIGATION;
import static com.android.car.scalableui.loader.xml.parser.SystemBarParser.TYPE_STATUS;
import static com.android.systemui.car.systembar.SystemBarConstants.BOTTOM_BAR_NAME;
import static com.android.systemui.car.systembar.SystemBarConstants.LEFT_BAR_NAME;
import static com.android.systemui.car.systembar.SystemBarConstants.NAVIGATION_BAR;
import static com.android.systemui.car.systembar.SystemBarConstants.RIGHT_BAR_NAME;
import static com.android.systemui.car.systembar.SystemBarConstants.STATUS_BAR;
import static com.android.systemui.car.systembar.SystemBarConstants.TOP_BAR_NAME;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.Log;
import android.view.Gravity;
import android.view.InsetsFrameProvider;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.scalableui.loader.xml.parser.SystemBarParser;
import com.android.car.scalableui.model.Corner;
import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.car.shared.R;
import com.android.systemui.car.wm.scalableui.configuration.SystemBarConfiguration;
import com.android.systemui.car.wm.scalableui.systemwindow.SystemBarWindow;
import com.android.systemui.car.wm.scalableui.systemwindow.SystemUiWindow;
import com.android.systemui.car.wm.scalableui.systemwindow.SystemUiWindowProvider;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.settings.DisplayTracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.inject.Inject;

/**
 * Reads configs for system bars for each side (TOP, BOTTOM, LEFT, and RIGHT) and returns the
 * corresponding {@link android.view.WindowManager.LayoutParams} per the configuration.
 */
public class SystemBarConfigsImpl implements SystemBarConfigs {
    // The z-order from which system bars will start to appear on top of HUN's.
    @VisibleForTesting
    static final int HUN_Z_ORDER = 10;
    private static final String TAG = SystemBarConfigs.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final Binder INSETS_OWNER = new Binder();

    /*
        NOTE: The elements' order in the map below must be preserved as-is since the correct
        corresponding values are obtained by the index.
     */
    private static final InsetsFrameProvider[] BAR_PROVIDER_MAP = {new InsetsFrameProvider(
            INSETS_OWNER, 0 /* index */, WindowInsets.Type.statusBars()), new InsetsFrameProvider(
            INSETS_OWNER, 0 /* index */, WindowInsets.Type.navigationBars()),
            new InsetsFrameProvider(INSETS_OWNER, 1 /* index */, WindowInsets.Type.statusBars()),
            new InsetsFrameProvider(INSETS_OWNER, 1 /* index */,
                    WindowInsets.Type.navigationBars())};

    private static final Map<String, Integer> BAR_GRAVITY_MAP = new ArrayMap<>();
    private static final Map<String, String> BAR_TITLE_MAP = new ArrayMap<>();
    private static final Map<String, InsetsFrameProvider> BAR_GESTURE_MAP = new ArrayMap<>();

    private final Context mContext;
    private final Resources mResources;
    private final int mDefaultDisplayId;
    private final List<String> mSystemBarNamesByZOrder = new ArrayList<>();
    /** Maps @WindowManager.LayoutParams.WindowType to window contexts for that type. */
    private final Map<Integer, Context> mWindowContexts = new ArrayMap<>();
    private final SystemUiWindowProvider mWindowProvider;
    private final Map<String, CarSystemBarViewSupplier> mViewSupplierMap;
    private final Map<String, CarSystemBarWindowSupplier> mWindowSupplierMap;
    Map<String, SystemBarWindow> mSystemBars = new ArrayMap<>();
    private boolean mTopNavBarEnabled;
    private boolean mBottomNavBarEnabled;
    private boolean mLeftNavBarEnabled;
    private boolean mRightNavBarEnabled;

    @Inject
    public SystemBarConfigsImpl(Context context, @Main Resources resources,
            SystemUiWindowProvider windowProvider,
            Map<String, CarSystemBarViewSupplier> viewSupplerMap,
            Map<String, CarSystemBarWindowSupplier> windowSupplierMap,
            DisplayTracker displayTracker) {
        mContext = context;
        mResources = resources;
        mWindowProvider = windowProvider;
        mViewSupplierMap = viewSupplerMap;
        mWindowSupplierMap = windowSupplierMap;
        mDefaultDisplayId = displayTracker.getDefaultDisplayId();
        init();
    }

    @SuppressLint("RtlHardcoded")
    private static void populateMaps() {
        BAR_GRAVITY_MAP.put(TOP_BAR_NAME, Gravity.TOP);
        BAR_GRAVITY_MAP.put(BOTTOM_BAR_NAME, Gravity.BOTTOM);
        BAR_GRAVITY_MAP.put(LEFT_BAR_NAME, Gravity.LEFT);
        BAR_GRAVITY_MAP.put(RIGHT_BAR_NAME, Gravity.RIGHT);

        BAR_GESTURE_MAP.put(TOP_BAR_NAME, new InsetsFrameProvider(INSETS_OWNER, 0 /* index */,
                WindowInsets.Type.mandatorySystemGestures()));
        BAR_GESTURE_MAP.put(BOTTOM_BAR_NAME, new InsetsFrameProvider(INSETS_OWNER, 1 /* index */,
                WindowInsets.Type.mandatorySystemGestures()));
        BAR_GESTURE_MAP.put(LEFT_BAR_NAME, new InsetsFrameProvider(INSETS_OWNER, 2 /* index */,
                WindowInsets.Type.mandatorySystemGestures()));
        BAR_GESTURE_MAP.put(RIGHT_BAR_NAME, new InsetsFrameProvider(INSETS_OWNER, 3 /* index */,
                WindowInsets.Type.mandatorySystemGestures()));
    }

    private static int mapZOrderToBarType(int zOrder) {
        return zOrder >= HUN_Z_ORDER ? WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL
                : WindowManager.LayoutParams.TYPE_STATUS_BAR_ADDITIONAL;
    }

    private void init() {
        mWindowContexts.clear();
        populateMaps();
        readConfigs();

        checkAllOverlappingBarsHaveDifferentZOrders();
        checkHideBottomBarForKeyboardConfigSync();

        setInsetPaddingsForOverlappingCorners();
        sortSystemBarTypesByZOrder();
    }

    /**
     * Invalidate cached resources and fetch from resources config file.
     * TODO: b/260206944, Can remove this after we have a fix for overlaid resources not applied.
     * <p>
     * Since SystemBarConfig is a Scoped(Dagger Singleton Annotation), We will have stale values, of
     * all the resources after the RRO is applied.
     * Another way is to remove the Scope(Singleton), but the downside is that it will be re-created
     * everytime.
     * </p>
     */
    @Override
    public void resetSystemBarConfigs() {
        init();
    }

    @Override
    public Context getWindowContextByName(@NonNull String name) {
        SystemBarWindow window = mSystemBars.get(name);
        if (window == null) {
            Log.e(TAG, "Window doesn't exist for name: " + name);
            return null;
        }
        if (window.getLayoutParams() == null) {
            Log.e(TAG, "Window LP doesn't exist for name: " + name);
            return null;
        }

        int windowType = window.getLayoutParams().type;
        if (mWindowContexts.containsKey(windowType)) {
            return mWindowContexts.get(windowType);
        }

        Context context = mContext.createWindowContext(windowType, /* options= */ null);
        mWindowContexts.put(windowType, context);
        return context;
    }

    /**
     * Returns the system bar layout. {@code null} if side is unknown.
     */
    @Override
    public ViewGroup getSystemBarLayoutByName(@NonNull String name, boolean isSetUp) {
        CarSystemBarViewSupplier supplier = mViewSupplierMap.get(name);
        if (supplier == null) {
            Log.e(TAG, "CarSystemBarViewSupplier not injected into map for: " + name);
            return null;
        }
        Context windowContext = getWindowContextByName(name);
        if (windowContext == null) {
            Log.e(TAG, "Window context doesn't exist for name: " + name);
            return null;
        }
        return supplier.getSystemBarView(windowContext, isSetUp);
    }

    /**
     * Returns the system bar window for the given side.
     */
    @Override
    public ViewGroup getWindowLayoutByName(@NonNull String name) {
        CarSystemBarWindowSupplier supplier = mWindowSupplierMap.get(name);
        if (supplier == null) {
            Log.e(TAG, "CarSystemBarWindowSupplier not injected into map for: " + name);
            return null;
        }
        Context windowContext = getWindowContextByName(name);
        if (windowContext == null) {
            Log.e(TAG, "Window context doesn't exist for name: " + name);
            return null;
        }
        return supplier.getSystemBarWindow(windowContext);
    }

    @Override
    public WindowManager.LayoutParams getLayoutParamsByName(@NonNull String name) {
        return mSystemBars.get(name) != null ? mSystemBars.get(name).getLayoutParams()
                : null;
    }

    @Override
    public boolean getEnabledStatusByName(@NonNull String name) {
        if (mSystemBars.containsKey(name)) {
            return true;
        }
        return switch (name) {
            case TOP_BAR_NAME -> mTopNavBarEnabled;
            case BOTTOM_BAR_NAME -> mBottomNavBarEnabled;
            case LEFT_BAR_NAME -> mLeftNavBarEnabled;
            case RIGHT_BAR_NAME -> mRightNavBarEnabled;
            default -> false;
        };
    }

    @Override
    public boolean getHideForKeyboardByName(@NonNull String name) {
        SystemBarWindow systemBarWindow = mSystemBars.get(name);
        return systemBarWindow != null && systemBarWindow.isHiddenForKeyboard();
    }

    @Override
    public void insetSystemBar(@NonNull String name, ViewGroup view) {
        if (mSystemBars.get(name) == null || !(mSystemBars.get(
                name) instanceof InternalSystemBarWindow)) {
            //This method only applies padding to InternalSystemBarWindow instances.
            return;
        }

        Insets insets = mSystemBars.get(name).getInsets();
        if (insets == null) {
            if (DEBUG) {
                Log.d(TAG, "Padding not set for system bar" + name);
            }
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "Set padding to side = " + name + ", to " + insets);
        }
        view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
    }

    @Override
    public List<String> getSystemBarNamesByZOrder() {
        return mSystemBarNamesByZOrder;
    }

    @Override
    public int getSystemBarInsetTypeByName(@NonNull String name) {
        SystemBarWindow systemBarWindow = mSystemBars.get(name);
        return systemBarWindow != null ? systemBarWindow.getType() : -1;
    }

    @Override
    public InsetsFrameProvider getInsetsFrameProviderByName(@NonNull String name) {
        SystemBarWindow systemBarWindow = mSystemBars.get(name);
        if (systemBarWindow == null) {
            return null;
        }
        WindowManager.LayoutParams lp = systemBarWindow.getLayoutParams();
        if (lp == null) {
            return null;
        }
        return lp.providedInsets[0];
    }

    @VisibleForTesting
    void updateInsetPaddings(String name, Map<String, Boolean> barVisibilities) {
        SystemBarWindow systemBarWindow = mSystemBars.get(name);
        if (systemBarWindow == null) return;

        int defaultLeftPadding = 0;
        int defaultRightPadding = 0;
        int defaultTopPadding = 0;
        int defaultBottomPadding = 0;

        switch (name) {
            case LEFT_BAR_NAME: {
                defaultLeftPadding = mResources.getDimensionPixelSize(
                        R.dimen.car_left_system_bar_left_padding);
                defaultRightPadding = mResources.getDimensionPixelSize(
                        R.dimen.car_left_system_bar_right_padding);
                defaultTopPadding = mResources.getDimensionPixelSize(
                        R.dimen.car_left_system_bar_top_padding);
                defaultBottomPadding = mResources.getDimensionPixelSize(
                        R.dimen.car_left_system_bar_bottom_padding);
                break;
            }
            case RIGHT_BAR_NAME: {
                defaultLeftPadding = mResources.getDimensionPixelSize(
                        R.dimen.car_right_system_bar_left_padding);
                defaultRightPadding = mResources.getDimensionPixelSize(
                        R.dimen.car_right_system_bar_right_padding);
                defaultTopPadding = mResources.getDimensionPixelSize(
                        R.dimen.car_right_system_bar_top_padding);
                defaultBottomPadding = mResources.getDimensionPixelSize(
                        R.dimen.car_right_system_bar_bottom_padding);
                break;
            }
            case TOP_BAR_NAME: {
                defaultLeftPadding = mResources.getDimensionPixelSize(
                        R.dimen.car_top_system_bar_left_padding);
                defaultRightPadding = mResources.getDimensionPixelSize(
                        R.dimen.car_top_system_bar_right_padding);
                defaultTopPadding = mResources.getDimensionPixelSize(
                        R.dimen.car_top_system_bar_top_padding);
                defaultBottomPadding = mResources.getDimensionPixelSize(
                        R.dimen.car_top_system_bar_bottom_padding);
                break;
            }
            case BOTTOM_BAR_NAME: {
                defaultLeftPadding = mResources.getDimensionPixelSize(
                        R.dimen.car_bottom_system_bar_left_padding);
                defaultRightPadding = mResources.getDimensionPixelSize(
                        R.dimen.car_bottom_system_bar_right_padding);
                defaultTopPadding = mResources.getDimensionPixelSize(
                        R.dimen.car_bottom_system_bar_top_padding);
                defaultBottomPadding = mResources.getDimensionPixelSize(
                        R.dimen.car_bottom_system_bar_bottom_padding);
                break;
            }
            default:
        }

        if (isHorizontalBar(name)) {
            if (mLeftNavBarEnabled && systemBarWindow.getZOrder() < mSystemBars.get(
                    LEFT_BAR_NAME).getZOrder()) {
                defaultLeftPadding = barVisibilities.get(LEFT_BAR_NAME) ? mSystemBars.get(
                        LEFT_BAR_NAME).getWidth() : defaultLeftPadding;
            }
            if (mRightNavBarEnabled && systemBarWindow.getZOrder() < mSystemBars.get(
                    RIGHT_BAR_NAME).getZOrder()) {
                defaultRightPadding = barVisibilities.get(RIGHT_BAR_NAME) ? mSystemBars.get(
                        RIGHT_BAR_NAME).getWidth() : defaultLeftPadding;
            }
        }
        if (isVerticalBar(name)) {
            if (mTopNavBarEnabled && systemBarWindow.getZOrder() < mSystemBars.get(
                    TOP_BAR_NAME).getZOrder()) {
                defaultTopPadding = barVisibilities.get(TOP_BAR_NAME) ? mSystemBars.get(
                        TOP_BAR_NAME).getHeight() : defaultTopPadding;
            }
            if (mBottomNavBarEnabled && systemBarWindow.getZOrder() < mSystemBars.get(
                    BOTTOM_BAR_NAME).getZOrder()) {
                defaultBottomPadding = barVisibilities.get(BOTTOM_BAR_NAME) ? mSystemBars.get(
                        BOTTOM_BAR_NAME).getHeight() : defaultBottomPadding;
            }

        }

        Insets insets = Insets.of(defaultLeftPadding, defaultTopPadding, defaultRightPadding,
                defaultBottomPadding);

        SystemUiWindow window = mSystemBars.get(name);
        if (window instanceof InternalSystemBarWindow) {
            ((InternalSystemBarWindow) window).setInsets(insets);
            if (DEBUG) {
                Log.d(TAG, "Update padding for side = " + name + " to " + insets);
            }
        }
    }

    private void readConfigs() {
        mSystemBars.clear();

        if (!mWindowProvider.getSystemBarWindows().isEmpty()) {
            mWindowProvider.getSystemBarWindows().forEach(systemUiWindow -> {
                SystemBarWindow systemBarWindow = (SystemBarWindow) systemUiWindow;
                mSystemBars.put(systemBarWindow.getName(), systemBarWindow);
            });
            return;
        }

        mTopNavBarEnabled = mResources.getBoolean(R.bool.config_enableTopSystemBar);
        mBottomNavBarEnabled = mResources.getBoolean(R.bool.config_enableBottomSystemBar);
        mLeftNavBarEnabled = mResources.getBoolean(R.bool.config_enableLeftSystemBar);
        mRightNavBarEnabled = mResources.getBoolean(R.bool.config_enableRightSystemBar);

        if (mTopNavBarEnabled) {
            int type = mResources.getInteger(R.integer.config_topSystemBarType);
            SystemBarConfiguration topBarConfig = new SystemBarConfigBuilder()
                    .setBarType(type == 0 ? STATUS_BAR : NAVIGATION_BAR)
                    .setZOrder(mResources.getInteger(R.integer.config_topSystemBarZOrder))
                    .setHideForKeyboard(
                            mResources.getBoolean(R.bool.config_hideTopSystemBarForKeyboard))
                    .setIndex(type == STATUS_BAR ? 0 : 1)
                    .setIndexOffset(type == STATUS_BAR  ? 1 : 0)
                    .setName(TOP_BAR_NAME)
                    .build();
            SystemBarWindow topBarWindow = new InternalSystemBarWindow(mContext,
                    mResources.getDimensionPixelSize(R.dimen.car_top_system_bar_height),
                    topBarConfig, this::isHorizontalBar, mDefaultDisplayId);

            mSystemBars.put(TOP_BAR_NAME, topBarWindow);
        }

        if (mBottomNavBarEnabled) {
            int type = mResources.getInteger(R.integer.config_bottomSystemBarType);
            SystemBarConfiguration bottomBarConfig = new SystemBarConfigBuilder()
                    .setBarType(type == 0 ? STATUS_BAR : NAVIGATION_BAR)
                    .setZOrder(mResources.getInteger(R.integer.config_bottomSystemBarZOrder))
                    .setHideForKeyboard(
                            mResources.getBoolean(R.bool.config_hideBottomSystemBarForKeyboard))
                    .setIndex(type == STATUS_BAR ? 0 : 1)
                    .setIndexOffset(type == STATUS_BAR  ? 1 : 0)
                    .setName(BOTTOM_BAR_NAME)
                    .build();
            SystemBarWindow bottomBarWindow = new InternalSystemBarWindow(mContext,
                    mResources.getDimensionPixelSize(R.dimen.car_bottom_system_bar_height),
                    bottomBarConfig, this::isHorizontalBar, mDefaultDisplayId);

            mSystemBars.put(BOTTOM_BAR_NAME, bottomBarWindow);
        }

        if (mLeftNavBarEnabled) {
            int type = mResources.getInteger(R.integer.config_leftSystemBarType);
            SystemBarConfiguration leftBarConfig = new SystemBarConfigBuilder()
                    .setBarType(type == 0 ? STATUS_BAR : NAVIGATION_BAR)
                    .setZOrder(mResources.getInteger(R.integer.config_leftSystemBarZOrder))
                    .setHideForKeyboard(
                            mResources.getBoolean(R.bool.config_hideLeftSystemBarForKeyboard))
                    .setIndex(type == STATUS_BAR ? 0 : 1)
                    .setIndexOffset(type == STATUS_BAR  ? 1 : 0)
                    .setName(LEFT_BAR_NAME)
                    .build();
            SystemBarWindow leftBarWindow = new InternalSystemBarWindow(mContext,
                    mResources.getDimensionPixelSize(R.dimen.car_left_system_bar_width),
                    leftBarConfig, this::isHorizontalBar, mDefaultDisplayId);

            mSystemBars.put(LEFT_BAR_NAME, leftBarWindow);
        }

        if (mRightNavBarEnabled) {
            int type = mResources.getInteger(R.integer.config_rightSystemBarType);
            SystemBarConfiguration rightBarConfig = new SystemBarConfigBuilder()
                    .setBarType(type == 0 ? STATUS_BAR : NAVIGATION_BAR)
                    .setZOrder(mResources.getInteger(R.integer.config_rightSystemBarZOrder))
                    .setHideForKeyboard(
                            mResources.getBoolean(R.bool.config_hideRightSystemBarForKeyboard))
                    .setIndex(type == STATUS_BAR ? 0 : 1)
                    .setIndexOffset(type == STATUS_BAR  ? 1 : 0)
                    .setName(RIGHT_BAR_NAME)
                    .build();
            SystemBarWindow rightBarWindow = new InternalSystemBarWindow(mContext,
                    mResources.getDimensionPixelSize(R.dimen.car_right_system_bar_width),
                    rightBarConfig, this::isHorizontalBar, mDefaultDisplayId);

            mSystemBars.put(RIGHT_BAR_NAME, rightBarWindow);
        }
    }

    private void checkAllOverlappingBarsHaveDifferentZOrders() {
        Set<String> checkedSet = new HashSet<>();
        mSystemBars.values().forEach(systemBarWindow -> {
            String name = systemBarWindow.getName();
            Rect bounds = systemBarWindow.getBounds();
            if (bounds == null) {
                return;
            }
            mSystemBars.values().forEach(systemBarWindow2 -> {
                String other = systemBarWindow2.getName();
                Rect bounds2 = systemBarWindow2.getBounds();
                if (bounds2 == null || checkedSet.contains(other) || name.equals(other)
                        || !Rect.intersects(bounds, bounds2)) {
                    return;
                }
                if (!Rect.intersects(systemBarWindow.getBounds(), bounds2)) {
                    return;
                }
                checkOverlappingBarsHaveDifferentZOrders(name, other);
            });
            checkedSet.add(name);
        });
    }

    private void checkHideBottomBarForKeyboardConfigSync() throws RuntimeException {
        if (mBottomNavBarEnabled) {
            boolean actual = mResources.getBoolean(R.bool.config_hideBottomSystemBarForKeyboard);
            boolean expected = mResources.getBoolean(
                    com.android.internal.R.bool.config_hideNavBarForKeyboard);

            if (actual != expected) {
                throw new RuntimeException("config_hideBottomSystemBarForKeyboard must not be "
                        + "overlaid directly and should always refer to"
                        + "config_hideNavBarForKeyboard. However, their values "
                        + "currently do not sync. Set config_hideBottomSystemBarForKeyguard to "
                        + "@*android:bool/config_hideNavBarForKeyboard. To change its "
                        + "value, overlay config_hideNavBarForKeyboard in "
                        + "framework/base/core/res/res.");
            }
        }
    }

    private void setInsetPaddingsForOverlappingCorners() {
        Map<String, Boolean> systemBarVisibilityOnInit = getSystemBarsVisibilityOnInit();
        updateInsetPaddings(TOP_BAR_NAME, systemBarVisibilityOnInit);
        updateInsetPaddings(BOTTOM_BAR_NAME, systemBarVisibilityOnInit);
        updateInsetPaddings(LEFT_BAR_NAME, systemBarVisibilityOnInit);
        updateInsetPaddings(RIGHT_BAR_NAME, systemBarVisibilityOnInit);
    }

    private void sortSystemBarTypesByZOrder() {
        List<Map.Entry<String, SystemBarWindow>> systemBarsByZOrder = new ArrayList<>();
        mSystemBars.keySet().forEach(name -> {
            systemBarsByZOrder.add(Map.entry(name, mSystemBars.get(name)));
        });

        systemBarsByZOrder.sort(Comparator.comparingInt(entry -> entry.getValue().getZOrder()));

        mSystemBarNamesByZOrder.clear();
        systemBarsByZOrder.forEach(entry -> mSystemBarNamesByZOrder.add(entry.getKey()));
    }

    // On init, system bars are visible as long as they are enabled.
    private Map<String, Boolean> getSystemBarsVisibilityOnInit() {
        ArrayMap<String, Boolean> visibilityMap = new ArrayMap<>();
        visibilityMap.put(TOP_BAR_NAME, mTopNavBarEnabled);
        visibilityMap.put(BOTTOM_BAR_NAME, mBottomNavBarEnabled);
        visibilityMap.put(LEFT_BAR_NAME, mLeftNavBarEnabled);
        visibilityMap.put(RIGHT_BAR_NAME, mRightNavBarEnabled);
        return visibilityMap;
    }

    private void checkOverlappingBarsHaveDifferentZOrders(String horizontalName,
            String verticalName) {

        if (isVerticalBar(horizontalName) || isHorizontalBar(verticalName)) {
            Log.w(TAG, "configureBarPaddings: Returning immediately since the horizontal and "
                    + "vertical sides were not provided correctly.");
            return;
        }

        SystemBarWindow horizontalWindow = mSystemBars.get(horizontalName);
        SystemBarWindow verticalWindow = mSystemBars.get(verticalName);

        if (verticalWindow != null && horizontalWindow != null) {
            int horizontalBarZOrder = horizontalWindow.getZOrder();
            int verticalBarZOrder = verticalWindow.getZOrder();

            if (horizontalBarZOrder == verticalBarZOrder) {
                throw new RuntimeException(
                        BAR_TITLE_MAP.get(horizontalName) + " " + BAR_TITLE_MAP.get(verticalName)
                                + " have the same Z-Order, and so their placing order cannot be "
                                + "determined. Determine which bar should be placed on top of the "
                                + "other bar and change the Z-order in config.xml accordingly.");
            }
        }
    }

    private boolean isHorizontalBar(String name) {
        SystemBarWindow window = mSystemBars.get(name);
        if (window == null) {
            return false;
        }
        Rect rect = window.getBounds();
        if (rect == null) {
            return false;
        }

        return rect.width() >= rect.height();
    }

    private boolean isVerticalBar(String name) {
        SystemBarWindow window = mSystemBars.get(name);
        if (window == null) {
            return false;
        }
        Rect rect = window.getBounds();
        if (rect == null) {
            return false;
        }

        return rect.width() < rect.height();
    }

    @Override
    public SystemUiWindow getWindowForName(@NonNull String name) {
        return mSystemBars.get(name) != null ? mSystemBars.get(name) : null;
    }

    private static final class SystemBarConfigBuilder {
        private int mBarType;
        private int mZOrder;
        private int mIndex;
        private int mIndexOffset;
        private String mName;
        private boolean mHideForKeyboard;

        private SystemBarConfigBuilder setBarType(int type) {
            mBarType = type;
            return this;
        }

        private SystemBarConfigBuilder setZOrder(int zOrder) {
            mZOrder = zOrder;
            return this;
        }

        private SystemBarConfigBuilder setIndex(int index) {
            mIndex = index;
            return this;
        }

        private SystemBarConfigBuilder setIndexOffset(int indexOffset) {
            mIndexOffset = indexOffset;
            return this;
        }

        private SystemBarConfigBuilder setHideForKeyboard(boolean hide) {
            mHideForKeyboard = hide;
            return this;
        }

        private SystemBarConfigBuilder setName(String name) {
            mName = name;
            return this;
        }

        private SystemBarConfiguration build() {
            Bundle bundle = new Bundle();
            bundle.putInt(SystemBarParser.BAR_Z_ORDER_ATTRIBUTE, mZOrder);
            bundle.putBoolean(SystemBarParser.HIDE_FOR_KEYBOARD_ATTRIBUTE, mHideForKeyboard);
            bundle.putString(SystemBarParser.TYPE_ATTRIBUTE,
                    mBarType == STATUS_BAR ? TYPE_STATUS : TYPE_NAVIGATION);
            return new SystemBarConfiguration(bundle, mName, mIndex, mIndexOffset);
        }
    }

    private static class InternalSystemBarWindow implements SystemBarWindow {
        private final int mGirth;
        private final int mDefaultDisplayId;
        private final SystemBarConfiguration mConfig;
        private final WindowManager mWindowManager;
        private final Function<String, Boolean> mHorizontalChecker;
        private final Rect mWindowBounds;
        private View mRootView;
        private Insets mInsets;

        private InternalSystemBarWindow(Context ctx, int girth, SystemBarConfiguration config,
                Function<String, Boolean> horizontalChecker, int defaultDisplayId) {
            mGirth = girth;
            mConfig = config;
            Context context = ctx.createWindowContext(mapZOrderToBarType(mConfig.getZOrder()),
                    null);
            mWindowManager = context.getSystemService(WindowManager.class);
            mHorizontalChecker = horizontalChecker;
            mWindowBounds = mWindowManager.getCurrentWindowMetrics().getBounds();
            mDefaultDisplayId = defaultDisplayId;
        }

        @NonNull
        @Override
        public String getName() {
            return mConfig.getName();
        }

        @Override
        public int getType() {
            return mConfig.getType();
        }

        @Override
        public int getZOrder() {
            return mConfig.getZOrder();
        }

        @Override
        public boolean isHiddenForKeyboard() {
            return mConfig.isHiddenForKeyboard();
        }

        @Override
        public void setRootView(@NonNull View view, WindowManager.LayoutParams layoutParams) {
            if (mRootView != null) {
                removeRootView();
            }
            mRootView = view;
            mWindowManager.addView(mRootView, layoutParams);
        }

        @Override
        public void removeRootView() {
            if (mRootView == null) {
                return;
            }
            mWindowManager.removeView(mRootView);
            mRootView = null;
        }

        @Override
        public void removeRootViewImmediate() {
            if (mRootView == null) {
                return;
            }
            mWindowManager.removeViewImmediate(mRootView);
            mRootView = null;
        }

        @Override
        public boolean isVisible() {
            if (mRootView == null) {
                return false;
            }
            return mRootView.getVisibility() == View.VISIBLE;
        }

        @Override
        public void hide() {
            if (mRootView == null) {
                return;
            }
            mRootView.setVisibility(View.GONE);
        }

        @Override
        public void show() {
            if (mRootView == null) {
                return;
            }
            mRootView.setVisibility(View.VISIBLE);
        }

        @Override
        public WindowManager.LayoutParams getLayoutParams() {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    mHorizontalChecker.apply(mConfig.getName())
                            ? ViewGroup.LayoutParams.MATCH_PARENT : mGirth,
                    mHorizontalChecker.apply(mConfig.getName()) ? mGirth
                            : ViewGroup.LayoutParams.MATCH_PARENT,
                    mapZOrderToBarType(mConfig.getZOrder()),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                            | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH, PixelFormat.TRANSLUCENT);
            lp.setTitle(mConfig.getName());
            lp.providedInsets = new InsetsFrameProvider[]{new InsetsFrameProvider(INSETS_OWNER,
                    mConfig.getIndex(),
                    mConfig.getType() == STATUS_BAR ? statusBars() : navigationBars()),
                    new InsetsFrameProvider(INSETS_OWNER,
                            mConfig.getType() == STATUS_BAR ? mConfig.getIndex()
                                    : mConfig.getMandatorySystemGestureIndexOffset()
                                            + mConfig.getIndex(), mandatorySystemGestures())};
            lp.setFitInsetsTypes(0);
            lp.windowAnimations = 0;
            lp.gravity = BAR_GRAVITY_MAP.get(mConfig.getName());
            lp.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            lp.privateFlags = lp.privateFlags
                    | WindowManager.LayoutParams.PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP;
            return lp;
        }

        @Nullable
        @Override
        public Rect getBounds() {
            Rect rect;
            if (mConfig.getName().equals(TOP_BAR_NAME)) {
                rect = new Rect(0, 0, mWindowBounds.width(), mGirth);
            } else if (mConfig.getName().equals(LEFT_BAR_NAME)) {
                rect = new Rect(0, 0, mGirth, mWindowBounds.height());
            } else if (mConfig.getName().equals(BOTTOM_BAR_NAME)) {
                rect = new Rect(0, mWindowBounds.height() - mGirth, mWindowBounds.width(),
                        mWindowBounds.height());
            } else {
                rect = new Rect(mWindowBounds.width() - mGirth, 0, mWindowBounds.width(),
                        mWindowBounds.height());
            }
            return rect;
        }

        @Override
        public int getHeight() {
            return mHorizontalChecker.apply(mConfig.getName()) ? mGirth : mWindowBounds.height();
        }

        @Override
        public int getWidth() {
            return mHorizontalChecker.apply(mConfig.getName()) ? mWindowBounds.width() : mGirth;
        }

        @Override
        public float getAlpha() {
            return 1;
        }

        @Override
        public Insets getInsets() {
            return mInsets;
        }

        private void setInsets(int[] padding) {
            mInsets = Insets.of(padding[0], padding[1], padding[2], padding[3]);
        }

        private void setInsets(Insets insets) {
            mInsets = insets;
        }

        @Override
        public Corner getCornerRadius() {
            return Corner.DEFAULT_CORNER;
        }

        @Override
        public int getDisplayId() {
            return mDefaultDisplayId;
        }

        @Override
        public void addCallback(@NonNull WindowUpdateCallback callback) {

        }

        @Override
        public void removeCallback(@NonNull WindowUpdateCallback callback) {

        }
    }
}
