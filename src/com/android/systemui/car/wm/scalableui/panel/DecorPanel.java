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
package com.android.systemui.car.wm.scalableui.panel;

import android.content.Context;
import android.graphics.Rect;
import android.os.Trace;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.scalableui.model.Corner;
import com.android.car.scalableui.model.Variant;
import com.android.car.scalableui.panel.DecorPanelController;
import com.android.car.scalableui.panel.Panel;
import com.android.car.scalableui.panel.PanelUpdatePublisher;
import com.android.systemui.car.wm.scalableui.panel.controller.PanelControllerInitializer;
import com.android.wm.shell.automotive.AutoDecor;
import com.android.wm.shell.automotive.AutoDecorManager;
import com.android.wm.shell.automotive.AutoSurfaceTransaction;
import com.android.wm.shell.automotive.AutoSurfaceTransactionFactory;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.annotations.ExternalMainThread;
import com.android.wm.shell.shared.annotations.ShellBackgroundThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.io.PrintWriter;
import java.util.Optional;

/**
 * A {@link AutoDecor} based implementation of a {@link Panel}.
 */
public final class DecorPanel extends SysUIPanel {
    private static final String TAG = DecorPanel.class.getSimpleName();

    private final AutoDecorManager mAutoDecorManager;
    private final PanelUtils mPanelUtils;
    private final PanelControllerInitializer mPanelControllerInitializer;

    private final AutoSurfaceTransactionFactory mAutoSurfaceTransactionFactory;
    @VisibleForTesting
    AutoDecor mAutoDecor;

    @Nullable
    private View mDecorView;
    @Nullable
    DecorPanelController mDecorPanelController;

    @AssistedInject
    public DecorPanel(
            @NonNull Context context,
            AutoDecorManager autoDecorManager,
            PanelUtils panelUtils,
            PanelControllerInitializer panelControllerInitializer,
            @ExternalMainThread ShellExecutor mainExecutor,
            @ShellMainThread ShellExecutor shellMainExecutor,
            @ShellBackgroundThread ShellExecutor shellBgExecutor,
            AutoSurfaceTransactionFactory autoSurfaceTransactionFactory,
            Optional<PanelUpdatePublisher> panelUpdatePublisherOptional,
            @Assisted String id
    ) {
        super(context, id, panelUpdatePublisherOptional, mainExecutor, shellMainExecutor,
                shellBgExecutor);
        mAutoDecorManager = autoDecorManager;
        mPanelUtils = panelUtils;
        mPanelControllerInitializer = panelControllerInitializer;
        mAutoSurfaceTransactionFactory = autoSurfaceTransactionFactory;
    }

    @VisibleForTesting
    @Nullable
    View inflateDecorView() {
        View view = getRole() != null ? getRole().getView(getContext()) : null;
        return view != null ? view : initFromController();
    }

    @Nullable
    private View initFromController() {
        mDecorPanelController = mPanelControllerInitializer.createDecorPanelController(
                getPanelId(), getPanelControllerMetadata());
        return mDecorPanelController == null ? null : mDecorPanelController.getView();
    }

    @Override
    public void init() {
        super.init();
        if (mPanelUtils.isUserUnlocked()) {
            reset();
        }
    }

    @Override
    public void destroy() {
        getShellMainExecutor().execute(() -> {
            if (mAutoDecor != null) {
                mAutoDecorManager.removeAutoDecor(mAutoDecor);
                mAutoDecor = null;
            }
        });
        if (mDecorPanelController != null) {
            mDecorPanelController.destroy();
        }
        super.destroy();
    }

    @Override
    public void reset() {
        super.reset();
        // modify the view on the main thread and AutoDecor on the shell main thread
        getMainExecutor().execute(() -> {
            // View inflation has to be on main thread.
            View newDecorView = inflateDecorView();

            // AutoDecorManager related call should be on shellMainThread
            getShellMainExecutor().execute(() -> {
                if (mAutoDecor != null) {
                    mAutoDecorManager.removeAutoDecor(mAutoDecor);
                    mAutoDecor = null;
                }

                mDecorView = newDecorView;
                if (mDecorView == null) {
                    Log.e(TAG, "DecorView is null, fail to create AutoDecor, " + getPanelId());
                    return;
                }

                Variant currentVariant = mPanelUtils.getCurrentVariant(getPanelId());

                mAutoDecor = mAutoDecorManager.createAutoDecor(mDecorView,
                        currentVariant != null ? currentVariant.getLayer() : getLayer(),
                        currentVariant != null ? currentVariant.getBounds() : getBounds(),
                        getPanelId());

                mAutoDecorManager.attachAutoDecorToDisplay(mAutoDecor, getDisplayId());

                AutoSurfaceTransaction autoSurfaceTransaction = mAutoSurfaceTransactionFactory
                        .createTransaction(RESET_TRANSACTION + getPanelId());

                update(autoSurfaceTransaction, currentVariant, /* updateChildren= */ true);
                autoSurfaceTransaction.apply();
            });
        });
    }

    @Override
    @ExternalMainThread
    public void refreshTheme() {
        super.refreshTheme();
        if (mDecorPanelController != null) {
            mDecorPanelController.refreshTheme();
        }
        reset();
    }

    @Nullable
    public AutoDecor getAutoDecor() {
        return mAutoDecor;
    }

    @VisibleForTesting
    void setDecorView(View view) {
        mDecorView = view;
    }

    @Override
    public void update(@NonNull SurfaceControl.Transaction tx, @Nullable Variant variant) {
        Log.e(TAG, "Cannot update DecorPanel without AutoSurfaceTransaction");
    }

    @Override
    protected void updateInternal(
            @Nullable AutoSurfaceTransaction autoSurfaceTransaction,
            @Nullable SurfaceControl.Transaction tx,
            @Nullable Variant variant,
            boolean updateChildren) {
        if (getAutoDecor() == null) {
            Log.e(TAG, "AutoDecor is null for " + getPanelId());
            return;
        }
        if (autoSurfaceTransaction == null) {
            Log.e(TAG, "AutoSurfaceTransaction cannot be null for DecorPanel updates");
            return;
        }
        Trace.beginSection(TAG + "#updateInternal");
        super.updateInternal(autoSurfaceTransaction, tx, variant, updateChildren);
        logIfDebuggable("updateDecorPanelSurface:" + this);
        Rect bounds = variant == null ? getBounds() : variant.getBounds();
        autoSurfaceTransaction.setBounds(getAutoDecor(), bounds);
        autoSurfaceTransaction.setVisibility(getAutoDecor(),
                variant == null ? isVisible() : variant.isVisible());
        autoSurfaceTransaction.setZOrder(getAutoDecor(),
                variant == null ? getLayer() : variant.getLayer());
        Corner radius = variant == null ? getCornerRadius() : variant.getCornerRadius();
        if (com.android.graphics.surfaceflinger.flags.Flags.setClientDrawnCornerRadii()) {
            autoSurfaceTransaction.setCornerRadius(getAutoDecor(),
                    radius.getTopLeftRadius(), radius.getTopRightRadius(),
                    radius.getBottomLeftRadius(), radius.getBottomRightRadius());
        } else {
            // Per-corner radius is not supported, applying a uniform radius to all corners.
            // Note: we could use any Corner#getRadius*(), as they will be the same.
            autoSurfaceTransaction.setCornerRadius(getAutoDecor(), radius.getTopLeftRadius());
        }
        autoSurfaceTransaction.setCrop(getAutoDecor(),
                new Rect(0, 0, bounds.width(), bounds.height()));
        autoSurfaceTransaction.setAlpha(getAutoDecor(),
                variant == null ? getAlpha() : variant.getAlpha());
        autoSurfaceTransaction.setVisibility(getAutoDecor(),
                variant == null ? isVisible() : variant.isVisible());
        Trace.endSection();
    }

    @AssistedFactory
    public interface Factory {
        /** Create instance of {@link DecorPanel} with specified id */
        DecorPanel create(String id);
    }

    @Override
    public void dump(@NonNull PrintWriter pw) {
        pw.print(this);
        pw.print(mDecorPanelController);
    }

    @Override
    public String toString() {
        return "DecorPanel{"
                + "mId='" + getPanelId() + '\''
                + ", mBounds=" + getBounds()
                + ", mLayer=" + getLayer()
                + ", mRole=" + getRole()
                + ", mIsVisible=" + isVisible()
                + ", mAlpha=" + getAlpha()
                + ", mDisplayId=" + getDisplayId()
                + ", mCornerRadius=" + getCornerRadius()
                + ", mDecorView=" + mDecorView + '}';
    }
}
