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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Binder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.InternalInsetsInfo;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.annotation.MainThread;

import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.shared.R;
import com.android.systemui.dagger.qualifiers.UiBackground;

import javax.inject.Inject;

public class ActivityWindowControllerImpl implements ActivityWindowController {

    public static final String TAG =
            ActivityWindowController.class.getSimpleName();

    /** Temporary debug marker; bump on every debug build to identify the running SystemUI. */
    public static final String BUILD_MARKER = "NAVDBG-20261001-B";

    /*
     * Navigator application sends this broadcast through scalable_ui_actions.xml.
     */
    private static final String ACTION_NAVIGATION_UI_MODE_CHANGED =
            "com.example.campernavigator.action.NAVIGATION_UI_MODE_CHANGED";

    private static final String EXTRA_NAVIGATION_UI_MODE =
            "com.example.campernavigator.extra.NAVIGATION_UI_MODE";

    private static final String NAVIGATION_MODE_FULLSCREEN =
            "FULLSCREEN";

    private static final String NAVIGATION_MODE_HOME =
            "HOME";

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

        /*
     * The RemoteCarDefaultRootTaskView hosts the launch-root tasks. The navigator itself is
     * hosted by the Launcher's own task view; this class only manages the root task bounds.
     */
    private RemoteCarDefaultRootTaskView mTaskView;

    /*
     * Current system bar insets, used to compute the HOME bounds.
     */
    private int mTopInset;
    private int mBottomInset;

    /*
     * Current navigation display mode.
     *
     * Default is HOME because the Launcher is the initial visible state.
     */
    private boolean mNavigationFullscreen = false;
    @NonNull
    private final BroadcastReceiver mNavigationUiModeReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (!ACTION_NAVIGATION_UI_MODE_CHANGED.equals(
                            intent.getAction())) {
                        return;
                    }

                    String mode = intent.getStringExtra(
                            EXTRA_NAVIGATION_UI_MODE);

Log.i(TAG, "Navigation UI broadcast received: build=" + BUILD_MARKER
                            + ", mode=" + mode
                            + ", fullscreen=" + mNavigationFullscreen
                            + ", taskViewReady=" + (mTaskView != null));

                    if (NAVIGATION_MODE_FULLSCREEN.equals(mode)) {
                        Log.i(TAG, "Navigation UI mode: FULLSCREEN");
                        showNavigationFullscreen();
                    } else if (NAVIGATION_MODE_HOME.equals(mode)) {
                        Log.i(TAG, "Navigation UI mode: HOME");
                        showHomeMode();
                    } else {
                        Log.w(
                                TAG,
                                "Unknown navigation UI mode: " + mode);
                    }
                }
            };

    @NonNull
    @UiBackground
    private final CarServiceProvider.CarServiceOnConnectedListener
            mCarServiceLifecycleListener = car -> {

        mCarActivityManager =
                car.getCarManager(CarActivityManager.class);

        inflate();
        setupRemoteCarTaskView();
    };

    @Inject
    public ActivityWindowControllerImpl(
            Context context,
            WindowManager windowManager,
            CarServiceProvider carServiceProvider,
            CarTaskViewControllerHostLifecycle
                    carTaskViewControllerHostLifecycle) {

        mContext = context;
        mWindowManager = windowManager;
        mCarServiceProvider = carServiceProvider;
        mCarTaskViewControllerHostLifecycle =
                carTaskViewControllerHostLifecycle;
    }

    @MainThread
    @Override
    public void init() {
        Log.i(TAG, "init: systemui build=" + BUILD_MARKER
                + ", pid=" + android.os.Process.myPid());
        /*
         * Listen for FULLSCREEN / HOME mode changes.
         */
        IntentFilter filter = new IntentFilter(
                ACTION_NAVIGATION_UI_MODE_CHANGED);

        mContext.registerReceiver(
                mNavigationUiModeReceiver,
                filter,
                Context.RECEIVER_EXPORTED);

        /*
         * Connect to CarService.
         */
        mCarServiceProvider.addListener(
                mCarServiceLifecycleListener);
    }

    @MainThread
    protected void inflate() {

        mLayout = (ViewGroup) LayoutInflater.from(mContext)
                .inflate(
                        R.layout.car_activity_window,
                        /* root= */ null);

        mWmLayoutParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);

        mWmLayoutParams.setTrustedOverlay();

        /*
         * ActivityWindow uses the full display.
         *
         * We handle the actual Task bounds ourselves with
         * RemoteCarTaskView.setWindowBounds().
         */
        mWmLayoutParams.setFitInsetsTypes(0);

        mWmLayoutParams.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        mWmLayoutParams.token = new Binder();

        mWmLayoutParams.setTitle("ActivityWindow!");

        mWmLayoutParams.packageName =
                mContext.getPackageName();

        mWmLayoutParams.layoutInDisplayCutoutMode =
                LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

        mWmLayoutParams.privateFlags |=
                WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;

        /*
         * The ActivityWindow itself must exist because the
         * RemoteCarTaskView/SurfaceView needs a host window.
         *
         * The window must NOT consume touch input.
         *
         * This is important for:
         *
         * - CarLauncher
         * - Navigator
         * - IME / on-screen keyboard
         * - Top SystemUI
         * - Bottom SystemUI
         */
        mWindowManager.addView(
                mLayout,
                mWmLayoutParams);

        /*
         * Keep ActivityWindow completely non-touchable.
         *
         * Do NOT use a top/bottom touch region here.
         *
         * Top and Bottom SystemUI are separate windows, and the IME
         * can also occupy areas that overlap those coordinates.
         */
        mLayout.getViewTreeObserver()
                .addOnComputeInternalInsetsListener(
                        info -> {
                            info.touchableRegion.setEmpty();

                            info.setTouchableInsets(
                                    InternalInsetsInfo
                                            .TOUCHABLE_INSETS_REGION);
                        });

        /*
         * Remember system bar insets.
         *
         * These values are later used to calculate the HOME bounds.
         */
        mLayout.setOnApplyWindowInsetsListener(
                (view, insets) -> {

                    android.graphics.Insets systemBars =
                            insets.getInsets(
                                    WindowInsets.Type.systemBars());

                    mTopInset = systemBars.top;
                    mBottomInset = systemBars.bottom;

                    Log.d(
                            TAG,
                            "Window insets: top="
                                    + mTopInset
                                    + " bottom="
                                    + mBottomInset);

                    /*
                     * We intentionally do not apply the insets as
                     * padding to mLayout.
                     *
                     * The ActivityWindow stays fullscreen.
                     * The RemoteCarTaskView gets explicit screen
                     * coordinates instead.
                     */
                    return insets;
                });
    }

    private void setupRemoteCarTaskView() {
                Log.i(TAG, "setupRemoteCarTaskView: requesting CarTaskViewController");

        mCarActivityManager.getCarTaskViewController(
                mContext,
                mCarTaskViewControllerHostLifecycle,
                mContext.getMainExecutor(),
                new CarTaskViewControllerCallback() {

                    @Override
                    public void onConnected(
                            CarTaskViewController
                                    carTaskViewController) {

                        Log.d(
                                TAG,
                                "CarTaskViewController connected");

                        mCarTaskViewController =
                                carTaskViewController;

                        taskViewControllerReady();
                    }

                    @Override
                    public void onDisconnected(
                            CarTaskViewController
                                    carTaskViewController) {

                        Log.d(
                                TAG,
                                "CarTaskViewController disconnected");

                        mCarTaskViewController = null;
                        mTaskView = null;
                    }
                });
    }

    private void taskViewControllerReady() {

        mCarTaskViewController
                .createRemoteCarDefaultRootTaskView(
                        new RemoteCarDefaultRootTaskViewConfig.Builder()
                                .setDisplayId(
                                        mContext.getDisplayId())
                                .embedHomeTask(true)
                                .embedRecentsTask(true)
                                .build(),
                        mContext.getMainExecutor(),
                        new RemoteCarDefaultRootTaskViewCallback() {

                            @Override
                            public void onTaskViewCreated(
                                    @NonNull
                                    RemoteCarDefaultRootTaskView
                                            taskView) {

                                Log.i(TAG, "Root Task View created: view="
                                        + System.identityHashCode(taskView)
                                        + ", fullscreen=" + mNavigationFullscreen);

                                mTaskView = taskView;

                                /*
                                 * Keep the task view above the
                                 * ActivityWindow background.
                                 */
                                taskView.setZOrderMediaOverlay(false);

                                ViewGroup layout =
                                        (ViewGroup) mLayout.findViewById(R.id.activity_area);

                                /*
                                 * The task view needs a SurfaceView host so the root task can
                                 * be organized.
                                 */
                                taskView.setVisibility(View.INVISIBLE);
                                layout.addView(taskView);

                                applyCurrentBounds();

                                Log.d(
                                        TAG,
                                        "RemoteCarDefaultRootTaskView attached");
                            }

                            @Override
                            public void onTaskViewInitialized() {

                                Log.i(TAG, "Root Task View initialized: fullscreen="
                                        + mNavigationFullscreen);

                                applyCurrentBounds();
                            }
                        });
    }

    private void applyCurrentBounds() {
        if (mTaskView == null) {
            return;
        }
        if (mNavigationFullscreen) {
            applyFullscreenBounds();
        } else {
            applyHomeBounds();
        }
    }

    /**
     * Switch to fullscreen navigation mode.
     *
     * The navigator is hosted by the Launcher's task view; only the bounds of the root task
     * (used for other launched apps) follow the mode.
     */
    private void showNavigationFullscreen() {

        Log.i(TAG, "showNavigationFullscreen: taskViewReady=" + (mTaskView != null));
        mNavigationFullscreen = true;

        if (mTaskView == null) {
            Log.w(
                    TAG,
                    "Cannot enter fullscreen navigation: "
                            + "TaskView is not ready");
            return;
        }

        applyFullscreenBounds();
    }

    /**
     * Switch back to normal HOME mode.
     */
    private void showHomeMode() {

        Log.i(TAG, "showHomeMode: taskViewReady=" + (mTaskView != null));
        mNavigationFullscreen = false;

        if (mTaskView == null) {
            Log.w(TAG, "Cannot enter home mode: TaskView is not ready");
            return;
        }

        applyHomeBounds();
    }

    /**
     * Makes the embedded task occupy the entire display.
     */
    private void applyFullscreenBounds() {

        if (mTaskView == null) {
            return;
        }

        int width = mLayout.getWidth();
        int height = mLayout.getHeight();

        /*
         * During very early startup the layout can still report zero.
         * Use the display metrics as fallback.
         */
        if (width <= 0) {
            width = mContext.getResources()
                    .getDisplayMetrics()
                    .widthPixels;
        }

        if (height <= 0) {
            height = mContext.getResources()
                    .getDisplayMetrics()
                    .heightPixels;
        }

        Rect bounds = new Rect(
                0,
                0,
                width,
                height);

        Log.i(TAG, "applyFullscreenBounds: " + bounds);

        /*
         * RemoteCarTaskView.setWindowBounds() expects
         * screen coordinates.
         */
        mTaskView.setWindowBounds(bounds);

        /*
         * Ensure the embedded task is visible.
         *
         * We intentionally do not reorder the root task here.
         * The launch-root/task transition machinery is responsible
         * for task ordering.
         */
        mTaskView.showEmbeddedTask();
    }

    /**
     * Makes the embedded task occupy the normal application/content
     * area between the Top and Bottom SystemUI windows.
     */
    private void applyHomeBounds() {

        if (mTaskView == null) {
            return;
        }

        int width = mLayout.getWidth();
        int height = mLayout.getHeight();

        /*
         * During very early startup the layout can still report zero.
         * Use the display metrics as fallback.
         */
        if (width <= 0) {
            width = mContext.getResources()
                    .getDisplayMetrics()
                    .widthPixels;
        }

        if (height <= 0) {
            height = mContext.getResources()
                    .getDisplayMetrics()
                    .heightPixels;
        }

        /*
         * Normal application/content area:
         *
         * top    = below Top SystemUI
         * bottom = above Bottom SystemUI
         */
        int top = mTopInset;
        int bottom = height - mBottomInset;

        /*
         * Safety fallback if insets have not arrived yet.
         *
         * Our current display is 1024x600 with:
         *
         *   Top    = 76
         *   Bottom = 96
         *
         * But we do NOT hard-code those values.
         */
        if (bottom <= top) {
            top = 0;
            bottom = height;
        }

        Rect bounds = new Rect(
                0,
                top,
                width,
                bottom);

        Log.d(
                TAG,
                "Applying HOME bounds: "
                        + bounds
                        + " topInset="
                        + mTopInset
                        + " bottomInset="
                        + mBottomInset);

        Log.i(TAG, "applyHomeBounds: " + bounds);

        mTaskView.setWindowBounds(bounds);
        mTaskView.showEmbeddedTask();
    }
}