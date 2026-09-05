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
package com.android.systemui.car.wm.scalableui.systemwindow;

import static android.view.WindowInsets.Type.mandatorySystemGestures;
import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import static com.android.systemui.car.systembar.SystemBarConstants.STATUS_BAR;
import static com.android.systemui.car.wm.scalableui.systemwindow.SystemBarWindowKt.HUN_Z_ORDER;

import android.content.Context;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.InsetsFrameProvider;
import android.view.WindowManager;

import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.car.wm.scalableui.configuration.SystemBarConfiguration;
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.ArrayList;
import java.util.List;

/**
 * An implementation of {@link SystemUiWindow} specifically for system bars.
 */
public class SystemBarWindowImpl extends SystemUiWindowBase implements SystemBarWindow {
    private static final Binder INSETS_OWNER = new Binder();
    private final SystemBarConfiguration mConfiguration;
    private final PanelUpdateConsumer mPanelUpdateConsumer;

    @AssistedInject
    public SystemBarWindowImpl(Context context, DisplayManager displayManager,
            EventDispatcher dispatcher,
            @Assisted PanelUpdateConsumer consumer, @Assisted SystemBarConfiguration config,
            @Assisted int displayId) {
        super(context, displayManager, consumer, dispatcher, config.getName(), displayId);
        mConfiguration = config;
        mPanelUpdateConsumer = consumer;
    }

    private static int mapZOrderToBarType(int zOrder) {
        return zOrder >= HUN_Z_ORDER ? WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL
                : WindowManager.LayoutParams.TYPE_STATUS_BAR_ADDITIONAL;
    }

    @Override
    public WindowManager.LayoutParams getLayoutParams() {
        Rect bounds = getPanelUpdateConsumer().getBounds(getId());
        if (bounds == null) {
            return null;
        }
        return getLayoutParamsFromBounds(bounds);
    }

    private WindowManager.LayoutParams getLayoutParamsFromBounds(Rect bounds) {
        if (getDisplayMetrics() == null) {
            return null;
        }
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                mapZOrderToBarType(mConfiguration.getZOrder()),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH, PixelFormat.TRANSLUCENT);
        SystemUiWindow.updateLayoutParams(lp, bounds, getDisplayMetrics());

        int panelGravity = mPanelUpdateConsumer.getGravity(getId());
        if (panelGravity != Gravity.NO_GRAVITY) {
            lp.gravity = panelGravity;
        }


        // Use insetsFrame, this ensures the system recognizes the bar as a valid inset even
        // for partial bars
        Rect insetsFrame = getInsetsFrame(bounds);
        List<InsetsFrameProvider> providers = new ArrayList<>();
        // 1. Standard Bar Provider (Nav/Status)
        providers.add(new InsetsFrameProvider(INSETS_OWNER, mConfiguration.getIndex(),
                STATUS_BAR == mConfiguration.getType() ? statusBars() : navigationBars())
                .setArbitraryRectangle(insetsFrame)
                .setSource(InsetsFrameProvider.SOURCE_ARBITRARY_RECTANGLE)
                .setInsetsSize(calculateInsetsSize(insetsFrame)));


        // 2. Gestures
        providers.add(new InsetsFrameProvider(INSETS_OWNER, getMandatorySystemGesturesIndex(),
                mandatorySystemGestures())
                .setArbitraryRectangle(insetsFrame)
                .setSource(InsetsFrameProvider.SOURCE_ARBITRARY_RECTANGLE)
                .setInsetsSize(calculateInsetsSize(insetsFrame)));

        lp.setTitle(mConfiguration.getName());
        lp.providedInsets = providers.toArray(new InsetsFrameProvider[0]);
        lp.setFitInsetsTypes(0);
        lp.windowAnimations = 0;
        lp.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.privateFlags = lp.privateFlags
                | WindowManager.LayoutParams.PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP;
        return lp;
    }

    /**
     * Constructs a fake frame spanning the entire width or height of the display based on the
     * provided bounds.
     *
     * <p>WindowInsets requires the source frame to span the entire width (for top/bottom bars) or
     * height (for left/right bars) to be recognized as a valid side inset. This method expands the
     * bounds to full width or height to ensure the system correctly identifies the insets.
     *
     */
    private Rect getInsetsFrame(Rect bounds) {
        Rect insetsFrame = new Rect(bounds);
        DisplayMetrics dm = getDisplayMetrics();
        Insets insetsSize = calculateInsetsSize(bounds);

        if (insetsSize.top > 0 || insetsSize.bottom > 0) {
            // Force full width for top/bottom bars
            insetsFrame.left = 0;
            insetsFrame.right = dm.widthPixels;
        } else if (insetsSize.left > 0 || insetsSize.right > 0) {
            // Force full height for left/right bars
            insetsFrame.top = 0;
            insetsFrame.bottom = dm.heightPixels;
        }
        return insetsFrame;
    }

    /**
     * Calculates the insets provided by this window based on its bounds relative to the display
     * edges.
     *
     * <p>If the window touches a screen edge (top, bottom, left, or right), it provides an inset
     * corresponding to its dimension on that side. For example, a bottom bar provides a bottom
     * inset equal to its height.
     */
    private Insets calculateInsetsSize(Rect bounds) {
        DisplayMetrics dm = getDisplayMetrics();
        if (dm == null) {
            return Insets.NONE;
        }

        // First Check for full width/height scenarios
        boolean fullWidth = bounds.width() == dm.widthPixels;
        boolean fullHeight = bounds.height() == dm.heightPixels;

        if (fullWidth && !fullHeight) {
            if (bounds.top == 0) {
                return Insets.of(0, bounds.height(), 0, 0);
            }
            if (bounds.bottom == dm.heightPixels) {
                return Insets.of(0, 0, 0, bounds.height());
            }
        }

        if (fullHeight && !fullWidth) {
            if (bounds.left == 0) {
                return Insets.of(bounds.width(), 0, 0, 0);
            }
            if (bounds.right == dm.widthPixels) {
                return Insets.of(0, 0, bounds.width(), 0);
            }
        }

        // For partial bars: calculate insets based on which edge the bounds touch.
        if (bounds.bottom == dm.heightPixels) {
            return Insets.of(0, 0, 0, bounds.height());
        } else if (bounds.top == 0) {
            return Insets.of(0, bounds.height(), 0, 0);
        } else if (bounds.left == 0) {
            return Insets.of(bounds.width(), 0, 0, 0);
        } else if (bounds.right == dm.widthPixels) {
            return Insets.of(0, 0, bounds.width(), 0);
        }
        return Insets.NONE;
    }

    @Override
    public int getType() {
        return mConfiguration.getType();
    }

    @Override
    public int getZOrder() {
        return mConfiguration.getZOrder();
    }

    @Override
    public boolean isHiddenForKeyboard() {
        return mConfiguration.isHiddenForKeyboard();
    }

    /**
     * Calculates the index for the {@link InsetsFrameProvider} for
     * {@link WindowInsets.Type#mandatorySystemGestures}.
     *
     * <p>For status bars, this index is the same as the bar's own index. For navigation bars, an
     * offset is added to prevent index collisions with status bars, ensuring each gesture provider
     * has a unique index.
     *
     * @return The unique index for the mandatory system gestures provider.
     */
    private int getMandatorySystemGesturesIndex() {
        if (STATUS_BAR == mConfiguration.getType()) {
            return mConfiguration.getIndex();
        }
        return mConfiguration.getMandatorySystemGestureIndexOffset() + mConfiguration.getIndex();
    }

    @AssistedFactory
    public interface Factory {
        /**
         * Create instance of {@link SystemBarWindowImpl} with specified
         * {@link SystemBarConfiguration}and a {@link PanelUpdateConsumer} and a display Id
         */
        SystemBarWindowImpl create(PanelUpdateConsumer consumer, SystemBarConfiguration config,
                int displayId);
    }
}
