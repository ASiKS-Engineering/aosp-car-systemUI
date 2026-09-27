package com.android.systemui.car.wm.activity.window;

import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import android.annotation.NonNull;
import android.car.app.CarActivityManager;
import android.car.app.CarTaskViewController;
import android.car.app.CarTaskViewControllerCallback;
import android.car.app.CarTaskViewControllerHostLifecycle;
import android.car.app.RemoteCarDefaultRootTaskView;
import android.car.app.RemoteCarDefaultRootTaskViewCallback;
import android.car.app.RemoteCarDefaultRootTaskViewConfig;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Binder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.annotation.MainThread;

import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.shared.R;
import com.android.systemui.dagger.qualifiers.UiBackground;

import javax.inject.Inject;

public class ActivityWindowControllerImpl implements ActivityWindowController {
    public static final String TAG = ActivityWindowController.class.getSimpleName();

    @NonNull
    private final Context mContext;
    @NonNull
    private final WindowManager mWindowManager;
    @NonNull
    private final CarServiceProvider mCarServiceProvider;
    @NonNull
    private ViewGroup mLayout;
    @NonNull
    private WindowManager.LayoutParams mWmLayoutParams;

    @NonNull
    private CarTaskViewController mCarTaskViewController;
    @NonNull
    private CarTaskViewControllerHostLifecycle mCarTaskViewControllerHostLifecycle;
    @NonNull
    private CarActivityManager mCarActivityManager;

    @NonNull
    @UiBackground
    private final CarServiceProvider.CarServiceOnConnectedListener mCarServiceLifecycleListener =
            car -> {
                mCarActivityManager = car.getCarManager(CarActivityManager.class);

                inflate();
                setupRemoteCarTaskView();
            };

    @Inject
    public ActivityWindowControllerImpl(Context context, WindowManager windowManager,
            CarServiceProvider carServiceProvider,
            CarTaskViewControllerHostLifecycle carTaskViewControllerHostLifecycle) {
        mContext = context;
        mWindowManager = windowManager;
        mCarServiceProvider = carServiceProvider;
        mCarTaskViewControllerHostLifecycle = carTaskViewControllerHostLifecycle;
    }

    @MainThread
    @Override
    public void init() {
        mCarServiceProvider.addListener(mCarServiceLifecycleListener);
    }

    @MainThread
    protected void inflate() {
        mLayout = (ViewGroup) LayoutInflater.from(mContext)
                .inflate(R.layout.car_activity_window, /* root= */ null);

        mWmLayoutParams = new WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT);

        mWmLayoutParams.setTrustedOverlay();
        mWmLayoutParams.setFitInsetsTypes(0);
        mWmLayoutParams.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        mWmLayoutParams.token = new Binder();
        mWmLayoutParams.setTitle("ActivityWindow!");
        mWmLayoutParams.packageName = mContext.getPackageName();
        mWmLayoutParams.layoutInDisplayCutoutMode =
                LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        mWmLayoutParams.privateFlags |=
                WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;

        mWindowManager.addView(mLayout, mWmLayoutParams);

        /*
         * The ActivityWindow covers the complete display, but it should only
         * receive touch input in the SystemUI top and bottom bars.
         *
         * The center area is intentionally excluded from the touchable region
         * so that the Launcher / Navigation window below can receive input.
         */
        mLayout.getViewTreeObserver().addOnComputeInternalInsetsListener(
                new ViewTreeObserver.OnComputeInternalInsetsListener() {
                    @Override
                    public void onComputeInternalInsets(
                            ViewTreeObserver.InternalInsetsInfo info) {

                        info.touchableRegion.setEmpty();

                        final int width = mLayout.getWidth();
                        final int height = mLayout.getHeight();

                        if (width <= 0 || height <= 0) {
                            info.setTouchableInsets(
                                    ViewTreeObserver.InternalInsetsInfo
                                            .TOUCHABLE_INSETS_REGION);
                            return;
                        }

                        // Current display: 1024 x 600
                        // Top bar:    y = 0 .. 76
                        // Bottom bar: y = 504 .. 600
                        final int topBarHeight = 76;
                        final int bottomBarHeight = 96;

                        info.touchableRegion.union(
                                new Rect(
                                        0,
                                        0,
                                        width,
                                        Math.min(topBarHeight, height)));

                        info.touchableRegion.union(
                                new Rect(
                                        0,
                                        Math.max(0, height - bottomBarHeight),
                                        width,
                                        height));

                        info.setTouchableInsets(
                                ViewTreeObserver.InternalInsetsInfo
                                        .TOUCHABLE_INSETS_REGION);
                    }
                });
    }

    private void setupRemoteCarTaskView() {
        mCarActivityManager.getCarTaskViewController(
                mContext,
                mCarTaskViewControllerHostLifecycle,
                mContext.getMainExecutor(),
                new CarTaskViewControllerCallback() {
                    @Override
                    public void onConnected(
                            CarTaskViewController carTaskViewController) {
                        mCarTaskViewController = carTaskViewController;
                        taskViewControllerReady();
                    }

                    @Override
                    public void onDisconnected(
                            CarTaskViewController carTaskViewController) {
                    }
                });
    }

    private void taskViewControllerReady() {
        mCarTaskViewController.createRemoteCarDefaultRootTaskView(
                new RemoteCarDefaultRootTaskViewConfig.Builder()
                        .setDisplayId(mContext.getDisplayId())
                        .embedHomeTask(true)
                        .embedRecentsTask(true)
                        .build(),
                mContext.getMainExecutor(),
                new RemoteCarDefaultRootTaskViewCallback() {
                    @Override
                    public void onTaskViewCreated(
                            @NonNull RemoteCarDefaultRootTaskView taskView) {

                        Log.d(TAG, "Root Task View is created");
                        taskView.setZOrderMediaOverlay(true);

                        mLayout.setOnApplyWindowInsetsListener(
                                new View.OnApplyWindowInsetsListener() {
                                    @Override
                                    public WindowInsets onApplyWindowInsets(
                                            View view,
                                            WindowInsets insets) {

                                        mLayout.setPadding(
                                                insets.getSystemWindowInsetLeft(),
                                                insets.getSystemWindowInsetTop(),
                                                insets.getSystemWindowInsetRight(),
                                                insets.getSystemWindowInsetBottom());

                                        return insets.replaceSystemWindowInsets(
                                                0, 0, 0, 0);
                                    }
                                });

                        ViewGroup layout = (ViewGroup)
                                mLayout.findViewById(R.id.activity_area);

                        // IMPORTANT:
                        // Do not attach RemoteCarDefaultRootTaskView.
                        // It creates the SurfaceView that previously produced
                        // the full-screen opaque black layer.
                        Log.d(TAG,
                                "RemoteCarDefaultRootTaskView NOT attached");
                    }

                    @Override
                    public void onTaskViewInitialized() {
                        Log.d(TAG, "Root Task View is ready");
                    }
                });
    }
}