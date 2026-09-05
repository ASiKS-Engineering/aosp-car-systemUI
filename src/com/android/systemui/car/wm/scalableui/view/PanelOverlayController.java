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
package com.android.systemui.car.wm.scalableui.view;

import static com.android.car.scalableui.loader.xml.parser.PanelControllerParser.BACKGROUND_COLOR_TAG;
import static com.android.car.scalableui.loader.xml.parser.PanelControllerParser.OVERLAY_PANEL_ID_TAG;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.car.scalableui.model.PanelState;
import com.android.car.scalableui.model.Variant;
import com.android.car.scalableui.panel.DecorPanelController;
import com.android.internal.graphics.drawable.BackgroundBlurDrawable;
import com.android.systemui.car.shared.R;
import com.android.systemui.car.wm.scalableui.panel.PanelUtils;
import com.android.systemui.car.wm.scalableui.panel.TaskPanel;
import com.android.systemui.car.wm.scalableui.panel.controller.DecorPanelViewMap;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.Map;
import java.util.Set;

import javax.inject.Provider;

/**
 * A controller for a {@link PanelOverlay} view.
 *
 * <p>This controller manages an overlay that provides a visual effect, typically a blurred
 * background and a centered icon (a "vail"), over another panel.
 *
 * <p>The controller's behavior is configured through {@link PanelControllerMetadata} using
 * the following tags:
 * <ul>
 *     <li>{@code overlay_panel_id}: The ID of the {@link TaskPanel} this overlay is
 *     associated with. The controller will display the icon of the top application from this
 *     TaskPanel.</li>
 *     <li>{@code background_color}: A hex string defining the background color of the
 *     overlay, which is applied on top of the blur effect.</li>
 * </ul>
 *
 * <p>When the panel becomes visible, the controller performs two main actions:
 * <ol>
 *     <li><b>Blur Effect</b>: It applies a {@link BackgroundBlurDrawable} to its view,
 *     creating a blur of the content underneath.</li>
 *     <li><b>Vail Icon</b>: It identifies the top application in the associated
 *     {@link TaskPanel} and displays its application icon centered within the overlay.</li>
 * </ol>
 */
public class PanelOverlayController extends DecorPanelControllerBase implements
        StateManager.PanelStateObserver {
    private static final String TAG = PanelOverlayController.class.getSimpleName();
    private final Context mContext;
    private final PanelUtils mPanelUtils;
    private boolean mIsVisible;
    private ConstraintLayout mPanelOverlay;
    private String mOverlayPanelId;
    private String mBackgroundColorHex;
    private BackgroundBlurDrawable mBackgroundBlurDrawable;
    private int mBlurRadius;

    @AssistedInject
    public PanelOverlayController(@Assisted String panelId,
            @Assisted PanelControllerMetadata metadata,
            @DecorPanelViewMap Map<Class<?>, Provider<View>> decorPanelViewMap,
            Context context,
            PanelUtils panelUtils) {
        super(panelId, metadata, decorPanelViewMap);
        mContext = context;
        init(metadata);
        mPanelUtils = panelUtils;
        mIsVisible = isPanelVisible();
        // TODO(b/462485520): add removePanelStateObserver when controller get destroyed due to
        //  config change.
        StateManager.getInstance().addPanelStateObserver(this, panelId);
    }

    private boolean isPanelVisible() {
        Variant currentVariant = mPanelUtils.getCurrentVariant(mPanelId);
        return currentVariant != null && currentVariant.isVisible();
    }

    @Override
    public void onBeforePanelStateChanged(@NonNull Set<String> changedPanelIds,
            @NonNull Map<String, PanelState> toPanelStates) {
        // Update the blur and vail before the state change is applied.
        if (changedPanelIds.contains(mPanelId)) {
            boolean currentVis = isPanelVisible();
            if (currentVis != mIsVisible && currentVis) {
                mPanelOverlay.post(() -> {
                    updatePanelOverlay();
                });
            }
            mIsVisible = currentVis;
        }
    }

    @Override
    public void onPanelStateChanged(@NonNull Set<String> changedPanelIds,
            @NonNull Map<String, PanelState> toPanelStates) {
        // no-op
    }

    @AssistedFactory
    public interface Factory extends DecorPanelController.Factory<PanelOverlayController> {
        /**
         * Create an instance of {@link PanelOverlayController} with the provided
         * {@link PanelControllerMetadata}.
         */
        PanelOverlayController create(String panelId, PanelControllerMetadata metadata);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    @NonNull
    public View getView() {
        View view = super.getView();
        if (view instanceof PanelOverlay panelOverlay) {
            mPanelOverlay = panelOverlay;
        } else {
            throw new RuntimeException("PanelOverlayController mush have a PanelOverlay view");
        }

        return mPanelOverlay;
    }

    private void updatePanelOverlay() {
        setBlur();
        updateVail();
    }

    private void init(@NonNull PanelControllerMetadata metadata) {
        mOverlayPanelId = metadata.getStringConfiguration(OVERLAY_PANEL_ID_TAG);
        mBackgroundColorHex = metadata.getStringConfiguration(BACKGROUND_COLOR_TAG);
    }

    @Override
    public void destroy() {
        super.destroy();
        StateManager.getInstance().removePanelStateObserver(this);
    }

    private void updateVail() {
        String packageName = null;
        TaskPanel taskPanel = mPanelUtils.getTaskPanel(tp -> tp.getPanelId().equals(
                mOverlayPanelId));
        logIfDebuggable(mPanelId + ", handleStateChange" + mOverlayPanelId);
        if (taskPanel != null) {
            packageName = taskPanel.getTopTaskPackageName();
            logIfDebuggable(mPanelId + ", setVail for" + packageName + ", on TaskPanel"
                    + taskPanel.getPanelId());
        }
        if (packageName == null) {
            logIfDebuggable(mPanelId + ", can't set vail as package name is null.");
            return;
        }
        Drawable icon;
        try {
            icon = mContext.getPackageManager().getApplicationIcon(
                    packageName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "vail can't be set for package name ", e);
            icon = mContext.getDrawable(R.drawable.car_ic_apps);
        }
        ImageView iconImageView = new ImageView(mContext);
        iconImageView.setImageDrawable(icon);
        int width = mContext.getResources().getDimensionPixelSize(
                R.dimen.overlay_panel_view_vail_width);
        int height = mContext.getResources().getDimensionPixelSize(
                R.dimen.overlay_panel_view_vail_height);
        addCenteredIconWithConstraintSet(iconImageView, width, height);
    }

    // Blur need to be set after view being attached to call createBackgroundBlurDrawable, no
    // need to be update once created.
    private void setBlur() {
        if (mPanelOverlay.getBackground() != null) {
            logIfDebuggable(mPanelId + ", Background already set " + mPanelOverlay.getBackground());
            return;
        }
        mBackgroundBlurDrawable =
                mPanelOverlay.getViewRootImpl().createBackgroundBlurDrawable();
        int color;
        if (mBackgroundColorHex != null && !mBackgroundColorHex.isEmpty()) {
            color = Color.parseColor(mBackgroundColorHex);
        } else {
            color = mContext.getResources().getColor(R.color.overlay_panel_bg_color);
        }

        mBackgroundBlurDrawable.setColor(color);
        mBackgroundBlurDrawable.setCornerRadius(
                mContext.getResources().getInteger(R.integer.overlay_panel_blur_corner_radius));
        mBlurRadius = mContext.getResources().getInteger(R.integer.overlay_panel_blur_radius);
        mBackgroundBlurDrawable.setBlurRadius(mBlurRadius);
        mPanelOverlay.setBackground(mBackgroundBlurDrawable);
        logIfDebuggable(mPanelId + ", Background set to " + mPanelOverlay.getBackground());
    }

    private void addCenteredIconWithConstraintSet(ImageView iconImageView, int width, int height) {
        if (iconImageView.getId() == View.NO_ID) {
            iconImageView.setId(View.generateViewId());
        }

        ConstraintLayout.LayoutParams initialParams = new ConstraintLayout.LayoutParams(width,
                height);
        iconImageView.setLayoutParams(initialParams);

        mPanelOverlay.post(() -> {
            mPanelOverlay.removeAllViews();
            mPanelOverlay.addView(iconImageView);

            ConstraintSet constraintSet = new ConstraintSet();
            constraintSet.clone(mPanelOverlay);

            // Center Horizontally
            constraintSet.connect(iconImageView.getId(), ConstraintSet.START,
                    ConstraintSet.PARENT_ID, ConstraintSet.START);
            constraintSet.connect(iconImageView.getId(), ConstraintSet.END, ConstraintSet.PARENT_ID,
                    ConstraintSet.END);

            // Center Vertically
            constraintSet.connect(iconImageView.getId(), ConstraintSet.TOP, ConstraintSet.PARENT_ID,
                    ConstraintSet.TOP);
            constraintSet.connect(iconImageView.getId(), ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);

            // Apply the constraints
            constraintSet.applyTo(mPanelOverlay);
        });
    }
}
